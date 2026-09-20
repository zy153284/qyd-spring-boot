package com.qyd.marketing;

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
@RequestMapping("/api/v1")
public class MarketingController {
    private final MarketingService service;
    public MarketingController(MarketingService service) { this.service = service; }

    @GetMapping("/marketing/coupons")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR')")
    List<CouponDto> coupons() { return service.coupons(false); }

    @GetMapping("/coupons/available")
    List<CouponDto> availableCoupons() { return service.coupons(true); }

    @PostMapping("/marketing/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR')")
    CouponDto createCoupon(@Valid @RequestBody CouponRequest request) { return service.createCoupon(request); }

    @PutMapping("/marketing/coupons/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR')")
    CouponDto updateCoupon(@PathVariable String id, @Valid @RequestBody CouponRequest request) {
        return service.updateCoupon(id, request);
    }

    @DeleteMapping("/marketing/coupons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    void deleteCoupon(@PathVariable String id) { service.deleteCoupon(id); }

    @PostMapping("/coupons/{id}/claim")
    @ResponseStatus(HttpStatus.CREATED)
    UserCouponDto claim(@PathVariable String id, Authentication auth) { return service.claim(id, auth.getName()); }

    @GetMapping("/me/coupons")
    List<UserCouponDto> myCoupons(Authentication auth) { return service.myCoupons(auth.getName()); }

    @PostMapping("/me/coupons/{id}/redeem")
    UserCouponDto redeem(@PathVariable String id, @Valid @RequestBody RedeemCouponRequest request, Authentication auth) {
        return service.redeem(id, request.orderId(), auth.getName());
    }

    @GetMapping("/me/membership")
    MemberDto membership(Authentication auth) { return service.membership(auth.getName()); }

    @GetMapping("/me/points")
    List<PointsDto> points(Authentication auth) { return service.points(auth.getName()); }

    @PostMapping("/marketing/members/{userId}/points")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR')")
    PointsDto changePoints(@PathVariable String userId,@Valid @RequestBody PointsChangeRequest request) {
        return service.changePoints(userId,request);
    }

    @GetMapping("/me/favorites")
    List<FavoriteDto> favorites(Authentication auth) { return service.favorites(auth.getName()); }

    @PostMapping("/me/favorites/{venueId}")
    @ResponseStatus(HttpStatus.CREATED)
    FavoriteDto favorite(@PathVariable String venueId, Authentication auth) {
        return service.favorite(auth.getName(), venueId);
    }

    @DeleteMapping("/me/favorites/{venueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfavorite(@PathVariable String venueId, Authentication auth) {
        service.unfavorite(auth.getName(), venueId);
    }

    @PostMapping("/orders/{orderId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    ReviewDto review(@PathVariable String orderId, @Valid @RequestBody ReviewRequest request, Authentication auth) {
        return service.review(orderId, auth.getName(), request);
    }

    @GetMapping("/venues/{venueId}/reviews")
    List<ReviewDto> venueReviews(@PathVariable String venueId) { return service.venueReviews(venueId); }

    public record CouponRequest(@NotBlank @Size(max=120) String name, @Size(max=500) String description,
            @NotNull @DecimalMin("0.01") BigDecimal discountAmount,
            @NotNull @DecimalMin("0.01") BigDecimal minimumAmount, @Min(1) int totalQuantity,
            @NotNull Instant startsAt, @NotNull Instant endsAt, CouponStatus status) {}
    public record CouponDto(String id, String name, String description, BigDecimal discountAmount,
            BigDecimal minimumAmount, int totalQuantity, int claimedQuantity, Instant startsAt,
            Instant endsAt, String status) { static CouponDto of(CouponDefinition c) {
        return new CouponDto(c.getId(), c.name, c.description, c.discountAmount, c.minimumAmount,
                c.totalQuantity, c.claimedQuantity, c.startsAt, c.endsAt, c.status.name()); }}
    public record RedeemCouponRequest(@NotBlank String orderId) {}
    public record UserCouponDto(String id, CouponDto coupon, String status, Instant claimedAt,
            Instant redeemedAt, String orderId) { static UserCouponDto of(UserCoupon c) {
        return new UserCouponDto(c.getId(), CouponDto.of(c.coupon), c.status.name(), c.claimedAt, c.redeemedAt, c.orderId); }}
    public record MemberDto(String id, String level, long pointsBalance, long growthValue) {
        static MemberDto of(MemberAccount m) { return new MemberDto(m.getId(), m.level, m.pointsBalance, m.growthValue); }}
    public record PointsDto(String id, long changeAmount, long balanceAfter, String type,
            String referenceType, String referenceId, String remark, Instant createdAt) {
        static PointsDto of(PointsLedger p) { return new PointsDto(p.getId(), p.changeAmount, p.balanceAfter,
                p.type, p.referenceType, p.referenceId, p.remark, p.getCreatedAt()); }}
    public record PointsChangeRequest(@NotNull @Min(-1000000) @Max(1000000)Long amount,
            @NotBlank @Size(max=30)String type,@Size(max=30)String referenceType,
            @Size(max=36)String referenceId,@Size(max=255)String remark){}
    public record FavoriteDto(String id, String venueId, Instant createdAt) {
        static FavoriteDto of(VenueFavorite f) { return new FavoriteDto(f.getId(), f.venueId, f.getCreatedAt()); }}
    public record ReviewRequest(@Min(1) @Max(5) int rating, @Size(max=1000) String content) {}
    public record ReviewDto(String id, String orderId, String venueId, int rating, String content, Instant createdAt) {
        static ReviewDto of(OrderReview r) { return new ReviewDto(r.getId(), r.orderId, r.venueId, r.rating, r.content, r.getCreatedAt()); }}
}

@Service
@Transactional
class MarketingService {
    private final CouponRepository coupons; private final UserCouponRepository userCoupons;
    private final MemberRepository members; private final PointsRepository pointEntries;
    private final FavoriteRepository favoriteEntries; private final ReviewRepository reviews;
    MarketingService(CouponRepository coupons, UserCouponRepository userCoupons, MemberRepository members,
            PointsRepository pointEntries, FavoriteRepository favoriteEntries, ReviewRepository reviews) {
        this.coupons=coupons; this.userCoupons=userCoupons; this.members=members; this.pointEntries=pointEntries;
        this.favoriteEntries=favoriteEntries; this.reviews=reviews;
    }

