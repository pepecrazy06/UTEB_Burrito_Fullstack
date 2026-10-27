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
}
