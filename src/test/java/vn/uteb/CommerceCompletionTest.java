package vn.uteb;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.uteb.Domain.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:completion;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","uteb.seed=false","uteb.mail.enabled=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc @Transactional
class CommerceCompletionTest {
 @PersistenceContext EntityManager em;
 @Autowired ShopService shop;@Autowired CatalogService catalog;@Autowired StatisticsService statistics;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
 Store a,b;Account admin,manager,vendor,buyer,stranger,shipper;Product first,second,foreign;
 Account account(String role,Long store){Account x=new Account();x.email=UUID.randomUUID()+"@test.local";x.name="Kiểm thử";x.roles=role;x.storeId=store;x.verified=true;x.phone="0901234567";em.persist(x);return x;}
 Store store(String name){Store x=new Store();x.name=name;x.address="123 Đường kiểm thử";x.phone="0901234567";em.persist(x);return x;}
 Product product(Store store,String name){Product p=new Product();p.name=name;p.storeId=store.id;p.category="Burrito";p.price=new BigDecimal("60000");p.stock=100;p.customizable=false;p.calories=400;p.createdAt=LocalDateTime.now();em.persist(p);return p;}
 void actor(Account x){SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(x.email,"",x.roles));}
 MealOrder order(Store store,Account owner,String status,Product product,int quantity){MealOrder o=new MealOrder();o.userId=owner.id;o.storeId=store.id;o.status=status;o.paymentStatus="PAID";o.paymentMethod="COD";o.type="DELIVERY";o.total=new BigDecimal("60000");o.subtotal=o.total;o.code="TEST-"+UUID.randomUUID();o.checkoutKey=UUID.randomUUID().toString();em.persist(o);OrderLine l=new OrderLine();l.orderId=o.id;l.productId=product.id;l.name=product.name;l.quantity=quantity;l.unitPrice=product.price;em.persist(l);return o;}
 ShippingCarrier carrier(Store store,int fee){ShippingCarrier c=new ShippingCarrier();c.storeId=store.id;c.name="Giao hàng kiểm thử";c.fee=BigDecimal.valueOf(fee);em.persist(c);return c;}
 Voucher voucher(String type,Long store,Long product,int value){Voucher v=new Voucher();v.code="TEST"+UUID.randomUUID().toString().substring(0,8).toUpperCase();v.type=type;v.storeId=store;v.productId=product;v.value=BigDecimal.valueOf(value);v.maxDiscount=new BigDecimal("50000");em.persist(v);return v;}
 void add(Product p,int quantity){shop.cart(Map.of("productId",p.id,"quantity",quantity,"ingredientIds",""));}
 Map<String,Object> checkout(){return new HashMap<>(Map.of("checkoutKey",UUID.randomUUID().toString(),"type","DELIVERY","paymentMethod","COD","contactName","Khách kiểm thử","phone","0901234567","address","123 Đường giao hàng","scheduledAt","ASAP"));}
 Map<String,Object> storeBody(Store st){return new HashMap<>(Map.of("id",st.id,"name",st.name,"address",st.address,"phone",st.phone,"hours","10:00–22:00","pickup",true,"delivery",true,"active",true,"deliveryFee",15000,"commission","0.15"));}
 @BeforeEach void setup(){a=store("Shop Ánh Dương");b=store("Shop khác");admin=account("ADMIN",null);manager=account("MANAGER",a.id);vendor=account("CUSTOMER,VENDOR",a.id);buyer=account("CUSTOMER",null);stranger=account("CUSTOMER",null);shipper=account("SHIPPER",a.id);Category category=new Category();category.name="Burrito";category.slug="burrito";em.persist(category);first=product(a,"Burrito Gà");second=product(a,"Burrito Bò");foreign=product(b,"Burrito shop khác");}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @SuppressWarnings("unchecked") List<Map<String,Object>> items(Map<String,Object> data){return (List<Map<String,Object>>)data.get("items");}
 Product ranked(Map<String,Object> data,int i){return (Product)items(data).get(i).get("product");}
 @Test void rankingsAreCompletePerShopAndExcludeCancelledUnpaidAndRefundedPurchases(){
  order(a,buyer,"COMPLETED",first,12);order(a,buyer,"COMPLETED",second,7);order(b,buyer,"COMPLETED",foreign,100);
  order(a,buyer,"CANCELLED",second,1000);MealOrder unpaid=order(a,buyer,"COMPLETED",second,1000);unpaid.paymentStatus="UNPAID";
  MealOrder refunded=order(a,buyer,"COMPLETED",second,1000);RefundRequest r=new RefundRequest();r.orderId=refunded.id;r.status="REFUNDED";em.persist(r);
  var top=catalog.list(a.id,"","","top",1,20,0);assertThat(ranked(top,0).id).isEqualTo(first.id);assertThat(((Map<?,?>)items(top).getFirst().get("stats")).get("sold")).isEqualTo(12L);
  var overTen=catalog.list(null,"","","top",1,20,11);assertThat(items(overTen)).hasSize(2);assertThat(ranked(overTen,0).id).isEqualTo(foreign.id);
 }
 @Test void newestUsesCreationTimeAndPaginationKeepsAllTwentyPlusItems(){
  first.createdAt=LocalDateTime.now().plusDays(1);for(int i=0;i<24;i++)product(a,"Món "+i);
  var newest=catalog.list(a.id,"","","new",1,20,0);assertThat(ranked(newest,0).id).isEqualTo(first.id);assertThat(items(newest)).hasSize(20);
  var all=catalog.list(a.id,"","","default",2,20,0);assertThat(all.get("total")).isEqualTo(26);assertThat(items(all)).hasSize(6);
 }
 @Test void ratingsAndFavoritesUseAllUsersRatherThanPersonalMarks(){
  for(int i=0;i<2;i++){UserMark m=new UserMark();m.userId=i==0?buyer.id:stranger.id;m.productId=first.id;m.kind="FAVORITE";em.persist(m);}
  Review r=new Review();r.productId=second.id;r.stars=5;em.persist(r);
  assertThat(ranked(catalog.list(a.id,"","","favorite",1,20,0),0).id).isEqualTo(first.id);
  assertThat(ranked(catalog.list(a.id,"","","rating",1,20,0),0).id).isEqualTo(second.id);
 }
 @Test void catalogSearchIgnoresVietnameseAccentsAndSupportsCategory(){assertThat(items(catalog.list(a.id,"Burrito","ga","default",1,20,0))).hasSize(1);assertThat(items(catalog.list(a.id,"Salad","","default",1,20,0))).isEmpty();}
 @Test void publicDetailAndShopDoNotExposeHiddenProductsOrInactiveShops()throws Exception{
  mvc.perform(get("/api/public/products/"+first.id)).andExpect(status().isOk()).andExpect(jsonPath("$.product.name").value(first.name));
  first.available=false;mvc.perform(get("/api/public/products/"+first.id)).andExpect(status().isNotFound());
  b.active=false;mvc.perform(get("/api/public/shops/"+b.id)).andExpect(status().isNotFound());assertThat(items(catalog.list(null,"","","default",1,20,0))).hasSize(1);
 }
 @ParameterizedTest @ValueSource(strings={"page=0","size=21","sort=unknown","minimumSold=-1","page=abc","size=2147483648"}) void catalogRejectsInvalidParameters(String query)throws Exception{mvc.perform(get("/api/public/catalog?"+query)).andExpect(status().isBadRequest());}
 @Test void productDetailPaginatesEveryPurchasedReview(){for(int i=0;i<10;i++){Review r=new Review();r.productId=first.id;r.orderLineId=10000L+i;r.stars=4;em.persist(r);}var detail=catalog.product(first.id,2);assertThat((List<?>)detail.get("reviews")).hasSize(2);assertThat(detail.get("reviewPages")).isEqualTo(2L);}
 @Test void productVoucherDiscountsOnlyTheSelectedProduct(){actor(buyer);add(first,1);add(second,1);Voucher v=voucher("PERCENT",a.id,first.id,25);var body=checkout();body.put("voucher",v.code);body.put("type","PICKUP");var preview=shop.preview(body);assertThat((BigDecimal)preview.get("discount")).isEqualByComparingTo("15000");assertThat((BigDecimal)preview.get("total")).isEqualByComparingTo("105000");assertThat(v.used).isZero();assertThat(shop.checkout(body).getFirst().total).isEqualByComparingTo("105000");assertThat(v.used).isEqualTo(1);}
 @Test void productVoucherCannotDiscountAnotherProductOrAnotherStore(){actor(buyer);add(second,1);Voucher v=voucher("PERCENT",a.id,first.id,25);var body=checkout();body.put("voucher",v.code);assertThatThrownBy(()->shop.preview(body)).hasMessageContaining("điều kiện");}
 @Test void shippingVoucherIsAllocatedAcrossStoresAndSnapshotted(){actor(buyer);add(first,1);add(foreign,1);ShippingCarrier c=carrier(a,21000);Voucher v=voucher("SHIPPING",null,null,18000);var body=checkout();body.put("carrier_"+a.id,c.id);body.put("voucher",v.code);var orders=shop.checkout(body);assertThat(orders).hasSize(2);assertThat(orders.stream().map(o->o.shippingDiscount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("18000");assertThat(orders.stream().map(o->o.total).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("138000");MealOrder selected=orders.stream().filter(o->o.storeId.equals(a.id)).findFirst().orElseThrow();assertThat(selected.carrierId).isEqualTo(c.id);assertThat(selected.deliveryFee).isEqualByComparingTo("21000");assertThat(selected.discount).isEqualByComparingTo("0");c.fee=BigDecimal.ONE;assertThat(selected.deliveryFee).isEqualByComparingTo("21000");}
 @Test void carrierAndShippingVoucherCannotBypassStoreOrPickupRules(){actor(buyer);add(first,1);ShippingCarrier wrong=carrier(b,1000);var body=checkout();body.put("carrier_"+a.id,wrong.id);assertThatThrownBy(()->shop.preview(body)).hasMessageContaining("chi nhánh");body.remove("carrier_"+a.id);Voucher v=voucher("SHIPPING",a.id,null,10000);body.put("voucher",v.code);body.put("type","PICKUP");assertThatThrownBy(()->shop.preview(body)).hasMessageContaining("giao tận nơi");}
 @Test void freeDeliveryCannotBeDiscountedBelowZero(){actor(buyer);add(first,3);Voucher v=voucher("SHIPPING",a.id,null,50000);var body=checkout();body.put("voucher",v.code);assertThatThrownBy(()->shop.preview(body)).hasMessageContaining("miễn phí");body.remove("voucher");assertThat((BigDecimal)shop.preview(body).get("deliveryFee")).isZero();}
 @Test void carrierAdministrationIsScopedAndArchivingKeepsOrderHistory(){actor(manager);Map<String,Object> body=new HashMap<>(Map.of("storeId",a.id,"name","Nhà vận chuyển mới","fee",22000,"active",true));ShippingCarrier c=(ShippingCarrier)shop.adminSave("carriers",body);body.put("id",c.id);body.put("storeId",b.id);assertThatThrownBy(()->shop.adminSave("carriers",body)).hasMessageContaining("quyền");shop.adminDelete("carriers",c.id);assertThat(c.active).isFalse();actor(vendor);assertThatThrownBy(()->shop.adminList("carriers")).hasMessageContaining("quyền");}
 @Test void managerCanManageLocalCategoriesButCannotRenameSharedOrForeignCategories(){actor(manager);Category c=(Category)shop.adminSave("categories",new HashMap<>(Map.of("name","Món cửa hàng","slug","mon-cua-hang","storeId",a.id)));assertThat(c.storeId).isEqualTo(a.id);actor(admin);Category other=(Category)shop.adminSave("categories",new HashMap<>(Map.of("name","Món shop khác","slug","mon-shop-khac","storeId",b.id)));actor(manager);assertThatThrownBy(()->shop.adminSave("categories",Map.of("id",other.id,"name","Giả mạo","slug","gia-mao","storeId",a.id))).hasMessageContaining("quyền");assertThatThrownBy(()->shop.adminDelete("categories",shop.where(Category.class,"name","Burrito").getFirst().id)).hasMessageContaining("quyền");}
 @Test void managerCustomerSearchIsScopedAndCannotTakeOverCustomerLogin(){order(a,buyer,"COMPLETED",first,1);actor(manager);assertThat(shop.adminSearch("users",buyer.email)).hasSize(1);assertThat(shop.adminSearch("users",buyer.email).getFirst()).isEqualTo(buyer);assertThat(shop.adminSearch("users",stranger.email)).isEmpty();var body=new HashMap<String,Object>(Map.of("id",buyer.id,"name","Tên liên hệ mới","phone",buyer.phone,"email",buyer.email,"roles","CUSTOMER","storeId","","active",false));shop.adminSave("users",body);assertThat(buyer.name).isEqualTo("Tên liên hệ mới");assertThat(buyer.active).isTrue();body.put("password","ChangeMe@123");assertThatThrownBy(()->shop.adminSave("users",body)).hasMessageContaining("Không đổi");}
 @Test void managerEditsOwnCommissionAndVendorEditsOnlyOwnBranding(){actor(manager);shop.adminSave("stores",storeBody(a));assertThat(a.commission).isEqualByComparingTo("0.15");assertThatThrownBy(()->shop.adminSave("stores",storeBody(b))).hasMessageContaining("quyền");actor(vendor);var body=storeBody(a);body.put("commission","0.99");body.put("introduction","Cửa hàng của chúng tôi");body.put("logo","/assets/uteb-logo.png");shop.adminSave("stores",body);assertThat(a.commission).isEqualByComparingTo("0.15");assertThat(catalog.shop(a.id).get("store")).isEqualTo(a);assertThat(a.introduction).contains("chúng tôi");}
 @Test void returnedOrderRequiresApprovalShippingReceiptThenProofAndReversesPointsOnlyOnce(){MealOrder o=order(a,buyer,"COMPLETED",first,1);o.pointsAwarded=true;buyer.points=60;actor(buyer);RefundRequest r=shop.refund(o.id,Map.of("kind","RETURN","reason","Món được giao sai so với nội dung đã đặt."));assertThat(o.status).isEqualTo("RETURN_REQUESTED");assertThatThrownBy(()->shop.sendReturn(o.id)).hasMessageContaining("chưa được duyệt");actor(manager);shop.refundDecision(r.id,Map.of("status","APPROVED","returnInstructions","Vui lòng gửi đến 123 đường kiểm thử."));assertThatThrownBy(()->shop.refundDecision(r.id,Map.of("status","REFUNDED","evidence","Giao dịch kiểm thử"))).hasMessageContaining("nhận lại hàng");actor(buyer);shop.sendReturn(o.id);assertThat(o.status).isEqualTo("RETURNING");actor(manager);shop.refundDecision(r.id,Map.of("status","RECEIVED"));assertThat(o.status).isEqualTo("RETURNED");shop.refundDecision(r.id,Map.of("status","REFUNDED","evidence","BANK-REFUND-123"));assertThat(o.paymentStatus).isEqualTo("REFUNDED");assertThat(o.status).isEqualTo("REFUNDED");assertThat(buyer.points).isZero();assertThatThrownBy(()->shop.refundDecision(r.id,Map.of("status","REFUNDED","evidence","BANK-REFUND-123"))).hasMessageContaining("nhận lại hàng");}
 @Test void rejectionRestoresCompletedOrderAndForeignOperatorsCannotApprove(){MealOrder o=order(a,buyer,"COMPLETED",first,1);actor(buyer);RefundRequest r=shop.refund(o.id,Map.of("kind","RETURN","reason","Yêu cầu kiểm tra chất lượng bữa ăn này."));actor(account("MANAGER",b.id));assertThatThrownBy(()->shop.refundDecision(r.id,Map.of("status","APPROVED"))).hasMessageContaining("quyền");actor(manager);shop.refundDecision(r.id,Map.of("status","REJECTED","reply","Không đủ điều kiện trả hàng theo kiểm tra."));assertThat(o.status).isEqualTo("COMPLETED");assertThat(o.paymentStatus).isEqualTo("PAID");}
 @Test void pickupCannotUseDeliveryPickupStepAndShipperCannotSkipSteps(){MealOrder o=order(a,buyer,"READY",first,1);o.shipperId=shipper.id;actor(shipper);assertThatThrownBy(()->shop.status(o.id,Map.of("status","DELIVERING"))).hasMessageContaining("trạng thái");shop.status(o.id,Map.of("status","PICKED_UP"));shop.status(o.id,Map.of("status","DELIVERING"));assertThat(o.status).isEqualTo("DELIVERING");o.type="PICKUP";o.status="READY";actor(manager);assertThatThrownBy(()->shop.status(o.id,Map.of("status","PICKED_UP"))).hasMessageContaining("trạng thái");}
 @Test void shipperStatsExcludeOwnPurchasesAndIncludeAssignedStatusCounts(){order(a,shipper,"COMPLETED",first,10);MealOrder assigned=order(b,buyer,"DELIVERING",foreign,1);assigned.shipperId=shipper.id;var data=statistics.dashboard(shipper,null,null,null);assertThat(data.get("totalOrders")).isEqualTo(1L);assertThat(((Map<?,?>)data.get("byStatus")).get("DELIVERING")).isEqualTo(1L);assertThat(data.get("assignedOnly")).isEqualTo(true);}
 @Test void recentRevisitMovesExistingMarkToTheTop(){actor(buyer);shop.mark(first.id,"RECENT");shop.mark(second.id,"RECENT");
  // Distinct historical visits keep this test independent of the operating system clock resolution.
  for(UserMark mark:shop.where(UserMark.class,"userId",buyer.id))mark.createdAt=LocalDateTime.now().minusDays(mark.productId.equals(first.id)?2:1);
  em.flush();shop.mark(first.id,"RECENT");List<?> marks=(List<?>)shop.accountData().get("marks");assertThat(marks).hasSize(2);assertThat(((UserMark)marks.getFirst()).productId).isEqualTo(first.id);}
}
