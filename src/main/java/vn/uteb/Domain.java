package vn.uteb;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.*;

public class Domain {
 @MappedSuperclass public static class Base { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Version @JsonIgnore public long version; }
 @Entity(name="Account") @Table(name="accounts",uniqueConstraints=@UniqueConstraint(columnNames="email")) public static class Account extends Base {
  @Column(nullable=false,length=160) public String email; @Column(nullable=false) public String name; public String phone=""; public LocalDate birthDate;
  @JsonIgnore public String passwordHash; public String roles="CUSTOMER"; public Long storeId;
  public boolean active=true, verified=false; public int points=0; @JsonIgnore public int tokenVersion=0;
  @JsonIgnore public String otpHash,otpPurpose; @JsonIgnore public LocalDateTime otpExpires,otpSent; @JsonIgnore public int otpAttempts;
 }
 @Entity(name="Store") @Table(name="stores") public static class Store extends Base {
  public String name,address,phone,hours="10:00–22:00"; public double latitude,longitude; public boolean pickup=true,delivery=true,active=true;
  public BigDecimal deliveryFee=new BigDecimal("15000"); public BigDecimal commission=new BigDecimal("0.10"); public Long ownerId;
 }
 @Entity(name="Category") @Table(name="categories") public static class Category extends Base { public String name,slug; }
 @Entity(name="Product") @Table(name="products") public static class Product extends Base {
  public String name,category,description,image="/assets/food.png"; public Long storeId; public BigDecimal price=BigDecimal.ZERO;
  public int calories,stock=100; public boolean available=true,customizable=true; public LocalDateTime createdAt=LocalDateTime.now();
 }
 @Entity(name="Ingredient") @Table(name="ingredients") public static class Ingredient extends Base {
  public String name,groupName; public BigDecimal price=BigDecimal.ZERO; public int calories; public boolean available=true; public String allergens="";
 }
 @Entity(name="Address") @Table(name="addresses") public static class Address extends Base { public Long userId; public String label,recipient,phone; @Column(length=500) public String detail; public boolean defaultAddress; }
 @Entity(name="CartLine") @Table(name="cart_lines") public static class CartLine extends Base { public Long userId,productId,storeId; public int quantity; @Column(length=2000) public String ingredientIds=""; @Column(length=300) public String note=""; }
 @Entity(name="MealOrder") @Table(name="meal_orders",uniqueConstraints=@UniqueConstraint(columnNames={"userId","checkoutKey","storeId"})) public static class MealOrder extends Base {
  @Transient public String cancellationStatus;
  public Long userId,storeId,shipperId; public String code,groupCode,checkoutKey,type,status="NEW",paymentMethod,paymentStatus="UNPAID",transactionRef="",contactName,phone;
  @Column(length=1500) public String address,note; public String scheduledAt="ASAP",voucherCode="";
  public BigDecimal subtotal=BigDecimal.ZERO,discount=BigDecimal.ZERO,deliveryFee=BigDecimal.ZERO,total=BigDecimal.ZERO,commissionRate=BigDecimal.ZERO;
  public LocalDateTime createdAt=LocalDateTime.now(),updatedAt=LocalDateTime.now(),paymentExpires; public boolean pointsAwarded=false;
 }
 @Entity(name="PaymentAttempt") @Table(name="payment_attempts",uniqueConstraints=@UniqueConstraint(columnNames="reference")) public static class PaymentAttempt extends Base {
  @Column(nullable=false) public Long orderId;
  @Column(nullable=false,length=64) public String reference;
  @Column(nullable=false,precision=38,scale=2) public BigDecimal amount;
  @Column(nullable=false,length=32) public String state="PENDING";
  public String transactionNo="";
  @Column(length=2) public String responseCode="",transactionStatus="";
  @Column(nullable=false) public LocalDateTime createdAt=LocalDateTime.now(),expiresAt;
 }
 @Entity(name="OrderLine") @Table(name="order_lines") public static class OrderLine extends Base {
  public Long orderId,productId; public String name,image; @Column(length=2000) public String customization; public int quantity,calories; public BigDecimal unitPrice;
 }
 @Entity(name="OrderEvent") @Table(name="order_events") public static class OrderEvent extends Base { public Long orderId,actorId; public String status,description; public LocalDateTime createdAt=LocalDateTime.now(); }
 @Entity(name="Voucher") @Table(name="vouchers",uniqueConstraints=@UniqueConstraint(columnNames="code")) public static class Voucher extends Base {
  public String code,name,type="PERCENT"; @Column(name="discount_value") public BigDecimal value=BigDecimal.ZERO; public BigDecimal minimum=BigDecimal.ZERO,maxDiscount=new BigDecimal("50000");
  public LocalDateTime startsAt=LocalDateTime.now().minusDays(1),endsAt=LocalDateTime.now().plusMonths(3); public int usageLimit=1000,used=0; public boolean active=true; public Long storeId,userId;
 }
 @Entity(name="Review") @Table(name="reviews",uniqueConstraints=@UniqueConstraint(columnNames="orderLineId")) public static class Review extends Base {
  public Long userId,productId,orderLineId; public String author; public int stars; @Column(length=3000) public String text; @Column(length=2000) public String media=""; public LocalDateTime createdAt=LocalDateTime.now();
 }
 @Entity(name="UserMark") @Table(name="user_marks",uniqueConstraints=@UniqueConstraint(columnNames={"userId","productId","kind"})) public static class UserMark extends Base { public Long userId,productId; public String kind; public LocalDateTime createdAt=LocalDateTime.now(); }
 @Entity(name="Notice") @Table(name="notices") public static class Notice extends Base { public Long userId,orderId; public String text; public boolean seen=false; public LocalDateTime createdAt=LocalDateTime.now(); }
 @Entity(name="RewardLog") @Table(name="reward_logs") public static class RewardLog extends Base { public Long userId; public int points; public String description; public LocalDateTime createdAt=LocalDateTime.now(); }
 @Entity(name="GroupMeal") @Table(name="group_meals",uniqueConstraints=@UniqueConstraint(columnNames="code")) public static class GroupMeal extends Base { public Long organizerId,storeId; public String code,title,status="OPEN"; public LocalDateTime createdAt=LocalDateTime.now(); }
 @Entity(name="GroupLine") @Table(name="group_lines") public static class GroupLine extends Base { public Long groupId,userId,productId; public String memberName,ingredientIds=""; public int quantity=1; }
 @Entity(name="CateringRequest") @Table(name="catering_requests") public static class CateringRequest extends Base { public Long userId,storeId; public String packageName,eventAt,contactName,phone,address,status="NEW"; public int people; public BigDecimal quotedTotal; public String note=""; }
 @Entity(name="RefundRequest") @Table(name="refund_requests",uniqueConstraints=@UniqueConstraint(columnNames="orderId")) public static class RefundRequest extends Base { public Long userId,orderId,storeId; @Column(length=2000) public String reason; public String status="REQUESTED",evidence="",reply=""; public BigDecimal amount; public LocalDateTime createdAt=LocalDateTime.now(); }
 @Entity(name="OrderCancellation") @Table(name="order_cancellations",uniqueConstraints={@UniqueConstraint(columnNames="orderId"),@UniqueConstraint(columnNames="refundReference")})
 public static class OrderCancellation extends Base {
  @Column(nullable=false) public Long orderId,userId,storeId;
  @Column(nullable=false,length=32) public String status="REQUESTED";
  @org.hibernate.annotations.Nationalized @Column(nullable=false,length=2000) public String reason;
  @Column(nullable=false,precision=38,scale=2) public BigDecimal amount;
  @Column(precision=38,scale=2) public BigDecimal confirmedAmount;
  @Column(nullable=false,length=32) public String paymentMethod;
  @org.hibernate.annotations.Nationalized @Column(nullable=false,length=255) public String paymentReference;
  @org.hibernate.annotations.Nationalized @Column(length=1000) public String decisionNote,refundNote;
  @org.hibernate.annotations.Nationalized @Column(length=120) public String refundReference;
  @org.hibernate.annotations.Nationalized @Column(length=255) public String evidence;
  public Long decidedBy,refundedBy;
  @Column(nullable=false) public LocalDateTime createdAt=LocalDateTime.now();
  public LocalDateTime decidedAt,refundedAt;
 }
 @Entity(name="ShopApplication") @Table(name="shop_applications") public static class ShopApplication extends Base { public Long userId; public String name,address,phone,status="PENDING",reply=""; }
 @Entity(name="MediaRecord") @Table(name="media_records") public static class MediaRecord extends Base { public Long userId; public String url,publicId,kind; public LocalDateTime createdAt=LocalDateTime.now(); }
 @Entity(name="AuditRecord") @Table(name="audit_records") public static class AuditRecord extends Base { public Long actorId; public String action; @Column(length=2000) public String detail; public LocalDateTime createdAt=LocalDateTime.now(); }
 @Entity(name="Setting") @Table(name="settings",uniqueConstraints=@UniqueConstraint(columnNames="name")) public static class Setting extends Base { public String name,settingValue; }
}
