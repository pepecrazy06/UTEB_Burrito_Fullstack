package vn.uteb;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import static org.mockito.Mockito.*;

/** Exercises the real outbound decorator without requiring an operating-system socket. */
class SessionWebSocketGuardTest {
    @Test void outboundDeliveryChecksDatabaseEvenWithoutLocalRevocationEvent()throws Exception{
        SessionSecurityService sessions=mock(SessionSecurityService.class);
        WebSocketTransportRegistration transport=mock(WebSocketTransportRegistration.class);
        new RealtimeConfig(sessions).configureWebSocketTransport(transport);
        var factory=ArgumentCaptor.forClass(WebSocketHandlerDecoratorFactory.class);
        verify(transport).addDecoratorFactory(factory.capture());
        WebSocketHandler broker=mock(WebSocketHandler.class);
        WebSocketHandler handler=factory.getValue().decorate(broker);
        WebSocketSession raw=mock(WebSocketSession.class);
        Map<String,Object> attributes=Map.of(SessionSecurityService.ACCOUNT_ID,1L,SessionSecurityService.TOKEN_VERSION,0);
        when(raw.getAttributes()).thenReturn(attributes);when(sessions.valid(attributes)).thenReturn(true);
        handler.afterConnectionEstablished(raw);
        var wrapped=ArgumentCaptor.forClass(WebSocketSession.class);verify(broker).afterConnectionEstablished(wrapped.capture());
        var before=new TextMessage("allowed");wrapped.getValue().sendMessage(before);verify(raw).sendMessage(before);
        when(sessions.valid(attributes)).thenReturn(false);
        var after=new TextMessage("private-after-revocation");wrapped.getValue().sendMessage(after);
        verify(raw,never()).sendMessage(after);verify(sessions).close(wrapped.getValue());
        handler.afterConnectionClosed(raw,CloseStatus.NORMAL);verify(sessions).unregister(raw);
    }
    @Test void staleHandshakeSnapshotNeverReachesBroker()throws Exception{
        SessionSecurityService sessions=mock(SessionSecurityService.class);
        WebSocketTransportRegistration transport=mock(WebSocketTransportRegistration.class);
        new RealtimeConfig(sessions).configureWebSocketTransport(transport);
        var factory=ArgumentCaptor.forClass(WebSocketHandlerDecoratorFactory.class);verify(transport).addDecoratorFactory(factory.capture());
        WebSocketHandler broker=mock(WebSocketHandler.class);WebSocketSession raw=mock(WebSocketSession.class);
        when(raw.getAttributes()).thenReturn(Map.of());
        factory.getValue().decorate(broker).afterConnectionEstablished(raw);
        verify(sessions).register(raw);verify(sessions).close(raw);verifyNoInteractions(broker);
    }
}
