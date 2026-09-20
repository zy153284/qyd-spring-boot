package com.qyd.product;

import com.qyd.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class InventoryService {
    private final SlotInventoryRepository inventory;
    private final InventoryReservationRepository reservations;
    private final SkuRepository skus;
    public InventoryService(SlotInventoryRepository i, InventoryReservationRepository r, SkuRepository s){inventory=i;reservations=r;skus=s;}

    @Transactional
    public ReservationResult reserve(String slotId, String orderId, String orderItemId, int quantity, Instant expiresAt) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        var existing=reservations.findByOrderItemId(orderItemId);
        if(existing.isPresent()) return ReservationResult.of(existing.get());
        if(inventory.reserve(slotId,quantity)!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"insufficient inventory");
        InventoryReservation r=new InventoryReservation();
        r.slotId=slotId;r.orderId=orderId;r.orderItemId=orderItemId;
        r.quantity=quantity;r.status=ReservationStatus.RESERVED;r.expiresAt=expiresAt;
        return ReservationResult.of(reservations.save(r));
    }
    @Transactional
    public void confirmOrder(String orderId) {
        var orderReservations=reservations.findAllByOrderId(orderId);
        if(orderReservations.isEmpty()||orderReservations.stream().anyMatch(r->r.status!=ReservationStatus.RESERVED))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"inventory reservation expired or unavailable");
        for(var r:orderReservations){
            if(inventory.confirm(r.slotId,r.quantity)!=1) throw new IllegalStateException("inventory confirmation failed");
            r.status=ReservationStatus.CONFIRMED;
        }
    }
    @Transactional
    public void releaseOrder(String orderId) {
        for(var r:reservations.findAllByOrderIdAndStatus(orderId,ReservationStatus.RESERVED)){
            if(inventory.release(r.slotId,r.quantity)!=1) throw new IllegalStateException("inventory release failed");
            r.status=ReservationStatus.RELEASED;
        }
    }
    @Scheduled(fixedDelayString="${qyd.inventory.expiry-interval:PT30S}")
    @Transactional
    public void releaseExpiredReservations() {
        var now=Instant.now();
        for(var r:reservations.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAt(ReservationStatus.RESERVED,now)){
            if(inventory.release(r.slotId,r.quantity)!=1) throw new IllegalStateException("expired inventory release failed");
            r.status=ReservationStatus.EXPIRED;
        }
    }
    @Transactional(readOnly=true)
    public SkuSnapshot sku(String skuId){
        ProductSku s=skus.findById(skuId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"sku not found"));
        if(!s.active)throw new ResponseStatusException(HttpStatus.CONFLICT,"sku inactive");
        return new SkuSnapshot(s.getId(),s.name,s.price,s.currency);
    }
    public record ReservationResult(String id,String slotId,int quantity,String status){static ReservationResult of(InventoryReservation r){return new ReservationResult(r.getId(),r.slotId,r.quantity,r.status.name());}}
    public record SkuSnapshot(String id,String name,BigDecimal price,String currency){}
}

@RestController
@RequestMapping("/api/v1/inventory/slots")
class InventoryController {
    private final SlotInventoryRepository repository;
    InventoryController(SlotInventoryRepository repository){this.repository=repository;}
    @PostMapping @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR','VENUE_ADMIN','VENUE_STAFF')") SlotDto create(@Valid @RequestBody SlotRequest r){
        if(!r.endsAt().isAfter(r.startsAt()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"endsAt must be after startsAt");
        SlotInventory e=new SlotInventory();e.skuId=r.skuId();e.startsAt=r.startsAt();e.endsAt=r.endsAt();e.capacity=r.capacity();
        return SlotDto.of(repository.save(e));
    }
    @GetMapping List<SlotDto> list(@RequestParam(required=false)String skuId){return(skuId==null?repository.findAll():repository.findAllBySkuIdAndStartsAtAfterOrderByStartsAt(skuId,Instant.EPOCH)).stream().map(SlotDto::of).toList();}
    @GetMapping("/{id}") SlotDto get(@PathVariable String id){return SlotDto.of(repository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"slot not found")));}
    record SlotRequest(@NotBlank String skuId,@NotNull Instant startsAt,@NotNull Instant endsAt,@Min(1)int capacity){}
    record SlotDto(String id,String skuId,Instant startsAt,Instant endsAt,int capacity,int reserved,int sold,long version){static SlotDto of(SlotInventory e){return new SlotDto(e.getId(),e.skuId,e.startsAt,e.endsAt,e.capacity,e.reserved,e.sold,e.getVersion());}}
}

@Entity @Table(name="slot_inventory",uniqueConstraints=@UniqueConstraint(name="uk_slot_sku_time",columnNames={"sku_id","starts_at","ends_at"}))
class SlotInventory extends BaseEntity {
    @Column(nullable=false,length=36)String skuId; @Column(nullable=false)Instant startsAt; @Column(nullable=false)Instant endsAt;
    @Column(nullable=false)int capacity; @Column(nullable=false)int reserved; @Column(nullable=false)int sold;
    protected SlotInventory(){}
}
enum ReservationStatus{RESERVED,CONFIRMED,RELEASED,EXPIRED}
@Entity @Table(name="inventory_reservation")
class InventoryReservation extends BaseEntity{
    @Column(nullable=false,length=36)String slotId;@Column(nullable=false,length=36)String orderId;@Column(nullable=false,length=36,unique=true)String orderItemId;
    @Column(nullable=false)int quantity;@Enumerated(EnumType.STRING)@Column(nullable=false,length=20)ReservationStatus status;@Column(nullable=false)Instant expiresAt;
    protected InventoryReservation(){}
}
interface SlotInventoryRepository extends JpaRepository<SlotInventory,String>{
    List<SlotInventory> findAllBySkuIdAndStartsAtAfterOrderByStartsAt(String skuId,Instant after);
    @Modifying @org.springframework.data.jpa.repository.Query(value="UPDATE slot_inventory SET reserved=reserved+:qty, version=version+1 WHERE id=:id AND capacity-reserved-sold>=:qty",nativeQuery=true)
    int reserve(@Param("id")String id,@Param("qty")int qty);
    @Modifying @org.springframework.data.jpa.repository.Query(value="UPDATE slot_inventory SET reserved=reserved-:qty, sold=sold+:qty, version=version+1 WHERE id=:id AND reserved>=:qty",nativeQuery=true)
    int confirm(@Param("id")String id,@Param("qty")int qty);
    @Modifying @org.springframework.data.jpa.repository.Query(value="UPDATE slot_inventory SET reserved=reserved-:qty, version=version+1 WHERE id=:id AND reserved>=:qty",nativeQuery=true)
    int release(@Param("id")String id,@Param("qty")int qty);
}
interface InventoryReservationRepository extends JpaRepository<InventoryReservation,String>{
    java.util.Optional<InventoryReservation> findByOrderItemId(String orderItemId);
    List<InventoryReservation> findAllByOrderIdAndStatus(String orderId,ReservationStatus status);
    List<InventoryReservation> findAllByOrderId(String orderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<InventoryReservation> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAt(ReservationStatus status,Instant before);
}
