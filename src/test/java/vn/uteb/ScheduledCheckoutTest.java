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
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.uteb.Domain.*;

/** No test transaction: verifies actual rollback/commit, including notification callbacks. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:scheduledcheckout;MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop", "uteb.seed=false", "uteb.mail.enabled=false", "uteb.vnpay.code=TESTCODE", "uteb.vnpay.secret=task5-test-signing-key", "uteb.base-url=https://task5.example.test"})
@ActiveProfiles("demo") @AutoConfigureMockMvc @Import(ScheduledCheckoutTest.TimeConfig.class)
class ScheduledCheckoutTest {
    @TestConfiguration static class TimeConfig {
        @Bean @Primary SchedulingPolicy testSchedulingPolicy(){return new SchedulingPolicy(Clock.fixed(Instant.parse("2026-10-25T01:00:00Z"),ZoneOffset.UTC));}
    }
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired PlatformTransactionManager manager;@PersistenceContext EntityManager em;
    @MockitoBean SimpMessagingTemplate socket;
    Account customer,admin;Store a,b;Product productA,productB;CartLine cart;Voucher voucher;
    TransactionTemplate tx(){return new TransactionTemplate(manager);}
    @BeforeEach void setup(){tx().executeWithoutResult(tx->{
        customer=new Account();customer.email=UUID.randomUUID()+"@example.test";customer.name="Schedule test";customer.verified=true;customer.points=500;em.persist(customer);
        admin=new Account();admin.email=UUID.randomUUID()+"@example.test";admin.name="Schedule admin";admin.verified=true;admin.roles="ADMIN";em.persist(admin);
        a=store("Chi nhánh A","10:00–22:00");b=store("Chi nhánh B","12:00–21:00");productA=product(a);productB=product(b);
        cart=new CartLine();cart.userId=customer.id;cart.storeId=a.id;cart.productId=productA.id;cart.quantity=2;em.persist(cart);
        voucher=new Voucher();voucher.code="T5"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);voucher.name="Test voucher";voucher.type="FIXED";voucher.value=new BigDecimal("5000");voucher.maxDiscount=voucher.value;em.persist(voucher);
    });clearInvocations(socket);}
    Store store(String name,String hours){Store s=new Store();s.name=name;s.address="123 Test Street";s.hours=hours;em.persist(s);return s;}
    Product product(Store s){Product p=new Product();p.storeId=s.id;p.name="Test meal";p.customizable=false;p.price=new BigDecimal("50000");p.stock=10;em.persist(p);return p;}
    @AfterEach void cleanup(){tx().executeWithoutResult(tx->{
        var ids=em.createQuery("select o.id from MealOrder o where o.userId=:id",Long.class).setParameter("id",customer.id).getResultList();
        for(Long id:ids){em.createQuery("delete from OrderEvent where orderId=:id").setParameter("id",id).executeUpdate();em.createQuery("delete from Notice where orderId=:id").setParameter("id",id).executeUpdate();em.createQuery("delete from OrderLine where orderId=:id").setParameter("id",id).executeUpdate();em.createQuery("delete from PaymentAttempt where orderId=:id").setParameter("id",id).executeUpdate();em.remove(em.find(MealOrder.class,id));}
        em.createQuery("delete from AuditRecord where actorId=:id").setParameter("id",admin.id).executeUpdate();
        em.createQuery("delete from CartLine where userId=:id").setParameter("id",customer.id).executeUpdate();
        for(Product p:List.of(productA,productB))em.remove(em.find(Product.class,p.id));em.remove(em.find(Voucher.class,voucher.id));
        for(Store s:List.of(a,b))em.remove(em.find(Store.class,s.id));em.remove(em.find(Account.class,customer.id));em.remove(em.find(Account.class,admin.id));
    });}
    Map<String,Object> body(String time){return new HashMap<>(Map.of("checkoutKey",UUID.randomUUID().toString(),"type","PICKUP","contactName","Schedule Test","phone","0901234567","paymentMethod","COD","scheduledAt",time,"voucher",voucher.code));}
    org.springframework.mock.web.MockHttpSession loginSnapshot(){
        Account account=tx().execute(tx->em.find(Account.class,customer.id));
        var session=new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(SessionSecurityService.ACCOUNT_ID,account.id);
        session.setAttribute(SessionSecurityService.TOKEN_VERSION,account.tokenVersion);
        return session;
    }
    ResultActions checkout(String prefix,Map<String,Object> body)throws Exception{
        var request=post(prefix+"/checkout").with(user(customer.email)).with(csrf()).contentType("application/json").content(json.writeValueAsString(body));
        if(prefix.equals("/shop-api"))request.session(loginSnapshot());
        return mvc.perform(request);
    }
    ResultActions preview(Map<String,Object> body)throws Exception{return mvc.perform(post("/api/checkout/preview").with(user(customer.email)).contentType("application/json").content(json.writeValueAsString(body)));}
    void untouched(){tx().executeWithoutResult(tx->{
        assertThat(em.find(Product.class,productA.id).stock).isEqualTo(10);assertThat(em.find(Product.class,productB.id).stock).isEqualTo(10);assertThat(em.find(Voucher.class,voucher.id).used).isZero();assertThat(em.find(Account.class,customer.id).points).isEqualTo(500);
        for(String entity:List.of("MealOrder","OrderLine","OrderEvent","Notice","PaymentAttempt")){Long count=em.createQuery("select count(x) from "+entity+" x",Long.class).getSingleResult();assertThat(count).isZero();}
        Long carts=em.createQuery("select count(c) from CartLine c where c.userId=:id",Long.class).setParameter("id",customer.id).getSingleResult();assertThat(carts).isGreaterThanOrEqualTo(1);
    });verifyNoInteractions(socket);}
    @ParameterizedTest @ValueSource(strings={"2026-10-26T10:00","2026-10-26T11:00","2026-10-26T21:59","2026-10-25T10:00"})
    void validHoursCreateOrder(String time)throws Exception{
        checkout("/api",body(time)).andExpect(status().isOk()).andExpect(jsonPath("$[0].scheduledAt").value(time));
        tx().executeWithoutResult(tx->{assertThat(em.find(Product.class,productA.id).stock).isEqualTo(8);assertThat(em.find(Voucher.class,voucher.id).used).isEqualTo(1);});
        verify(socket,atLeastOnce()).convertAndSendToUser(eq(customer.email),eq("/queue/events"),any(Object.class));
    }
    @ParameterizedTest @ValueSource(strings={"2026-10-26T09:59","2026-10-26T22:00","2026-10-26T22:01","2026-10-24T12:00","2026-10-25T08:30","2026-11-01T08:00","invalid","2026-10-26T12:00Z"})
    void invalidDirectApiRequestHasNoSideEffects(String time)throws Exception{checkout("/api",body(time)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());untouched();}
    @ParameterizedTest @ValueSource(strings={"COD","VNPAY"})
    void invalidScheduleNeverStartsAnyPaymentMethod(String method)throws Exception{var body=body("2026-10-26T23:00");body.put("paymentMethod",method);checkout("/shop-api",body).andExpect(status().isBadRequest());untouched();}
    @ParameterizedTest @ValueSource(strings={"COD","VNPAY"})
    void validSchedulePreservesPaymentMethod(String method)throws Exception{var body=body("2026-10-26T12:00");body.put("paymentMethod",method);checkout("/api",body).andExpect(status().isOk()).andExpect(jsonPath("$[0].paymentMethod").value(method)).andExpect(jsonPath("$[0].paymentStatus").value(method.equals("COD")?"UNPAID":"PENDING"));}
    @Test void previewChecksHoursAndCanBeRetriedWithoutLosingCart()throws Exception{
        preview(body("2026-10-26T09:00")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("10:00–22:00")));untouched();
        preview(body("2026-10-26T11:00")).andExpect(status().isOk());untouched();checkout("/api",body("2026-10-26T11:00")).andExpect(status().isOk());
    }
    @Test void changingActualCartBranchRechecksSchedule()throws Exception{
        var body=body("2026-10-26T11:00");preview(body).andExpect(status().isOk());
        tx().executeWithoutResult(tx->{CartLine line=em.find(CartLine.class,cart.id);line.storeId=b.id;line.productId=productB.id;});
        preview(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Chi nhánh B")));untouched();
        body.put("scheduledAt","2026-10-26T12:00");checkout("/api",body).andExpect(status().isOk()).andExpect(jsonPath("$[0].storeId").value(b.id));
    }
    @Test void allBranchesMustAcceptSameTimeBeforeAnyInventoryIsReserved()throws Exception{
        tx().executeWithoutResult(tx->{CartLine line=new CartLine();line.userId=customer.id;line.storeId=b.id;line.productId=productB.id;line.quantity=1;em.persist(line);});
        checkout("/api",body("2026-10-26T11:00")).andExpect(status().isBadRequest());untouched();
        checkout("/api",body("2026-10-26T12:00")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
    @ParameterizedTest @ValueSource(strings={"22:00–02:00","bad configuration","10:00–10:00"})
    void closedOrMalformedDailyScheduleDoesNotBecomeAlwaysOpen(String hours)throws Exception{
        tx().executeWithoutResult(tx->em.find(Store.class,a.id).hours=hours);
        checkout("/api",body("2026-10-26T12:00")).andExpect(status().isBadRequest());untouched();
    }
    @Test void supportsOvernightOrders()throws Exception{tx().executeWithoutResult(tx->em.find(Store.class,a.id).hours="22:00–02:00");checkout("/api",body("2026-10-26T01:00")).andExpect(status().isOk());}
    @Test void inactiveBranchRejectsScheduledOrder()throws Exception{tx().executeWithoutResult(tx->em.find(Store.class,a.id).active=false);checkout("/api",body("2026-10-26T12:00")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ngừng nhận đơn")));untouched();}
    @Test void clientCannotDowngradeMissingScheduledTimeToAsap()throws Exception{var body=body("");body.put("scheduleMode","SCHEDULE");checkout("/api",body).andExpect(status().isBadRequest());untouched();}
    @Test void preserveAsapEvenWhenLegacyHoursAreInvalid()throws Exception{tx().executeWithoutResult(tx->em.find(Store.class,a.id).hours="legacy free text");checkout("/api",body("ASAP")).andExpect(status().isOk()).andExpect(jsonPath("$[0].scheduledAt").value("ASAP"));}
    @Test void rereadsStoreHoursWhenCustomerSubmits()throws Exception{var body=body("2026-10-26T11:00");preview(body).andExpect(status().isOk());tx().executeWithoutResult(tx->em.find(Store.class,a.id).hours="12:00–21:00");checkout("/api",body).andExpect(status().isBadRequest());untouched();}
    @ParameterizedTest @ValueSource(strings={"10:00–10:00","24/24","10:00-25:00"})
    void storeManagementRejectsNewAmbiguousHoursWithoutPartialSave(String hours)throws Exception{
        var body=new HashMap<String,Object>(Map.of("id",a.id,"name","Changed store name","address",a.address,"phone","0901234567","hours",hours,"pickup",true,"delivery",true,"active",true,"deliveryFee",15000,"commission",0.1));
        mvc.perform(post("/api/admin/stores").with(user(admin.email)).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        tx().executeWithoutResult(tx->{var store=em.find(Store.class,a.id);assertThat(store.hours).isEqualTo("10:00–22:00");assertThat(store.name).isEqualTo(a.name);});
    }
    @Test void storeManagementCanSaveOvernightHours()throws Exception{
        var body=new HashMap<String,Object>(Map.of("id",a.id,"name",a.name,"address",a.address,"phone","0901234567","hours","22:00–02:00","pickup",true,"delivery",true,"active",true,"deliveryFee",15000,"commission",0.1));
        mvc.perform(post("/api/admin/stores").with(user(admin.email)).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
        checkout("/api",body("2026-10-26T01:00")).andExpect(status().isOk());
    }
    @Test void retrySameCheckoutKeyDoesNotCreateAnotherOrder()throws Exception{var body=body("2026-10-26T12:00");var result=checkout("/api",body).andExpect(status().isOk()).andReturn();long id=json.readTree(result.getResponse().getContentAsString()).get(0).get("id").asLong();checkout("/api",body).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));tx().executeWithoutResult(tx->assertThat(em.find(Product.class,productA.id).stock).isEqualTo(8));}
    @Test void revokedBrowserCannotCreateAScheduledOrder()throws Exception{
        var oldSession=loginSnapshot();
        tx().executeWithoutResult(tx->em.find(Account.class,customer.id).tokenVersion++);
        mvc.perform(post("/shop-api/checkout").session(oldSession).with(user(customer.email)).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(body("2026-10-26T12:00"))))
            .andExpect(status().isUnauthorized());
        untouched();
    }
    @Test void renewedBrowserCanCreateAScheduledOrderAfterRevocation()throws Exception{
        tx().executeWithoutResult(tx->em.find(Account.class,customer.id).tokenVersion++);
        checkout("/shop-api",body("2026-10-26T12:00"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].scheduledAt").value("2026-10-26T12:00"));
        tx().executeWithoutResult(tx->assertThat(em.find(Product.class,productA.id).stock).isEqualTo(8));
    }
}
