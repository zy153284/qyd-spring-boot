package com.qyd.order;

import com.qyd.product.InventoryService;
import com.qyd.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class OrderApplicationService {
    private final OrderRepository orders; private final OrderItemRepository orderItems; private final InventoryService inventory; private final RedemptionRepository redemptions;
    public OrderApplicationService(OrderRepository o,OrderItemRepository oi,InventoryService i,RedemptionRepository r){orders=o;orderItems=oi;inventory=i;redemptions=r;}

    @Transactional
    public OrderView create(String userId,String key,CreateOrder command){
        if(key==null||key.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key required");
        var prior=orders.findByUserIdAndIdempotencyKey(userId,key);if(prior.isPresent())return view(prior.get());
        CustomerOrder order=new CustomerOrder();order.orderNo="O"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        order.userId=userId;order.idempotencyKey=key;order.status=OrderStatus.PENDING_PAYMENT;order.currency="CNY";order.amount=BigDecimal.ZERO;
        orders.save(order);
        for(CreateItem c:command.items()){
            var sku=inventory.sku(c.skuId()); if(!order.currency.equals(sku.currency()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"mixed currency");
            OrderItem item=new OrderItem();item.order=order;item.skuId=c.skuId();item.slotId=c.slotId();item.skuName=sku.name();item.quantity=c.quantity();item.unitPrice=sku.price();item.amount=sku.price().multiply(BigDecimal.valueOf(c.quantity()));
            order.items.add(item);orderItems.save(item);order.amount=order.amount.add(item.amount);
            inventory.reserve(c.slotId(),order.getId(),item.getId(),c.quantity(),Instant.now().plus(15,ChronoUnit.MINUTES));
        }
        history(order,null,OrderStatus.PENDING_PAYMENT,"order created");
        return view(orders.save(order));
    }
    @Transactional
    public OrderView cancel(String orderId,String userId){
        CustomerOrder o=require(orderId);if(!o.userId.equals(userId))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(o.status==OrderStatus.CANCELLED)return view(o);
        if(o.status!=OrderStatus.PENDING_PAYMENT)throw new ResponseStatusException(HttpStatus.CONFLICT,"order cannot be cancelled");
        inventory.releaseOrder(o.getId());transition(o,OrderStatus.CANCELLED,"cancelled by user");return view(o);
    }
    @Transactional
    public String markPaid(String orderId){
        CustomerOrder o=require(orderId);if(o.status==OrderStatus.PAID)return "";
        if(o.status!=OrderStatus.PENDING_PAYMENT)throw new ResponseStatusException(HttpStatus.CONFLICT,"order is not payable");
        inventory.confirmOrder(orderId);transition(o,OrderStatus.PAID,"payment succeeded");
        String code=UUID.randomUUID().toString().replace("-","").toUpperCase();
        RedemptionCode r=new RedemptionCode();r.orderId=orderId;r.codeHash=sha256(code);r.status=RedemptionStatus.UNUSED;redemptions.save(r);
        return code;
    }
    @Transactional(readOnly=true) public OrderSnapshot snapshot(String orderId){CustomerOrder o=require(orderId);return new OrderSnapshot(o.getId(),o.orderNo,o.userId,o.amount,o.currency,o.status.name());}
    @Transactional public void markRefunded(String orderId){
        CustomerOrder o=require(orderId);
        if(o.status==OrderStatus.REFUNDED)return;
        if(o.status!=OrderStatus.PAID)throw new ResponseStatusException(HttpStatus.CONFLICT,"order is not refundable");
        transition(o,OrderStatus.REFUNDED,"payment fully refunded");
    }
    @Transactional(readOnly=true) public OrderView get(String id,String userId){CustomerOrder o=require(id);if(!o.userId.equals(userId))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return view(o);}
    @Transactional(readOnly=true) public Page<OrderView> list(String userId,Pageable p){return orders.findAllByUserIdOrderByCreatedAtDesc(userId,p).map(this::view);}
    @Transactional public void redeem(String code){
        RedemptionCode r=redemptions.findByCodeHash(sha256(code)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"invalid redemption code"));
        if(redemptions.markUsed(r.getId(),Instant.now())!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"code already redeemed");
        CustomerOrder o=require(r.orderId);transition(o,OrderStatus.COMPLETED,"service redeemed");
    }
    private CustomerOrder require(String id){return orders.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"order not found"));}
    private void transition(CustomerOrder o,OrderStatus to,String reason){OrderStatus from=o.status;o.status=to;history(o,from,to,reason);}
    private void history(CustomerOrder o,OrderStatus from,OrderStatus to,String reason){OrderStatusHistory h=new OrderStatusHistory();h.order=o;h.fromStatus=from;h.toStatus=to;h.reason=reason;o.history.add(h);}
    private OrderView view(CustomerOrder o){return new OrderView(o.getId(),o.orderNo,o.amount,o.currency,o.status.name(),o.items.stream().map(i->new ItemView(i.getId(),i.skuId,i.slotId,i.skuName,i.quantity,i.unitPrice,i.amount)).toList(),o.history.stream().map(h->new HistoryView(h.fromStatus==null?null:h.fromStatus.name(),h.toStatus.name(),h.reason,h.getCreatedAt())).toList());}
    private static String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record CreateOrder(@NotEmpty List<@Valid CreateItem> items){}
    public record CreateItem(@NotBlank String skuId,@NotBlank String slotId,@Min(1)int quantity){}
    public record OrderSnapshot(String id,String orderNo,String userId,BigDecimal amount,String currency,String status){}
    public record OrderView(String id,String orderNo,BigDecimal amount,String currency,String status,List<ItemView> items,List<HistoryView> history){}
    public record ItemView(String id,String skuId,String slotId,String name,int quantity,BigDecimal unitPrice,BigDecimal amount){}
    public record HistoryView(String from,String to,String reason,Instant at){}
}

