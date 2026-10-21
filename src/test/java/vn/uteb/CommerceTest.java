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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:utebtest;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","uteb.vnpay.secret=test-signing-secret-only","uteb.vnpay.code=TESTCODE","uteb.mail.enabled=true"})
@org.springframework.test.context.ActiveProfiles("demo")
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
 @Autowired org.springframework.transaction.PlatformTransactionManager otpTransactionManager;
 <T> T otpTransaction(java.util.function.Supplier<T> work){
  return new org.springframework.transaction.support.TransactionTemplate(otpTransactionManager).execute(status->work.get());
 }
 Account otpAccount(String purpose){
  Account a=new Account();a.email="otp-"+UUID.randomUUID()+"@example.com";a.name="OTP Test";
  a.passwordHash=s.hashPassword("Original@123");a.otpHash=s.hashPassword("123456");a.otpPurpose=purpose;
  a.otpSent=java.time.LocalDateTime.now().minusMinutes(2);a.otpExpires=java.time.LocalDateTime.now().plusMinutes(3);
  return s.save(a);
 }
 Account otpReload(String email){registrationEm.flush();registrationEm.clear();return s.byEmail(email);}
 ResultActions otpSubmit(Map<String,Object> body)throws Exception{
  return mvc.perform(post("/shop-api/auth/otp/verify").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)));
 }
 ResultActions otpSend(Map<String,Object> body)throws Exception{
  return mvc.perform(post("/shop-api/auth/otp/request").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)));
 }
 SimpleMailMessage otpMessage(){
  ArgumentCaptor<SimpleMailMessage> capture=ArgumentCaptor.forClass(SimpleMailMessage.class);
  verify(registrationMail).send(capture.capture());return capture.getValue();
 }
 String otpCode(SimpleMailMessage message){
  var matcher=java.util.regex.Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(Objects.requireNonNull(message.getText()));
  assertThat(matcher.find()).isTrue();return matcher.group();
 }
 @ParameterizedTest @ValueSource(strings={"VERIFY","RESET"})
 void otpEmailContainsSixDigitsForAccountAndDatabaseContainsOnlyHash(String purpose)throws Exception{
  Account a=otpAccount(purpose);String email=a.email;
  otpSend(Map.of("email",email,"purpose",purpose)).andExpect(status().isOk());
  SimpleMailMessage message=otpMessage();String code=otpCode(message);a=otpReload(email);
  assertThat(code).matches("[0-9]{6}");assertThat(message.getTo()).containsExactly(email);
  assertThat(message.getFrom()).isNotBlank();
  assertThat(message.getSubject()).contains(purpose.equals("VERIFY")?"xác thực tài khoản":"đặt lại mật khẩu");
  assertThat(message.getText()).contains("5 phút");
  assertThat(a.otpHash).startsWith("$2").isNotEqualTo(code);
  assertThat(new BCryptPasswordEncoder().matches(code,a.otpHash)).isTrue();
  assertThat(a.otpPurpose).isEqualTo(purpose);assertThat(a.otpExpires).isEqualTo(a.otpSent.plusMinutes(5));
  assertThat(json.readTree(json.writeValueAsBytes(a)).has("otpHash")).isFalse();
 }
 @ParameterizedTest @ValueSource(strings={"VERIFY","RESET"})
 void otpCorrectPurposeWorksWithAndWithoutExplicitPurpose(String purpose)throws Exception{
  for(boolean explicit:List.of(false,true)){
   Account a=otpAccount(purpose);String email=a.email;
   Map<String,Object> body=new HashMap<>(Map.of("email",email,"otp","123456"));
   if(purpose.equals("RESET"))body.put("password","Replacement@123");if(explicit)body.put("purpose",purpose);
   otpSubmit(body).andExpect(status().isOk());a=otpReload(email);
   assertThat(a.otpHash).isNull();assertThat(a.verified).isEqualTo(purpose.equals("VERIFY"));
   assertThat(new BCryptPasswordEncoder().matches(purpose.equals("RESET")?"Replacement@123":"Original@123",a.passwordHash)).isTrue();
  }
 }
 @ParameterizedTest @ValueSource(strings={"654321","","12345","1234567","abcdef","１２３４５６"," 123456 "})
 void otpWrongOrMalformedCodeIsRejected(String code)throws Exception{
  Account a=otpAccount("VERIFY");String email=a.email;
  otpSubmit(Map.of("email",email,"otp",code)).andExpect(status().isBadRequest());
  a=otpReload(email);assertThat(a.verified).isFalse();assertThat(a.otpAttempts).isEqualTo(1);assertThat(a.otpHash).isNotNull();
 }
 @Test void otpMissingOrNumericCodeIsRejected()throws Exception{
  Account a=otpAccount("VERIFY");
  otpSubmit(Map.of("email",a.email)).andExpect(status().isBadRequest());
  otpSubmit(Map.of("email",a.email,"otp",123456)).andExpect(status().isBadRequest());
  assertThat(otpReload(a.email).verified).isFalse();
 }
 @Test void otpFromAnotherAccountIsRejected()throws Exception{
  Account a=otpAccount("VERIFY"),b=otpAccount("VERIFY");b.otpHash=s.hashPassword("654321");
  otpSubmit(Map.of("email",b.email,"otp","123456")).andExpect(status().isBadRequest());
  assertThat(otpReload(b.email).verified).isFalse();assertThat(s.byEmail(a.email).verified).isFalse();
 }
 @ParameterizedTest @ValueSource(ints={-1,0,1})
 void otpExpirationBoundary(int secondsFromExpiry)throws Exception{
  Account a=otpAccount("VERIFY");var expiry=java.time.LocalDateTime.of(2026,10,16,12,0);
  a.otpExpires=expiry;var now=expiry.plusSeconds(secondsFromExpiry);
  try(var clock=mockStatic(java.time.LocalDateTime.class,CALLS_REAL_METHODS)){
   clock.when(java.time.LocalDateTime::now).thenReturn(now);
   assertThat(s.checkOtp(Map.of("email",a.email,"otp","123456"))).isEqualTo(secondsFromExpiry<0);
  }
  assertThat(otpReload(a.email).verified).isEqualTo(secondsFromExpiry<0);
 }
 @Test void otpMissingExpiryIsRejected()throws Exception{
  Account a=otpAccount("VERIFY");a.otpExpires=null;
  otpSubmit(Map.of("email",a.email,"otp","123456")).andExpect(status().isBadRequest());
  assertThat(otpReload(a.email).verified).isFalse();
 }
 @ParameterizedTest @ValueSource(ints={4,5})
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 void otpAttemptLimitPersistsAcrossRequests(int wrongAttempts)throws Exception{
  Account a=otpTransaction(()->otpAccount("VERIFY"));
  try{
   for(int i=1;i<=wrongAttempts;i++){
    otpSubmit(Map.of("email",a.email,"otp","654321")).andExpect(status().isBadRequest());
    assertThat(otpTransaction(()->s.byEmail(a.email).otpAttempts)).isEqualTo(i);
   }
   otpSubmit(Map.of("email",a.email,"otp","123456")).andExpect(status().is(wrongAttempts==4?200:400));
   assertThat(otpTransaction(()->s.byEmail(a.email).verified)).isEqualTo(wrongAttempts==4);
   assertThat(otpTransaction(()->s.byEmail(a.email).otpAttempts)).isEqualTo(5);
  }finally{otpTransaction(()->{registrationEm.remove(registrationEm.find(Account.class,a.id));return null;});}
 }
 @Test void otpCannotBeUsedTwice()throws Exception{
  Account a=otpAccount("VERIFY");Map<String,Object> body=Map.of("email",a.email,"otp","123456");
  otpSubmit(body).andExpect(status().isOk());assertThat(otpReload(a.email).otpHash).isNull();
  otpSubmit(body).andExpect(status().isBadRequest());
 }
 @ParameterizedTest @ValueSource(ints={59,60,61})
 void otpResendBoundaryAndState(int elapsedSeconds)throws Exception{
  Account a=otpAccount("VERIFY");var sent=java.time.LocalDateTime.of(2026,10,16,12,0);
  a.otpSent=sent;a.otpExpires=sent.plusMinutes(5);a.otpAttempts=4;String oldHash=a.otpHash;
  var now=sent.plusSeconds(elapsedSeconds);
  try(var clock=mockStatic(java.time.LocalDateTime.class,CALLS_REAL_METHODS)){
   clock.when(java.time.LocalDateTime::now).thenReturn(now);
   otpSend(Map.of("email",a.email,"purpose","VERIFY")).andExpect(status().is(elapsedSeconds<60?400:200));
  }
  a=otpReload(a.email);
  if(elapsedSeconds<60){assertThat(a.otpHash).isEqualTo(oldHash);assertThat(a.otpAttempts).isEqualTo(4);verifyNoInteractions(registrationMail);}
  else{assertThat(a.otpHash).isNotEqualTo(oldHash);assertThat(a.otpSent).isEqualTo(now);
   assertThat(a.otpExpires).isEqualTo(now.plusMinutes(5));assertThat(a.otpAttempts).isZero();
   assertThat(new BCryptPasswordEncoder().matches(otpCode(otpMessage()),a.otpHash)).isTrue();}
 }
 @ParameterizedTest @ValueSource(strings={"VERIFY","RESET"})
 void otpCannotBeUsedForOppositeOperation(String storedPurpose)throws Exception{
  Account a=otpAccount(storedPurpose);String hash=a.passwordHash;
  Map<String,Object> body=new HashMap<>(Map.of("email",a.email,"otp","123456"));
  if(storedPurpose.equals("VERIFY"))body.put("password","Replacement@123");
  otpSubmit(body).andExpect(status().isBadRequest());
  body.put("purpose",storedPurpose);otpSubmit(body).andExpect(status().isBadRequest());
  a=otpReload(a.email);assertThat(a.verified).isFalse();assertThat(a.passwordHash).isEqualTo(hash);
  assertThat(a.otpHash).isNotNull();assertThat(a.otpAttempts).isZero();
 }
 @ParameterizedTest @ValueSource(strings={"","INVALID","RESET"})
 void otpExplicitPurposeMustMatchOperationAndDatabase(String requestedPurpose)throws Exception{
  Account a=otpAccount("VERIFY");
  otpSubmit(Map.of("email",a.email,"otp","123456","purpose",requestedPurpose)).andExpect(status().isBadRequest());
  a=otpReload(a.email);assertThat(a.verified).isFalse();assertThat(a.otpHash).isNotNull();assertThat(a.otpAttempts).isZero();
 }
 @ParameterizedTest @ValueSource(strings={"","INVALID","verify"})
 void otpRequestRejectsInvalidPurpose(String purpose)throws Exception{
  Account a=otpAccount("VERIFY");String hash=a.otpHash;
  otpSend(Map.of("email",a.email,"purpose",purpose)).andExpect(status().isBadRequest());
  assertThat(otpReload(a.email).otpHash).isEqualTo(hash);verifyNoInteractions(registrationMail);
 }
 @Test void otpRequestRejectsMissingPurpose()throws Exception{
  otpSend(Map.of("email","customer@uteb.vn")).andExpect(status().isBadRequest());verifyNoInteractions(registrationMail);
 }
 @ParameterizedTest @ValueSource(strings={"","INVALID"})
 void otpInvalidStoredPurposeIsRejected(String purpose)throws Exception{
  Account a=otpAccount(purpose);
  otpSubmit(Map.of("email",a.email,"otp","123456")).andExpect(status().isBadRequest());
  assertThat(otpReload(a.email).verified).isFalse();
 }
 @Test void otpNullStoredPurposeIsRejected()throws Exception{
  Account a=otpAccount(null);
  otpSubmit(Map.of("email",a.email,"otp","123456")).andExpect(status().isBadRequest());assertThat(otpReload(a.email).verified).isFalse();
 }
 @Test void otpUnknownEmailDoesNotSendMail()throws Exception{
  otpSend(Map.of("email","missing-otp@example.com","purpose","VERIFY")).andExpect(status().isOk())
   .andExpect(jsonPath("$.message").value("Nếu email tồn tại, mã xác thực sẽ được gửi."));verifyNoInteractions(registrationMail);
 }
 @Test void otpVerifiedAccountDoesNotReceiveAnotherVerifyCode()throws Exception{
  Account a=otpAccount("VERIFY");a.verified=true;String hash=a.otpHash;
  otpSend(Map.of("email",a.email,"purpose","VERIFY")).andExpect(status().isOk())
   .andExpect(jsonPath("$.message").value("Tài khoản đã xác thực."));
  assertThat(otpReload(a.email).otpHash).isEqualTo(hash);verifyNoInteractions(registrationMail);
 }
 @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 void otpSmtpFailureRollsBackOtpState()throws Exception{
  Account a=otpTransaction(()->{Account created=otpAccount("VERIFY");created.otpAttempts=3;registrationEm.flush();registrationEm.refresh(created);return created;});
  try{
   doThrow(new org.springframework.mail.MailSendException("SMTP test failure")).when(registrationMail).send(any(SimpleMailMessage.class));
   otpSend(Map.of("email",a.email,"purpose","RESET")).andExpect(status().isServiceUnavailable());
   Account after=otpTransaction(()->s.byEmail(a.email));
   assertThat(after.otpHash).isEqualTo(a.otpHash);assertThat(after.otpPurpose).isEqualTo(a.otpPurpose);
   assertThat(after.otpSent).isEqualTo(a.otpSent);assertThat(after.otpExpires).isEqualTo(a.otpExpires);
   assertThat(after.otpAttempts).isEqualTo(3);assertThat(after.passwordHash).isEqualTo(a.passwordHash);assertThat(after.verified).isFalse();
   verify(registrationMail).send(any(SimpleMailMessage.class));
  }finally{otpTransaction(()->{registrationEm.remove(registrationEm.find(Account.class,a.id));return null;});}
 }
 Account passwordAccount(){
  Account a=new Account();a.email="password-"+UUID.randomUUID()+"@example.com";a.name="Password Test";
  a.active=true;a.verified=true;a.passwordHash=s.hashPassword("Original@123");return s.save(a);
 }
 MvcResult passwordApiLogin(String email,String password)throws Exception{
  SecurityContextHolder.clearContext();
  return mvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody(email,password)))
   .andExpect(status().isOk()).andReturn();
 }
 MvcResult passwordWebLogin(String email,String password)throws Exception{
  SecurityContextHolder.clearContext();
  return mvc.perform(post("/shop-api/auth/login").with(csrf()).contentType("application/json").content(loginBody(email,password)))
   .andExpect(status().isOk()).andReturn();
 }
 Map<String,Object> resetBody(String email,String otp,String password){
  return Map.of("email",email,"otp",otp,"password",password);
 }
 ResultActions changePasswordApi(String token,String current,String next)throws Exception{
  return mvc.perform(put("/api/account/password").header("Authorization","Bearer "+token)
   .contentType("application/json").content(json.writeValueAsString(Map.of("currentPassword",current,"password",next))));
 }
 @Test void passwordResetUsesResetOtpAndRevokesOldJwt()throws Exception{
  Account a=passwordAccount();String email=a.email;String oldHash=a.passwordHash;int oldVersion=a.tokenVersion;
  String oldJwt=loginToken(passwordApiLogin(email,"Original@123"));
  otpSend(Map.of("email",email,"purpose","RESET")).andExpect(status().isOk());
  String otp=otpCode(otpMessage());a=otpReload(email);assertThat(a.otpPurpose).isEqualTo("RESET");
  otpSubmit(resetBody(email,otp,"Replacement@123")).andExpect(status().isOk());
  a=otpReload(email);assertThat(a.passwordHash).startsWith("$2").isNotEqualTo(oldHash);
  assertThat(new BCryptPasswordEncoder().matches("Replacement@123",a.passwordHash)).isTrue();
  assertThat(a.tokenVersion).isEqualTo(oldVersion+1);assertThat(a.otpHash).isNull();
  SecurityContextHolder.clearContext();
  mvc.perform(get("/api/account").header("Authorization","Bearer "+oldJwt)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody(email,"Original@123")))
   .andExpect(status().isBadRequest());
  String newJwt=loginToken(passwordApiLogin(email,"Replacement@123"));
  mvc.perform(get("/api/account").header("Authorization","Bearer "+newJwt)).andExpect(status().isOk());
  otpSubmit(resetBody(email,otp,"Another@123")).andExpect(status().isBadRequest());
 }
 @Test void passwordResetKeepsInactiveAndUnverifiedStates()throws Exception{
  Account a=otpAccount("RESET");a.active=false;a.verified=false;
  otpSubmit(resetBody(a.email,"123456","Replacement@123")).andExpect(status().isOk());
  a=otpReload(a.email);assertThat(a.active).isFalse();assertThat(a.verified).isFalse();
  mvc.perform(post("/api/auth/login").contentType("application/json")
   .content(loginBody(a.email,"Replacement@123"))).andExpect(status().isBadRequest());
 }
 @ParameterizedTest @ValueSource(strings={"654321","EXPIRED","VERIFY","USED"})
 void passwordResetRejectsBadOtpWithoutChangingCredentials(String failure)throws Exception{
  Account a=otpAccount("RESET");a.verified=true;String hash=a.passwordHash;int version=a.tokenVersion;
  if(failure.equals("EXPIRED"))a.otpExpires=java.time.LocalDateTime.now().minusSeconds(1);
  if(failure.equals("VERIFY"))a.otpPurpose="VERIFY";
  if(failure.equals("USED"))a.otpHash=null;
  otpSubmit(resetBody(a.email,failure.equals("654321")?"654321":"123456","Replacement@123"))
   .andExpect(status().isBadRequest());
  a=otpReload(a.email);assertThat(a.passwordHash).isEqualTo(hash);assertThat(a.tokenVersion).isEqualTo(version);
  assertThat(new BCryptPasswordEncoder().matches("Original@123",a.passwordHash)).isTrue();
 }
 static java.util.stream.Stream<Arguments> invalidNewPasswords(){return java.util.stream.Stream.of(
  Arguments.of("", "Mật khẩu cần từ 8 đến 100 ký tự."),
  Arguments.of("short", "Mật khẩu cần từ 8 đến 100 ký tự."),
  Arguments.of("a".repeat(101), "Mật khẩu cần từ 8 đến 100 ký tự."),
  Arguments.of("a".repeat(73), "Mật khẩu mới không được vượt quá 72 byte UTF-8."),
  Arguments.of("ậ".repeat(25), "Mật khẩu mới không được vượt quá 72 byte UTF-8."));}
 @ParameterizedTest @MethodSource("invalidNewPasswords")
 void passwordResetRejectsInvalidNewPasswordWithoutConsumingOtp(String invalid,String message)throws Exception{
  Account a=otpAccount("RESET");a.verified=true;String oldHash=a.passwordHash,otpHash=a.otpHash;int oldVersion=a.tokenVersion;
  otpSubmit(resetBody(a.email,"123456",invalid)).andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.message").value(message));
  a=otpReload(a.email);assertThat(a.passwordHash).isEqualTo(oldHash);assertThat(a.tokenVersion).isEqualTo(oldVersion);
  assertThat(a.otpHash).isEqualTo(otpHash);assertThat(a.otpAttempts).isZero();
 }
 @Test void passwordResetMissingPasswordCannotConsumeOtp()throws Exception{
  Account a=otpAccount("RESET");String hash=a.passwordHash,otpHash=a.otpHash;int version=a.tokenVersion;
  otpSubmit(Map.of("email",a.email,"otp","123456","purpose","RESET")).andExpect(status().isBadRequest());
  a=otpReload(a.email);assertThat(a.passwordHash).isEqualTo(hash);assertThat(a.otpHash).isEqualTo(otpHash);
  assertThat(a.tokenVersion).isEqualTo(version);
 }
 @Test void passwordChangeUsesBcryptRevokesOldJwtAndPendingResetOtp()throws Exception{
  Account a=passwordAccount();String email=a.email;String oldHash=a.passwordHash;int oldVersion=a.tokenVersion;
  String oldJwt=loginToken(passwordApiLogin(email,"Original@123"));
  otpSend(Map.of("email",email,"purpose","RESET")).andExpect(status().isOk());
  String otp=otpCode(otpMessage());a=otpReload(email);assertThat(a.otpHash).isNotNull();
  changePasswordApi(oldJwt,"Original@123","Replacement@123").andExpect(status().isOk());
  a=otpReload(email);assertThat(a.passwordHash).startsWith("$2").isNotEqualTo(oldHash);
  assertThat(new BCryptPasswordEncoder().matches("Replacement@123",a.passwordHash)).isTrue();
  assertThat(a.tokenVersion).isEqualTo(oldVersion+1);assertThat(a.otpHash).isNull();
  SecurityContextHolder.clearContext();
  mvc.perform(get("/api/account").header("Authorization","Bearer "+oldJwt)).andExpect(status().isUnauthorized());
  otpSubmit(resetBody(email,otp,"Another@123")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody(email,"Original@123")))
   .andExpect(status().isBadRequest());
  String newJwt=loginToken(passwordApiLogin(email,"Replacement@123"));
  mvc.perform(get("/api/account").header("Authorization","Bearer "+newJwt)).andExpect(status().isOk());
 }
 @Test void passwordChangeRejectsWrongCurrentPasswordWithoutChangingState()throws Exception{
  Account a=passwordAccount();a.otpPurpose="RESET";a.otpHash=s.hashPassword("123456");
  String hash=a.passwordHash,otpHash=a.otpHash;int version=a.tokenVersion;
  String token=loginToken(passwordApiLogin(a.email,"Original@123"));
  changePasswordApi(token,"Wrong@123","Replacement@123").andExpect(status().isBadRequest());
  a=otpReload(a.email);assertThat(a.passwordHash).isEqualTo(hash);assertThat(a.tokenVersion).isEqualTo(version);
  assertThat(a.otpHash).isEqualTo(otpHash);
  mvc.perform(get("/api/account").header("Authorization","Bearer "+token)).andExpect(status().isOk());
 }
 @ParameterizedTest @MethodSource("invalidNewPasswords")
 void passwordChangeRejectsInvalidNewPasswordWithoutPartialSave(String invalid,String message)throws Exception{
  Account a=passwordAccount();a.otpPurpose="RESET";a.otpHash=s.hashPassword("123456");
  String hash=a.passwordHash,otpHash=a.otpHash;int version=a.tokenVersion;
  String token=loginToken(passwordApiLogin(a.email,"Original@123"));
  changePasswordApi(token,"Original@123",invalid).andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.message").value(message));
  a=otpReload(a.email);assertThat(a.passwordHash).isEqualTo(hash);assertThat(a.tokenVersion).isEqualTo(version);
  assertThat(a.otpHash).isEqualTo(otpHash);
 }
 @Test void passwordChangeRequiresAuthenticationAndWebCsrf()throws Exception{
  Account a=passwordAccount();String body=json.writeValueAsString(Map.of("currentPassword","Original@123","password","Replacement@123"));
  SecurityContextHolder.clearContext();
  mvc.perform(put("/api/account/password").contentType("application/json").content(body))
   .andExpect(status().isUnauthorized());
  MvcResult login=passwordWebLogin(a.email,"Original@123");
  MockHttpSession session=(MockHttpSession)login.getRequest().getSession(false);
  SecurityContextHolder.clearContext();
  mvc.perform(put("/shop-api/account/password").session(session).contentType("application/json").content(body))
   .andExpect(status().isForbidden());
  mvc.perform(put("/shop-api/account/password").session(session).with(csrf().useInvalidToken())
   .contentType("application/json").content(body)).andExpect(status().isForbidden());
  assertThat(otpReload(a.email).passwordHash).isEqualTo(a.passwordHash);
  mvc.perform(put("/shop-api/account/password").session(session).with(csrf())
   .contentType("application/json").content(body)).andExpect(status().isOk());
 }
 @Test void passwordChangeKeepsExistingWebSession()throws Exception{
  Account a=passwordAccount();MvcResult login=passwordWebLogin(a.email,"Original@123");
  MockHttpSession session=(MockHttpSession)login.getRequest().getSession(false);
  SecurityContextHolder.clearContext();
  mvc.perform(put("/shop-api/account/password").session(session).with(csrf()).contentType("application/json")
   .content(json.writeValueAsString(Map.of("currentPassword","Original@123","password","Replacement@123"))))
   .andExpect(status().isOk());
  SecurityContextHolder.clearContext();
  mvc.perform(get("/shop-api/account").session(session)).andExpect(status().isOk())
   .andExpect(jsonPath("$.user.email").value(a.email));
 }
 String yc13Token(Account account)throws Exception{return loginToken(passwordApiLogin(account.email,"Original@123"));}
 ResultActions yc13Profile(String token,Map<String,Object> body)throws Exception{
  SecurityContextHolder.clearContext();
  return mvc.perform(put("/api/account/profile").header("Authorization","Bearer "+token)
   .contentType("application/json").content(json.writeValueAsString(body)));
 }
 Map<String,Object> yc13AddressBody(String label,String detail,boolean selected){
  return new HashMap<>(Map.of("label",label,"recipient","Người nhận","phone","0901234567",
   "detail",detail,"defaultAddress",selected));
 }
 ResultActions yc13AddressRequest(String token,Map<String,Object> body)throws Exception{
  SecurityContextHolder.clearContext();
  return mvc.perform(post("/api/addresses").header("Authorization","Bearer "+token)
   .contentType("application/json").content(json.writeValueAsString(body)));
 }
 Address yc13Address(String token,String label,String detail,boolean selected)throws Exception{
  MvcResult result=yc13AddressRequest(token,yc13AddressBody(label,detail,selected))
   .andExpect(status().isOk()).andReturn();
  return s.get(Address.class,json.readTree(result.getResponse().getContentAsByteArray()).get("id").asLong());
 }
 @Test void yc13ProfileUpdatesOwnNameOnly()throws Exception{
  Account a=passwordAccount(),b=passwordAccount();String oldEmail=a.email,otherName=b.name;
  yc13Profile(yc13Token(a),Map.of("name","Tên hồ sơ mới","phone",a.phone))
   .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Tên hồ sơ mới"));
  assertThat(s.byEmail(oldEmail).name).isEqualTo("Tên hồ sơ mới");
  assertThat(s.byEmail(b.email).name).isEqualTo(otherName);
 }
 @Test void yc13ProfileUpdatesOwnPhoneOnly()throws Exception{
  Account a=passwordAccount(),b=passwordAccount();String otherPhone=b.phone;
  yc13Profile(yc13Token(a),Map.of("name",a.name,"phone","0987654321"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("0987654321"));
  assertThat(s.byEmail(a.email).phone).isEqualTo("0987654321");
  assertThat(s.byEmail(b.email).phone).isEqualTo(otherPhone);
 }
 @Test void yc13ProfileIgnoresForgedAccountFields()throws Exception{
  Account a=passwordAccount(),b=passwordAccount();String otherName=b.name,otherPhone=b.phone;
  Map<String,Object> body=new HashMap<>(Map.of("name","Chỉ đổi tên A","phone","0901234567"));
  body.put("id",b.id);body.put("email",b.email);body.put("roles","ADMIN");
  yc13Profile(yc13Token(a),body).andExpect(status().isOk());
  assertThat(s.byEmail(a.email).name).isEqualTo("Chỉ đổi tên A");
  assertThat(s.byEmail(a.email).roles).isEqualTo("CUSTOMER");
  assertThat(s.byEmail(b.email).name).isEqualTo(otherName);
  assertThat(s.byEmail(b.email).phone).isEqualTo(otherPhone);
 }
 static java.util.stream.Stream<Arguments> yc13InvalidProfiles(){return java.util.stream.Stream.of(
  Arguments.of("name"," ","Tên không hợp lệ."),
  Arguments.of("name","a".repeat(101),"Tên không hợp lệ."),
  Arguments.of("name",null,"Tên không hợp lệ."),
  Arguments.of("phone","bad-phone","Số điện thoại không hợp lệ."),
  Arguments.of("phone","123","Số điện thoại không hợp lệ."));}
 @ParameterizedTest @MethodSource("yc13InvalidProfiles")
 void yc13ProfileRejectsInvalidDataWithoutChangingOwner(String field,String value,String message)throws Exception{
  Account a=passwordAccount();String oldName=a.name,oldPhone=a.phone;
  Map<String,Object> body=new HashMap<>(Map.of("name","Tên hợp lệ","phone","0901234567"));
  if(value==null)body.remove(field);else body.put(field,value);
  yc13Profile(yc13Token(a),body).andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.message").value(message));
  assertThat(s.byEmail(a.email).name).isEqualTo(oldName);
  assertThat(s.byEmail(a.email).phone).isEqualTo(oldPhone);
 }
 @Test void yc13MultipleAddressesAreVisibleOnlyToTheirOwner()throws Exception{
  Account a=passwordAccount(),b=passwordAccount();String tokenA=yc13Token(a),tokenB=yc13Token(b);
  Address a1=yc13Address(tokenA,"Nhà A","11 Đường số Một, Thủ Đức",false);
  Address a2=yc13Address(tokenA,"Công ty A","22 Đường số Hai, Thủ Đức",false);
  Address b1=yc13Address(tokenB,"Nhà B","33 Đường số Ba, Thủ Đức",false);
  Address b2=yc13Address(tokenB,"Công ty B","44 Đường số Bốn, Thủ Đức",false);
  SecurityContextHolder.clearContext();
  var visibleA=json.readTree(mvc.perform(get("/api/account").header("Authorization","Bearer "+tokenA))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("addresses");
  assertThat(visibleA.size()).isEqualTo(2);
  assertThat(visibleA.findValuesAsText("id")).containsExactlyInAnyOrder(a1.id.toString(),a2.id.toString());
  SecurityContextHolder.clearContext();
  var visibleB=json.readTree(mvc.perform(get("/api/account").header("Authorization","Bearer "+tokenB))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("addresses");
  assertThat(visibleB.size()).isEqualTo(2);
  assertThat(visibleB.findValuesAsText("id")).containsExactlyInAnyOrder(b1.id.toString(),b2.id.toString());
 }
 @Test void yc13OwnAddressCanBeEditedAndDefaultMovesWithinOneAccount()throws Exception{
  Account a=passwordAccount(),b=passwordAccount();String tokenA=yc13Token(a),tokenB=yc13Token(b);
  Address a1=yc13Address(tokenA,"Nhà A","11 Đường số Một, Thủ Đức",true);
  Address a2=yc13Address(tokenA,"Công ty A","22 Đường số Hai, Thủ Đức",false);
  Address b1=yc13Address(tokenB,"Nhà B","33 Đường số Ba, Thủ Đức",true);
  yc13Address(tokenB,"Công ty B","44 Đường số Bốn, Thủ Đức",false);
  Map<String,Object> edit=yc13AddressBody("Công ty A mới","25 Đường số Hai, Thủ Đức",true);
  edit.put("id",a2.id);
  yc13AddressRequest(tokenA,edit).andExpect(status().isOk())
   .andExpect(jsonPath("$.defaultAddress").value(true));
  assertThat(s.get(Address.class,a2.id).detail).isEqualTo("25 Đường số Hai, Thủ Đức");
  assertThat(s.get(Address.class,a1.id).defaultAddress).isFalse();
  assertThat(s.get(Address.class,a2.id).defaultAddress).isTrue();
  assertThat(s.get(Address.class,b1.id).defaultAddress).isTrue();
  edit=yc13AddressBody("Nhà A","11 Đường số Một, Thủ Đức",true);edit.put("id",a1.id);
  yc13AddressRequest(tokenA,edit).andExpect(status().isOk());
  assertThat(s.where(Address.class,"userId",a.id).stream().filter(x->x.defaultAddress)).extracting(x->x.id)
   .containsExactly(a1.id);
  assertThat(s.get(Address.class,b1.id).defaultAddress).isTrue();
 }
 @Test void yc13ForeignAddressCannotBeEditedMadeDefaultOrDeleted()throws Exception{
  Account a=passwordAccount(),b=passwordAccount();String tokenA=yc13Token(a),tokenB=yc13Token(b);
  Address a1=yc13Address(tokenA,"Nhà A","11 Đường số Một, Thủ Đức",true);
  Address b1=yc13Address(tokenB,"Nhà B","33 Đường số Ba, Thủ Đức",true);
  Address b2=yc13Address(tokenB,"Công ty B","44 Đường số Bốn, Thủ Đức",false);
  String original=b2.detail;
  Map<String,Object> edit=yc13AddressBody("Trộm địa chỉ","55 Đường khác, Thủ Đức",true);
  edit.put("id",b2.id);
  yc13AddressRequest(tokenA,edit).andExpect(status().isForbidden());
  SecurityContextHolder.clearContext();
  mvc.perform(delete("/api/addresses/"+b2.id).header("Authorization","Bearer "+tokenA))
   .andExpect(status().isForbidden());
  assertThat(s.get(Address.class,b2.id).detail).isEqualTo(original);
  assertThat(s.get(Address.class,b2.id).defaultAddress).isFalse();
  assertThat(s.get(Address.class,b1.id).defaultAddress).isTrue();
  assertThat(s.get(Address.class,a1.id).defaultAddress).isTrue();
 }
 @Test void yc13OwnAddressCanBeDeletedWithoutAffectingOtherOwners()throws Exception{
  Account a=passwordAccount(),b=passwordAccount();String tokenA=yc13Token(a),tokenB=yc13Token(b);
  Address a1=yc13Address(tokenA,"Nhà A","11 Đường số Một, Thủ Đức",true);
  Address a2=yc13Address(tokenA,"Công ty A","22 Đường số Hai, Thủ Đức",false);
  Address b1=yc13Address(tokenB,"Nhà B","33 Đường số Ba, Thủ Đức",true);
  SecurityContextHolder.clearContext();
  mvc.perform(delete("/api/addresses/"+a2.id).header("Authorization","Bearer "+tokenA))
   .andExpect(status().isOk());
  assertThat(s.where(Address.class,"userId",a.id).stream().map(x->x.id).toList()).containsExactly(a1.id);
  assertThat(s.where(Address.class,"userId",b.id).stream().map(x->x.id).toList()).containsExactly(b1.id);
  assertThat(s.get(Address.class,b1.id).defaultAddress).isTrue();
 }
 @Test void yc13CheckoutUsesOwnSelectedAddressAndRejectsForeignAddress()throws Exception{
  Account a=passwordAccount(),b=passwordAccount();String tokenA=yc13Token(a),tokenB=yc13Token(b);
  Address own=yc13Address(tokenA,"Nhà A","11 Đường số Một, Thủ Đức",true);
  Address foreign=yc13Address(tokenB,"Nhà B","33 Đường số Ba, Thủ Đức",true);
  Product product=custom(1L);
  SecurityContextHolder.clearContext();
  mvc.perform(post("/api/cart").header("Authorization","Bearer "+tokenA).contentType("application/json")
   .content(json.writeValueAsString(Map.of("productId",product.id,"quantity",1,"ingredientIds",ingredients()))))
   .andExpect(status().isOk());
  Map<String,Object> order=checkout("yc13-foreign-order-01","DELIVERY");
  order.put("addressId",foreign.id);order.put("address","Địa chỉ phải bị bỏ qua");
  SecurityContextHolder.clearContext();
  mvc.perform(post("/api/checkout").header("Authorization","Bearer "+tokenA).contentType("application/json")
   .content(json.writeValueAsString(order))).andExpect(status().isForbidden());
  assertThat(s.where(MealOrder.class,"userId",a.id)).isEmpty();
  order.put("checkoutKey","yc13-own-order-0001");order.put("addressId",own.id);
  SecurityContextHolder.clearContext();
  mvc.perform(post("/api/checkout").header("Authorization","Bearer "+tokenA).contentType("application/json")
   .content(json.writeValueAsString(order))).andExpect(status().isOk())
   .andExpect(jsonPath("$[0].address").value(own.detail))
   .andExpect(jsonPath("$[0].userId").value(a.id));
  assertThat(s.get(Address.class,foreign.id).detail).isEqualTo("33 Đường số Ba, Thủ Đức");
 }
 static java.util.stream.Stream<Arguments> yc13InvalidAddresses(){return java.util.stream.Stream.of(
  Arguments.of("label"," ","Tên gợi nhớ không hợp lệ."),
  Arguments.of("label",null,"Tên gợi nhớ không hợp lệ."),
  Arguments.of("recipient","X","Tên người nhận không hợp lệ."),
  Arguments.of("recipient",null,"Tên người nhận không hợp lệ."),
  Arguments.of("phone","abc","Số điện thoại không hợp lệ."),
  Arguments.of("phone",null,"Số điện thoại không hợp lệ."),
  Arguments.of("detail","ngắn","Địa chỉ cần từ 8 đến 500 ký tự."),
  Arguments.of("detail",null,"Địa chỉ cần từ 8 đến 500 ký tự."),
  Arguments.of("defaultAddress","unknown","Địa chỉ mặc định không hợp lệ."));}
 @ParameterizedTest @MethodSource("yc13InvalidAddresses")
 void yc13AddressRejectsInvalidDataWithClearError(String field,String value,String message)throws Exception{
  Account a=passwordAccount();Map<String,Object> body=yc13AddressBody("Nhà","11 Đường số Một, Thủ Đức",false);
  if(value==null)body.remove(field);else body.put(field,value);
  yc13AddressRequest(yc13Token(a),body).andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.message").value(message));
  assertThat(s.where(Address.class,"userId",a.id)).isEmpty();
 }
 @Test void yc13InvalidEditDoesNotChangeAddressOrDefault()throws Exception{
  Account a=passwordAccount();String token=yc13Token(a);
  Address first=yc13Address(token,"Nhà","11 Đường số Một, Thủ Đức",true);
  Address second=yc13Address(token,"Công ty","22 Đường số Hai, Thủ Đức",false);
  String detail=second.detail;
  Map<String,Object> edit=yc13AddressBody("Công ty","25 Đường số Hai, Thủ Đức",true);
  edit.put("id",second.id);edit.put("phone","invalid");
  yc13AddressRequest(token,edit).andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.message").value("Số điện thoại không hợp lệ."));
  assertThat(s.get(Address.class,first.id).defaultAddress).isTrue();
  assertThat(s.get(Address.class,second.id).defaultAddress).isFalse();
  assertThat(s.get(Address.class,second.id).detail).isEqualTo(detail);
 }
 @Test void yc13ProfileAndAddressRequireAuthentication()throws Exception{
  SecurityContextHolder.clearContext();
  mvc.perform(put("/api/account/profile").contentType("application/json")
   .content(json.writeValueAsString(Map.of("name","Tên mới","phone","0901234567"))))
   .andExpect(status().isUnauthorized());
  mvc.perform(post("/api/addresses").contentType("application/json")
   .content(json.writeValueAsString(yc13AddressBody("Nhà","11 Đường số Một, Thủ Đức",false))))
   .andExpect(status().isUnauthorized());
  mvc.perform(post("/api/checkout").contentType("application/json")
   .content(json.writeValueAsString(checkout("yc13-no-auth-order-01","DELIVERY"))))
   .andExpect(status().isUnauthorized());
 }
 String yc14Token(String account)throws Exception{return loginToken(passwordApiLogin(account+"@uteb.vn","Uteb@12345"));}
 ResultActions yc14Get(String token,String resource)throws Exception{
  SecurityContextHolder.clearContext();
  return mvc.perform(get("/api/admin/"+resource).header("Authorization","Bearer "+token));
 }
 ResultActions yc14Save(String token,String resource,Map<String,Object> body)throws Exception{
  SecurityContextHolder.clearContext();
  return mvc.perform(post("/api/admin/"+resource).header("Authorization","Bearer "+token)
   .contentType("application/json").content(json.writeValueAsString(body)));
 }
 Account yc14Account(String roles){
  Account a=new Account();a.email="yc14-"+UUID.randomUUID()+"@example.com";a.name="Role Test";
  a.phone="0901234567";a.roles=roles;a.storeId=1L;a.active=true;a.verified=true;
  a.passwordHash=s.hashPassword("Original@123");return s.save(a);
 }
 Map<String,Object> yc14UserBody(Account a,String roles,boolean active){
  return new HashMap<>(Map.of("id",a.id,"email",a.email,"name","Updated Role Test",
   "phone",a.phone,"storeId",a.storeId==null?"":a.storeId,"roles",roles,"active",active));
 }
 static java.util.stream.Stream<Arguments> yc14DeniedReads(){return java.util.stream.Stream.of(
  Arguments.of("customer","users"),Arguments.of("customer","products"),Arguments.of("customer","settings"),
  Arguments.of("staff","users"),Arguments.of("staff","products"),Arguments.of("staff","audit"),
  Arguments.of("shipper","users"),Arguments.of("shipper","products"),Arguments.of("shipper","vouchers"),
  Arguments.of("vendor","users"),Arguments.of("vendor","settings"),Arguments.of("vendor","audit"),
   Arguments.of("manager","settings"),Arguments.of("manager","audit"),Arguments.of("manager","applications"));
 }
 @ParameterizedTest @MethodSource("yc14DeniedReads")
 void yc14RoleMatrixRejectsDirectAdminApiReads(String actor,String resource)throws Exception{
  yc14Get(yc14Token(actor),resource).andExpect(status().isForbidden());
 }
 static java.util.stream.Stream<Arguments> yc14AllowedReads(){return java.util.stream.Stream.of(
  Arguments.of("manager","users"),Arguments.of("manager","products"),
  Arguments.of("vendor","products"),Arguments.of("vendor","vouchers"),
   Arguments.of("admin","users"),Arguments.of("admin","audit"),Arguments.of("admin","settings"));
 }
 @ParameterizedTest @MethodSource("yc14AllowedReads")
 void yc14RoleMatrixAllowsDesignedAdminApiReads(String actor,String resource)throws Exception{
  yc14Get(yc14Token(actor),resource).andExpect(status().isOk());
 }
 @ParameterizedTest @ValueSource(strings={"staff","manager","vendor","shipper","admin"})
 void yc14OperationalRolesCanAccessDashboard(String actor)throws Exception{
  yc14Get(yc14Token(actor),"dashboard").andExpect(status().isOk());
 }
 @Test void yc14CustomerCannotAccessDashboard()throws Exception{
  yc14Get(yc14Token("customer"),"dashboard").andExpect(status().isForbidden());
 }
 @ParameterizedTest @ValueSource(strings={"customer","staff","shipper","vendor"})
 void yc14NonAccountAdminsCannotWriteUsersEvenWithForgedRole(String actor)throws Exception{
  Account target=s.byEmail("staff@uteb.vn");String oldRoles=target.roles;int oldVersion=target.tokenVersion;
  yc14Save(yc14Token(actor),"users",yc14UserBody(target,"ADMIN",true)).andExpect(status().isForbidden());
  assertThat(target.roles).isEqualTo(oldRoles);assertThat(target.tokenVersion).isEqualTo(oldVersion);
 }
 @ParameterizedTest @ValueSource(strings={"staff","shipper","vendor","manager"})
 void yc14NonAdminsCannotWriteSystemSettings(String actor)throws Exception{
  yc14Save(yc14Token(actor),"settings",Map.of("name","yc14","settingValue","forbidden"))
   .andExpect(status().isForbidden());
 }
 @ParameterizedTest @ValueSource(strings={"staff","shipper"})
 void yc14ManagerCanEditOrdinaryOperationalAccount(String role)throws Exception{
  Account target=s.byEmail(role+"@uteb.vn");int oldVersion=target.tokenVersion;
  yc14Save(yc14Token("manager"),"users",yc14UserBody(target,role.toUpperCase(Locale.ROOT),true))
   .andExpect(status().isOk());
  assertThat(target.roles).isEqualTo(role.toUpperCase(Locale.ROOT));
  assertThat(target.name).isEqualTo("Updated Role Test");assertThat(target.tokenVersion).isEqualTo(oldVersion+1);
 }
 @ParameterizedTest @ValueSource(strings={"STAFF","SHIPPER"})
 void yc14ManagerCannotCreateOperationalAccountUnderCurrentPolicy(String role)throws Exception{
  String email="yc14-created-"+UUID.randomUUID()+"@example.com";
  Map<String,Object> body=Map.of("email",email,"name","New Operational User","phone","0901234567",
   "storeId",1,"roles",role,"active",true,"password","Original@123");
  yc14Save(yc14Token("manager"),"users",body).andExpect(status().isForbidden());
  assertThat(s.byEmail(email)).isNull();
 }
 @ParameterizedTest @ValueSource(strings={"manager","vendor"})
 void yc14ManagerAndVendorCanManageProducts(String actor)throws Exception{
  Map<String,Object> body=Map.of("storeId",1,"name","YC14 Burrito","category","Burrito",
   "description","Role matrix product","price",69000,"stock",5,"calories",300,
   "available",true,"customizable",false);
  yc14Save(yc14Token(actor),"products",body).andExpect(status().isOk());
  assertThat(s.all(Product.class)).anyMatch(p->p.name.equals("YC14 Burrito")&&p.storeId.equals(1L));
 }
 @Test void yc14StaffCanOperateOrder()throws Exception{
  MealOrder order=s.all(MealOrder.class).stream().filter(o->"NEW".equals(o.status)).findFirst().orElseThrow();
  String token=yc14Token("staff");SecurityContextHolder.clearContext();
  mvc.perform(put("/api/orders/{id}/status",order.id).header("Authorization","Bearer "+token)
   .contentType("application/json").content(json.writeValueAsString(Map.of("status","CONFIRMED"))))
   .andExpect(status().isOk());
  assertThat(order.status).isEqualTo("CONFIRMED");
 }
 @Test void yc14ShipperCanAdvanceAssignedDelivery()throws Exception{
  MealOrder order=s.all(MealOrder.class).stream().filter(o->"DELIVERING".equals(o.status)).findFirst().orElseThrow();
  String token=yc14Token("shipper");SecurityContextHolder.clearContext();
  mvc.perform(put("/api/orders/{id}/status",order.id).header("Authorization","Bearer "+token)
   .contentType("application/json").content(json.writeValueAsString(Map.of("status","COMPLETED","codCollected",true))))
   .andExpect(status().isOk());
  assertThat(order.status).isEqualTo("COMPLETED");
 }
 @ParameterizedTest @ValueSource(strings={"ADMIN","MANAGER","VENDOR","ADMIN,STAFF","ADMIN,SHIPPER","MANAGER,STAFF","MANAGER,SHIPPER","VENDOR,STAFF","ROOT,STAFF"})
 void yc14ManagerCannotEditPrivilegedOrMixedRoleAccount(String roles)throws Exception{
  Account target=yc14Account(roles);String oldName=target.name;int oldVersion=target.tokenVersion;
  String manager=yc14Token("manager");
  yc14Save(manager,"users",yc14UserBody(target,"STAFF",false)).andExpect(status().isForbidden());
  assertThat(target.roles).isEqualTo(roles);assertThat(target.name).isEqualTo(oldName);
  assertThat(target.active).isTrue();assertThat(target.tokenVersion).isEqualTo(oldVersion);
  var users=json.readTree(yc14Get(manager,"users").andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
  assertThat(users.findValuesAsText("email")).doesNotContain(target.email);
 }
 @ParameterizedTest @ValueSource(strings={"ADMIN","MANAGER","VENDOR","ADMIN,STAFF"})
 void yc14ManagerCannotGrantPrivilegedRoles(String roles)throws Exception{
  Account target=s.byEmail("staff@uteb.vn");int oldVersion=target.tokenVersion;
  yc14Save(yc14Token("manager"),"users",yc14UserBody(target,roles,true)).andExpect(status().isBadRequest());
  assertThat(target.roles).isEqualTo("STAFF");assertThat(target.tokenVersion).isEqualTo(oldVersion);
 }
 @ParameterizedTest @ValueSource(strings={"ROOT","ADMIN,","ADMIN,,STAFF","","ADMIN,ROOT"})
 void yc14AdminRejectsInvalidRoleList(String roles)throws Exception{
  Account target=yc14Account("STAFF");int oldVersion=target.tokenVersion;
  yc14Save(yc14Token("admin"),"users",yc14UserBody(target,roles,true)).andExpect(status().isBadRequest());
  assertThat(target.roles).isEqualTo("STAFF");assertThat(target.tokenVersion).isEqualTo(oldVersion);
 }
 @Test void yc14AdminCanManageMixedRoleAccountAndSystemSettings()throws Exception{
  Account target=yc14Account("ADMIN,STAFF");String admin=yc14Token("admin");
  yc14Save(admin,"users",yc14UserBody(target,"ADMIN,SHIPPER",true)).andExpect(status().isOk());
  assertThat(target.roles).isEqualTo("ADMIN,SHIPPER");
  yc14Save(admin,"settings",Map.of("name","yc14-matrix","settingValue","enabled"))
   .andExpect(status().isOk());
 }
 @Test void yc14AdminCannotSelfLockOrRemoveAdminRole()throws Exception{
  Account admin=s.byEmail("admin@uteb.vn");String token=yc14Token("admin");int oldVersion=admin.tokenVersion;
  yc14Save(token,"users",yc14UserBody(admin,"ADMIN",false)).andExpect(status().isBadRequest());
  yc14Save(token,"users",yc14UserBody(admin,"CUSTOMER",true)).andExpect(status().isBadRequest());
  assertThat(admin.active).isTrue();assertThat(admin.roles).isEqualTo("ADMIN");
  assertThat(admin.tokenVersion).isEqualTo(oldVersion);
 }
 @Test void yc14MixedRoleActorsKeepTheirFullAuthorities()throws Exception{
  Account adminStaff=yc14Account("ADMIN,STAFF");
  Account managerShipper=yc14Account("MANAGER,SHIPPER");
  String adminToken=loginToken(passwordApiLogin(adminStaff.email,"Original@123"));
  yc14Get(adminToken,"audit").andExpect(status().isOk());
  String managerToken=loginToken(passwordApiLogin(managerShipper.email,"Original@123"));
  yc14Get(managerToken,"users").andExpect(status().isOk());
  yc14Get(managerToken,"settings").andExpect(status().isForbidden());
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
 @Test @WithMockUser(username="customer@uteb.vn")void verifiedIpnAndRepeatedCallback()throws Exception{Product p=custom(1L);s.cart(Map.of("productId",p.id,"quantity",1,"ingredientIds",ingredients()));Map<String,Object>b=checkout("ipn-test-order-0001","PICKUP");b.put("paymentMethod","VNPAY");MealOrder o=s.checkout(b).getFirst();Map<String,String>ipn=new TreeMap<>(Map.of("vnp_TmnCode","TESTCODE","vnp_TxnRef",payments.start(o.id,"127.0.0.1").get("reference").toString(),"vnp_Amount",VnpayService.minorAmount(o.total),"vnp_TransactionNo","140000001","vnp_ResponseCode","00","vnp_TransactionStatus","00"));ipn.put("vnp_SecureHash",Payments.sign("HmacSHA512","test-signing-secret-only",Payments.vnRaw(ipn)));assertThat(payments.vnIpn(ipn)).containsEntry("RspCode","00");assertThat(o.paymentStatus).isEqualTo("PAID");assertThat(payments.vnIpn(ipn)).containsEntry("RspCode","02");}
 @Test @WithMockUser(username="customer@uteb.vn")void groupLocksAndTransfersOnce(){GroupMeal g=s.createGroup(Map.of("storeId",1,"title","Test group"));Product p=custom(1L);s.groupAdd(g.code,Map.of("productId",p.id,"quantity",1,"ingredientIds",ingredients()));s.groupToCart(g.code);assertThat(g.status).isEqualTo("LOCKED");assertThat(s.where(CartLine.class,"userId",s.me().id)).hasSize(1);assertThatThrownBy(()->s.groupToCart(g.code)).hasMessageContaining("chuyển vào giỏ");}
 @Test void sessionLoginAndJwt()throws Exception{MvcResult r=mvc.perform(post("/shop-api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("email","customer@uteb.vn","password","Uteb@12345")))).andExpect(status().isOk()).andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn();String token=json.readTree(r.getResponse().getContentAsString()).get("token").asText();org.springframework.security.core.context.SecurityContextHolder.clearContext();mvc.perform(get("/api/account").header("Authorization","Bearer "+token)).andExpect(status().isOk());org.springframework.security.core.context.SecurityContextHolder.clearContext();mvc.perform(get("/api/account").header("Authorization","Bearer "+token+"x")).andExpect(status().isUnauthorized());}

 @ParameterizedTest @ValueSource(strings={"INGREDIENT","DELETED_INGREDIENT","PRODUCT","DELETED_PRODUCT","MOVED_PRODUCT","STORE","STOCK"})
 @WithMockUser(username="customer@uteb.vn")
 void invalidCartLineRemainsVisibleAndCheckoutStaysBlocked(String problem)throws Exception{
  Product p=custom(1L);String ids=ingredients();
  CartLine bad=s.cart(Map.of("productId",p.id,"quantity",1,"ingredientIds",ids));
  Product fixed=s.all(Product.class).stream().filter(x->!x.customizable&&x.storeId.equals(2L)).findFirst().orElseThrow();
  CartLine good=s.cart(Map.of("productId",fixed.id,"quantity",1,"ingredientIds",""));
  Ingredient ingredient=s.get(Ingredient.class,Long.parseLong(ids.split(",")[0]));
  switch(problem){
   case "INGREDIENT"->ingredient.available=false;
   case "DELETED_INGREDIENT"->registrationEm.remove(ingredient);
   case "PRODUCT"->p.available=false;
   case "MOVED_PRODUCT"->p.storeId=2L;
   case "DELETED_PRODUCT"->registrationEm.remove(p);
   case "STORE"->s.get(Store.class,1L).active=false;
   case "STOCK"->p.stock=0;
  }
  mvc.perform(get("/shop-api/cart")).andExpect(status().isOk())
   .andExpect(jsonPath("$.length()").value(2));
  reviewActor("customer@uteb.vn");
  Map<String,Object> row=s.cartView().stream().filter(r->((CartLine)r.get("line")).id.equals(bad.id)).findFirst().orElseThrow();
  assertThat(row.get("available")).isEqualTo(false);assertThat((List<?>)row.get("issues")).isNotEmpty();
  assertThat(s.cartView().stream().filter(r->((CartLine)r.get("line")).id.equals(good.id)).findFirst().orElseThrow().get("available")).isEqualTo(true);
  assertThatThrownBy(()->s.preview(checkout("invalid-cart-review-0001","PICKUP"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  s.deleteCart(bad.id);assertThat(s.cartView()).hasSize(1);assertThat(s.preview(checkout("valid-cart-review-0001","PICKUP")).get("total")).isNotNull();
 }
 @Test @WithMockUser(username="customer@uteb.vn")
 void unavailableIngredientCanBeReplacedWithoutLosingCartNote(){
  Product p=custom(1L);String ids=ingredients();CartLine line=s.cart(Map.of("productId",p.id,"quantity",2,"ingredientIds",ids,"note","Không cay"));
  Ingredient old=s.get(Ingredient.class,Long.parseLong(ids.split(",")[0]));old.available=false;
  Ingredient replacement=s.all(Ingredient.class).stream().filter(i->i.available&&i.groupName.equals(old.groupName)).findFirst().orElseThrow();
  s.cart(Map.of("id",line.id,"productId",p.id,"quantity",2,"ingredientIds",ids.replaceFirst(old.id.toString(),replacement.id.toString()),"note",line.note));
  assertThat(s.cartView()).hasSize(1);assertThat(s.cartView().getFirst().get("available")).isEqualTo(true);assertThat(line.note).isEqualTo("Không cay");
 }
 @Test @WithMockUser(username="customer@uteb.vn")
 void cartChecksCumulativeProductStockAcrossCustomizations(){
  Product p=custom(1L);String ids=ingredients();s.cart(Map.of("productId",p.id,"quantity",2,"ingredientIds",ids));s.cart(Map.of("productId",p.id,"quantity",2,"ingredientIds",ids));p.stock=3;
  assertThat(s.cartView()).allMatch(r->Boolean.FALSE.equals(r.get("available")));
  assertThatThrownBy(()->s.preview(checkout("combined-stock-review-0001","PICKUP"))).hasMessageContaining("tồn kho");
 }
 MealOrder assignmentOrder(){return s.all(MealOrder.class).stream().filter(o->o.storeId.equals(1L)&&o.type.equals("DELIVERY")&&o.status.equals("NEW")).findFirst().orElseThrow();}
 @ParameterizedTest @ValueSource(strings={"PICKUP","DELIVERING","COMPLETED","CANCELLED"})
 @WithMockUser(username="manager@uteb.vn")
 void assignmentRejectsWrongTypeAndClosedOrStartedOrders(String state)throws Exception{
  MealOrder o=assignmentOrder();if(state.equals("PICKUP"))o.type=state;else o.status=state;
  int events=s.where(OrderEvent.class,"orderId",o.id).size();Long old=o.shipperId;
  mvc.perform(put("/shop-api/orders/"+o.id+"/shipper").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("shipperId",s.byEmail("shipper@uteb.vn").id)))).andExpect(status().isBadRequest());
  assertThat(o.shipperId).isEqualTo(old);assertThat(s.where(OrderEvent.class,"orderId",o.id)).hasSize(events);
  mvc.perform(get("/shop-api/admin/shippers").param("orderId",o.id.toString())).andExpect(status().isBadRequest());
 }
 @ParameterizedTest @ValueSource(strings={"FOREIGN_BRANCH","INACTIVE","UNVERIFIED","ROLE","NO_BRANCH"})
 @WithMockUser(username="admin@uteb.vn")
 void assignmentRejectsIneligibleShippersEvenForAdmin(String reason)throws Exception{
  MealOrder o=assignmentOrder();Account shipper=s.byEmail("shipper@uteb.vn");
  switch(reason){case "FOREIGN_BRANCH"->shipper.storeId=2L;case "INACTIVE"->shipper.active=false;case "UNVERIFIED"->shipper.verified=false;case "ROLE"->shipper.roles="CUSTOMER";case "NO_BRANCH"->shipper.storeId=null;}
  mvc.perform(get("/shop-api/admin/shippers").param("orderId",o.id.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(put("/shop-api/orders/"+o.id+"/shipper").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("shipperId",shipper.id)))).andExpect(status().isBadRequest());assertThat(o.shipperId).isNull();
 }
 @ParameterizedTest @ValueSource(strings={"NEW","CONFIRMED","PREPARING","READY"})
 @WithMockUser(username="manager@uteb.vn")
 void managerCanAssignEligibleShipperAndRepeatIsIdempotent(String state)throws Exception{
  MealOrder o=assignmentOrder();o.status=state;Account shipper=s.byEmail("shipper@uteb.vn");
  mvc.perform(get("/shop-api/admin/shippers").param("orderId",o.id.toString())).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(shipper.id.intValue()));
  reviewActor("manager@uteb.vn");
  int events=s.where(OrderEvent.class,"orderId",o.id).size();s.assign(o.id,shipper.id);s.assign(o.id,shipper.id);
  assertThat(o.shipperId).isEqualTo(shipper.id);assertThat(s.where(OrderEvent.class,"orderId",o.id)).hasSize(events+1);
 }
 @Test @WithMockUser(username="vendor2@uteb.vn") void foreignBranchCannotInspectOrAssignOrderShippers()throws Exception{
  MealOrder o=assignmentOrder();mvc.perform(get("/shop-api/admin/shippers").param("orderId",o.id.toString())).andExpect(status().isForbidden());
  mvc.perform(put("/shop-api/orders/"+o.id+"/shipper").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("shipperId",s.byEmail("shipper@uteb.vn").id)))).andExpect(status().isForbidden());
  reviewActor("vendor2@uteb.vn");assertThat(s.shippers(null)).isEmpty();
 }
 @Test @WithMockUser(username="customer@uteb.vn") void customerCannotListEligibleShippers()throws Exception{
  mvc.perform(get("/shop-api/admin/shippers").param("orderId",assignmentOrder().id.toString())).andExpect(status().isForbidden());
 }
 void reviewActor(String email){SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken(email,"","ROLE_USER"));}
 @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 void registrationSmtpFailureLeavesNoAccountAndCanBeRetried()throws Exception{
  Map<String,Object> body=registration();body.put("email","smtp-rollback-"+UUID.randomUUID()+"@example.com");
  doThrow(new org.springframework.mail.MailAuthenticationException("private-test-password-must-not-appear")).when(registrationMail).send(any(SimpleMailMessage.class));
  registerRequest(body).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Chưa gửi được email. Vui lòng thử lại sau hoặc liên hệ cửa hàng."));
  assertThat(otpTransaction(()->s.byEmail(body.get("email").toString()))).isNull();
  reset(registrationMail);
  try{registerRequest(body).andExpect(status().isOk());assertThat(otpTransaction(()->s.byEmail(body.get("email").toString()))).isNotNull();}
  finally{otpTransaction(()->{Account a=s.byEmail(body.get("email").toString());if(a!=null)registrationEm.remove(a);return null;});}
 }
}
