package vn.uteb;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketSession;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.uteb.Domain.Account;

/** No test-level transaction: HTTP service calls really commit, including afterCommit. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:sessionsecurity;MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop", "uteb.seed=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc
class SessionSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ShopService shop;
    @Autowired SessionSecurityService sessions;
    @Autowired PlatformTransactionManager transactions;
    @PersistenceContext EntityManager em;
    Account a,b;
    static final String OLD="Session-test-old-123", NEXT="Session-test-new-123";

    @BeforeEach void createAccounts(){
        new TransactionTemplate(transactions).executeWithoutResult(tx->{ a=account();b=account(); });
    }
    Account account(){Account account=new Account();account.email=UUID.randomUUID()+"@example.test";account.name="Session test";account.verified=true;account.passwordHash=shop.hashPassword(OLD);em.persist(account);return account;}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();new TransactionTemplate(transactions).executeWithoutResult(tx->{for(Account account:List.of(a,b)){Account stored=em.find(Account.class,account.id);if(stored!=null)em.remove(stored);}});}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder browserCsrf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,MockHttpSession session)throws Exception{
        SecurityContextHolder.clearContext();var bootstrap=get("/shop-api/public/bootstrap");if(session!=null)bootstrap.session(session);
        var response=mvc.perform(bootstrap).andExpect(status().isOk()).andReturn();
        String token=json.readTree(response.getResponse().getContentAsString()).get("csrf").asText();
        var cookie=response.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        request.cookie(cookie).header("X-XSRF-TOKEN",token);if(session!=null&&!session.isInvalid())request.session(session);return request;
    }
    MvcResult login(Account account,String password)throws Exception{
        SecurityContextHolder.clearContext();
        return mvc.perform(browserCsrf(post("/shop-api/auth/login"),null).contentType("application/json").content(json.writeValueAsString(Map.of("email",account.email,"password",password))))
                .andExpect(status().isOk()).andReturn();
    }
    MockHttpSession session(MvcResult result){return (MockHttpSession)result.getRequest().getSession(false);}
    String token(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString()).get("token").asText();}
    ResultActions getAccount(MockHttpSession session)throws Exception{SecurityContextHolder.clearContext();return mvc.perform(get("/shop-api/account").session(session));}
    ResultActions jwtAccount(String token)throws Exception{SecurityContextHolder.clearContext();return mvc.perform(get("/api/account").header("Authorization","Bearer "+token));}
    ResultActions change(MockHttpSession session,String current,String next)throws Exception{
        SecurityContextHolder.clearContext();return mvc.perform(browserCsrf(put("/shop-api/account/password"),session).contentType("application/json").content(json.writeValueAsString(Map.of("currentPassword",current,"password",next))));
    }
    int version(Account account){return new TransactionTemplate(transactions).execute(tx->em.find(Account.class,account.id).tokenVersion);}
    void prepareOtp(){new TransactionTemplate(transactions).executeWithoutResult(tx->{Account account=em.find(Account.class,a.id);account.otpPurpose="RESET";account.otpHash=shop.hashPassword("123456");account.otpExpires=java.time.LocalDateTime.now().plusMinutes(5);account.otpAttempts=0;});}
    ResultActions reset(MockHttpSession session,String code)throws Exception{
        SecurityContextHolder.clearContext();var request=browserCsrf(post("/shop-api/auth/otp/verify"),session).contentType("application/json").content(json.writeValueAsString(Map.of("email",a.email,"purpose","RESET","otp",code,"password",NEXT)));
        if(session!=null)request.session(session);return mvc.perform(request);
    }

    @Test void passwordChangeRevokesAllDevicesAndJwtButPreservesOtherAccount()throws Exception{
        var first=login(a,OLD);var second=login(a,OLD);var other=login(b,OLD);
        assertThat(session(first).getAttribute(SessionSecurityService.ACCOUNT_ID)).isEqualTo(a.id);
        change(session(first),OLD,NEXT).andExpect(status().isOk());assertThat(version(a)).isEqualTo(1);
        getAccount(session(first)).andExpect(status().isUnauthorized());getAccount(session(second)).andExpect(status().isUnauthorized());
        jwtAccount(token(first)).andExpect(status().isUnauthorized());jwtAccount(token(second)).andExpect(status().isUnauthorized());
        getAccount(session(other)).andExpect(status().isOk());jwtAccount(token(other)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email",a.email,"password",OLD)))).andExpect(status().isBadRequest());
        var fresh=login(a,NEXT);getAccount(session(fresh)).andExpect(status().isOk());jwtAccount(token(fresh)).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void resetRevokesOnlyTargetAndConsumesOtp(boolean loggedInAsOtherAccount)throws Exception{
        var first=login(a,OLD);var second=login(a,OLD);var other=login(b,OLD);prepareOtp();
        reset(loggedInAsOtherAccount?session(other):null,"123456").andExpect(status().isOk());assertThat(version(a)).isEqualTo(1);assertThat(version(b)).isZero();
        getAccount(session(first)).andExpect(status().isUnauthorized());getAccount(session(second)).andExpect(status().isUnauthorized());jwtAccount(token(first)).andExpect(status().isUnauthorized());
        getAccount(session(other)).andExpect(status().isOk());jwtAccount(token(other)).andExpect(status().isOk());
        var fresh=login(a,NEXT);getAccount(session(fresh)).andExpect(status().isOk());jwtAccount(token(fresh)).andExpect(status().isOk());
        reset(null,"123456").andExpect(status().isBadRequest());assertThat(version(a)).isEqualTo(1);
        getAccount(session(fresh)).andExpect(status().isOk());jwtAccount(token(fresh)).andExpect(status().isOk());
    }
    @Test void jwtPasswordChangeAlsoRevokesBrowserSession()throws Exception{
        var first=login(a,OLD);SecurityContextHolder.clearContext();
        mvc.perform(put("/api/account/password").header("Authorization","Bearer "+token(first)).contentType("application/json").content(json.writeValueAsString(Map.of("currentPassword",OLD,"password",NEXT)))).andExpect(status().isOk());
        assertThat(version(a)).isEqualTo(1);getAccount(session(first)).andExpect(status().isUnauthorized());jwtAccount(token(first)).andExpect(status().isUnauthorized());
    }
    @Test void apiResetAlsoRevokesBrowserSession()throws Exception{
        var first=login(a,OLD);prepareOtp();SecurityContextHolder.clearContext();
        mvc.perform(post("/api/auth/otp/verify").contentType("application/json").content(json.writeValueAsString(Map.of("email",a.email,"otp","123456","password",NEXT)))).andExpect(status().isOk());
        assertThat(version(a)).isEqualTo(1);getAccount(session(first)).andExpect(status().isUnauthorized());jwtAccount(token(first)).andExpect(status().isUnauthorized());
    }
    @Test void invalidPasswordAndOtpKeepSessionsValid()throws Exception{
        var first=login(a,OLD);prepareOtp();
        change(session(first),"wrong-password",NEXT).andExpect(status().isBadRequest());
        change(session(first),OLD,"short").andExpect(status().isBadRequest());
        reset(null,"000000").andExpect(status().isBadRequest());
        new TransactionTemplate(transactions).executeWithoutResult(tx->em.find(Account.class,a.id).otpExpires=java.time.LocalDateTime.now().minusSeconds(1));
        reset(null,"123456").andExpect(status().isBadRequest());
        assertThat(version(a)).isZero();getAccount(session(first)).andExpect(status().isOk());jwtAccount(token(first)).andExpect(status().isOk());
    }
    @Test void legacySessionAndInvalidAccountAreRejectedAndBootstrapStaysPublic()throws Exception{
        var first=login(a,OLD);session(first).removeAttribute(SessionSecurityService.TOKEN_VERSION);
        getAccount(session(first)).andExpect(status().isUnauthorized());
        var second=login(a,OLD);new TransactionTemplate(transactions).executeWithoutResult(tx->em.find(Account.class,a.id).active=false);
        SecurityContextHolder.clearContext();mvc.perform(get("/shop-api/public/bootstrap").session(session(second))).andExpect(status().isOk()).andExpect(jsonPath("$.user").doesNotExist()).andExpect(jsonPath("$.csrf").isNotEmpty());
        var other=login(b,OLD);new TransactionTemplate(transactions).executeWithoutResult(tx->em.find(Account.class,b.id).verified=false);
        getAccount(session(other)).andExpect(status().isUnauthorized());
    }
    @Test void csrfRemainsRequiredAndCanBeRenewedAfterRevocation()throws Exception{
        var first=login(a,OLD);String body=json.writeValueAsString(Map.of("currentPassword",OLD,"password",NEXT));
        SecurityContextHolder.clearContext();mvc.perform(put("/shop-api/account/password").session(session(first)).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/shop-api/account/password").session(session(first)).cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN","valid-cookie-token")).header("X-XSRF-TOKEN","wrong-header-token").contentType("application/json").content(body)).andExpect(status().isForbidden());
        assertThat(version(a)).isZero();change(session(first),OLD,NEXT).andExpect(status().isOk());
        SecurityContextHolder.clearContext();var bootstrap=mvc.perform(get("/shop-api/public/bootstrap")).andExpect(status().isOk()).andReturn();
        String csrf=json.readTree(bootstrap.getResponse().getContentAsString()).get("csrf").asText();
        var cookie=bootstrap.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        mvc.perform(post("/shop-api/auth/login").cookie(cookie).header("X-XSRF-TOKEN",csrf).contentType("application/json").content(json.writeValueAsString(Map.of("email",a.email,"password",NEXT)))).andExpect(status().isOk());
    }
    @Test void removedAccountAndAlreadyInvalidatedSessionFailClosed()throws Exception{
        var first=login(a,OLD);new TransactionTemplate(transactions).executeWithoutResult(tx->em.remove(em.find(Account.class,a.id)));
        getAccount(session(first)).andExpect(status().isUnauthorized());jwtAccount(token(first)).andExpect(status().isUnauthorized());
        var invalid=new MockHttpSession();invalid.invalidate();assertThat(sessions.valid(invalid)).isFalse();sessions.invalidate(invalid);
    }
    void assertNoClose(WebSocketSession socket){assertThat(mockingDetails(socket).getInvocations()).noneMatch(invocation->invocation.getMethod().getName().equals("close"));}
    @Test void socketRevocationRunsOnlyAfterCommitAndOnlyForOlderTargetVersion()throws Exception{
        WebSocketSession old=mock(WebSocketSession.class),other=mock(WebSocketSession.class),fresh=mock(WebSocketSession.class);
        when(old.getId()).thenReturn("old");when(old.getAttributes()).thenReturn(Map.of(SessionSecurityService.ACCOUNT_ID,a.id,SessionSecurityService.TOKEN_VERSION,0));
        when(other.getId()).thenReturn("other");when(other.getAttributes()).thenReturn(Map.of(SessionSecurityService.ACCOUNT_ID,b.id,SessionSecurityService.TOKEN_VERSION,0));
        when(fresh.getId()).thenReturn("fresh");when(fresh.getAttributes()).thenReturn(Map.of(SessionSecurityService.ACCOUNT_ID,a.id,SessionSecurityService.TOKEN_VERSION,1));
        sessions.register(old);sessions.register(other);sessions.register(fresh);
        try{
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(a.email,"","ROLE_CUSTOMER"));
            new TransactionTemplate(transactions).executeWithoutResult(tx->{shop.changePassword(Map.of("currentPassword",OLD,"password",NEXT));assertNoClose(old);tx.setRollbackOnly();});
            assertThat(version(a)).isZero();verify(old,never()).close(any());
            new TransactionTemplate(transactions).executeWithoutResult(tx->{shop.changePassword(Map.of("currentPassword",OLD,"password",NEXT));assertNoClose(old);});
            assertThat(version(a)).isEqualTo(1);verify(old).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);verify(other,never()).close(any());verify(fresh,never()).close(any());
        }finally{sessions.unregister(old);sessions.unregister(other);sessions.unregister(fresh);}
    }
}
