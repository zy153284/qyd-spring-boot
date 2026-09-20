package com.qyd.payment;

import com.qyd.order.OrderApplicationService;
import com.qyd.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service; public PaymentController(PaymentService s){service=s;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) PaymentDto create(@Valid @RequestBody CreatePayment r,Authentication a){return PaymentDto.of(service.create(r.orderId(),a.getName()));}
    @GetMapping("/{id}") PaymentDto get(@PathVariable String id,Authentication a){return PaymentDto.of(service.getOwned(id,a.getName()));}
    @PostMapping("/callbacks/mock") CallbackResult callback(@RequestHeader("X-Mock-Signature")String signature,@Valid @RequestBody CallbackRequest r){return service.callback(signature,r);}
    @PostMapping("/{id}/refunds") @ResponseStatus(HttpStatus.CREATED) RefundDto refund(@PathVariable String id,@Valid @RequestBody RefundRequest r,Authentication a){return RefundDto.of(service.refund(id,r.amount(),r.reason(),a.getName()));}
    public record CreatePayment(@NotBlank String orderId){}
    public record CallbackRequest(@NotBlank String callbackId,@NotBlank String paymentNo,@NotBlank String providerTransactionId,@NotNull BigDecimal amount,@NotBlank String status){}
    public record RefundRequest(@NotNull @DecimalMin("0.01")BigDecimal amount,@Size(max=255)String reason){}
    public record PaymentDto(String id,String paymentNo,String orderId,BigDecimal amount,String currency,String status,String checkoutToken,Instant paidAt){static PaymentDto of(Payment e){return new PaymentDto(e.getId(),e.paymentNo,e.orderId,e.amount,e.currency,e.status.name(),e.checkoutToken,e.paidAt);}}
    public record RefundDto(String id,String refundNo,BigDecimal amount,String status,String reason){static RefundDto of(Refund e){return new RefundDto(e.getId(),e.refundNo,e.amount,e.status.name(),e.reason);}}
    public record CallbackResult(boolean duplicate,String paymentStatus,String redemptionCode){}
}

@Service @Transactional
class PaymentService{
    private final PaymentRepository payments;private final RefundRepository refunds;private final CallbackRepository callbacks;private final OrderApplicationService orders;private final PaymentProvider provider;
    PaymentService(PaymentRepository p,RefundRepository r,CallbackRepository c,OrderApplicationService o,PaymentProvider provider){payments=p;refunds=r;callbacks=c;orders=o;this.provider=provider;}
    Payment create(String orderId,String userId){
        var o=orders.snapshot(orderId);if(!o.userId().equals(userId))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var existing=payments.findByOrderIdAndStatusIn(orderId,List.of(PaymentStatus.PENDING,PaymentStatus.SUCCEEDED));if(existing.isPresent())return existing.get();
        if(!"PENDING_PAYMENT".equals(o.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"order not payable");
        Payment p=new Payment();p.orderId=orderId;p.paymentNo="P"+token(20);p.amount=o.amount();p.currency=o.currency();p.status=PaymentStatus.PENDING;p.checkoutToken=provider.initiate(p.paymentNo,p.amount,p.currency);return payments.save(p);
    }
    Payment get(String id){return payments.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"payment not found"));}
    Payment getOwned(String id,String userId){Payment p=get(id);if(!orders.snapshot(p.orderId).userId().equals(userId))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return p;}
    Payment getOwnedForUpdate(String id,String userId){Payment p=payments.findByIdForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"payment not found"));if(!orders.snapshot(p.orderId).userId().equals(userId))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return p;}
    PaymentController.CallbackResult callback(String signature,PaymentController.CallbackRequest r){
        if(!provider.verify(signature,r.callbackId(),r.paymentNo(),r.status()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"invalid callback signature");
        var old=callbacks.findByProviderAndCallbackId(provider.name(),r.callbackId());
        if(old.isPresent()){
            Payment p=get(old.get().paymentId);
            return new PaymentController.CallbackResult(true,p.status.name(),"");
        }
        Payment p=byNo(r.paymentNo());if(p.amount.compareTo(r.amount())!=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"amount mismatch");
        PaymentCallback event=new PaymentCallback();event.provider=provider.name();event.callbackId=r.callbackId();event.paymentId=p.getId();event.payloadStatus=r.status();callbacks.save(event);
        String code="";
        if("SUCCESS".equalsIgnoreCase(r.status())&&p.status==PaymentStatus.PENDING){p.status=PaymentStatus.SUCCEEDED;p.providerTransactionId=r.providerTransactionId();p.paidAt=Instant.now();code=orders.markPaid(p.orderId);}
        else if(!"SUCCESS".equalsIgnoreCase(r.status())&&p.status==PaymentStatus.PENDING)p.status=PaymentStatus.FAILED;
        return new PaymentController.CallbackResult(false,p.status.name(),code);
    }
    Refund refund(String paymentId,BigDecimal amount,String reason,String userId){
        Payment p=getOwnedForUpdate(paymentId,userId);if(p.status!=PaymentStatus.SUCCEEDED)throw new ResponseStatusException(HttpStatus.CONFLICT,"payment not refundable");
        BigDecimal used=refunds.sumActiveByPaymentId(paymentId);if(used.add(amount).compareTo(p.amount)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"refund exceeds paid amount");
        Refund r=new Refund();r.paymentId=paymentId;r.refundNo="R"+token(20);r.amount=amount;r.reason=reason;r.status=RefundStatus.REQUESTED;refunds.save(r);
        try{r.providerRefundId=provider.refund(r.refundNo,p.providerTransactionId,amount);}
        catch(RuntimeException e){r.status=RefundStatus.FAILED;return r;}
        r.status=RefundStatus.SUCCEEDED;
        if(used.add(amount).compareTo(p.amount)==0)orders.markRefunded(p.orderId);
        return r;
    }
    private Payment byNo(String no){return payments.findByPaymentNo(no).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"payment not found"));}
    private static String token(int n){return UUID.randomUUID().toString().replace("-","").substring(0,n).toUpperCase();}
}

