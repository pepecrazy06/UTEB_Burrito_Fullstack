package vn.uteb;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LoginThrottleTest {
 @Test void unsavedAccountCannotTriggerSocketRevocation()throws Exception{SessionSecurityService sessions=new SessionSecurityService();var socket=org.mockito.Mockito.mock(org.springframework.web.socket.WebSocketSession.class);org.mockito.Mockito.when(socket.getId()).thenReturn("existing");sessions.register(socket);assertThatCode(()->sessions.revokeAfterCommit(new Domain.Account())).doesNotThrowAnyException();org.mockito.Mockito.verify(socket,org.mockito.Mockito.never()).getAttributes();org.mockito.Mockito.verify(socket,org.mockito.Mockito.never()).close(org.mockito.ArgumentMatchers.any());}
 static class Time extends Clock {Instant now=Instant.parse("2026-10-30T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}}
 @Test void accountBudgetNormalizesEmailAndCannotBeBypassedWithAnotherIp(){LoginThrottle t=new LoginThrottle(new Time(),2,5,15);t.reserve("USER@test.local","a");t.reserve("user@test.local","b");assertThatThrownBy(()->t.reserve(" User@test.local ","c")).hasMessageContaining("429");}
 @Test void ipBudgetCannotBeBypassedByChangingEmail(){LoginThrottle t=new LoginThrottle(new Time(),2,3,15);for(int i=0;i<3;i++)t.reserve("user"+i,"a");assertThatThrownBy(()->t.reserve("another","a")).hasMessageContaining("429");assertThatCode(()->t.reserve("another","b")).doesNotThrowAnyException();}
 @Test void expiryAndSuccessfulLoginRestoreTheCorrectBudgets(){Time time=new Time();LoginThrottle t=new LoginThrottle(time,2,3,15);t.reserve("a","ip");t.success("a","ip");t.reserve("a","ip");t.reserve("a","ip");assertThatThrownBy(()->t.reserve("a","ip")).hasMessageContaining("429");time.now=time.now.plusSeconds(900);assertThatCode(()->t.reserve("a","ip")).doesNotThrowAnyException();}
 @Test void concurrentAttemptsCannotOverrunTheBudget()throws Exception{LoginThrottle t=new LoginThrottle(new Time(),5,30,15);AtomicInteger allowed=new AtomicInteger();try(var pool=Executors.newFixedThreadPool(8)){var jobs=java.util.stream.IntStream.range(0,40).mapToObj(i->pool.submit(()->{try{t.reserve("same","ip");allowed.incrementAndGet();}catch(org.springframework.web.server.ResponseStatusException ignored){}})).toList();for(var job:jobs)job.get();}assertThat(allowed).hasValue(5);}
}