    @Transactional(readOnly=true)
    List<MarketingController.CouponDto> coupons(boolean available) {
        Instant now=Instant.now();
        return (available ? coupons.findAvailable(now) : coupons.findAll(Sort.by(Sort.Direction.DESC,"createdAt")))
                .stream().map(MarketingController.CouponDto::of).toList();
    }
    MarketingController.CouponDto createCoupon(MarketingController.CouponRequest r) {
        validateCoupon(r); return MarketingController.CouponDto.of(coupons.save(new CouponDefinition(r)));
    }
    MarketingController.CouponDto updateCoupon(String id, MarketingController.CouponRequest r) {
        validateCoupon(r); CouponDefinition c=requireCoupon(id);
        if (r.totalQuantity()<c.claimedQuantity) throw new ResponseStatusException(HttpStatus.CONFLICT,"quantity below claimed count");
        c.update(r); return MarketingController.CouponDto.of(c);
    }
    void deleteCoupon(String id) {
        CouponDefinition c=requireCoupon(id);
        if(c.claimedQuantity>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"claimed coupon cannot be deleted");
        coupons.delete(c);
    }
    MarketingController.UserCouponDto claim(String couponId,String userId) {
        userCoupons.findByCouponIdAndUserId(couponId,userId).ifPresent(x->{throw new ResponseStatusException(HttpStatus.CONFLICT,"coupon already claimed");});
        CouponDefinition c=coupons.lockById(couponId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"coupon not found"));
        Instant now=Instant.now();
        if(c.status!=CouponStatus.ACTIVE||now.isBefore(c.startsAt)||now.isAfter(c.endsAt)||c.claimedQuantity>=c.totalQuantity)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"coupon is unavailable");
        c.claimedQuantity++;
        return MarketingController.UserCouponDto.of(userCoupons.save(new UserCoupon(c,userId,now)));
    }
    @Transactional(readOnly=true)
    List<MarketingController.UserCouponDto> myCoupons(String userId) {
        return userCoupons.findAllByUserIdOrderByClaimedAtDesc(userId).stream().map(MarketingController.UserCouponDto::of).toList();
    }
    MarketingController.UserCouponDto redeem(String id,String orderId,String userId) {
        UserCoupon c=userCoupons.findByIdAndUserId(id,userId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"user coupon not found"));
        if(c.status!=UserCouponStatus.UNUSED) throw new ResponseStatusException(HttpStatus.CONFLICT,"coupon is not unused");
        BigDecimal amount=userCoupons.ownedOrderAmount(orderId,userId).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"order does not belong to user"));
        if(amount.compareTo(c.coupon.minimumAmount)<0) throw new ResponseStatusException(HttpStatus.CONFLICT,"order does not meet minimum amount");
        c.status=UserCouponStatus.USED;c.orderId=orderId;c.redeemedAt=Instant.now();return MarketingController.UserCouponDto.of(c);
    }
    MarketingController.MemberDto membership(String userId) {
        return MarketingController.MemberDto.of(members.findByUserId(userId).orElseGet(()->members.save(new MemberAccount(userId))));
    }
    @Transactional(readOnly=true)
    List<MarketingController.PointsDto> points(String userId) {
        return pointEntries.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(MarketingController.PointsDto::of).toList();
    }
    MarketingController.PointsDto changePoints(String userId,MarketingController.PointsChangeRequest r) {
        if(r.amount()==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"points amount cannot be zero");
        MemberAccount account=members.findByUserId(userId).orElseGet(()->members.save(new MemberAccount(userId)));
        long next;
        try{next=Math.addExact(account.pointsBalance,r.amount());}catch(ArithmeticException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"points overflow");}
        if(next<0)throw new ResponseStatusException(HttpStatus.CONFLICT,"insufficient points");
        account.pointsBalance=next;
        return MarketingController.PointsDto.of(pointEntries.save(new PointsLedger(userId,r.amount(),next,r.type(),r.referenceType(),r.referenceId(),r.remark())));
    }
    MarketingController.FavoriteDto favorite(String userId,String venueId) {
        favoriteEntries.assertVenueExists(venueId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"venue not found"));
        return MarketingController.FavoriteDto.of(favoriteEntries.findByUserIdAndVenueId(userId,venueId)
                .orElseGet(()->favoriteEntries.save(new VenueFavorite(userId,venueId))));
    }
    @Transactional(readOnly=true)
    List<MarketingController.FavoriteDto> favorites(String userId) {
        return favoriteEntries.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(MarketingController.FavoriteDto::of).toList();
    }
    void unfavorite(String userId,String venueId) { favoriteEntries.deleteByUserIdAndVenueId(userId,venueId); }
    MarketingController.ReviewDto review(String orderId,String userId,MarketingController.ReviewRequest r) {
        String venueId=reviews.findVenueForCompletedOwnedOrder(orderId,userId)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"only completed owned orders can be reviewed"));
        if(reviews.existsByOrderId(orderId)) throw new ResponseStatusException(HttpStatus.CONFLICT,"order already reviewed");
        return MarketingController.ReviewDto.of(reviews.save(new OrderReview(orderId,userId,venueId,r.rating(),r.content())));
    }
    @Transactional(readOnly=true)
    List<MarketingController.ReviewDto> venueReviews(String venueId) {
        return reviews.findAllByVenueIdOrderByCreatedAtDesc(venueId).stream().map(MarketingController.ReviewDto::of).toList();
    }
    private CouponDefinition requireCoupon(String id){return coupons.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"coupon not found"));}
    private void validateCoupon(MarketingController.CouponRequest r){
        if(!r.endsAt().isAfter(r.startsAt()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"endsAt must be after startsAt");
        if(r.minimumAmount().compareTo(r.discountAmount())<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"minimumAmount must cover discount");
    }
}

