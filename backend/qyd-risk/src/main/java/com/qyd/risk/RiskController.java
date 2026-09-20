package com.qyd.risk;

import com.qyd.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/risk")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR')")
public class RiskController {
    private final RiskService service;public RiskController(RiskService service){this.service=service;}
    @GetMapping("/blacklists")List<BlacklistDto> blacklists(){return service.blacklists();}
    @PostMapping("/blacklists")@ResponseStatus(HttpStatus.CREATED)BlacklistDto createBlacklist(@Valid @RequestBody BlacklistRequest r,Authentication a){return service.createBlacklist(r,a.getName());}
    @PutMapping("/blacklists/{id}")BlacklistDto updateBlacklist(@PathVariable String id,@Valid @RequestBody BlacklistRequest r,Authentication a){return service.updateBlacklist(id,r,a.getName());}
    @DeleteMapping("/blacklists/{id}")@ResponseStatus(HttpStatus.NO_CONTENT)void deleteBlacklist(@PathVariable String id,Authentication a){service.deleteBlacklist(id,a.getName());}
    @GetMapping("/rules")List<RuleDto> rules(){return service.rules();}
    @PostMapping("/rules")@ResponseStatus(HttpStatus.CREATED)RuleDto createRule(@Valid @RequestBody RuleRequest r,Authentication a){return service.createRule(r,a.getName());}
    @PutMapping("/rules/{id}")RuleDto updateRule(@PathVariable String id,@Valid @RequestBody RuleRequest r,Authentication a){return service.updateRule(id,r,a.getName());}
    @DeleteMapping("/rules/{id}")@ResponseStatus(HttpStatus.NO_CONTENT)void deleteRule(@PathVariable String id,Authentication a){service.deleteRule(id,a.getName());}
    @PostMapping("/orders/{orderId}/check")CheckDto check(@PathVariable String orderId,Authentication a){return service.check(orderId,a.getName());}
    @GetMapping("/checks")List<CheckDto> checks(){return service.checks();}
    @GetMapping("/audit")@PreAuthorize("hasRole('PLATFORM_ADMIN')")List<AuditDto> audit(){return service.audit();}

    public record BlacklistRequest(@NotBlank @Size(max=30)String subjectType,@NotBlank @Size(max=150)String subjectValue,
            @NotBlank @Size(max=500)String reason,boolean active,Instant expiresAt){}
    public record BlacklistDto(String id,String subjectType,String subjectValue,String reason,boolean active,Instant expiresAt){
        static BlacklistDto of(RiskBlacklist x){return new BlacklistDto(x.getId(),x.subjectType,x.subjectValue,x.reason,x.active,x.expiresAt);}}
    public record RuleRequest(@NotBlank @Size(max=120)String name,@NotBlank @Size(max=60)String code,
            @NotBlank @Size(max=1000)String expression,@NotNull RiskLevel level,boolean enabled){}
    public record RuleDto(String id,String name,String code,String expression,String level,boolean enabled){
        static RuleDto of(RiskRule x){return new RuleDto(x.getId(),x.name,x.code,x.expression,x.level.name(),x.enabled);}}
    public record CheckDto(String id,String orderId,String userId,String decision,String matchedRules,String detail,Instant createdAt){
        static CheckDto of(OrderRiskCheck x){return new CheckDto(x.getId(),x.orderId,x.userId,x.decision.name(),x.matchedRules,x.detail,x.getCreatedAt());}}
    public record AuditDto(String id,String actorId,String action,String resourceType,String resourceId,String detail,Instant createdAt){
        static AuditDto of(AuditLog x){return new AuditDto(x.getId(),x.actorId,x.action,x.resourceType,x.resourceId,x.detail,x.getCreatedAt());}}
}

enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
enum RiskDecision { PASS, REVIEW, REJECT }

