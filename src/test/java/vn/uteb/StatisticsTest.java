package vn.uteb;

import com.fasterxml.jackson.databind.*;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.uteb.Domain.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:statisticstest;MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop", "uteb.seed=false", "uteb.mail.enabled=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc
class StatisticsTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired PlatformTransactionManager transactions;@PersistenceContext EntityManager em;
    Store a,b,c;Account admin,manager,unassigned,vendor,staff,shipper,buyer,other;MealOrder first,second,branchB;
    TransactionTemplate tx(){return new TransactionTemplate(transactions);}
    Account account(String role,Long store){Account account=new Account();account.email=UUID.randomUUID()+"@example.test";account.name="Statistics test";account.roles=role;account.storeId=store;account.verified=true;em.persist(account);return account;}
    Store store(String name){Store store=new Store();store.name=name;em.persist(store);return store;}
    MealOrder order(Store store,Account owner,String date,String status,String payment,String total){
        MealOrder order=new MealOrder();order.storeId=store.id;order.userId=owner.id;order.code="STAT-"+UUID.randomUUID().toString().substring(0,8);order.checkoutKey=UUID.randomUUID().toString();order.createdAt=LocalDateTime.parse(date);order.status=status;order.paymentStatus=payment;order.paymentMethod="MOMO";order.total=new BigDecimal(total);order.subtotal=order.total;order.commissionRate=new BigDecimal("0.10");em.persist(order);
        OrderLine line=new OrderLine();line.orderId=order.id;line.name="Burrito";line.quantity=1;line.unitPrice=order.total;em.persist(line);return order;
    }
    void refund(MealOrder order){RefundRequest refund=new RefundRequest();refund.orderId=order.id;refund.userId=order.userId;refund.storeId=order.storeId;refund.status="REFUNDED";refund.amount=order.total;em.persist(refund);}
    @BeforeEach void setup(){tx().executeWithoutResult(tx->{
        a=store("Branch A");b=store("Branch B");c=store("Closed historical branch");c.active=false;
        admin=account("ADMIN",null);manager=account("MANAGER",a.id);unassigned=account("MANAGER",null);vendor=account("VENDOR",a.id);staff=account("STAFF",a.id);shipper=account("SHIPPER",a.id);buyer=account("CUSTOMER",null);other=account("CUSTOMER",null);
        first=order(a,buyer,"2026-10-01T00:00","COMPLETED","PAID","100000");
        second=order(a,other,"2026-10-01T23:59:59.999999","COMPLETED","PAID","200000");second.paymentMethod="COD";
        branchB=order(b,buyer,"2026-10-02T12:00","COMPLETED","PAID","300000");branchB.paymentMethod="VNPAY";
        order(a,buyer,"2026-09-30T23:59:59.999999","COMPLETED","PAID","50000");
        order(a,buyer,"2026-10-02T00:00","COMPLETED","PAID","400000");
        order(a,buyer,"2026-10-01T12:00","CANCELLED","PAID","900000");
        refund(order(a,buyer,"2026-10-01T13:00","COMPLETED","REFUNDED","800000"));
        order(a,buyer,"2026-10-01T14:00","NEW","PAID","700000");order(a,buyer,"2026-10-01T15:00","COMPLETED","UNPAID","600000");
        order(a,buyer,"2026-10-01T16:00","COMPLETED","FAILED","500000");order(a,buyer,"2026-10-01T17:00","COMPLETED","PENDING","450000");
        refund(order(a,buyer,"2026-10-01T18:00","COMPLETED","PAID","350000"));
        order(a,buyer,"2026-10-01T19:00","CONFIRMED","PAID","250000");order(a,buyer,"2026-10-01T20:00","CANCELLED","REFUNDED","150000");
        OrderLine extra=new OrderLine();extra.orderId=first.id;extra.name="Burrito";extra.quantity=2;em.persist(extra);
        for(String state:List.of("PAID","FAILED","PENDING")){PaymentAttempt attempt=new PaymentAttempt();attempt.orderId=first.id;attempt.reference=UUID.randomUUID().toString();attempt.amount=first.total;attempt.state=state;attempt.expiresAt=LocalDateTime.now().plusMinutes(15);em.persist(attempt);}
    });}
    @AfterEach void cleanup(){tx().executeWithoutResult(tx->{
        // This context has no seed data and its own in-memory database.
        for(String entity:List.of("PaymentAttempt","RefundRequest","OrderLine","MealOrder","Account","Store"))em.createQuery("delete from "+entity).executeUpdate();
    });}
    ResultActions getStats(Account actor,Map<String,String> params)throws Exception{
        var request=get("/api/admin/dashboard").with(user(actor.email));params.forEach(request::param);return mvc.perform(request);
    }
    JsonNode stats(Account actor,Map<String,String> params)throws Exception{return json.readTree(getStats(actor,params).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    void money(JsonNode data,String key,String expected){assertThat(data.get(key).decimalValue()).isEqualByComparingTo(expected);}
    Map<String,String> day(){return Map.of("startDate","2026-10-01","endDate","2026-10-01");}

    @Test void inclusiveWholeDayUsesCreationTimeAndOneScopeForEveryMetric()throws Exception{
        JsonNode data=stats(admin,day());money(data,"revenue","300000");money(data,"commission","30000");money(data,"net","270000");
        assertThat(data.get("totalOrders").asLong()).isEqualTo(11);assertThat(data.get("completedOrders").asLong()).isEqualTo(2);assertThat(data.get("customers").asLong()).isEqualTo(2);assertThat(data.get("stores").asLong()).isEqualTo(1);
        assertThat(data.get("daily").size()).isEqualTo(1);money(data.get("daily"),"2026-10-01","300000");assertThat(data.get("topProducts").get("Burrito").asLong()).isEqualTo(4);
        assertThat(data.get("orders").size()).isEqualTo(5);for(JsonNode order:data.get("orders")){assertThat(order.get("createdAt").asText()).startsWith("2026-10-01");assertThat(order.get("storeId").asLong()).isEqualTo(a.id);}
        assertThat(data.get("filters").get("timeZone").asText()).isEqualTo("Asia/Ho_Chi_Minh");assertThat(data.get("filters").get("dateField").asText()).isEqualTo("createdAt");
    }
    @Test void combinedDateAndBranchFilters()throws Exception{
        JsonNode data=stats(admin,Map.of("startDate","2026-10-01","endDate","2026-10-02","storeId",b.id.toString()));money(data,"revenue","300000");assertThat(data.get("totalOrders").asLong()).isEqualTo(1);assertThat(data.get("daily").has("2026-10-01")).isFalse();
    }
    @Test void noFiltersForAdminCoverAllPermittedBranches()throws Exception{JsonNode data=stats(admin,Map.of());money(data,"revenue","1050000");assertThat(data.get("totalOrders").asLong()).isEqualTo(14);assertThat(data.get("availableStores").size()).isEqualTo(3);}
    @Test void oneSidedDatesAreSupported()throws Exception{money(stats(admin,Map.of("startDate","2026-10-02")),"revenue","700000");money(stats(admin,Map.of("endDate","2026-10-01")),"revenue","350000");}
    @Test void emptyWindowReturnsZerosAndEmptyChartsAndOrders()throws Exception{
        var data=stats(admin,Map.of("startDate","2026-10-03","endDate","2026-10-03"));for(String key:List.of("revenue","commission","net","totalOrders","customers","completedOrders","stores"))money(data,key,"0");assertThat(data.get("orders").size()).isZero();assertThat(data.get("daily").size()).isZero();assertThat(data.get("topProducts").size()).isZero();
    }
    @Test void reversedDatesAreBadRequest()throws Exception{getStats(admin,Map.of("startDate","2026-10-25","endDate","2026-10-01")).andExpect(status().isBadRequest());}
    @ParameterizedTest @ValueSource(strings={"2026-02-30","2026-13-01","01/10/2026","2026-10-01T00:00","0000-01-01","+10000-01-01"})
    void malformedDatesNeverCauseServerError(String value)throws Exception{getStats(admin,Map.of("startDate",value)).andExpect(status().isBadRequest());getStats(admin,Map.of("endDate",value)).andExpect(status().isBadRequest());}
    @ParameterizedTest @ValueSource(strings={"0","-1","abc","1.5","9999999999999999999999999"})
    void invalidBranchIdsAreBadRequest(String value)throws Exception{getStats(admin,Map.of("storeId",value)).andExpect(status().isBadRequest());}
    @Test void validExtremeDatesDoNotOverflowSqlServerPrecision()throws Exception{money(stats(admin,Map.of("startDate","0001-01-01","endDate","9999-12-31")),"revenue","1050000");}
    @Test void administratorCanFilterInactiveHistoricalStore()throws Exception{tx().executeWithoutResult(tx->order(c,buyer,"2026-11-03T10:00","COMPLETED","PAID","123"));money(stats(admin,Map.of("storeId",c.id.toString())),"revenue","123");}
    @Test void unknownStoreIsNotFoundForAdminButForbiddenForManager()throws Exception{getStats(admin,Map.of("storeId","9223372036854775807")).andExpect(status().isNotFound());getStats(manager,Map.of("storeId","9223372036854775807")).andExpect(status().isForbidden());}
    @Test void managerCannotEscapeAssignedStoreByOmittingOrForgingFilter()throws Exception{
        var data=stats(manager,Map.of());money(data,"revenue","750000");assertThat(data.get("availableStores").size()).isEqualTo(1);assertThat(data.get("availableStores").get(0).get("id").asLong()).isEqualTo(a.id);
        getStats(manager,Map.of("storeId",b.id.toString())).andExpect(status().isForbidden());money(stats(manager,Map.of("storeId",a.id.toString(),"startDate","2026-10-01","endDate","2026-10-01")),"revenue","300000");
    }
    @Test void unassignedManagerGetsEmptyScopeInsteadOfGlobalData()throws Exception{var data=stats(unassigned,Map.of());money(data,"revenue","0");assertThat(data.get("availableStores").size()).isZero();getStats(unassigned,Map.of("storeId",a.id.toString())).andExpect(status().isForbidden());}
    @Test void vendorAndStaffKeepExistingBranchScope()throws Exception{for(Account actor:List.of(vendor,staff)){money(stats(actor,Map.of()),"revenue","750000");getStats(actor,Map.of("storeId",b.id.toString())).andExpect(status().isForbidden());}}
    @Test void shipperSeesAssignedOrdersAndOwnOrdersAtHomeBranchNotWholeBranches()throws Exception{
        tx().executeWithoutResult(tx->{em.find(MealOrder.class,branchB.id).shipperId=shipper.id;em.find(MealOrder.class,first.id).userId=shipper.id;});
        var data=stats(shipper,Map.of());money(data,"revenue","400000");assertThat(data.get("totalOrders").asLong()).isEqualTo(2);money(stats(shipper,Map.of("storeId",b.id.toString())),"revenue","300000");getStats(shipper,Map.of("storeId",c.id.toString())).andExpect(status().isForbidden());
    }
    @Test void combinedAdminRoleKeepsGlobalPrivilege()throws Exception{tx().executeWithoutResult(tx->em.find(Account.class,admin.id).roles="ADMIN,STAFF");money(stats(admin,Map.of("storeId",b.id.toString())),"revenue","300000");}
    @Test void anonymousAndCustomerCannotReadStatistics()throws Exception{mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());getStats(buyer,Map.of()).andExpect(status().isForbidden());}
    @ParameterizedTest @ValueSource(strings={"CANCELLED:PAID","CANCELLED:REFUNDED","COMPLETED:REFUNDED","COMPLETED:UNPAID","COMPLETED:FAILED","COMPLETED:PENDING","NEW:PAID","CONFIRMED:PAID","PREPARING:PAID","COMPLETED:REVIEW_REQUIRED"})
    void ineligibleOrdersNeverContributeToFinancialOrProductAggregates(String state)throws Exception{
        var split=state.split(":");tx().executeWithoutResult(tx->order(a,buyer,"2026-11-01T12:00",split[0],split[1],"999999"));var data=stats(admin,Map.of("startDate","2026-11-01","endDate","2026-11-01"));money(data,"revenue","0");money(data,"commission","0");assertThat(data.get("topProducts").size()).isZero();assertThat(data.get("totalOrders").asLong()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"COD","MOMO","VNPAY"})
    void completedPaidOrderQualifiesRegardlessOfPaymentMethod(String method)throws Exception{tx().executeWithoutResult(tx->order(a,buyer,"2026-11-01T12:00","COMPLETED","PAID","12345").paymentMethod=method);money(stats(admin,Map.of("startDate","2026-11-01","endDate","2026-11-01")),"revenue","12345");}
    @Test void multipleLinesAttemptsAndRefundRequestsCannotDuplicateRevenue()throws Exception{var data=stats(admin,day());money(data,"revenue","300000");money(data.get("daily"),"2026-10-01","300000");assertThat(data.get("topProducts").get("Burrito").asLong()).isEqualTo(4);}
    @Test void commissionIsRoundedPerOrderAndDecimalTotalsStayExact()throws Exception{
        tx().executeWithoutResult(tx->{for(int i=0;i<2;i++){var order=order(a,buyer,"2026-11-02T10:00","COMPLETED","PAID","100.25");order.subtotal=new BigDecimal("15");order.discount=new BigDecimal("10");}});
        var data=stats(admin,Map.of("startDate","2026-11-02","endDate","2026-11-02"));money(data,"revenue","200.50");money(data,"commission","2");money(data,"net","198.50");
    }
    @Test void nullsNegativeTotalsAndMissingCreationTimeDoNotCreateRevenueOrServerErrors()throws Exception{
        tx().executeWithoutResult(tx->{order(a,buyer,"2026-11-02T10:00","COMPLETED","PAID","1").total=null;order(a,buyer,"2026-11-02T11:00","COMPLETED","PAID","-100");var unknown=order(a,buyer,"2026-11-02T12:00","COMPLETED","PAID","1000");unknown.createdAt=null;var valid=order(a,buyer,"2026-11-02T13:00","COMPLETED","PAID","100.25");valid.subtotal=null;valid.discount=null;valid.commissionRate=null;});
        var data=stats(admin,Map.of("startDate","2026-11-02","endDate","2026-11-02"));money(data,"revenue","100.25");money(data,"commission","0");money(stats(admin,Map.of()),"revenue","1050100.25");
    }
    @Test void confirmedLegacyRefundExcludesStalePaidOrderButPendingRefundDoesNot()throws Exception{
        tx().executeWithoutResult(tx->{var refunded=order(a,buyer,"2026-11-02T10:00","COMPLETED","PAID","1000");refund(refunded);var pending=order(a,buyer,"2026-11-02T11:00","COMPLETED","PAID","2000");var request=new RefundRequest();request.orderId=pending.id;request.status="APPROVED";em.persist(request);});money(stats(admin,Map.of("startDate","2026-11-02","endDate","2026-11-02")),"revenue","2000");
    }
}