enum CouponStatus { DRAFT, ACTIVE, INACTIVE }
enum UserCouponStatus { UNUSED, USED, EXPIRED }

@Entity @Table(name="coupon_definition")
class CouponDefinition extends BaseEntity {
    @Column(nullable=false,length=120)String name;@Column(length=500)String description;
    @Column(nullable=false,precision=19,scale=2)BigDecimal discountAmount;
    @Column(nullable=false,precision=19,scale=2)BigDecimal minimumAmount;
    @Column(nullable=false)int totalQuantity;@Column(nullable=false)int claimedQuantity;
    @Column(nullable=false)Instant startsAt;@Column(nullable=false)Instant endsAt;
    @Enumerated(EnumType.STRING)@Column(nullable=false,length=20)CouponStatus status;
    protected CouponDefinition(){}
    CouponDefinition(MarketingController.CouponRequest r){update(r);claimedQuantity=0;}
    void update(MarketingController.CouponRequest r){name=r.name();description=r.description();discountAmount=r.discountAmount();
        minimumAmount=r.minimumAmount();totalQuantity=r.totalQuantity();startsAt=r.startsAt();endsAt=r.endsAt();status=r.status()==null?CouponStatus.DRAFT:r.status();}
}
@Entity @Table(name="user_coupon",uniqueConstraints=@UniqueConstraint(name="uk_user_coupon",columnNames={"coupon_id","user_id"}))
class UserCoupon extends BaseEntity {
    @ManyToOne(fetch=FetchType.EAGER,optional=false)@JoinColumn(name="coupon_id")CouponDefinition coupon;
    @Column(nullable=false,length=36)String userId;@Enumerated(EnumType.STRING)@Column(nullable=false,length=20)UserCouponStatus status;
    @Column(nullable=false)Instant claimedAt;Instant redeemedAt;@Column(length=36)String orderId;
    protected UserCoupon(){} UserCoupon(CouponDefinition c,String userId,Instant at){coupon=c;this.userId=userId;status=UserCouponStatus.UNUSED;claimedAt=at;}
}
@Entity @Table(name="member_account")
class MemberAccount extends BaseEntity {
    @Column(nullable=false,unique=true,length=36)String userId;@Column(nullable=false,length=30)String level;
    @Column(nullable=false)long pointsBalance;@Column(nullable=false)long growthValue;
    protected MemberAccount(){}MemberAccount(String userId){this.userId=userId;level="BASIC";}
}
@Entity @Table(name="points_ledger")
class PointsLedger extends BaseEntity {
    @Column(nullable=false,length=36)String userId;@Column(nullable=false)long changeAmount;@Column(nullable=false)long balanceAfter;
    @Column(nullable=false,length=30)String type;@Column(length=30)String referenceType;@Column(length=36)String referenceId;@Column(length=255)String remark;
    protected PointsLedger(){}PointsLedger(String userId,long amount,long balance,String type,String referenceType,String referenceId,String remark){
        this.userId=userId;changeAmount=amount;balanceAfter=balance;this.type=type;this.referenceType=referenceType;this.referenceId=referenceId;this.remark=remark;}
}
@Entity @Table(name="venue_favorite",uniqueConstraints=@UniqueConstraint(name="uk_favorite_user_venue",columnNames={"user_id","venue_id"}))
class VenueFavorite extends BaseEntity {
    @Column(nullable=false,length=36)String userId;@Column(nullable=false,length=36)String venueId;
    protected VenueFavorite(){}VenueFavorite(String userId,String venueId){this.userId=userId;this.venueId=venueId;}
}
@Entity @Table(name="order_review")
class OrderReview extends BaseEntity {
    @Column(nullable=false,unique=true,length=36)String orderId;@Column(nullable=false,length=36)String userId;
    @Column(nullable=false,length=36)String venueId;@Column(nullable=false)int rating;@Column(length=1000)String content;
    protected OrderReview(){}OrderReview(String orderId,String userId,String venueId,int rating,String content){this.orderId=orderId;this.userId=userId;this.venueId=venueId;this.rating=rating;this.content=content;}
}