@Service @Transactional
class RiskService {
    private final BlacklistRepository blacklists;private final RuleRepository rules;private final CheckRepository checks;private final AuditRepository audits;
    RiskService(BlacklistRepository blacklists,RuleRepository rules,CheckRepository checks,AuditRepository audits){this.blacklists=blacklists;this.rules=rules;this.checks=checks;this.audits=audits;}
    @Transactional(readOnly=true)List<RiskController.BlacklistDto>blacklists(){return blacklists.findAll(Sort.by(Sort.Direction.DESC,"createdAt")).stream().map(RiskController.BlacklistDto::of).toList();}
    RiskController.BlacklistDto createBlacklist(RiskController.BlacklistRequest r,String actor){RiskBlacklist x=new RiskBlacklist();x.update(r);blacklists.save(x);audit(actor,"CREATE","BLACKLIST",x.getId(),r.subjectType()+":"+r.subjectValue());return RiskController.BlacklistDto.of(x);}
    RiskController.BlacklistDto updateBlacklist(String id,RiskController.BlacklistRequest r,String actor){RiskBlacklist x=requireBlacklist(id);x.update(r);audit(actor,"UPDATE","BLACKLIST",id,r.subjectType()+":"+r.subjectValue());return RiskController.BlacklistDto.of(x);}
    void deleteBlacklist(String id,String actor){blacklists.delete(requireBlacklist(id));audit(actor,"DELETE","BLACKLIST",id,null);}
    @Transactional(readOnly=true)List<RiskController.RuleDto>rules(){return rules.findAll(Sort.by("code")).stream().map(RiskController.RuleDto::of).toList();}
    RiskController.RuleDto createRule(RiskController.RuleRequest r,String actor){RiskRule x=new RiskRule();x.update(r);rules.save(x);audit(actor,"CREATE","RISK_RULE",x.getId(),r.code());return RiskController.RuleDto.of(x);}
    RiskController.RuleDto updateRule(String id,RiskController.RuleRequest r,String actor){RiskRule x=requireRule(id);x.update(r);audit(actor,"UPDATE","RISK_RULE",id,r.code());return RiskController.RuleDto.of(x);}
    void deleteRule(String id,String actor){rules.delete(requireRule(id));audit(actor,"DELETE","RISK_RULE",id,null);}
    RiskController.CheckDto check(String orderId,String actor){
        OrderRiskData order=checks.orderData(orderId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"order not found"));
        List<String>matched=new ArrayList<>();Instant now=Instant.now();
        if(blacklists.existsActive("USER_ID",order.getUserId(),now))matched.add("BLACKLIST_USER");
        RiskLevel highest=RiskLevel.LOW;
        for(RiskRule rule:rules.findAllByEnabledTrue()){
            if(matches(rule.expression,order.getAmount())){matched.add(rule.code);if(rule.level.ordinal()>highest.ordinal())highest=rule.level;}
        }
        RiskDecision decision=matched.isEmpty()?RiskDecision.PASS:(highest.ordinal()>=RiskLevel.HIGH.ordinal()?RiskDecision.REJECT:RiskDecision.REVIEW);
        OrderRiskCheck x=new OrderRiskCheck();x.orderId=orderId;x.userId=order.getUserId();x.decision=decision;x.matchedRules=String.join(",",matched);
        x.detail="amount="+order.getAmount();checks.save(x);audit(actor,"CHECK","ORDER",orderId,decision.name());return RiskController.CheckDto.of(x);
    }
    @Transactional(readOnly=true)List<RiskController.CheckDto>checks(){return checks.findAll(Sort.by(Sort.Direction.DESC,"createdAt")).stream().map(RiskController.CheckDto::of).toList();}
    @Transactional(readOnly=true)List<RiskController.AuditDto>audit(){return audits.findAll(Sort.by(Sort.Direction.DESC,"createdAt")).stream().map(RiskController.AuditDto::of).toList();}
    private boolean matches(String expression,BigDecimal amount){
        String normalized=expression.replace(" ","").toLowerCase(Locale.ROOT);
        try{
            if(normalized.startsWith("amount>="))return amount.compareTo(new BigDecimal(normalized.substring(8)))>=0;
            if(normalized.startsWith("amount>"))return amount.compareTo(new BigDecimal(normalized.substring(7)))>0;
        }catch(NumberFormatException ignored){}
        return false;
    }
    private void audit(String actor,String action,String type,String id,String detail){audits.save(new AuditLog(actor,action,type,id,detail));}
    private RiskBlacklist requireBlacklist(String id){return blacklists.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"blacklist not found"));}
    private RiskRule requireRule(String id){return rules.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"risk rule not found"));}
}

