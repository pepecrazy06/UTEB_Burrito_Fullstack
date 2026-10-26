package vn.uteb;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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

/** Real commit boundaries, isolated H2 data, mocked notifications; no payment gateway calls. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:cancellationtest;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "spring.jpa.hibernate.ddl-auto=create-drop", "uteb.seed=false", "uteb.mail.enabled=false", "uteb.momo.partner=TASK2TEST", "uteb.momo.access=task2-test-access", "uteb.momo.secret=task2-test-signing-key"})
@ActiveProfiles("demo") @AutoConfigureMockMvc
class OrderCancellationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired OrderCancellationService cancellations;@Autowired ShopService shop;
    @Autowired Payments payments;@Autowired PlatformTransactionManager manager;@PersistenceContext EntityManager em;
    @MockitoBean SimpMessagingTemplate socket;
    Store store,otherStore;Account customer,other,admin,branchManager,foreignManager,staff,vendor;Product product;MealOrder order;
    final List<Long> orderIds=new ArrayList<>();
    static final String REASON="Tôi có việc đột xuất nên xin hủy đơn trước khi cửa hàng chế biến.";
    TransactionTemplate tx(){return new TransactionTemplate(manager);}
    @BeforeEach void setup(){tx().executeWithoutResult(tx->{
        store=new Store();store.name="Cancellation test";em.persist(store);otherStore=new Store();otherStore.name="Other test";em.persist(otherStore);
        customer=account("CUSTOMER",null);other=account("CUSTOMER",null);admin=account("ADMIN",null);branchManager=account("MANAGER",store.id);foreignManager=account("MANAGER",otherStore.id);staff=account("STAFF",store.id);vendor=account("CUSTOMER,VENDOR",store.id);
        product=new Product();product.name="Test meal";product.storeId=store.id;product.stock=8;product.price=new BigDecimal("50000");em.persist(product);
        order=newOrder(customer);clearInvocations(socket);
    });}
    Account account(String roles,Long storeId){Account a=new Account();a.email=UUID.randomUUID()+"@example.test";a.name="Cancellation test";a.roles=roles;a.storeId=storeId;a.verified=true;em.persist(a);return a;}
    MealOrder newOrder(Account owner){
        MealOrder o=new MealOrder();o.userId=owner.id;o.storeId=store.id;o.code="TEST-"+UUID.randomUUID().toString().substring(0,8);o.checkoutKey=UUID.randomUUID().toString();o.status="NEW";o.paymentMethod="MOMO";o.paymentStatus="PAID";o.transactionRef="MOMO-"+UUID.randomUUID();o.type="PICKUP";o.total=new BigDecimal("100000");o.paymentExpires=LocalDateTime.now().plusMinutes(15);em.persist(o);orderIds.add(o.id);
        OrderLine line=new OrderLine();line.orderId=o.id;line.productId=product.id;line.quantity=2;line.unitPrice=new BigDecimal("50000");line.name="Test meal";em.persist(line);return o;
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();tx().executeWithoutResult(tx->{
        for(Long id:orderIds){em.createQuery("delete from OrderCancellation where orderId=:id").setParameter("id",id).executeUpdate();em.createQuery("delete from OrderEvent where orderId=:id").setParameter("id",id).executeUpdate();em.createQuery("delete from Notice where orderId=:id").setParameter("id",id).executeUpdate();em.createQuery("delete from PaymentAttempt where orderId=:id").setParameter("id",id).executeUpdate();em.createQuery("delete from OrderLine where orderId=:id").setParameter("id",id).executeUpdate();em.remove(em.find(MealOrder.class,id));}
        em.createQuery("delete from AuditRecord where actorId in :ids").setParameter("ids",List.of(customer.id,other.id,admin.id,branchManager.id,foreignManager.id,staff.id,vendor.id)).executeUpdate();
        em.remove(em.find(Product.class,product.id));for(Account a:List.of(customer,other,admin,branchManager,foreignManager,staff,vendor))em.remove(em.find(Account.class,a.id));em.remove(em.find(Store.class,store.id));em.remove(em.find(Store.class,otherStore.id));
    });}
    ResultActions request(Account actor,long id)throws Exception{return mvc.perform(post("/api/orders/"+id+"/cancellation").with(user(actor.email)).contentType("application/json").content(json.writeValueAsString(Map.of("reason",REASON))));}
    long requested()throws Exception{return json.readTree(request(customer,order.id).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();}
    ResultActions decide(Account actor,long id,String status,String note)throws Exception{return mvc.perform(put("/api/cancellations/"+id+"/decision").with(user(actor.email)).contentType("application/json").content(json.writeValueAsString(Map.of("status",status,"note",note))));}
    Map<String,Object> evidence(){return new HashMap<>(Map.of("confirmed",true,"reference","REFUND-"+UUID.randomUUID(),"amount",100000,"refundedAt",LocalDateTime.now().toString(),"evidence","Ngân hàng đã xác nhận chuyển tiền cho khách.","note","Hoàn đủ tiền thủ công"));}
    ResultActions confirm(Account actor,long id,Map<String,Object> body)throws Exception{return mvc.perform(put("/api/cancellations/"+id+"/refund").with(user(actor.email)).contentType("application/json").content(json.writeValueAsString(body)));}
    MealOrder reload(){return tx().execute(tx->em.find(MealOrder.class,order.id));}
    int stock(){return tx().execute(tx->em.find(Product.class,product.id).stock);}

    @Test void requestDoesNotCancelOrRefundAndCanBeReadByOwner()throws Exception{
        long id=requested();assertThat(reload().status).isEqualTo("NEW");assertThat(reload().paymentStatus).isEqualTo("PAID");assertThat(stock()).isEqualTo(8);
        mvc.perform(get("/api/orders/"+order.id+"/cancellation").with(user(customer.email))).andExpect(status().isOk()).andExpect(jsonPath("$.request.id").value(id)).andExpect(jsonPath("$.request.status").value("REQUESTED"));
        mvc.perform(get("/api/orders").with(user(customer.email))).andExpect(status().isOk()).andExpect(jsonPath("$[0].cancellationStatus").value("REQUESTED"));
        mvc.perform(get("/api/orders/"+order.id).with(user(other.email))).andExpect(status().isForbidden());
        verify(socket,atLeastOnce()).convertAndSendToUser(eq(customer.email),eq("/queue/events"),any(Object.class));
    }
    @ParameterizedTest @ValueSource(strings={"PREPARING","READY","DELIVERING","COMPLETED","CANCELLED"})
    void cannotRequestAfterPreparation(String state)throws Exception{tx().executeWithoutResult(tx->em.find(MealOrder.class,order.id).status=state);request(customer,order.id).andExpect(status().isBadRequest());}
    @ParameterizedTest @ValueSource(strings={"UNPAID","PENDING","FAILED","EXPIRED","REVIEW_REQUIRED","REFUNDED"})
    void cannotRequestWithoutConfirmedPayment(String state)throws Exception{tx().executeWithoutResult(tx->em.find(MealOrder.class,order.id).paymentStatus=state);request(customer,order.id).andExpect(status().isBadRequest());assertThat(stock()).isEqualTo(8);}
    @Test void ownershipAndDuplicateProtection()throws Exception{
        request(other,order.id).andExpect(status().isForbidden());requested();request(customer,order.id).andExpect(status().isConflict());
        Long count=tx().execute(tx->em.createQuery("select count(c) from OrderCancellation c",Long.class).getSingleResult());assertThat(count).isEqualTo(1L);
    }
    @Test void approvalCancelsOnceButDoesNotRefund()throws Exception{
        long id=requested();decide(admin,id,"APPROVED","").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(reload().status).isEqualTo("CANCELLED");assertThat(reload().paymentStatus).isEqualTo("PAID");assertThat(stock()).isEqualTo(10);
        decide(admin,id,"APPROVED","").andExpect(status().isConflict());confirm(admin,id,evidence()).andExpect(status().isOk()).andExpect(jsonPath("$.confirmedAmount").value(100000));
        assertThat(reload().paymentStatus).isEqualTo("REFUNDED");assertThat(stock()).isEqualTo(10);confirm(admin,id,evidence()).andExpect(status().isConflict());
        mvc.perform(get("/api/orders/"+order.id+"/cancellation").with(user(customer.email))).andExpect(jsonPath("$.request.evidence").isNotEmpty()).andExpect(jsonPath("$.request.refundedBy").value(admin.id));
    }
    @Test void rejectionRequiresReasonAndResumesOrderWithoutChangingPayment()throws Exception{
        long id=requested();decide(admin,id,"REJECTED","").andExpect(status().isBadRequest());
        mvc.perform(put("/api/orders/"+order.id+"/status").with(user(admin.email)).contentType("application/json").content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isBadRequest());
        decide(branchManager,id,"REJECTED","Cửa hàng đã chuẩn bị nguyên liệu cho đơn.").andExpect(status().isOk()).andExpect(jsonPath("$.decisionNote").isNotEmpty());
        assertThat(reload().status).isEqualTo("NEW");assertThat(reload().paymentStatus).isEqualTo("PAID");assertThat(stock()).isEqualTo(8);
        mvc.perform(put("/api/orders/"+order.id+"/status").with(user(admin.email)).contentType("application/json").content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
        confirm(admin,id,evidence()).andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(strings={"CUSTOMER","STAFF","VENDOR","FOREIGN_MANAGER"})
    void onlyAuthorizedAdministratorsCanProcessOrViewEvidence(String role)throws Exception{
        Account actor=switch(role){case "STAFF"->staff;case "VENDOR"->vendor;case "FOREIGN_MANAGER"->foreignManager;default->customer;};
        long id=requested();decide(actor,id,"APPROVED","").andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/cancellations").with(user(actor.email))).andExpect(status().is(role.equals("FOREIGN_MANAGER")?200:403));
        decide(admin,id,"APPROVED","").andExpect(status().isOk());confirm(actor,id,evidence()).andExpect(status().isForbidden());
        if(actor==staff||actor==vendor)mvc.perform(get("/api/orders/"+order.id).with(user(actor.email))).andExpect(status().isOk()).andExpect(jsonPath("$.cancellation.request").doesNotExist()).andExpect(jsonPath("$.cancellation.status").value("APPROVED"));
    }
    @ParameterizedTest @ValueSource(strings={"confirmed","reference","amount","refundedAt","evidence","wrongAmount","futureTime","oldTime","longEvidence"})
    void invalidEvidenceCannotMarkRefunded(String missing)throws Exception{
        long id=requested();decide(admin,id,"APPROVED","").andExpect(status().isOk());var body=evidence();
        switch(missing){case "wrongAmount"->body.put("amount",99999);case "futureTime"->body.put("refundedAt",LocalDateTime.now().plusDays(1).toString());case "oldTime"->body.put("refundedAt",LocalDateTime.now().minusDays(1).toString());case "longEvidence"->body.put("evidence","x".repeat(256));default->body.remove(missing);}
        confirm(admin,id,body).andExpect(status().isBadRequest());assertThat(reload().paymentStatus).isEqualTo("PAID");
    }
    @Test void vnPayRequiresVerifiedAttemptAndMatchingAmount()throws Exception{
        tx().executeWithoutResult(tx->em.find(MealOrder.class,order.id).paymentMethod="VNPAY");request(customer,order.id).andExpect(status().isBadRequest());
        tx().executeWithoutResult(tx->{PaymentAttempt p=new PaymentAttempt();p.orderId=order.id;p.reference="ATTEMPT-"+UUID.randomUUID();p.amount=order.total;p.state="PAID";p.responseCode="00";p.transactionStatus="00";p.transactionNo=order.transactionRef;p.expiresAt=LocalDateTime.now().plusMinutes(5);em.persist(p);});
        requested();
    }
    @Test void codKeepsExistingUnpaidCancellationFlow()throws Exception{
        tx().executeWithoutResult(tx->{var o=em.find(MealOrder.class,order.id);o.paymentMethod="COD";o.paymentStatus="UNPAID";});request(customer,order.id).andExpect(status().isBadRequest());
        mvc.perform(put("/api/orders/"+order.id+"/status").with(user(customer.email)).contentType("application/json").content("{\"status\":\"CANCELLED\"}")).andExpect(status().isOk());assertThat(stock()).isEqualTo(10);
    }
    @Test void longVietnameseReasonAndRejectionAreStoredWithoutTruncation()throws Exception{
        String reason="ấ".repeat(2000),note="ề".repeat(1000);
        var result=mvc.perform(post("/api/orders/"+order.id+"/cancellation").with(user(customer.email)).contentType("application/json").content(json.writeValueAsString(Map.of("reason",reason)))).andExpect(status().isOk()).andReturn();
        long id=json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        decide(admin,id,"REJECTED",note).andExpect(status().isOk()).andExpect(jsonPath("$.reason").value(reason)).andExpect(jsonPath("$.decisionNote").value(note));
    }
    @Test void duplicateRefundReferenceCannotConfirmAnotherOrder()throws Exception{
        long id=requested();decide(admin,id,"APPROVED","").andExpect(status().isOk());var proof=evidence();confirm(admin,id,proof).andExpect(status().isOk());
        MealOrder second=tx().execute(tx->newOrder(customer));
        long next=json.readTree(request(customer,second.id).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        decide(admin,next,"APPROVED","").andExpect(status().isOk());proof.put("refundedAt",LocalDateTime.now().toString());confirm(admin,next,proof).andExpect(status().isConflict());
        assertThat(tx().execute(tx->em.find(MealOrder.class,second.id)).paymentStatus).isEqualTo("PAID");
    }
    @Test void legacyPostDeliveryRefundRemainsSeparate()throws Exception{
        tx().executeWithoutResult(tx->em.find(MealOrder.class,order.id).status="COMPLETED");
        var result=mvc.perform(post("/api/orders/"+order.id+"/refund").with(user(customer.email)).contentType("application/json").content(json.writeValueAsString(Map.of("reason",REASON)))).andExpect(status().isOk()).andReturn();
        long id=json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put("/api/refunds/"+id).with(user(admin.email)).contentType("application/json").content("{\"status\":\"APPROVED\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/refunds/"+id).with(user(admin.email)).contentType("application/json").content("{\"status\":\"REFUNDED\",\"evidence\":\"Legacy manual refund proof\"}")).andExpect(status().isOk());
        assertThat(reload().paymentStatus).isEqualTo("REFUNDED");assertThat(stock()).isEqualTo(8);
    }
    Map<String,Object> momoCallback(){
        Map<String,Object> fields=new LinkedHashMap<>(Map.of("partnerCode","TASK2TEST","orderId",order.id.toString(),"amount","100000","resultCode","0","transId","123456789"));
        String raw="accessKey=task2-test-access&amount=100000&extraData=&message=&orderId="+order.id+"&orderInfo=&orderType=&partnerCode=TASK2TEST&payType=&requestId=&responseTime=&resultCode=0&transId=123456789";
        fields.put("signature",Payments.sign("HmacSHA256","task2-test-signing-key",raw));return fields;
    }
    @Test void signedMomoPaymentAndDuplicateCallbacksCannotUndoManualRefund()throws Exception{
        tx().executeWithoutResult(tx->{var o=em.find(MealOrder.class,order.id);o.paymentStatus="PENDING";o.transactionRef="";});
        payments.momoIpn(momoCallback());assertThat(reload().paymentStatus).isEqualTo("PAID");
        long id=requested();decide(admin,id,"APPROVED","").andExpect(status().isOk());payments.momoIpn(momoCallback());
        assertThat(reload().status).isEqualTo("CANCELLED");assertThat(reload().paymentStatus).isEqualTo("PAID");
        confirm(admin,id,evidence()).andExpect(status().isOk());payments.momoIpn(momoCallback());
        assertThat(reload().paymentStatus).isEqualTo("REFUNDED");assertThat(stock()).isEqualTo(10);
    }
    @Test void rollbackDoesNotPersistApprovalStockOrNotifications()throws Exception{
        long id=requested();clearInvocations(socket);SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(admin.email,"","ROLE_ADMIN"));
        tx().executeWithoutResult(tx->{cancellations.decide(id,Map.of("status","APPROVED"));verifyNoInteractions(socket);tx.setRollbackOnly();});
        assertThat(reload().status).isEqualTo("NEW");assertThat(stock()).isEqualTo(8);verifyNoInteractions(socket);
    }
    List<Integer> concurrent(Callable<Integer> first,Callable<Integer> second)throws Exception{
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try{List<Future<Integer>> results=new ArrayList<>();for(Callable<Integer> call:List.of(first,second))results.add(executor.submit(()->{ready.countDown();go.await();return call.call();}));assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();return List.of(results.get(0).get(15,TimeUnit.SECONDS),results.get(1).get(15,TimeUnit.SECONDS));}finally{executor.shutdownNow();}
    }
    @Test void concurrentRequestsCreateExactlyOneRequest()throws Exception{
        assertThat(concurrent(()->request(customer,order.id).andReturn().getResponse().getStatus(),()->request(customer,order.id).andReturn().getResponse().getStatus())).containsExactlyInAnyOrder(200,409);
    }
    @Test void concurrentApprovalRestoresStockOnlyOnce()throws Exception{
        long id=requested();assertThat(concurrent(()->decide(admin,id,"APPROVED","").andReturn().getResponse().getStatus(),()->decide(branchManager,id,"APPROVED","").andReturn().getResponse().getStatus())).containsExactlyInAnyOrder(200,409);assertThat(stock()).isEqualTo(10);assertThat(reload().paymentStatus).isEqualTo("PAID");
    }
    @Test void concurrentConfirmationRecordsOneRefund()throws Exception{
        long id=requested();decide(admin,id,"APPROVED","").andExpect(status().isOk());var body=evidence();
        assertThat(concurrent(()->confirm(admin,id,body).andReturn().getResponse().getStatus(),()->confirm(branchManager,id,body).andReturn().getResponse().getStatus())).containsExactlyInAnyOrder(200,409);assertThat(stock()).isEqualTo(10);assertThat(reload().paymentStatus).isEqualTo("REFUNDED");
    }
    @Test void requestRacingPreparationNeverLeavesPendingRequestOnPreparedOrder()throws Exception{
        tx().executeWithoutResult(tx->em.find(MealOrder.class,order.id).status="CONFIRMED");
        var outcomes=concurrent(()->request(customer,order.id).andReturn().getResponse().getStatus(),()->mvc.perform(put("/api/orders/"+order.id+"/status").with(user(admin.email)).contentType("application/json").content("{\"status\":\"PREPARING\"}")).andReturn().getResponse().getStatus());
        assertThat(outcomes).contains(200);assertThat(outcomes).allMatch(code->code==200||code==400||code==409);
        tx().executeWithoutResult(tx->{var o=em.find(MealOrder.class,order.id);var requests=shop.where(OrderCancellation.class,"orderId",o.id);if(o.status.equals("PREPARING"))assertThat(requests).isEmpty();else{assertThat(o.status).isEqualTo("CONFIRMED");assertThat(requests).hasSize(1);}});
    }
}