@Component
class MockPaymentProvider implements PaymentProvider{
    public String name(){return"mock";}public String initiate(String no,BigDecimal amount,String currency){return"mock://pay/"+no;}
    public boolean verify(String signature,String callbackId,String paymentNo,String status){return"mock-signature".equals(signature);}
    public String refund(String refundNo,String txn,BigDecimal amount){return"MOCK-REFUND-"+refundNo;}
}
enum PaymentStatus{PENDING,SUCCEEDED,FAILED,CLOSED}
@Entity @Table(name="payment_order")
class Payment extends BaseEntity{@Column(nullable=false,length=36)String orderId;@Column(nullable=false,unique=true,length=32)String paymentNo;@Column(nullable=false,precision=19,scale=2)BigDecimal amount;@Column(nullable=false,length=3)String currency;@Enumerated(EnumType.STRING)@Column(nullable=false,length=20)PaymentStatus status;@Column(length=100)String checkoutToken;@Column(unique=true,length=100)String providerTransactionId;Instant paidAt;protected Payment(){}}
@Entity @Table(name="payment_callback",uniqueConstraints=@UniqueConstraint(name="uk_callback_provider_id",columnNames={"provider","callback_id"}))
class PaymentCallback extends BaseEntity{@Column(nullable=false,length=30)String provider;@Column(nullable=false,length=100)String callbackId;@Column(nullable=false,length=36)String paymentId;@Column(nullable=false,length=30)String payloadStatus;protected PaymentCallback(){}}
enum RefundStatus{REQUESTED,SUCCEEDED,FAILED}
@Entity @Table(name="payment_refund")
class Refund extends BaseEntity{@Column(nullable=false,length=36)String paymentId;@Column(nullable=false,unique=true,length=32)String refundNo;@Column(nullable=false,precision=19,scale=2)BigDecimal amount;@Column(length=255)String reason;@Enumerated(EnumType.STRING)@Column(nullable=false,length=20)RefundStatus status;@Column(length=100)String providerRefundId;protected Refund(){}}
interface PaymentRepository extends JpaRepository<Payment,String>{
    Optional<Payment>findByPaymentNo(String no);Optional<Payment>findByOrderIdAndStatusIn(String orderId,Collection<PaymentStatus>status);
    @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.id=:id")
    Optional<Payment>findByIdForUpdate(@org.springframework.data.repository.query.Param("id")String id);
}
interface CallbackRepository extends JpaRepository<PaymentCallback,String>{Optional<PaymentCallback>findByProviderAndCallbackId(String provider,String id);}
interface RefundRepository extends JpaRepository<Refund,String>{
    @org.springframework.data.jpa.repository.Query(value="SELECT COALESCE(SUM(amount),0) FROM payment_refund WHERE payment_id=:paymentId AND status IN ('REQUESTED','SUCCEEDED')",nativeQuery=true)
    BigDecimal sumActiveByPaymentId(@org.springframework.data.repository.query.Param("paymentId")String paymentId);
}
