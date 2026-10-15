package vn.uteb;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.*;
import java.util.*;
import static vn.uteb.Domain.*;
import static vn.uteb.ShopService.*;

@Controller class PageController { @GetMapping("/") String index(){return "index";} @GetMapping("/payments/return")String paymentReturn(){return "redirect:/#orders";} }
@RestController @RequestMapping({"/shop-api","/api"})
public class WebController {
 final ShopService s;final JwtService jwt;final Payments payments;final MediaService media;
 WebController(ShopService s,JwtService jwt,Payments payments,MediaService media){this.s=s;this.jwt=jwt;this.payments=payments;this.media=media;}
 @GetMapping("/public/bootstrap")Map<String,Object> bootstrap(HttpServletRequest req){Map<String,Object>d=s.publicData();CsrfToken csrf=(CsrfToken)req.getAttribute(CsrfToken.class.getName());if(csrf!=null)d.put("csrf",csrf.getToken());try{d.put("user",s.me());}catch(ResponseStatusException ignored){}return d;}
 @GetMapping("/public/top")List<Map<String,Object>>top(){Map<Long,Integer>counts=new HashMap<>();for(MealOrder o:s.all(MealOrder.class))if(o.status.equals("COMPLETED"))for(OrderLine l:s.where(OrderLine.class,"orderId",o.id))counts.merge(l.productId,l.quantity,Integer::sum);return counts.entrySet().stream().sorted(Map.Entry.<Long,Integer>comparingByValue().reversed()).limit(10).map(e->Map.<String,Object>of("product",s.get(Product.class,e.getKey()),"sold",e.getValue())).toList();}
 @PostMapping("/auth/login")Map<String,Object> login(@RequestBody Map<String,Object>b,HttpServletRequest req,HttpServletResponse res)throws Exception{Account a=s.login(str(b,"email"),str(b,"password"));if(req.getRequestURI().startsWith("/shop-api")){if(req.getSession(false)!=null)req.changeSessionId();SecurityContext ctx=SecurityContextHolder.createEmptyContext();ctx.setAuthentication(SecurityConfig.authentication(a));SecurityContextHolder.setContext(ctx);new HttpSessionSecurityContextRepository().saveContext(ctx,req,res);CookieCsrfTokenRepository csrf=CookieCsrfTokenRepository.withHttpOnlyFalse();csrf.setCookieCustomizer(c->c.sameSite("Lax").path("/"));csrf.saveToken(null,req,res);}return Map.of("user",a,"token",jwt.issue(a),"expiresIn",3600);}
 @PostMapping("/auth/logout")Map<String,Object>logout(HttpServletRequest req,HttpServletResponse res){s.revokeTokens();if(req.getSession(false)!=null)req.getSession(false).invalidate();SecurityContextHolder.clearContext();if(req.getRequestURI().startsWith("/shop-api")){CookieCsrfTokenRepository csrf=CookieCsrfTokenRepository.withHttpOnlyFalse();csrf.setCookieCustomizer(c->c.sameSite("Lax").path("/"));csrf.saveToken(null,req,res);}return Map.of("message","Đã đăng xuất.");}
 @PostMapping("/auth/register")Map<String,Object>register(@RequestBody Map<String,Object>b){return s.register(b);}
 @PostMapping("/auth/otp/request")Map<String,Object>otpRequest(@RequestBody Map<String,Object>b){return s.otpRequest(b);}
 @PostMapping("/auth/otp/verify")Map<String,Object>otpVerify(@RequestBody Map<String,Object>b){boolean ok=s.checkOtp(b);require(ok,"OTP không đúng, hết hạn hoặc đã sử dụng.");return Map.of("message","Xác thực thành công. Bạn có thể đăng nhập.");}
 @GetMapping("/account")Map<String,Object>account(){return s.accountData();}
 @PutMapping("/account/profile")Account profile(@RequestBody Map<String,Object>b){return s.profile(b);}
 @PutMapping("/account/password")Map<String,Object>password(@RequestBody Map<String,Object>b){s.changePassword(b);return Map.of("message","Đã đổi mật khẩu.");}
 @PostMapping("/addresses")Address address(@RequestBody Map<String,Object>b){return s.address(b);}
 @DeleteMapping("/addresses/{id}")void deleteAddress(@PathVariable long id){s.deleteAddress(id);}
 @GetMapping("/cart")List<Map<String,Object>>cart(){return s.cartView();}
 @PostMapping("/cart")CartLine cartSave(@RequestBody Map<String,Object>b){return s.cart(b);}
 @DeleteMapping("/cart/{id}")void cartDelete(@PathVariable long id){s.deleteCart(id);}
 @PostMapping("/checkout/preview")Map<String,Object>preview(@RequestBody Map<String,Object>b){return s.preview(b);}
 @PostMapping("/checkout")List<MealOrder>checkout(@RequestBody Map<String,Object>b){return s.checkout(b);}
 @GetMapping("/orders")List<MealOrder>orders(){return s.orders();}
 @GetMapping("/orders/{id}")Map<String,Object>order(@PathVariable long id){return s.detail(id);}
 @PutMapping("/orders/{id}/status")void status(@PathVariable long id,@RequestBody Map<String,Object>b){s.status(id,b);}
 @PutMapping("/orders/{id}/shipper")void assign(@PathVariable long id,@RequestBody Map<String,Object>b){s.assign(id,number(b,"shipperId"));}
 @PostMapping("/orders/{id}/payment")Map<String,Object>pay(@PathVariable long id,HttpServletRequest req)throws Exception{return payments.start(id,req.getRemoteAddr());}
 @PostMapping("/orders/{id}/simulate")void simulate(@PathVariable long id,@RequestBody Map<String,Object>b){s.simulate(id,Boolean.parseBoolean(str(b,"success")));}
 @PostMapping("/orders/{id}/refund")RefundRequest refund(@PathVariable long id,@RequestBody Map<String,Object>b){return s.refund(id,b);}
 @PutMapping("/refunds/{id}")void refundDecision(@PathVariable long id,@RequestBody Map<String,Object>b){s.refundDecision(id,b);}
 @PostMapping("/reviews")Review review(@RequestBody Map<String,Object>b){return s.review(b);}
 @PostMapping("/marks/{productId}")void mark(@PathVariable long productId,@RequestBody Map<String,Object>b){s.mark(productId,str(b,"kind"));}
 @PutMapping("/notifications/{id}/read")void notice(@PathVariable long id){s.readNotice(id);}
 @PostMapping("/rewards/redeem")Voucher redeem(@RequestBody Map<String,Object>b){return s.redeem(str(b,"reward"));}
 @GetMapping("/groups/{code}")Map<String,Object>group(@PathVariable String code){return s.groupView(code);}
 @PostMapping("/groups")GroupMeal createGroup(@RequestBody Map<String,Object>b){return s.createGroup(b);}
 @PostMapping("/groups/{code}/items")GroupLine groupAdd(@PathVariable String code,@RequestBody Map<String,Object>b){return s.groupAdd(code,b);}
 @DeleteMapping("/groups/items/{id}")void groupDelete(@PathVariable long id){s.groupDelete(id);}
 @PostMapping("/groups/{code}/cart")void groupCart(@PathVariable String code){s.groupToCart(code);}
 @GetMapping("/catering")List<CateringRequest>catering(){return s.where(CateringRequest.class,"userId",s.me().id);}
 @PostMapping("/catering")CateringRequest cateringSave(@RequestBody Map<String,Object>b){return s.catering(b);}
 @PostMapping("/shops/apply")ShopApplication apply(@RequestBody Map<String,Object>b){return s.shopApplication(b);}
 @PutMapping("/shops/applications/{id}")void approve(@PathVariable long id,@RequestBody Map<String,Object>b){s.approveShop(id,b);}
 @GetMapping("/admin/dashboard")Map<String,Object>dashboard(){return s.dashboard();}
 @GetMapping("/admin/{resource}")List<?>adminList(@PathVariable String resource){return s.adminList(resource);}
 @PostMapping("/admin/{resource}")Object adminSave(@PathVariable String resource,@RequestBody Map<String,Object>b){return s.adminSave(resource,b);}
 @DeleteMapping("/admin/{resource}/{id}")void adminDelete(@PathVariable String resource,@PathVariable long id){s.adminDelete(resource,id);}
 @PostMapping(value="/media",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)MediaRecord upload(@RequestParam("file")MultipartFile file)throws Exception{return media.upload(file);}
}
@RestController class CallbackController {
 final Payments p;CallbackController(Payments p){this.p=p;}
 @GetMapping("/payments/vnpay/ipn")Map<String,String>vn(@RequestParam Map<String,String>b){return p.vnIpn(b);}
 @PostMapping("/payments/momo/ipn")ResponseEntity<Void>momo(@RequestBody Map<String,Object>b){p.momoIpn(b);return ResponseEntity.noContent().build();}
}
@RestControllerAdvice class Errors {
 @ExceptionHandler(ResponseStatusException.class)ResponseEntity<?>status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",Objects.requireNonNullElse(e.getReason(),"Yêu cầu không hợp lệ.")));}
 @ExceptionHandler({NumberFormatException.class,java.time.format.DateTimeParseException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})ResponseEntity<?>bad(Exception e){return ResponseEntity.badRequest().body(Map.of("message","Dữ liệu nhập không hợp lệ."));}
 @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,jakarta.persistence.PersistenceException.class})ResponseEntity<?>conflict(Exception e){return ResponseEntity.status(409).body(Map.of("message","Dữ liệu đã thay đổi hoặc bị trùng. Tải lại và thử lại."));}
 @ExceptionHandler(Exception.class)ResponseEntity<?>other(Exception e){org.slf4j.LoggerFactory.getLogger(Errors.class).error("Operation failed: {}",e.getClass().getSimpleName());return ResponseEntity.status(500).body(Map.of("message","Không thể hoàn tất thao tác. Kiểm tra cấu hình dịch vụ hoặc thử lại."));}
}
