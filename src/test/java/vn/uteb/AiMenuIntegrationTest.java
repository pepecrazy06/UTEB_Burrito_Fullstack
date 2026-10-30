package vn.uteb;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static vn.uteb.Domain.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:ai_menu_test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("demo") @AutoConfigureMockMvc @Transactional
class AiMenuIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ShopService shop; @Autowired AiMenuService service;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
    @MockitoBean MenuAiClient model;
    static final String REQUEST="{\"storeId\":1,\"message\":\"Gà dưới 600 kcal\"}";
    @BeforeEach void model() {
        when(model.configured()).thenReturn(true);when(model.ready()).thenReturn(true);
        when(model.understand(anyString(),anyList(),anyList())).thenReturn(new MenuPreferences("FOOD",List.of("CHICKEN"),List.of(),false,false,null,599,100000,List.of(),false,false,"NO",List.of(),false,""));
    }
    @Test void publicGuestCanAskWithCsrfAndEveryResultMatchesCheckoutQuote() throws Exception {
        mvc.perform(post("/shop-api/public/ai/recommend").with(csrf()).with(r->{r.setRemoteAddr("test-guest");return r;}).contentType("application/json").content(REQUEST))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].productId").isNumber());
        var response=service.recommend(1,"Gà",List.of());
        List<?> items=(List<?>)response.get("items");assertThat(items).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        for(Object raw:items){Map<?,?> item=(Map<?,?>)raw;Product p=shop.get(Product.class,((Number)item.get("productId")).longValue());
            String ids=String.join(",",((List<?>)item.get("ingredientIds")).stream().map(Object::toString).toList());
            var quote=shop.quote(p,ids);assertThat(item.get("price")).isEqualTo(quote.price());assertThat(item.get("calories")).isEqualTo(quote.calories());assertThat(quote.calories()).isLessThan(600);
        }
    }
    @Test void noCsrfCannotTriggerLocalModelThroughSessionEndpoint() throws Exception {
        mvc.perform(post("/shop-api/public/ai/recommend").contentType("application/json").content(REQUEST)).andExpect(status().isForbidden());
        verify(model,never()).understand(anyString(),anyList(),anyList());
    }
    @Test void invalidInputDoesNotTriggerModel() throws Exception {
        mvc.perform(post("/api/public/ai/recommend").contentType("application/json").content("{\"storeId\":1,\"message\":\"\"}")).andExpect(status().isBadRequest());
        verify(model,never()).understand(anyString(),anyList(),anyList());
    }
    @Test void unavailableAiReportsStatusWithoutFakeRecommendations() throws Exception {
        when(model.ready()).thenReturn(false);
        mvc.perform(get("/shop-api/public/ai/status")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        when(model.configured()).thenReturn(false);
        mvc.perform(post("/api/public/ai/recommend").contentType("application/json").content(REQUEST)).andExpect(status().isServiceUnavailable());
    }
    @Test void repeatedRequestsAreThrottledBeforeCallingModelAgain() throws Exception {
        var request=post("/api/public/ai/recommend").with(r->{r.setRemoteAddr("test-throttle");return r;}).contentType("application/json").content(REQUEST);
        mvc.perform(request).andExpect(status().isOk());mvc.perform(request).andExpect(status().isTooManyRequests());
        verify(model,times(1)).understand(anyString(),anyList(),anyList());
    }
    @Test void allergyRequestDoesNotClaimIncompleteCatalogIsSafe() {
        when(model.understand(anyString(),anyList(),anyList())).thenReturn(new MenuPreferences("FOOD",List.of(),List.of(),false,false,null,null,null,List.of(),false,false,"ANY",List.of(),true,""));
        var reply=service.recommend(1,"Dị ứng sữa",List.of());assertThat((List<?>)reply.get("items")).isEmpty();assertThat(reply.get("message").toString()).contains("xác nhận trực tiếp");
    }
    @Test void closedBranchIsRejectedBeforeModelCall() {
        shop.get(Store.class,1).active=false;em.flush();
        assertThatThrownBy(()->service.recommend(1,"Gà",List.of())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(model,never()).understand(anyString(),anyList(),anyList());
    }
    MenuPreferences extended(String topic,String goal,int servings,Integer budget){return new MenuPreferences("FOOD",List.of("CHICKEN"),List.of(),false,false,null,null,null,List.of(),false,false,"ANY",List.of(),false,"",topic,goal,List.of(),List.of(),servings,budget);}
    @Test void storeQuestionGetsAnAnswerWithoutUnrelatedCalorieRecommendations(){
        var result=service.recommend(1,"Cửa hàng mở mấy giờ?",List.of());assertThat((List<?>)result.get("items")).isEmpty();assertThat(result.get("message").toString()).contains("10:00").doesNotContain("kcal");
        verify(model,never()).understand(anyString(),anyList(),anyList());verify(model,never()).respond(anyString(),anyList(),anyMap());
    }
    @Test void groupRecommendationsExposeExactTotalsAndDoNotForceCaloriesIntoNormalConversation(){
        when(model.understand(anyString(),anyList(),anyList())).thenReturn(extended("RECOMMEND","FILLING",2,200000));
        var result=service.recommend(1,"Hai người ăn no trong 200k",List.of());assertThat((List<?>)result.get("items")).isNotEmpty();
        for(Object raw:(List<?>)result.get("items")){Map<?,?> item=(Map<?,?>)raw;assertThat(item.get("quantity")).isEqualTo(2);assertThat((java.math.BigDecimal)item.get("totalPrice")).isEqualByComparingTo(((java.math.BigDecimal)item.get("price")).multiply(java.math.BigDecimal.valueOf(2))).isLessThanOrEqualTo(java.math.BigDecimal.valueOf(200000));assertThat(item.get("showCalories")).isEqualTo(false);}
    }
    @Test void malformedHistoryDoesNotReachEitherModelStage()throws Exception{
        mvc.perform(post("/api/public/ai/recommend").contentType("application/json").content("{\"storeId\":1,\"message\":\"Đổi sang bò\",\"conversation\":[{\"user\":\"gà\",\"answer\":1}]}" )).andExpect(status().isBadRequest());verify(model,never()).respond(anyString(),anyList(),anyMap());verify(model,never()).understand(anyString(),anyList(),anyList(),anyList());
    }
    @Test void followupIncludesPreviousAssistantReplyAndDishNames()throws Exception{
        when(model.understand(anyString(),anyList(),anyList(),anyList())).thenReturn(extended("RECOMMEND","BALANCED",1,null));
        mvc.perform(post("/api/public/ai/recommend").with(r->{r.setRemoteAddr("conversation");return r;}).contentType("application/json").content("{\"storeId\":1,\"message\":\"Món thứ hai nhé\",\"conversation\":[{\"user\":\"Thèm gà\",\"answer\":\"Hai lựa chọn cho bạn\",\"dishes\":[\"Burrito Gà nướng\",\"Bowl Gà nướng\"]}]}" )).andExpect(status().isOk());
        verify(model).understand(eq("Món thứ hai nhé"),anyList(),anyList(),argThat(turns->turns.size()==1&&turns.getFirst().dishes().get(1).equals("Bowl Gà nướng")));
    }
    @Test void categoryComparisonUsesKnownFormatsAndNeverInventsNutritionOrUnrelatedDishes(){
        when(model.understand(anyString(),anyList(),anyList())).thenReturn(new MenuPreferences("FOOD",List.of(),List.of(),false,false,null,null,null,List.of("Burrito","Bowl"),false,false,"ANY",List.of(),false,"","COMPARE","BALANCED",List.of(),List.of(),1,null));
        var result=service.recommend(1,"Burrito và Bowl khác nhau thế nào?",List.of());
        assertThat(result.get("message").toString()).contains("tortilla cuộn nhân","trong tô").doesNotContain("ít calo","ít no");
        assertThat((List<?>)result.get("items")).isNotEmpty();for(Object raw:(List<?>)result.get("items"))assertThat(((Map<?,?>)raw).get("category")).isIn("Burrito","Bowl");
        verify(model,never()).respond(anyString(),anyList(),anyMap());
    }
    @Test void proseCannotClaimTheValidatedRecommendationsExceedTheGroupBudget(){
        when(model.understand(anyString(),anyList(),anyList())).thenReturn(extended("RECOMMEND","BALANCED",2,200000));
        when(model.respond(anyString(),anyList(),anyMap())).thenReturn("Tacos phù hợp, còn Salad và Bowl vượt quá ngân sách hoặc có nguyên liệu không mong muốn.");
        var result=service.recommend(1,"Hai người ăn trong 200k, thích gà",List.of());
        assertThat((List<?>)result.get("items")).isNotEmpty();assertThat(result.get("message").toString()).contains("2 phần cùng món").doesNotContain("vượt quá","không mong muốn");
    }
    @Test void branchListAfterBeefAllergyAnswersTheNewQuestionFromSqlWithoutModelGuessing(){
        var history=List.of(new AiConversationTurn("dị ứng bò","Cần xác nhận trực tiếp với cửa hàng",List.of()));
        var result=service.recommend(1,"đưa ra danh sách các chi nhánh",List.of(),history);
        String text=result.get("message").toString();for(Store store:shop.all(Store.class))if(store.active)assertThat(text).contains(store.name,store.address);
        assertThat(text).contains("Đổi cửa hàng").doesNotContain("dị ứng","phần nền");assertThat((List<?>)result.get("items")).isEmpty();
        verify(model,never()).understand(anyString(),anyList(),anyList(),anyList());verify(model,never()).respond(anyString(),anyList(),anyMap());
    }
    @Test void inactiveStoresAreNotDisclosedByTheBranchList(){
        Store hidden=shop.get(Store.class,2);hidden.active=false;em.flush();
        var result=service.recommend(1,"Liệt kê tất cả cửa hàng UTEB",List.of());assertThat(result.get("message").toString()).doesNotContain(hidden.name);
    }
    @Test void specificBranchHoursAndPhoneAreNotReplacedByTheCurrentlySelectedStore(){
        Store target=shop.get(Store.class,2);target.hours="08:00–20:00";target.phone="0908888777";em.flush();
        var result=service.recommend(1,"Chi nhánh Quận 1 ở đâu, số điện thoại và giờ mở cửa?",List.of("Tôi dị ứng bò"));
        assertThat(result.get("message").toString()).contains(target.name,target.address,"08:00–20:00","0908888777").doesNotContain("dị ứng");
    }
    @Test void beefAllergyNamesKnownBeefWithoutClaimingOtherMealsAreSafe(){
        var result=service.recommend(1,"dị ứng bò",List.of());String text=result.get("message").toString();
        assertThat(text).contains("bò","Bò BBQ","xác nhận trực tiếp").doesNotContain("an toàn tuyệt đối");assertThat((List<?>)result.get("items")).isEmpty();
        verify(model,never()).understand(anyString(),anyList(),anyList());
    }
    @Test void ingredientAllergenLabelsAreUsedForMilkAndDoNotMistakeAvocadoForBeef(){
        var milk=service.recommend(1,"Mình dị ứng sữa",List.of());assertThat(milk.get("message").toString()).contains("Phô mai","Kem chua");
        var avocado=service.recommend(1,"dị ứng bơ",List.of());assertThat(avocado.get("message").toString()).contains("Guacamole").doesNotContain("Bò BBQ");
    }
    @Test void paymentShippingAndOrderingQuestionsStillWorkAfterAnAllergyTurn(){
        List<String> previous=List.of("Tôi dị ứng bò");
        assertThat(service.recommend(1,"Có thanh toán MoMo không?",previous).get("message").toString()).contains("COD","VNPay","không còn MoMo");
        assertThat(service.recommend(1,"Phí giao hàng là bao nhiêu?",previous).get("message").toString()).contains("150.000","chi nhánh");
        assertThat(service.recommend(1,"Hướng dẫn đặt món",previous).get("message").toString()).contains("Giỏ hàng","Đơn của tôi");
    }
    @Test void publicPromotionsExcludePrivateExpiredForeignAndInactiveCodes(){
        Voucher valid=new Voucher();valid.code="PUBLICQA";valid.storeId=1L;valid.value=java.math.BigDecimal.TEN;em.persist(valid);
        Voucher foreign=new Voucher();foreign.code="FOREIGNQA";foreign.storeId=2L;em.persist(foreign);
        Voucher expired=new Voucher();expired.code="EXPIREDQA";expired.endsAt=java.time.LocalDateTime.now().minusDays(1);em.persist(expired);
        Voucher personal=new Voucher();personal.code="PERSONALQA";personal.userId=999L;em.persist(personal);
        Voucher disabled=new Voucher();disabled.code="DISABLEDQA";disabled.active=false;em.persist(disabled);em.flush();
        String answer=service.recommend(1,"Có mã giảm giá nào không?",List.of("dị ứng bò")).get("message").toString();
        assertThat(answer).contains("PUBLICQA").doesNotContain("FOREIGNQA","EXPIREDQA","PERSONALQA","DISABLEDQA");
    }
    @Test void generalMealResponsesReceivePublicActiveStoreFactsOnly(){
        service.recommend(1,"Thèm gà",List.of());var facts=org.mockito.ArgumentCaptor.forClass(Map.class);verify(model).respond(anyString(),anyList(),facts.capture());
        assertThat(facts.getValue().keySet()).contains("activeStores","store").doesNotContain("accounts","SMTP_PASSWORD","DB_PASSWORD");
    }
    @Test void laterFoodWishesDoNotBecomeAdditionalReportedAllergies(){
        var history=List.of(new AiConversationTurn("Dị ứng bò","Thông tin các món có bò",List.of()));
        String answer=service.recommend(1,"Tôi muốn gà thêm bơ",List.of(),history).get("message").toString();
        assertThat(answer).contains("dị ứng hoặc không dung nạp bò").doesNotContain("bò, bơ","Guacamole");
    }
    @Test void unknownAllergenIsAcknowledgedWithoutInventingALabelOrRepeatedlyAskingForItsName(){
        String answer=service.recommend(1,"Tôi dị ứng hải sản",List.of()).get("message").toString();
        assertThat(answer).contains("hải sản","chưa thể khẳng định").doesNotContain("Bạn dị ứng hoặc không dung nạp nguyên liệu nào?");
    }
}
