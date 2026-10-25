package vn.uteb;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.http.server.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;
import java.io.IOException;
@Configuration @EnableWebSocketMessageBroker
public class RealtimeConfig implements WebSocketMessageBrokerConfigurer {
 private final SessionSecurityService sessions;
 public RealtimeConfig(SessionSecurityService sessions){this.sessions=sessions;}
 public void registerStompEndpoints(StompEndpointRegistry r){r.addEndpoint("/ws").addInterceptors(new HandshakeInterceptor(){
  public boolean beforeHandshake(ServerHttpRequest req,ServerHttpResponse res,WebSocketHandler handler,Map<String,Object> attributes){
   if(!(req instanceof ServletServerHttpRequest servlet))return false;
   var snapshot=sessions.snapshot(servlet.getServletRequest().getSession(false));
   if(!sessions.valid(snapshot))return false;
   attributes.putAll(snapshot);return true;
  }
  public void afterHandshake(ServerHttpRequest req,ServerHttpResponse res,WebSocketHandler handler,Exception exception){}
 });}
 public void configureWebSocketTransport(WebSocketTransportRegistration r){r.addDecoratorFactory(handler->new WebSocketHandlerDecorator(handler){
  public void afterConnectionEstablished(WebSocketSession session)throws Exception{
   // Register before rechecking to cover a password change racing with the handshake.
   sessions.register(session);
   if(!sessions.valid(session.getAttributes())){sessions.close(session);return;}
   try{super.afterConnectionEstablished(new WebSocketSessionDecorator(session){
    public void sendMessage(WebSocketMessage<?> message)throws IOException{
     // Check outbound delivery too: an idle subscriber need not send another frame.
     if(!sessions.valid(getAttributes())){sessions.close(this);return;}
     super.sendMessage(message);
    }
   });}catch(Exception e){sessions.close(session);throw e;}
  }
  public void afterConnectionClosed(WebSocketSession session,CloseStatus status)throws Exception{sessions.unregister(session);super.afterConnectionClosed(session,status);}
 });}
 public void configureMessageBroker(MessageBrokerRegistry r){r.enableSimpleBroker("/queue");r.setApplicationDestinationPrefixes("/app");r.setUserDestinationPrefix("/user");}
 public void configureClientInboundChannel(ChannelRegistration r){r.interceptors(new ChannelInterceptor(){public Message<?> preSend(Message<?> m,MessageChannel c){StompHeaderAccessor a=MessageHeaderAccessor.getAccessor(m,StompHeaderAccessor.class);if(a!=null&&a.getCommand()!=StompCommand.DISCONNECT){if(a.getUser()==null||!sessions.valid(a.getSessionAttributes()))throw new IllegalArgumentException("Authentication required");if(a.getCommand()==StompCommand.SUBSCRIBE&&!"/user/queue/events".equals(a.getDestination()))throw new IllegalArgumentException("Forbidden subscription");if(a.getCommand()==StompCommand.SEND)throw new IllegalArgumentException("Client messages disabled");}return m;}});}
}
