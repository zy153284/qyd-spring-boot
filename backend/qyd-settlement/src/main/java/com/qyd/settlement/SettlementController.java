package com.qyd.settlement;

import com.qyd.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class SettlementController {
    private final SettlementService service;private final DashboardService dashboard;
    public SettlementController(SettlementService service,DashboardService dashboard){this.service=service;this.dashboard=dashboard;}

    @GetMapping("/settlements")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','FINANCE')")
    List<StatementDto> list(){return service.list();}
    @PostMapping("/settlements/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','FINANCE')")
    StatementDto generate(@RequestHeader("Idempotency-Key")@NotBlank String key,@Valid @RequestBody GenerateRequest r){
        return service.generate(key,r);
    }
    @PostMapping("/settlements/{id}/transition")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','FINANCE')")
    StatementDto transition(@PathVariable String id,@Valid @RequestBody TransitionRequest request){
        return service.transition(id,request.status());
    }
    @PostMapping("/settlements/adjustments/sync")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','FINANCE')")
    int syncAdjustments(){return service.syncAdjustments();}
    @GetMapping("/settlements/adjustments")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','FINANCE')")
    List<AdjustmentDto> adjustments(){return service.adjustments();}
    @GetMapping("/dashboard/statistics")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR','FINANCE')")
    DashboardDto statistics(){return dashboard.statistics();}

    public record GenerateRequest(@NotBlank String venueId,@NotNull Instant periodStart,@NotNull Instant periodEnd){}
    public record TransitionRequest(@NotNull SettlementStatus status){}
    public record StatementDto(String id,String statementNo,String venueId,Instant periodStart,Instant periodEnd,
            BigDecimal grossAmount,BigDecimal adjustmentAmount,BigDecimal netAmount,String currency,String status,
            int itemCount){static StatementDto of(SettlementBatch s,int count){return new StatementDto(s.getId(),s.statementNo,
            s.venueId,s.periodStart,s.periodEnd,s.grossAmount,s.adjustmentAmount,s.netAmount,s.currency,s.status.name(),count);}}
    public record AdjustmentDto(String id,String statementId,String orderId,String refundId,BigDecimal amount,String reason,Instant createdAt){
        static AdjustmentDto of(SettlementAdjustment a){return new AdjustmentDto(a.getId(),a.statementId,a.orderId,a.refundId,a.amount,a.reason,a.getCreatedAt());}}
    public record DashboardDto(long venues,long orders,long paidOrders,BigDecimal orderAmount,long payments,
            BigDecimal paidAmount,long refunds,BigDecimal refundAmount,long settlements,BigDecimal settlementAmount){}
}

enum SettlementStatus { DRAFT, CONFIRMED, FROZEN, PAYING, PAID, COMPLETED }