@Entity @Table(name="risk_blacklist",uniqueConstraints=@UniqueConstraint(name="uk_blacklist_subject",columnNames={"subject_type","subject_value"}))
class RiskBlacklist extends BaseEntity {
    @Column(nullable=false,length=30)String subjectType;@Column(nullable=false,length=150)String subjectValue;@Column(nullable=false,length=500)String reason;
    @Column(nullable=false)boolean active;Instant expiresAt;protected RiskBlacklist(){}
    void update(RiskController.BlacklistRequest r){subjectType=r.subjectType().toUpperCase(Locale.ROOT);subjectValue=r.subjectValue();reason=r.reason();active=r.active();expiresAt=r.expiresAt();}
}
@Entity @Table(name="risk_rule")
class RiskRule extends BaseEntity {
    @Column(nullable=false,length=120)String name;@Column(nullable=false,unique=true,length=60)String code;@Column(nullable=false,length=1000)String expression;
    @Enumerated(EnumType.STRING)@Column(nullable=false,length=20)RiskLevel level;@Column(nullable=false)boolean enabled;protected RiskRule(){}
    void update(RiskController.RuleRequest r){name=r.name();code=r.code().toUpperCase(Locale.ROOT);expression=r.expression();level=r.level();enabled=r.enabled();}
}
@Entity @Table(name="order_risk_check")
class OrderRiskCheck extends BaseEntity {
    @Column(nullable=false,length=36)String orderId;@Column(nullable=false,length=36)String userId;
    @Enumerated(EnumType.STRING)@Column(nullable=false,length=20)RiskDecision decision;@Column(length=1000)String matchedRules;@Column(length=1000)String detail;protected OrderRiskCheck(){}
}
@Entity @Table(name="audit_log")
class AuditLog extends BaseEntity {
    @Column(nullable=false,length=36)String actorId;@Column(nullable=false,length=80)String action;@Column(nullable=false,length=60)String resourceType;
    @Column(length=36)String resourceId;@Column(length=1000)String detail;protected AuditLog(){}
    AuditLog(String actor,String action,String type,String id,String detail){actorId=actor;this.action=action;resourceType=type;resourceId=id;this.detail=detail;}
}
interface BlacklistRepository extends JpaRepository<RiskBlacklist,String>{
    @org.springframework.data.jpa.repository.Query("select (count(b)>0) from RiskBlacklist b where b.subjectType=:type and b.subjectValue=:value and b.active=true and (b.expiresAt is null or b.expiresAt>:now)")
    boolean existsActive(@Param("type")String type,@Param("value")String value,@Param("now")Instant now);
}
interface RuleRepository extends JpaRepository<RiskRule,String>{List<RiskRule>findAllByEnabledTrue();}
interface OrderRiskData {String getUserId();BigDecimal getAmount();}
interface CheckRepository extends JpaRepository<OrderRiskCheck,String>{
    @org.springframework.data.jpa.repository.Query(value="select user_id userId,amount from customer_order where id=:orderId",nativeQuery=true)Optional<OrderRiskData>orderData(@Param("orderId")String orderId);
}
interface AuditRepository extends JpaRepository<AuditLog,String>{}
