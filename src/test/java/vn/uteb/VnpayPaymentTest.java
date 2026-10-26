package vn.uteb;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static vn.uteb.Domain.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:vnpaytest;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "uteb.vnpay.code=TESTCODE", "uteb.vnpay.secret=private-unit-test-signing-key",
    "uteb.base-url=https://shop.example.test", "uteb.mail.enabled=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc @Transactional
@WithMockUser(username="customer@uteb.vn")
class VnpayPaymentTest {
    static final String KEY="private-unit-test-signing-key";
    @Autowired ShopService shop; @Autowired Payments payments; @Autowired VnpayService vnpay;
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @MockitoBean JavaMailSender registrationMail;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    MealOrder order() {
        Product p=shop.all(Product.class).stream().filter(x->!x.customizable&&x.available).findFirst().orElseThrow();
        shop.cart(Map.of("productId",p.id,"quantity",1,"ingredientIds",""));
        return shop.checkout(Map.of("checkoutKey",UUID.randomUUID().toString(),"paymentMethod","VNPAY","type","PICKUP",
            "contactName","Test Customer","phone","0901234567","scheduledAt","ASAP")).getFirst();
    }
    PaymentAttempt begin(MealOrder o)throws Exception {
        payments.start(o.id,"127.0.0.1");return vnpay.attempts(o.id).getFirst();
    }
    Map<String,String> signed(Map<String,String> fields) {
        fields.put("vnp_SecureHash",Payments.sign("HmacSHA512",KEY,Payments.vnRaw(fields)));return fields;
    }
    Map<String,String> result(PaymentAttempt a,boolean success) {
        return signed(new TreeMap<>(Map.of("vnp_TmnCode","TESTCODE","vnp_TxnRef",a.reference,
            "vnp_Amount",VnpayService.minorAmount(a.amount),"vnp_TransactionNo",success?"140000001":"0",
            "vnp_ResponseCode",success?"00":"24","vnp_TransactionStatus",success?"00":"02")));
    }
    @Test void urlUsesDatabaseTotalExactExpiryAndUniquePersistedReference()throws Exception {
        MealOrder o=order();Map<String,Object> started=payments.start(o.id,"::1");
        Map<String,String> fields=new TreeMap<>();
        for(String field:URI.create(started.get("url").toString()).getRawQuery().split("&")){
            String[] pair=field.split("=",2);fields.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
        }
        assertThat(started.get("simulation")).isEqualTo(false);
        assertThat(fields).containsEntry("vnp_TmnCode","TESTCODE").containsEntry("vnp_IpAddr","127.0.0.1")
            .containsEntry("vnp_Amount",VnpayService.minorAmount(o.total)).containsEntry("vnp_ReturnUrl","https://shop.example.test/payments/vnpay/return")
            .containsEntry("vnp_ExpireDate",o.paymentExpires.format(VnpayService.TIME));
        assertThat(fields.get("vnp_Amount")).matches("[0-9]+");
        assertThat(fields.get("vnp_TxnRef")).isNotEqualTo(o.id.toString()).isEqualTo(vnpay.attempts(o.id).getFirst().reference);
        assertThat(fields.get("vnp_SecureHash")).isEqualTo(Payments.sign("HmacSHA512",KEY,Payments.vnRaw(fields)));
        assertThat(started.get("url").toString()).doesNotContain(KEY);
        assertThat(payments.start(o.id,"::1").get("url")).isEqualTo(started.get("url"));
        assertThat(vnpay.attempts(o.id)).hasSize(1);
    }
    @Test void successAndDuplicateCallbackUpdateExactlyOnce()throws Exception {
        MealOrder o=order();PaymentAttempt a=begin(o);int before=shop.where(OrderEvent.class,"orderId",o.id).size();
        assertThat(payments.vnIpn(result(a,true))).containsEntry("RspCode","00");
        assertThat(o.paymentStatus).isEqualTo("PAID");assertThat(o.transactionRef).isEqualTo("140000001");assertThat(a.state).isEqualTo("PAID");
        assertThat(payments.vnIpn(result(a,true))).containsEntry("RspCode","02");
        assertThat(shop.where(OrderEvent.class,"orderId",o.id)).hasSize(before+1);
    }
    @Test void failedPaymentCanRetryWithANewReferenceAndOldFailureCannotOverwriteSuccess()throws Exception {
        MealOrder o=order();PaymentAttempt first=begin(o);
        assertThat(payments.vnIpn(result(first,false))).containsEntry("RspCode","00");assertThat(o.paymentStatus).isEqualTo("FAILED");
        PaymentAttempt second=begin(o);assertThat(second.reference).isNotEqualTo(first.reference);
        assertThat(payments.vnIpn(result(second,true))).containsEntry("RspCode","00");
        assertThat(payments.vnIpn(result(first,false))).containsEntry("RspCode","02");assertThat(o.paymentStatus).isEqualTo("PAID");
    }
    @ParameterizedTest @ValueSource(strings={"MERCHANT","SIGNATURE","TAMPER","UNKNOWN","AMOUNT","DECIMAL","EXPONENT","STATUS","TRANSACTION"})
    void rejectsInvalidCallbacksWithoutChangingOrder(String problem)throws Exception {
        MealOrder o=order();PaymentAttempt a=begin(o);Map<String,String> fields=result(a,true);String expected="97";
        switch(problem){
            case "MERCHANT"->{fields.put("vnp_TmnCode","WRONG123");signed(fields);}
            case "SIGNATURE"->fields.put("vnp_SecureHash","a".repeat(128));
            case "TAMPER"->fields.put("vnp_Amount","1");
            case "UNKNOWN"->{fields.put("vnp_TxnRef","UnknownReference");signed(fields);expected="01";}
            case "AMOUNT"->{fields.put("vnp_Amount","1");signed(fields);expected="04";}
            case "DECIMAL"->{fields.put("vnp_Amount",fields.get("vnp_Amount")+".00");signed(fields);expected="04";}
            case "EXPONENT"->{fields.put("vnp_Amount","1E7");signed(fields);expected="04";}
            case "STATUS"->{fields.remove("vnp_TransactionStatus");signed(fields);expected="99";}
            case "TRANSACTION"->{fields.put("vnp_TransactionNo","0");signed(fields);expected="99";}
        }
        assertThat(payments.vnIpn(fields)).containsEntry("RspCode",expected);
        assertThat(o.paymentStatus).isEqualTo("PENDING");assertThat(a.state).isEqualTo("PENDING");
    }
    @Test void bothResponseAndTransactionStatusMustBeSuccessful()throws Exception {
        MealOrder o=order();PaymentAttempt a=begin(o);Map<String,String> fields=result(a,true);
        fields.put("vnp_TransactionStatus","02");signed(fields);
        assertThat(payments.vnIpn(fields)).containsEntry("RspCode","00");assertThat(o.paymentStatus).isEqualTo("FAILED");
    }
    @Test void delayedSuccessAfterExpiryIsRecordedForReviewAndStockReturnsOnlyOnce()throws Exception {
        MealOrder o=order();PaymentAttempt a=begin(o);OrderLine line=shop.where(OrderLine.class,"orderId",o.id).getFirst();Product p=shop.get(Product.class,line.productId);int reserved=p.stock;
        o.paymentExpires=LocalDateTime.now().minusSeconds(1);
        assertThat(payments.vnIpn(result(a,true))).containsEntry("RspCode","00");
        assertThat(o.status).isEqualTo("CANCELLED");assertThat(o.paymentStatus).isEqualTo("REVIEW_REQUIRED");assertThat(a.state).isEqualTo("REVIEW_REQUIRED");
        assertThat(p.stock).isEqualTo(reserved+line.quantity);
        assertThat(payments.vnIpn(result(a,true))).containsEntry("RspCode","02");shop.expirePayments();assertThat(p.stock).isEqualTo(reserved+line.quantity);
    }
    @Test void lateSuccessCannotReopenACancelledOrder()throws Exception {
        MealOrder o=order();PaymentAttempt a=begin(o);shop.status(o.id,Map.of("status","CANCELLED"));
        assertThat(payments.vnIpn(result(a,true))).containsEntry("RspCode","00");assertThat(o.status).isEqualTo("CANCELLED");assertThat(o.paymentStatus).isEqualTo("REVIEW_REQUIRED");
    }
    @Test void browserReturnNeverMarksAnOrderPaid()throws Exception {
        MealOrder o=order();PaymentAttempt a=begin(o);var request=get("/payments/vnpay/return");result(a,true).forEach(request::param);
        mvc.perform(request).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/#payment-result/"+o.id+"/received"));
        assertThat(o.paymentStatus).isEqualTo("PENDING");assertThat(a.state).isEqualTo("PENDING");
    }
    @Test void forgedAndDuplicateReturnFieldsAreRejected()throws Exception {
        MealOrder o=order();PaymentAttempt a=begin(o);
        mvc.perform(get("/payments/vnpay/return").param("vnp_ResponseCode","00")).andExpect(redirectedUrl("/#payment-result/invalid"));
        var request=get("/payments/vnpay/return");result(a,true).forEach(request::param);
        request.param("vnp_TxnRef",a.reference);mvc.perform(request).andExpect(redirectedUrl("/#payment-result/invalid"));assertThat(o.paymentStatus).isEqualTo("PENDING");
    }
    @Test void ipnIsPublicAndRejectsDuplicateParameters()throws Exception {
        MealOrder o=order();PaymentAttempt a=begin(o);var request=get("/payments/vnpay/ipn").with(anonymous());result(a,true).forEach(request::param);request.param("vnp_Amount","1");
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("97"));assertThat(o.paymentStatus).isEqualTo("PENDING");
        var valid=get("/payments/vnpay/ipn").with(anonymous());result(a,true).forEach(valid::param);
        mvc.perform(valid).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));assertThat(o.paymentStatus).isEqualTo("PAID");
    }
    @ParameterizedTest @ValueSource(strings={"COD","PAID","CANCELLED","EXPIRED"})
    void doesNotStartPaymentsForIneligibleOrders(String reason)throws Exception {
        MealOrder o=order();switch(reason){case "COD"->o.paymentMethod="COD";case "PAID"->o.paymentStatus="PAID";case "CANCELLED"->o.status="CANCELLED";case "EXPIRED"->o.paymentExpires=LocalDateTime.now().minusSeconds(1);}
        assertThatThrownBy(()->payments.start(o.id,"127.0.0.1")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(vnpay.attempts(o.id)).isEmpty();
    }
    @Test void cannotSettleVnpayThroughDemoSimulationEndpoint()throws Exception {
        MealOrder o=order();mvc.perform(post("/shop-api/orders/"+o.id+"/simulate").with(user("customer@uteb.vn")).with(csrf()).contentType("application/json").content("{\"success\":true}"))
            .andExpect(status().isBadRequest());assertThat(o.paymentStatus).isEqualTo("PENDING");
    }
    @Test void wrongCustomerCannotCreatePayment()throws Exception {
        MealOrder o=order();mvc.perform(post("/shop-api/orders/"+o.id+"/payment").with(user("vendor2@uteb.vn")).with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());assertThat(vnpay.attempts(o.id)).isEmpty();
    }
    @Test void missingMerchantSettingsFailWithoutCreatingAnAttempt() {
        VnpayService disabled=new VnpayService(shop,"https://shop.example.test","","secret");assertThat(disabled.configured()).isFalse();
        assertThatThrownBy(disabled::requireConfigured).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void conflictingSuccessIsKeptForReviewEvenAfterTheOrderExpires()throws Exception {
        MealOrder o=order();PaymentAttempt a=begin(o);payments.vnIpn(result(a,false));
        assertThat(payments.vnIpn(result(a,true))).containsEntry("RspCode","00");
        assertThat(a.state).isEqualTo("REVIEW_REQUIRED");assertThat(o.paymentStatus).isEqualTo("REVIEW_REQUIRED");
        o.paymentExpires=LocalDateTime.now().minusSeconds(1);shop.expirePayments();
        assertThat(o.status).isEqualTo("CANCELLED");assertThat(o.paymentStatus).isEqualTo("REVIEW_REQUIRED");
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousIpnsCommitOnlyOneConfirmation()throws Exception {
        MealOrder o=order();PaymentAttempt a=begin(o);Map<String,String> fields=result(a,true);
        var transaction=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        int events=shop.where(OrderEvent.class,"orderId",o.id).size();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var barrier=new java.util.concurrent.CyclicBarrier(2);
            java.util.concurrent.Callable<String> callback=()->{barrier.await(5,java.util.concurrent.TimeUnit.SECONDS);return payments.vnIpn(fields).get("RspCode");};
            var first=executor.submit(callback);var second=executor.submit(callback);
            assertThat(List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("00","02");
            assertThat(shop.get(MealOrder.class,o.id).paymentStatus).isEqualTo("PAID");
            assertThat(shop.where(OrderEvent.class,"orderId",o.id)).hasSize(events+1);
        } finally {
            transaction.executeWithoutResult(status->{
                for(OrderLine line:shop.where(OrderLine.class,"orderId",o.id)){
                    shop.get(Product.class,line.productId).stock+=line.quantity;em.remove(line);
                }
                for(PaymentAttempt attempt:shop.where(PaymentAttempt.class,"orderId",o.id))em.remove(attempt);
                for(OrderEvent event:shop.where(OrderEvent.class,"orderId",o.id))em.remove(event);
                em.createQuery("delete from Notice where orderId=:id").setParameter("id",o.id).executeUpdate();
                em.flush();em.remove(shop.get(MealOrder.class,o.id));
            });
        }
    }
}