@RestController @RequestMapping("/api/v1/orders")
class OrderController{
    private final OrderApplicationService service;OrderController(OrderApplicationService s){service=s;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) OrderApplicationService.OrderView create(@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody OrderApplicationService.CreateOrder r,Authentication a){return service.create(a.getName(),key,r);}
    @GetMapping("/{id}") OrderApplicationService.OrderView get(@PathVariable String id,Authentication a){return service.get(id,a.getName());}
    @GetMapping Page<OrderApplicationService.OrderView> list(Authentication a,@PageableDefault(size=20)Pageable p){return service.list(a.getName(),p);}
    @PostMapping("/{id}/cancel") OrderApplicationService.OrderView cancel(@PathVariable String id,Authentication a){return service.cancel(id,a.getName());}
    @PostMapping("/redeem") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR','VENUE_ADMIN','VENUE_STAFF')")
    void redeem(@RequestBody @Valid RedeemRequest r){service.redeem(r.code());}
    record RedeemRequest(@NotBlank String code){}
}

enum OrderStatus{PENDING_PAYMENT,PAID,CANCELLED,COMPLETED,REFUNDED}
@Entity @Table(name="customer_order",uniqueConstraints=@UniqueConstraint(name="uk_order_user_idempotency",columnNames={"user_id","idempotency_key"}))
class CustomerOrder extends BaseEntity{
    @Column(nullable=false,unique=true,length=32)String orderNo;@Column(nullable=false,length=36)String userId;@Column(nullable=false,length=100)String idempotencyKey;
    @Column(nullable=false,precision=19,scale=2)BigDecimal amount;@Column(nullable=false,length=3)String currency;@Enumerated(EnumType.STRING)@Column(nullable=false,length=30)OrderStatus status;
    @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true)@OrderBy("createdAt")List<OrderItem>items=new ArrayList<>();
    @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true)@OrderBy("createdAt")List<OrderStatusHistory>history=new ArrayList<>();
    protected CustomerOrder(){}
}
@Entity @Table(name="order_item")
class OrderItem extends BaseEntity{@ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="order_id")CustomerOrder order;@Column(nullable=false,length=36)String skuId;@Column(nullable=false,length=36)String slotId;@Column(nullable=false,length=150)String skuName;@Column(nullable=false)int quantity;@Column(nullable=false,precision=19,scale=2)BigDecimal unitPrice;@Column(nullable=false,precision=19,scale=2)BigDecimal amount;protected OrderItem(){}}
@Entity @Table(name="order_status_history")
class OrderStatusHistory extends BaseEntity{@ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="order_id")CustomerOrder order;@Enumerated(EnumType.STRING)@Column(length=30)OrderStatus fromStatus;@Enumerated(EnumType.STRING)@Column(nullable=false,length=30)OrderStatus toStatus;@Column(length=255)String reason;protected OrderStatusHistory(){}}
enum RedemptionStatus{UNUSED,USED}
@Entity @Table(name="redemption_code")
class RedemptionCode extends BaseEntity{@Column(nullable=false,length=36)String orderId;@Column(nullable=false,unique=true,length=64)String codeHash;@Enumerated(EnumType.STRING)@Column(nullable=false,length=20)RedemptionStatus status;Instant redeemedAt;protected RedemptionCode(){}}
interface OrderRepository extends JpaRepository<CustomerOrder,String>{Optional<CustomerOrder>findByUserIdAndIdempotencyKey(String userId,String key);Page<CustomerOrder>findAllByUserIdOrderByCreatedAtDesc(String userId,Pageable p);}
interface OrderItemRepository extends JpaRepository<OrderItem,String>{}
interface RedemptionRepository extends JpaRepository<RedemptionCode,String>{
    Optional<RedemptionCode>findByCodeHash(String hash);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value="UPDATE redemption_code SET status='USED', redeemed_at=:at, version=version+1 WHERE id=:id AND status='UNUSED'",nativeQuery=true)
    int markUsed(@org.springframework.data.repository.query.Param("id")String id,@org.springframework.data.repository.query.Param("at")Instant at);
}
