package vn.uteb;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import static vn.uteb.Domain.*;

@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(10);}
 @Bean @Order(1) SecurityFilterChain api(HttpSecurity h,JwtService jwt) throws Exception {
  return h.securityMatcher("/api/**").csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers("/api/public/**","/api/auth/**").permitAll().anyRequest().authenticated())
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->jsonError(res,401,"Vui lòng đăng nhập.")).accessDeniedHandler((req,res,x)->jsonError(res,403,"Bạn không có quyền thực hiện thao tác này.")))
   .addFilterBefore(new OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
    String header=req.getHeader("Authorization"); if(header!=null&&header.startsWith("Bearer "))try{SecurityContextHolder.getContext().setAuthentication(jwt.verify(header.substring(7)));}catch(Exception ignored){}
    chain.doFilter(req,res);
   }},UsernamePasswordAuthenticationFilter.class).build();
 }
 @Bean @Order(2) SecurityFilterChain site(HttpSecurity h,SessionSecurityService sessions)throws Exception {
  CookieCsrfTokenRepository repo=CookieCsrfTokenRepository.withHttpOnlyFalse(); repo.setCookieCustomizer(c->c.sameSite("Lax").path("/"));
  CsrfTokenRequestAttributeHandler handler=new CsrfTokenRequestAttributeHandler();
  return h.csrf(c->c.csrfTokenRepository(repo).csrfTokenRequestHandler(handler))
   .securityContext(s->s.securityContextRepository(new HttpSessionSecurityContextRepository()))
   .addFilterAfter(new OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
    HttpSession session=req.getSession(false);
    var auth=SecurityContextHolder.getContext().getAuthentication();
    if(session!=null&&auth!=null&&auth.isAuthenticated()&&!sessions.valid(session)){
     sessions.invalidate(session);SecurityContextHolder.clearContext();
     String path=req.getServletPath();if(path.isEmpty())path=req.getRequestURI();
     if((path.startsWith("/shop-api/")&&!path.startsWith("/shop-api/public/")&&!path.startsWith("/shop-api/auth/"))||path.equals("/ws")){
      jsonError(res,401,"Phiên đăng nhập đã hết hiệu lực. Vui lòng đăng nhập lại.");return;
     }
    }
    chain.doFilter(req,res);
   }},org.springframework.security.web.context.SecurityContextHolderFilter.class)
   .authorizeHttpRequests(a->a.requestMatchers("/shop-api/public/**","/shop-api/auth/**","/","/assets/**","/app.js","/style.css","/favicon.svg","/error","/payments/**").permitAll().anyRequest().authenticated())
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->jsonError(res,401,"Vui lòng đăng nhập.")).accessDeniedHandler((req,res,x)->jsonError(res,403,"Phiên làm việc hoặc quyền truy cập không hợp lệ. Tải lại trang.")))
   .headers(x->x.contentTypeOptions(c->{}).frameOptions(f->f.deny()).referrerPolicy(r->r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)).contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; img-src 'self' https://res.cloudinary.com https://tile.openstreetmap.org https://a.tile.openstreetmap.fr data:; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self' ws: wss:; font-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'"))).build();
 }
 static void jsonError(HttpServletResponse res,int status,String message)throws IOException{res.setStatus(status);res.setContentType("application/json;charset=UTF-8");res.getWriter().write(new ObjectMapper().writeValueAsString(Map.of("message",message)));}
 static UsernamePasswordAuthenticationToken authentication(Account a){return new UsernamePasswordAuthenticationToken(a.email,null,Arrays.stream(a.roles.split(",")).map(r->new SimpleGrantedAuthority("ROLE_"+r)).toList());}
}

@org.springframework.stereotype.Service
class JwtService {
 private final byte[] key; private final EntityManager em; private final ObjectMapper json;
 JwtService(@Value("${uteb.jwt-secret:}")String secret,@Value("${uteb.demo:false}")boolean demo,EntityManager em,ObjectMapper json){
  if(secret.length()<32&&!demo)throw new IllegalStateException("JWT_SECRET phải có ít nhất 32 ký tự.");
  this.key=secret.length()>=32?secret.getBytes(StandardCharsets.UTF_8):random();this.em=em;this.json=json;
 }
 static byte[] random(){byte[] b=new byte[64];new SecureRandom().nextBytes(b);return b;}
 String issue(Account a)throws Exception{String body=enc(json.writeValueAsBytes(Map.of("sub",a.email,"exp",Instant.now().plusSeconds(3600).getEpochSecond(),"ver",a.tokenVersion)));String raw=enc("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))+"."+body;return raw+"."+enc(sign(raw));}
 UsernamePasswordAuthenticationToken verify(String token)throws Exception{String[] p=token.split("\\.");if(p.length!=3||!MessageDigest.isEqual(sign(p[0]+"."+p[1]),Base64.getUrlDecoder().decode(p[2])))throw new SecurityException();Map<?,?> claims=json.readValue(Base64.getUrlDecoder().decode(p[1]),Map.class);if(((Number)claims.get("exp")).longValue()<=Instant.now().getEpochSecond())throw new SecurityException();Account a=em.createQuery("from Account where email=:e",Account.class).setParameter("e",claims.get("sub")).getSingleResult();if(!a.active||!a.verified||a.tokenVersion!=((Number)claims.get("ver")).intValue())throw new SecurityException();return SecurityConfig.authentication(a);}
 byte[] sign(String raw)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));}
 static String enc(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
}