interface CouponRepository extends JpaRepository<CouponDefinition,String>{
    @Lock(LockModeType.PESSIMISTIC_WRITE)@org.springframework.data.jpa.repository.Query("select c from CouponDefinition c where c.id=:id")Optional<CouponDefinition>lockById(@Param("id")String id);
    @org.springframework.data.jpa.repository.Query("select c from CouponDefinition c where c.status=com.qyd.marketing.CouponStatus.ACTIVE and c.startsAt<=:now and c.endsAt>=:now and c.claimedQuantity<c.totalQuantity order by c.endsAt")
    List<CouponDefinition>findAvailable(@Param("now")Instant now);
}
interface UserCouponRepository extends JpaRepository<UserCoupon,String>{
    Optional<UserCoupon>findByCouponIdAndUserId(String couponId,String userId);Optional<UserCoupon>findByIdAndUserId(String id,String userId);
    List<UserCoupon>findAllByUserIdOrderByClaimedAtDesc(String userId);
    @org.springframework.data.jpa.repository.Query(value="select amount from customer_order where id=:orderId and user_id=:userId",nativeQuery=true)Optional<BigDecimal>ownedOrderAmount(@Param("orderId")String orderId,@Param("userId")String userId);
}
interface MemberRepository extends JpaRepository<MemberAccount,String>{Optional<MemberAccount>findByUserId(String userId);}
interface PointsRepository extends JpaRepository<PointsLedger,String>{List<PointsLedger>findAllByUserIdOrderByCreatedAtDesc(String userId);}
interface FavoriteRepository extends JpaRepository<VenueFavorite,String>{
    Optional<VenueFavorite>findByUserIdAndVenueId(String userId,String venueId);List<VenueFavorite>findAllByUserIdOrderByCreatedAtDesc(String userId);
    void deleteByUserIdAndVenueId(String userId,String venueId);
    @org.springframework.data.jpa.repository.Query(value="select id from venue where id=:venueId",nativeQuery=true)Optional<String>assertVenueExists(@Param("venueId")String venueId);
}
interface ReviewRepository extends JpaRepository<OrderReview,String>{
    boolean existsByOrderId(String orderId);List<OrderReview>findAllByVenueIdOrderByCreatedAtDesc(String venueId);
    @org.springframework.data.jpa.repository.Query(value="select sr.venue_id from customer_order o join order_item oi on oi.order_id=o.id join product_sku s on s.id=oi.sku_id join sport_resource sr on sr.id=s.resource_id where o.id=:orderId and o.user_id=:userId and o.status='COMPLETED' limit 1",nativeQuery=true)
    Optional<String>findVenueForCompletedOwnedOrder(@Param("orderId")String orderId,@Param("userId")String userId);
}
