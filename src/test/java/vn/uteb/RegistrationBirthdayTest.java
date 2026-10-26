package vn.uteb;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:registrationbirthtest;MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop", "uteb.mail.enabled=true"})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("demo")
@Transactional
class RegistrationBirthdayTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ShopService service;
    @Autowired EntityManager em;
    @MockitoBean JavaMailSender mail;

    Map<String, Object> registration() {
        return new HashMap<>(Map.of("email", "birthday-test@example.com", "name", "Nguyễn Văn An", "phone", "0901234567", "password", "Register@123"));
    }

    void register(Map<String, Object> body) throws Exception {
        mvc.perform(post("/shop-api/auth/register").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(body))).andExpect(status().isOk());
    }

    @Test void birthdayPersistsWithRegistration() throws Exception {
        var body = registration();
        body.put("birthDate", "2004-02-29");
        register(body);
        em.flush();
        em.clear();
        var account = service.byEmail("birthday-test@example.com");
        assertThat(account.birthDate).isEqualTo(LocalDate.of(2004, 2, 29));
        assertThat(account.name).isEqualTo("Nguyễn Văn An");
        assertThat(account.verified).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings={"2003-02-29", "02/29/2004", "not-a-date", "1899-12-31", "2999-01-01"})
    void invalidBirthdayDoesNotCreateAccountOrSendOtp(String birthDate) throws Exception {
        var body = registration();
        body.put("birthDate", birthDate);
        mvc.perform(post("/shop-api/auth/register").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        assertThat(service.byEmail("birthday-test@example.com")).isNull();
        verifyNoInteractions(mail);
    }

    @Test void originalRegistrationContractStillWorksWithoutBirthday() throws Exception {
        register(registration());
        em.flush();
        em.clear();
        assertThat(service.byEmail("birthday-test@example.com").birthDate).isNull();
    }

    @Test @WithMockUser(username="customer@uteb.vn")
    void profileUpdateCannotOverwriteBirthday() throws Exception {
        var account = service.byEmail("customer@uteb.vn");
        account.birthDate = LocalDate.of(2000, 1, 2);
        em.flush();
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(SessionSecurityService.ACCOUNT_ID, account.id);
        session.setAttribute(SessionSecurityService.TOKEN_VERSION, account.tokenVersion);
        mvc.perform(put("/shop-api/account/profile").session(session).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("name", "Customer Updated", "phone", "0901234567", "birthDate", "2001-02-03"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.birthDate").value("2000-01-02"));
        em.flush();
        em.clear();
        assertThat(service.byEmail("customer@uteb.vn").birthDate).isEqualTo(LocalDate.of(2000, 1, 2));
    }

    @Test void registrationAssetsArePublic() throws Exception {
        mvc.perform(get("/assets/registration.js")).andExpect(status().isOk());
        mvc.perform(get("/assets/registration.css")).andExpect(status().isOk());
    }
}