@Service @Transactional
class SettlementService {
    private final EligibilityRepository eligibility;private final StatementRepository statements;
    private final AdjustmentRepository adjustments;private final JdbcTemplate jdbc;
    SettlementService(EligibilityRepository eligibility,StatementRepository statements,AdjustmentRepository adjustments,JdbcTemplate jdbc){
        this.eligibility=eligibility;this.statements=statements;this.adjustments=adjustments;this.jdbc=jdbc;
    }
    @Transactional(readOnly=true)
    List<SettlementController.StatementDto> list(){return statements.findAll(Sort.by(Sort.Direction.DESC,"createdAt")).stream()
            .map(s->SettlementController.StatementDto.of(s,eligibility.countByStatementId(s.getId()))).toList();}
    @Transactional(readOnly=true)
    List<SettlementController.AdjustmentDto> adjustments(){return adjustments.findAll(Sort.by(Sort.Direction.DESC,"createdAt")).stream().map(SettlementController.AdjustmentDto::of).toList();}
    SettlementController.StatementDto generate(String key,SettlementController.GenerateRequest r){
        if(!r.periodEnd().isAfter(r.periodStart()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"periodEnd must be after periodStart");
        Optional<SettlementBatch> prior=statements.findByIdempotencyKey(key);
        if(prior.isPresent())return SettlementController.StatementDto.of(prior.get(),eligibility.countByStatementId(prior.get().getId()));
        syncEligibility();
        syncAdjustments();
        List<SettlementEligibility> items=eligibility.findEligible(r.venueId(),r.periodStart(),r.periodEnd());
        if(items.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"no eligible fulfilled paid orders");
        Set<String> currencies=new HashSet<>();BigDecimal gross=BigDecimal.ZERO;
        for(SettlementEligibility item:items){currencies.add(item.currency);gross=gross.add(item.eligibleAmount);}
        if(currencies.size()!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"mixed currencies");
        List<SettlementAdjustment> pending=adjustments.findPendingForVenue(r.venueId());
        BigDecimal adjustment=pending.stream().map(x->x.amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        SettlementBatch batch=new SettlementBatch();batch.statementNo="S"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase();
        batch.venueId=r.venueId();batch.periodStart=r.periodStart();batch.periodEnd=r.periodEnd();batch.grossAmount=gross;
        batch.adjustmentAmount=adjustment;batch.netAmount=gross.add(adjustment);batch.currency=currencies.iterator().next();
        batch.status=SettlementStatus.DRAFT;batch.idempotencyKey=key;statements.save(batch);
        items.forEach(x->x.statementId=batch.getId());pending.forEach(x->x.statementId=batch.getId());
        return SettlementController.StatementDto.of(batch,items.size());
    }
    SettlementController.StatementDto transition(String id,SettlementStatus target){
        SettlementBatch batch=statements.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"settlement not found"));
        SettlementStatus[] flow=SettlementStatus.values();
        if(target.ordinal()!=batch.status.ordinal()+1)throw new ResponseStatusException(HttpStatus.CONFLICT,"settlement status must advance one step");
        batch.status=target;return SettlementController.StatementDto.of(batch,eligibility.countByStatementId(id));
    }
    int syncAdjustments(){
        List<Map<String,Object>> rows=jdbc.queryForList("""
            SELECT r.id refund_id,p.order_id,r.amount,r.reason
            FROM payment_refund r JOIN payment_order p ON p.id=r.payment_id
            WHERE r.status='SUCCEEDED' AND NOT EXISTS(SELECT 1 FROM settlement_adjustment a WHERE a.refund_id=r.id)
            """);
        int created=0;
        for(Map<String,Object> row:rows){
            String orderId=String.valueOf(row.get("order_id"));
            if(eligibility.findFirstByOrderId(orderId).isEmpty())continue;
            SettlementAdjustment a=new SettlementAdjustment();a.orderId=orderId;a.refundId=String.valueOf(row.get("refund_id"));
            a.amount=((BigDecimal)row.get("amount")).negate();a.reason=(String)row.get("reason");adjustments.save(a);created++;
        }
        return created;
    }
    private int syncEligibility(){
        List<Map<String,Object>> rows=jdbc.queryForList("""
            SELECT o.id order_id,sr.venue_id,SUM(oi.amount) eligible_amount,o.currency,o.updated_at fulfilled_at
            FROM customer_order o JOIN order_item oi ON oi.order_id=o.id
            JOIN product_sku sku ON sku.id=oi.sku_id JOIN sport_resource sr ON sr.id=sku.resource_id
            WHERE o.status='COMPLETED' AND EXISTS(SELECT 1 FROM payment_order p WHERE p.order_id=o.id AND p.status='SUCCEEDED')
              AND NOT EXISTS(SELECT 1 FROM settlement_eligibility e WHERE e.order_id=o.id AND e.venue_id=sr.venue_id)
            GROUP BY o.id,sr.venue_id,o.currency,o.updated_at
            """);
        rows.forEach(row->{SettlementEligibility e=new SettlementEligibility();e.orderId=String.valueOf(row.get("order_id"));
            e.venueId=String.valueOf(row.get("venue_id"));e.eligibleAmount=(BigDecimal)row.get("eligible_amount");
            e.currency=String.valueOf(row.get("currency"));e.fulfilledAt=((java.sql.Timestamp)row.get("fulfilled_at")).toInstant();eligibility.save(e);});
        return rows.size();
    }
}

