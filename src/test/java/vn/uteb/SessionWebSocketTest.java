package vn.uteb;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import static org.assertj.core.api.Assertions.*;
import static vn.uteb.Domain.Account;

/** Real HTTP cookies, STOMP handshake, subscription and outbound delivery on a random port. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:sessionws;MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop", "uteb.seed=false"})
@ActiveProfiles("demo") @Import(SessionWebSocketTest.EventsConfig.class)
class SessionWebSocketTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired ShopService shop;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SimpMessagingTemplate messaging;
    @Autowired Subscriptions subscriptions;
    @PersistenceContext EntityManager em;
    Account a,b;
    final List<Connection> connections=new ArrayList<>();
    static final String OLD="Websocket-test-old-123", NEXT="Websocket-test-new-123";

    @TestConfiguration static class EventsConfig {@Bean Subscriptions subscriptions(){return new Subscriptions();}}
    static class Subscriptions {
        final BlockingQueue<String> received=new LinkedBlockingQueue<>();
        @EventListener public void subscribed(SessionSubscribeEvent event){received.add(event.getUser().getName());}
    }
    @BeforeEach void setup(){new TransactionTemplate(transactions).executeWithoutResult(tx->{a=account();b=account();});}
    Account account(){Account account=new Account();account.email=UUID.randomUUID()+"@example.test";account.name="Socket test";account.verified=true;account.passwordHash=shop.hashPassword(OLD);em.persist(account);return account;}
    @AfterEach void cleanup(){connections.forEach(c->c.socket.abort());new TransactionTemplate(transactions).executeWithoutResult(tx->{em.remove(em.find(Account.class,a.id));em.remove(em.find(Account.class,b.id));});}
    String base(){return "http://localhost:"+port;}

    class Browser implements AutoCloseable {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).cookieHandler(cookies).build();
        String csrf;
        void bootstrap()throws Exception{var response=request("GET","/public/bootstrap",null);assertThat(response.statusCode()).isEqualTo(200);csrf=json.readTree(response.body()).get("csrf").asText();}
        HttpResponse<String> request(String method,String path,Map<String,Object> body)throws Exception{
            var request=HttpRequest.newBuilder(URI.create(base()+"/shop-api"+path)).timeout(Duration.ofSeconds(10));
            if(csrf!=null)request.header("X-XSRF-TOKEN",csrf);
            if(body==null)request.GET();else request.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        }
        void login(Account account,String password)throws Exception{bootstrap();assertThat(request("POST","/auth/login",Map.of("email",account.email,"password",password)).statusCode()).isEqualTo(200);bootstrap();}
        Connection connect(Account account)throws Exception{
            var listener=new Frames();
            WebSocket socket=client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(URI.create("ws://localhost:"+port+"/ws"),listener).get(10,TimeUnit.SECONDS);
            Connection connection=new Connection(socket,listener);connections.add(connection);
            socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\n\n\u0000",true).join();
            assertThat(listener.frames.poll(10,TimeUnit.SECONDS)).startsWith("CONNECTED");
            socket.sendText("SUBSCRIBE\nid:events\ndestination:/user/queue/events\n\n\u0000",true).join();
            assertThat(subscriptions.received.poll(10,TimeUnit.SECONDS)).isEqualTo(account.email);
            return connection;
        }
        public void close(){client.shutdownNow();}
    }
    record Connection(WebSocket socket,Frames listener){}
    static class Frames implements WebSocket.Listener {
        final BlockingQueue<String> frames=new LinkedBlockingQueue<>();
        final CountDownLatch closed=new CountDownLatch(1);
        final StringBuilder buffer=new StringBuilder();
        public void onOpen(WebSocket socket){socket.request(1);}
        public CompletionStage<?> onText(WebSocket socket,CharSequence text,boolean last){buffer.append(text);if(last){frames.add(buffer.toString());buffer.setLength(0);}socket.request(1);return null;}
        public CompletionStage<?> onClose(WebSocket socket,int status,String reason){closed.countDown();return null;}
        public void onError(WebSocket socket,Throwable error){closed.countDown();}
    }
    void delivers(Account account,Connection connection,String marker)throws Exception{
        messaging.convertAndSendToUser(account.email,"/queue/events",Map.of("text",marker));
        assertThat(connection.listener.frames.poll(10,TimeUnit.SECONDS)).contains(marker);
    }

    @Test void changeClosesOldSocketsPreservesOtherUserAndAllowsFreshLogin()throws Exception{
        try(var first=new Browser();var second=new Browser();var other=new Browser()){
            first.login(a,OLD);second.login(a,OLD);other.login(b,OLD);
            var socketA=first.connect(a);var socketA2=second.connect(a);var socketB=other.connect(b);
            delivers(a,socketA,"before-change");assertThat(socketA2.listener.frames.poll(10,TimeUnit.SECONDS)).contains("before-change");
            assertThat(first.request("PUT","/account/password",Map.of("currentPassword",OLD,"password",NEXT)).statusCode()).isEqualTo(200);
            assertThat(socketA.listener.closed.await(10,TimeUnit.SECONDS)).isTrue();assertThat(socketA2.listener.closed.await(10,TimeUnit.SECONDS)).isTrue();
            messaging.convertAndSendToUser(a.email,"/queue/events",Map.of("text","private-after-change"));
            assertThat(socketA.listener.frames.poll(300,TimeUnit.MILLISECONDS)).isNull();assertThat(socketA2.listener.frames.poll(300,TimeUnit.MILLISECONDS)).isNull();
            delivers(b,socketB,"other-still-connected");assertThat(socketB.listener.closed.getCount()).isEqualTo(1);
            assertThat(first.request("GET","/account",null).statusCode()).isEqualTo(401);
            assertThatThrownBy(()->second.client.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:"+port+"/ws"),new Frames()).get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            first.login(a,NEXT);var fresh=first.connect(a);delivers(a,fresh,"after-login");
        }
    }
    @Test void resetByOtherUserClosesOnlyTargetsSocket()throws Exception{
        try(var target=new Browser();var other=new Browser()){
            target.login(a,OLD);other.login(b,OLD);var socketA=target.connect(a);var socketB=other.connect(b);
            new TransactionTemplate(transactions).executeWithoutResult(tx->{Account account=em.find(Account.class,a.id);account.otpHash=shop.hashPassword("123456");account.otpPurpose="RESET";account.otpExpires=java.time.LocalDateTime.now().plusMinutes(5);});
            assertThat(other.request("POST","/auth/otp/verify",Map.of("email",a.email,"purpose","RESET","otp","123456","password",NEXT)).statusCode()).isEqualTo(200);
            assertThat(socketA.listener.closed.await(10,TimeUnit.SECONDS)).isTrue();delivers(b,socketB,"reset-other-unaffected");
            assertThat(other.request("GET","/account",null).statusCode()).isEqualTo(200);
            target.login(a,NEXT);delivers(a,target.connect(a),"reset-new-login");
        }
    }
}
