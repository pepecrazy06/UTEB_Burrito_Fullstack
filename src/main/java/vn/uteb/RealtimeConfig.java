package vn.uteb;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.web.socket.config.annotation.*;
@Configuration @EnableWebSocketMessageBroker
public class RealtimeConfig implements WebSocketMessageBrokerConfigurer {
 public void registerStompEndpoints(StompEndpointRegistry r){r.addEndpoint("/ws");}
 public void configureMessageBroker(MessageBrokerRegistry r){r.enableSimpleBroker("/queue");r.setApplicationDestinationPrefixes("/app");r.setUserDestinationPrefix("/user");}
 public void configureClientInboundChannel(ChannelRegistration r){r.interceptors(new ChannelInterceptor(){public Message<?> preSend(Message<?> m,MessageChannel c){StompHeaderAccessor a=MessageHeaderAccessor.getAccessor(m,StompHeaderAccessor.class);if(a!=null){if(a.getUser()==null)throw new IllegalArgumentException("Authentication required");if(a.getCommand()==StompCommand.SUBSCRIBE&&!"/user/queue/events".equals(a.getDestination()))throw new IllegalArgumentException("Forbidden subscription");if(a.getCommand()==StompCommand.SEND)throw new IllegalArgumentException("Client messages disabled");}return m;}});}
}
