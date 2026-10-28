package vn.uteb;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.uteb.Domain.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:paymentavailability;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop","uteb.mail.enabled=false","uteb.vnpay.code=","uteb.vnpay.secret="})
@ActiveProfiles("demo") @AutoConfigureMockMvc @Transactional @WithMockUser(username="customer@uteb.vn")
class PaymentAvailabilityTest {
    @Autowired ShopService shop;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
    @PersistenceContext EntityManager em;@MockitoBean JavaMailSender mail;
    MockHttpSession session(){Account actor=shop.byEmail("customer@uteb.vn");var session=new MockHttpSession();
        session.setAttribute(SessionSecurityService.ACCOUNT_ID,actor.id);session.setAttribute(SessionSecurityService.TOKEN_VERSION,actor.tokenVersion);return session;}
    Product item(){return shop.all(Product.class).stream().filter(p->!p.customizable&&p.storeId.equals(1L)).findFirst().orElseThrow();}
    ResultActions checkout(String prefix,String method)throws Exception{
        var body=Map.of("checkoutKey",UUID.randomUUID().toString(),"paymentMethod",method,"type","PICKUP",
            "contactName","Payment test","phone","0901234567","scheduledAt","ASAP");
        return mvc.perform(post(prefix+"/checkout").session(session()).with(user("customer@uteb.vn")).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(body)));
    }
    void unchanged(int stock,long orders){em.flush();em.clear();assertThat(item().stock).isEqualTo(stock);
        assertThat(shop.all(MealOrder.class)).hasSize((int)orders);assertThat(shop.where(CartLine.class,"userId",shop.byEmail("customer@uteb.vn").id)).hasSize(1);}
    @Test void bootstrapReportsMissingVnpayWithoutExposingCredentials()throws Exception{
        mvc.perform(get("/shop-api/public/bootstrap").with(anonymous())).andExpect(status().isOk())
            .andExpect(jsonPath("$.vnpayConfigured").value(false)).andExpect(jsonPath("$.VNPAY_HASH_SECRET").doesNotExist());
    }
    @ParameterizedTest @ValueSource(strings={"/api","/shop-api"})
    void disabledVnpayCannotCreateOrdersOrReserveStock(String prefix)throws Exception{
        Product product=item();int stock=product.stock;long orders=shop.all(MealOrder.class).size();
        shop.cart(Map.of("productId",product.id,"quantity",1));checkout(prefix,"VNPAY").andExpect(status().isServiceUnavailable());unchanged(stock,orders);
    }
    @ParameterizedTest @ValueSource(strings={"/api","/shop-api"})
    void removedMomoCannotCreateOrdersOrReserveStock(String prefix)throws Exception{
        Product product=item();int stock=product.stock;long orders=shop.all(MealOrder.class).size();
        shop.cart(Map.of("productId",product.id,"quantity",1));checkout(prefix,"MOMO").andExpect(status().isBadRequest());unchanged(stock,orders);
    }
    @ParameterizedTest @ValueSource(strings={"/api","/shop-api"})
    void codStillWorksWithoutVnpayCredentials(String prefix)throws Exception{
        Product product=item();int stock=product.stock;shop.cart(Map.of("productId",product.id,"quantity",1));
        checkout(prefix,"COD").andExpect(status().isOk()).andExpect(jsonPath("$[0].paymentMethod").value("COD"))
            .andExpect(jsonPath("$[0].paymentStatus").value("UNPAID"));
        em.flush();em.clear();assertThat(item().stock).isEqualTo(stock-1);
    }
    @Test void removedMomoWebhookHasNoCsrfExemption()throws Exception{
        mvc.perform(post("/payments/momo/ipn").with(anonymous()).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
}
