package vn.uteb;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import static org.mockito.Mockito.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static vn.uteb.Domain.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:utebtest;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","uteb.vnpay.secret=test-signing-secret-only","uteb.mail.enabled=true"})
@AutoConfigureMockMvc @Transactional
class CommerceTest {
 @Autowired ShopService s;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired Payments payments;
 @MockitoBean JavaMailSender registrationMail;
 @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager registrationEm;

 Map<String,Object> registration(){return new HashMap<>(Map.of("email","new-registration@example.com","name","New Customer","phone","0901234567","password","Register@123"));}
 ResultActions registerRequest(Map<String,Object> body)throws Exception{
  return mvc.perform(post("/shop-api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)));
 }
 Account registeredAccount() {
  registrationEm.flush();registrationEm.clear();
  return s.byEmail("new-registration@example.com");
 }
 String registrationOtp(){
  ArgumentCaptor<SimpleMailMessage> message=ArgumentCaptor.forClass(SimpleMailMessage.class);
  verify(registrationMail).send(message.capture());
  assertThat(message.getValue().getTo()).containsExactly("new-registration@example.com");
  var matcher=java.util.regex.Pattern.compile("\\b[0-9]{6}\\b").matcher(Objects.requireNonNull(message.getValue().getText()));
  assertThat(matcher.find()).isTrue();return matcher.group();
 }
 @Test void registrationSucceedsWithBcryptAndUnverifiedCustomer()throws Exception{
  Map<String,Object> body=registration();body.put("email","  NEW-REGISTRATION@EXAMPLE.COM  ");
  registerRequest(body).andExpect(status().isOk());
  Account a=registeredAccount();assertThat(a).isNotNull();
  assertThat(a.name).isEqualTo("New Customer");assertThat(a.phone).isEqualTo("0901234567");
  assertThat(a.active).isTrue();assertThat(a.verified).isFalse();assertThat(a.roles).isEqualTo("CUSTOMER");
  assertThat(a.passwordHash).startsWith("$2").isNotEqualTo(body.get("password"));
  assertThat(new BCryptPasswordEncoder().matches("Register@123",a.passwordHash)).isTrue();
  String otp=registrationOtp();assertThat(a.otpPurpose).isEqualTo("VERIFY");
  assertThat(new BCryptPasswordEncoder().matches(otp,a.otpHash)).isTrue();
 }
 @Test void registrationRejectsDuplicateEmailIgnoringCase()throws Exception{
  Account existing=s.byEmail("customer@uteb.vn");String hash=existing.passwordHash;
  Map<String,Object> body=registration();body.put("email","  CUSTOMER@UTEB.VN  ");
  registerRequest(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Email đã được đăng ký."));
  assertThat(s.byEmail("customer@uteb.vn").passwordHash).isEqualTo(hash);
  verifyNoInteractions(registrationMail);
 }
 static java.util.stream.Stream<Arguments> invalidRegistrations(){return java.util.stream.Stream.of(
  Arguments.of("email","","Email không hợp lệ."),Arguments.of("email","invalid-email","Email không hợp lệ."),
  Arguments.of("email","a".repeat(150)+"@example.com","Email không hợp lệ."),
  Arguments.of("name"," ","Tên cần từ 2 đến 100 ký tự."),Arguments.of("name","a".repeat(101),"Tên cần từ 2 đến 100 ký tự."),
  Arguments.of("phone","invalid-phone","Số điện thoại không hợp lệ."),Arguments.of("phone","123","Số điện thoại không hợp lệ."),
  Arguments.of("password","","Mật khẩu cần từ 8 đến 100 ký tự."),Arguments.of("password","short","Mật khẩu cần từ 8 đến 100 ký tự."),
  Arguments.of("password","a".repeat(101),"Mật khẩu cần từ 8 đến 100 ký tự."),
  Arguments.of("password","a".repeat(73),"Mật khẩu đăng ký không được vượt quá 72 byte UTF-8."),
  Arguments.of("password","ậ".repeat(25),"Mật khẩu đăng ký không được vượt quá 72 byte UTF-8."),
  Arguments.of("email",null,"Email không hợp lệ."),Arguments.of("name",null,"Tên cần từ 2 đến 100 ký tự."),
  Arguments.of("password",null,"Mật khẩu cần từ 8 đến 100 ký tự."));}
 @ParameterizedTest @MethodSource("invalidRegistrations")
 void registrationRejectsInvalidData(String field,Object value,String message)throws Exception{
  long before=s.all(Account.class).size();Map<String,Object> body=registration();
  if(value==null)body.remove(field);else body.put(field,value);
  registerRequest(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(message));
  assertThat(s.all(Account.class)).hasSize((int)before);verifyNoInteractions(registrationMail);
 }
 @Test void registrationUnverifiedAccountCannotLogin()throws Exception{
  registerRequest(registration()).andExpect(status().isOk());assertThat(registeredAccount().verified).isFalse();
  mvc.perform(post("/shop-api/auth/login").with(csrf()).contentType("application/json")
   .content(json.writeValueAsString(Map.of("email","new-registration@example.com","password","Register@123"))))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").doesNotExist())
   .andExpect(jsonPath("$.message").value("Tài khoản chưa xác thực hoặc đã bị khóa."));
 }
 @Test void registrationValidOtpVerifiesNewAccount()throws Exception{
  registerRequest(registration()).andExpect(status().isOk());assertThat(registeredAccount().verified).isFalse();
  mvc.perform(post("/shop-api/auth/otp/verify").with(csrf()).contentType("application/json")
   .content(json.writeValueAsString(Map.of("email","new-registration@example.com","otp",registrationOtp()))))
   .andExpect(status().isOk());
  Account a=registeredAccount();assertThat(a.verified).isTrue();assertThat(a.otpHash).isNull();
 }
 @Test void registrationWrongOtpDoesNotVerifyAccount()throws Exception{
  registerRequest(registration()).andExpect(status().isOk());String otp=registrationOtp();
  String wrong=otp.equals("000000")?"000001":"000000";
  mvc.perform(post("/shop-api/auth/otp/verify").with(csrf()).contentType("application/json")
   .content(json.writeValueAsString(Map.of("email","new-registration@example.com","otp",wrong))))
   .andExpect(status().isBadRequest());
  Account a=registeredAccount();assertThat(a.verified).isFalse();assertThat(a.otpHash).isNotNull();
 }
 @ParameterizedTest @ValueSource(strings={"ADMIN","MANAGER","STAFF","VENDOR","SHIPPER","CUSTOMER,ADMIN"})
 void registrationCannotGrantPrivilegedRole(String role)throws Exception{
  Map<String,Object> body=registration();body.put("role",role);body.put("roles",role);body.put("verified",true);body.put("storeId",1);
  registerRequest(body).andExpect(status().isOk());
  Account a=registeredAccount();assertThat(a.roles).isEqualTo("CUSTOMER");assertThat(a.verified).isFalse();assertThat(a.storeId).isNull();
 }
 @Autowired JwtService loginJwt;

 String loginBody(String email,String password)throws Exception{
  return json.writeValueAsString(Map.of("email",email,"password",password));
 }
 String loginToken(MvcResult result)throws Exception{
  return json.readTree(result.getResponse().getContentAsByteArray()).get("token").asText();
 }
 MvcResult apiLogin()throws Exception{
  SecurityContextHolder.clearContext();
  return mvc.perform(post("/api/auth/login").contentType("application/json")
   .content(loginBody("customer@uteb.vn","Uteb@12345"))).andExpect(status().isOk()).andReturn();
 }
 @ParameterizedTest @ValueSource(strings={"customer","staff","manager","admin","vendor","shipper"})
 void loginSessionAndJwtHaveAccountAuthorities(String account)throws Exception{
  String email=account+"@uteb.vn";MockHttpSession session=new MockHttpSession();String oldId=session.getId();
  MvcResult result=mvc.perform(post("/shop-api/auth/login").session(session).with(csrf()).contentType("application/json")
   .content(loginBody(email,"Uteb@12345"))).andExpect(status().isOk()).andReturn();
  assertThat(session.getId()).isNotEqualTo(oldId);
  SecurityContext context=(SecurityContext)session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
  String[] expected=Arrays.stream(s.byEmail(email).roles.split(",")).map(role->"ROLE_"+role).toArray(String[]::new);
  assertThat(context.getAuthentication().getName()).isEqualTo(email);
  assertThat(context.getAuthentication().getAuthorities()).extracting("authority").containsExactlyInAnyOrder((Object[])expected);
  assertThat(loginJwt.verify(loginToken(result)).getAuthorities()).extracting("authority").containsExactlyInAnyOrder((Object[])expected);
  SecurityContextHolder.clearContext();
  mvc.perform(get("/shop-api/account").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(email));
  SecurityContextHolder.clearContext();
  mvc.perform(get("/api/account").session(session)).andExpect(status().isUnauthorized());
  SecurityContextHolder.clearContext();
  mvc.perform(get("/api/account").header("Authorization","Bearer "+loginToken(result)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(email));
 }
 @ParameterizedTest @ValueSource(strings={"/shop-api","/api"})
 void loginRejectsWrongPassword(String prefix)throws Exception{
  MvcResult result=mvc.perform(post(prefix+"/auth/login").with(csrf()).contentType("application/json")
   .content(loginBody("customer@uteb.vn","Wrong@12345"))).andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.message").value("Email hoặc mật khẩu không đúng."))
   .andExpect(jsonPath("$.token").doesNotExist()).andReturn();
  assertThat(result.getRequest().getSession(false)).isNull();
 }
 @ParameterizedTest @ValueSource(strings={"/shop-api","/api"})
 void loginRejectsLockedAccount(String prefix)throws Exception{
  s.byEmail("customer@uteb.vn").active=false;registrationEm.flush();registrationEm.clear();
  MvcResult result=mvc.perform(post(prefix+"/auth/login").with(csrf()).contentType("application/json")
   .content(loginBody("customer@uteb.vn","Uteb@12345"))).andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.message").value("Tài khoản chưa xác thực hoặc đã bị khóa."))
   .andExpect(jsonPath("$.token").doesNotExist()).andReturn();
  assertThat(result.getRequest().getSession(false)).isNull();
 }
 @ParameterizedTest @ValueSource(strings={"/shop-api","/api"})
 void loginRequiredForProtectedAccount(String prefix)throws Exception{
  SecurityContextHolder.clearContext();
  mvc.perform(get(prefix+"/account")).andExpect(status().isUnauthorized());
 }
 // csrf() in other tests replaces the filter repository; reload the real cookie-based chain here.
 @org.springframework.test.annotation.DirtiesContext(methodMode=org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
 @Test void loginLogoutUseBrowserCsrfAndInvalidateOnlyCurrentSession()throws Exception{
  MvcResult bootstrap=mvc.perform(get("/shop-api/public/bootstrap")).andExpect(status().isOk()).andReturn();
  Cookie before=bootstrap.getResponse().getCookie("XSRF-TOKEN");assertThat(before).isNotNull();
  assertThat(json.readTree(bootstrap.getResponse().getContentAsByteArray()).get("csrf").asText()).isEqualTo(before.getValue());
  String body=loginBody("customer@uteb.vn","Uteb@12345");
  mvc.perform(post("/shop-api/auth/login").cookie(before).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/shop-api/auth/login").cookie(before).header("X-XSRF-TOKEN","wrong").contentType("application/json").content(body))
   .andExpect(status().isForbidden());
  MvcResult loggedIn=mvc.perform(post("/shop-api/auth/login").cookie(before).header("X-XSRF-TOKEN",before.getValue())
   .contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
  assertThat(loggedIn.getResponse().getCookie("XSRF-TOKEN")).isNotNull();
  assertThat(loggedIn.getResponse().getCookie("XSRF-TOKEN").getMaxAge()).isZero();
  MockHttpSession session=(MockHttpSession)loggedIn.getRequest().getSession(false);assertThat(session).isNotNull();
  SecurityContextHolder.clearContext();
  MvcResult afterLogin=mvc.perform(get("/shop-api/public/bootstrap").session(session)).andExpect(status().isOk()).andReturn();
  Cookie after=afterLogin.getResponse().getCookie("XSRF-TOKEN");assertThat(after).isNotNull();
  assertThat(after.getValue()).isNotEqualTo(before.getValue());
  SecurityContextHolder.clearContext();
  MvcResult secondLogin=mvc.perform(post("/shop-api/auth/login").cookie(after).header("X-XSRF-TOKEN",after.getValue()).contentType("application/json").content(body))
   .andExpect(status().isOk()).andReturn();
  MockHttpSession other=(MockHttpSession)secondLogin.getRequest().getSession(false);
  SecurityContextHolder.clearContext();
  mvc.perform(post("/shop-api/auth/logout").session(session).cookie(after)).andExpect(status().isForbidden());
  mvc.perform(post("/shop-api/auth/logout").session(session).cookie(after).header("X-XSRF-TOKEN",before.getValue()))
   .andExpect(status().isForbidden());
  assertThat(session.isInvalid()).isFalse();
  SecurityContextHolder.clearContext();
  mvc.perform(get("/api/account").header("Authorization","Bearer "+loginToken(loggedIn))).andExpect(status().isOk());
  SecurityContextHolder.clearContext();
  MvcResult logout=mvc.perform(post("/shop-api/auth/logout").session(session).cookie(after).header("X-XSRF-TOKEN",after.getValue()))
   .andExpect(status().isOk()).andReturn();
  assertThat(session.isInvalid()).isTrue();assertThat(other.isInvalid()).isFalse();
  assertThat(logout.getResponse().getCookie("XSRF-TOKEN").getMaxAge()).isZero();
  SecurityContextHolder.clearContext();
  mvc.perform(get("/shop-api/account").cookie(new Cookie("JSESSIONID",session.getId()))).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/account").header("Authorization","Bearer "+loginToken(loggedIn))).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/account").header("Authorization","Bearer "+loginToken(secondLogin))).andExpect(status().isUnauthorized());
  mvc.perform(get("/shop-api/account").session(other)).andExpect(status().isOk());
  SecurityContextHolder.clearContext();
  MvcResult afterLogout=mvc.perform(get("/shop-api/public/bootstrap")).andExpect(status().isOk()).andReturn();
  assertThat(afterLogout.getResponse().getCookie("XSRF-TOKEN").getValue()).isNotEqualTo(after.getValue());
 }
 @Test void apiLogoutRevokesOldJwtAndNewLoginWorksWithoutSessionOrCsrf()throws Exception{
  MvcResult first=apiLogin();String oldToken=loginToken(first);assertThat(first.getRequest().getSession(false)).isNull();
  mvc.perform(get("/api/account").header("Authorization","Bearer "+oldToken)).andExpect(status().isOk());
  SecurityContextHolder.clearContext();
  mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+oldToken)).andExpect(status().isOk());
  registrationEm.flush();registrationEm.clear();SecurityContextHolder.clearContext();
  mvc.perform(get("/api/account").header("Authorization","Bearer "+oldToken)).andExpect(status().isUnauthorized());
  MvcResult second=apiLogin();String newToken=loginToken(second);assertThat(newToken).isNotEqualTo(oldToken);
  assertThat(second.getRequest().getSession(false)).isNull();
  mvc.perform(get("/api/account").header("Authorization","Bearer "+newToken)).andExpect(status().isOk());
 }
 String signedLoginToken(long expiration)throws Exception{
  Account a=s.byEmail("customer@uteb.vn");
  String header=JwtService.enc("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  String payload=JwtService.enc(json.writeValueAsBytes(Map.of("sub",a.email,"exp",expiration,"ver",a.tokenVersion)));
  String raw=header+"."+payload;return raw+"."+JwtService.enc(loginJwt.sign(raw));
 }
 @Test void jwtPastExpirationIsRejectedByApi()throws Exception{
  String expired=signedLoginToken(Instant.now().minusSeconds(60).getEpochSecond());
  mvc.perform(get("/api/account").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
 }
 @Test void jwtExpirationEqualToNowIsRejected()throws Exception{
  Instant fixed=Instant.parse("2026-10-16T00:00:00Z");
  String expired=signedLoginToken(fixed.getEpochSecond());String valid=signedLoginToken(fixed.plusSeconds(1).getEpochSecond());
  try(var time=mockStatic(Instant.class,CALLS_REAL_METHODS)){
   time.when(Instant::now).thenReturn(fixed);
   assertThatThrownBy(()->loginJwt.verify(expired)).isInstanceOf(SecurityException.class);
   assertThat(loginJwt.verify(valid).getName()).isEqualTo("customer@uteb.vn");
  }
 }
 Product custom(Long store){return s.all(Product.class).stream().filter(p->p.storeId.equals(store)&&p.customizable).findFirst().orElseThrow();}
 String ingredients(){return List.of("PROTEIN","RICE","BEANS","SAUCE").stream().map(g->s.all(Ingredient.class).stream().filter(i->i.groupName.equals(g)&&i.price.signum()==0).findFirst().orElseThrow().id.toString()).collect(java.util.stream.Collectors.joining(","));}
 Map<String,Object> checkout(String key,String type){return new HashMap<>(Map.of("checkoutKey",key,"type",type,"contactName","Test Customer","phone","0901234567","address","25 Võ Văn Ngân, Thủ Đức","paymentMethod","COD","scheduledAt","ASAP"));}
 @Test void publicCatalogAndAnonymousProtection()throws Exception{mvc.perform(get("/shop-api/public/bootstrap")).andExpect(status().isOk()).andExpect(jsonPath("$.products.length()").value(48)).andExpect(jsonPath("$.csrf").exists());mvc.perform(get("/shop-api/cart")).andExpect(status().isUnauthorized());}
 @Test @WithMockUser(username="customer@uteb.vn")void csrfAndPrivilegeChecks()throws Exception{mvc.perform(post("/shop-api/cart").contentType("application/json").content("{}" )).andExpect(status().isForbidden());mvc.perform(get("/shop-api/admin/users")).andExpect(status().isForbidden());}
 @Test @WithMockUser(username="customer@uteb.vn")void checkoutRecalculatesIdempotencyAndCancel(){Product p=custom(1L);int stock=p.stock;s.cart(Map.of("productId",p.id,"quantity",2,"ingredientIds",ingredients()));Map<String,Object>b=checkout("unique-checkout-0001","PICKUP");b.put("total",1);MealOrder o=s.checkout(b).getFirst();assertThat(o.total).isGreaterThan(new BigDecimal("1"));assertThat(p.stock).isEqualTo(stock-2);assertThat(s.checkout(b).getFirst().id).isEqualTo(o.id);assertThat(s.where(OrderLine.class,"orderId",o.id)).hasSize(1);s.status(o.id,Map.of("status","CANCELLED"));assertThat(p.stock).isEqualTo(stock);assertThat(o.status).isEqualTo("CANCELLED");assertThatThrownBy(()->s.status(o.id,Map.of("status","CONFIRMED"))).hasMessageContaining("Không thể chuyển");}
 @Test @WithMockUser(username="customer@uteb.vn")void multiStoreVoucherAllocation(){Product p1=custom(1L),p2=custom(2L);s.cart(Map.of("productId",p1.id,"quantity",2,"ingredientIds",ingredients()));s.cart(Map.of("productId",p2.id,"quantity",2,"ingredientIds",ingredients()));Map<String,Object>b=checkout("multi-store-test-0001","DELIVERY");b.put("voucher","UTEB20");Map<String,Object>q=s.preview(b);List<MealOrder> os=s.checkout(b);assertThat(os).hasSize(2);assertThat(os.stream().map(o->o.discount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo((BigDecimal)q.get("discount"));assertThat(os.stream().map(o->o.total).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo((BigDecimal)q.get("total"));assertThat(s.findVoucher("UTEB20").used).isEqualTo(1);}
 @Test @WithMockUser(username="vendor2@uteb.vn")void vendorCannotReadOtherStore(){MealOrder o=s.all(MealOrder.class).getFirst();assertThatThrownBy(()->s.accessibleOrder(o.id)).hasMessageContaining("quyền");assertThat(s.adminList("products")).allMatch(x->((Product)x).storeId.equals(2L));assertThatThrownBy(()->s.adminDelete("products",custom(1L).id)).hasMessageContaining("quyền");}
 @Test @WithMockUser(username="customer@uteb.vn")void invalidCustomizationStockAndVoucher(){Product p=custom(1L);assertThatThrownBy(()->s.cart(Map.of("productId",p.id,"quantity",0,"ingredientIds",ingredients()))).hasMessageContaining("Số lượng");assertThatThrownBy(()->s.cart(Map.of("productId",p.id,"quantity",1,"ingredientIds",""))).hasMessageContaining("thành phần");s.cart(Map.of("productId",p.id,"quantity",1,"ingredientIds",ingredients()));Map<String,Object>b=checkout("voucher-test-0001","PICKUP");b.put("voucher","BURRITO50");assertThatThrownBy(()->s.preview(b)).hasMessageContaining("điều kiện");}
 @Test @WithMockUser(username="customer@uteb.vn")void reviewEligibilityAndDuplicate(){MealOrder completed=s.all(MealOrder.class).stream().filter(o->o.status.equals("COMPLETED")).findFirst().orElseThrow();OrderLine l=s.where(OrderLine.class,"orderId",completed.id).getFirst();String text="Burrito rất ngon, nguyên liệu tươi và nhân vừa miệng. Tôi sẽ đặt lại lần sau.";Review r=s.review(Map.of("orderLineId",l.id,"stars",5,"text",text));assertThat(r.stars).isEqualTo(5);assertThatThrownBy(()->s.review(Map.of("orderLineId",l.id,"stars",5,"text",text))).hasMessageContaining("đã đánh giá");MealOrder fresh=s.all(MealOrder.class).stream().filter(o->o.status.equals("NEW")).findFirst().orElseThrow();assertThatThrownBy(()->s.review(Map.of("orderLineId",s.where(OrderLine.class,"orderId",fresh.id).getFirst().id,"stars",5,"text",text))).hasMessageContaining("nhận món");}
 @Test @WithMockUser(username="customer@uteb.vn")void rewardVoucherOnlyOnce(){int before=s.me().points;Voucher v=s.redeem("DRINK");assertThat(s.me().points).isEqualTo(before-500);assertThat(v.userId).isEqualTo(s.me().id);assertThat(v.usageLimit).isEqualTo(1);assertThat(s.where(RewardLog.class,"userId",s.me().id)).anyMatch(l->l.points==-500);}
 @Test void forgedPaymentRejected(){assertThat(payments.vnIpn(Map.of("vnp_SecureHash","fake","vnp_TxnRef","1"))).containsEntry("RspCode","97");assertThatThrownBy(()->payments.momoIpn(Map.of("signature","fake"))).hasMessageContaining("cấu hình");}
 @Test void otpAttemptsExpiryAndSingleUse(){Account a=s.byEmail("customer@uteb.vn");a.otpHash=s.hashPassword("123456");a.otpPurpose="VERIFY";a.otpExpires=java.time.LocalDateTime.now().plusMinutes(5);a.otpAttempts=0;assertThat(s.checkOtp(Map.of("email",a.email,"otp","000000"))).isFalse();assertThat(a.otpAttempts).isEqualTo(1);assertThat(s.checkOtp(Map.of("email",a.email,"otp","123456"))).isTrue();assertThat(s.checkOtp(Map.of("email",a.email,"otp","123456"))).isFalse();a.otpHash=s.hashPassword("123456");a.otpExpires=java.time.LocalDateTime.now().minusSeconds(1);assertThat(s.checkOtp(Map.of("email",a.email,"otp","123456"))).isFalse();}
 @Test @WithMockUser(username="customer@uteb.vn")void paymentExpiryReturnsStockOnce(){Product p=custom(1L);int before=p.stock;s.cart(Map.of("productId",p.id,"quantity",1,"ingredientIds",ingredients()));Map<String,Object>b=checkout("expire-test-order-01","PICKUP");b.put("paymentMethod","VNPAY");MealOrder o=s.checkout(b).getFirst();o.paymentExpires=java.time.LocalDateTime.now().minusMinutes(1);s.expirePayments();assertThat(o.paymentStatus).isEqualTo("EXPIRED");assertThat(p.stock).isEqualTo(before);s.expirePayments();assertThat(p.stock).isEqualTo(before);}
 @Test @WithMockUser(username="customer@uteb.vn")void verifiedIpnAndRepeatedCallback(){Product p=custom(1L);s.cart(Map.of("productId",p.id,"quantity",1,"ingredientIds",ingredients()));Map<String,Object>b=checkout("ipn-test-order-0001","PICKUP");b.put("paymentMethod","VNPAY");MealOrder o=s.checkout(b).getFirst();Map<String,String>ipn=new TreeMap<>(Map.of("vnp_TxnRef",o.id.toString(),"vnp_Amount",o.total.multiply(new BigDecimal("100")).toPlainString(),"vnp_TransactionNo","TEST-REF-001","vnp_ResponseCode","00","vnp_TransactionStatus","00"));ipn.put("vnp_SecureHash",Payments.sign("HmacSHA512","test-signing-secret-only",Payments.vnRaw(ipn)));assertThat(payments.vnIpn(ipn)).containsEntry("RspCode","00");assertThat(o.paymentStatus).isEqualTo("PAID");assertThat(payments.vnIpn(ipn)).containsEntry("RspCode","02");}
 @Test @WithMockUser(username="customer@uteb.vn")void groupLocksAndTransfersOnce(){GroupMeal g=s.createGroup(Map.of("storeId",1,"title","Test group"));Product p=custom(1L);s.groupAdd(g.code,Map.of("productId",p.id,"quantity",1,"ingredientIds",ingredients()));s.groupToCart(g.code);assertThat(g.status).isEqualTo("LOCKED");assertThat(s.where(CartLine.class,"userId",s.me().id)).hasSize(1);assertThatThrownBy(()->s.groupToCart(g.code)).hasMessageContaining("chuyển vào giỏ");}
 @Test void sessionLoginAndJwt()throws Exception{MvcResult r=mvc.perform(post("/shop-api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("email","customer@uteb.vn","password","Uteb@12345")))).andExpect(status().isOk()).andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn();String token=json.readTree(r.getResponse().getContentAsString()).get("token").asText();org.springframework.security.core.context.SecurityContextHolder.clearContext();mvc.perform(get("/api/account").header("Authorization","Bearer "+token)).andExpect(status().isOk());org.springframework.security.core.context.SecurityContextHolder.clearContext();mvc.perform(get("/api/account").header("Authorization","Bearer "+token+"x")).andExpect(status().isUnauthorized());}
}