@Service
class DashboardService {
    private final JdbcTemplate jdbc;DashboardService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(readOnly=true)
    SettlementController.DashboardDto statistics(){
        long venues=count("SELECT COUNT(*) FROM venue");
        long orders=count("SELECT COUNT(*) FROM customer_order");
        long paidOrders=count("SELECT COUNT(*) FROM customer_order WHERE status IN ('PAID','COMPLETED','REFUNDED')");
        BigDecimal orderAmount=amount("SELECT COALESCE(SUM(amount),0) FROM customer_order");
        long payments=count("SELECT COUNT(*) FROM payment_order");
        BigDecimal paidAmount=amount("SELECT COALESCE(SUM(amount),0) FROM payment_order WHERE status='SUCCEEDED'");
        long refunds=count("SELECT COUNT(*) FROM payment_refund WHERE status='SUCCEEDED'");
        BigDecimal refundAmount=amount("SELECT COALESCE(SUM(amount),0) FROM payment_refund WHERE status='SUCCEEDED'");
        long settlements=count("SELECT COUNT(*) FROM settlement_batch");
        BigDecimal settlementAmount=amount("SELECT COALESCE(SUM(net_amount),0) FROM settlement_batch");
        return new SettlementController.DashboardDto(venues,orders,paidOrders,orderAmount,payments,paidAmount,refunds,refundAmount,settlements,settlementAmount);
    }
    private long count(String sql){Number value=jdbc.queryForObject(sql,Number.class);return value==null?0:value.longValue();}
    private BigDecimal amount(String sql){BigDecimal value=jdbc.queryForObject(sql,BigDecimal.class);return value==null?BigDecimal.ZERO:value;}
}

@Entity @Table(name="settlement_eligibility",uniqueConstraints=@UniqueConstraint(name="uk_eligibility_order_venue",columnNames={"order_id","venue_id"}))
class SettlementEligibility extends BaseEntity {
    @Column(nullable=false,length=36)String orderId;@Column(nullable=false,length=36)String venueId;
    @Column(nullable=false,precision=19,scale=2)BigDecimal eligibleAmount;@Column(nullable=false,length=3)String currency;
    @Column(nullable=false)Instant fulfilledAt;@Column(length=36)String statementId;protected SettlementEligibility(){}
}
@Entity @Table(name="settlement_batch")
class SettlementBatch extends BaseEntity {
    @Column(nullable=false,unique=true,length=40)String statementNo;@Column(nullable=false,length=36)String venueId;
    @Column(nullable=false)Instant periodStart;@Column(nullable=false)Instant periodEnd;
    @Column(nullable=false,precision=19,scale=2)BigDecimal grossAmount;@Column(nullable=false,precision=19,scale=2)BigDecimal adjustmentAmount;
    @Column(nullable=false,precision=19,scale=2)BigDecimal netAmount;@Column(nullable=false,length=3)String currency;
    @Enumerated(EnumType.STRING)@Column(nullable=false,length=20)SettlementStatus status;
    @Column(nullable=false,unique=true,length=100)String idempotencyKey;protected SettlementBatch(){}
}
@Entity @Table(name="settlement_adjustment")
class SettlementAdjustment extends BaseEntity {
    @Column(length=36)String statementId;@Column(nullable=false,length=36)String orderId;
    @Column(nullable=false,unique=true,length=36)String refundId;@Column(nullable=false,precision=19,scale=2)BigDecimal amount;
    @Column(length=255)String reason;protected SettlementAdjustment(){}
}
interface EligibilityRepository extends JpaRepository<SettlementEligibility,String>{
    @org.springframework.data.jpa.repository.Query("select e from SettlementEligibility e where e.venueId=:venueId and e.statementId is null and e.fulfilledAt>=:start and e.fulfilledAt<:end order by e.fulfilledAt")
    List<SettlementEligibility>findEligible(@Param("venueId")String venueId,@Param("start")Instant start,@Param("end")Instant end);
    int countByStatementId(String statementId);Optional<SettlementEligibility>findFirstByOrderId(String orderId);
}
interface StatementRepository extends JpaRepository<SettlementBatch,String>{Optional<SettlementBatch>findByIdempotencyKey(String key);}
interface AdjustmentRepository extends JpaRepository<SettlementAdjustment,String>{
    @org.springframework.data.jpa.repository.Query(value="select a.* from settlement_adjustment a join settlement_eligibility e on e.order_id=a.order_id where a.statement_id is null and e.venue_id=:venueId",nativeQuery=true)
    List<SettlementAdjustment>findPendingForVenue(@Param("venueId")String venueId);
}
