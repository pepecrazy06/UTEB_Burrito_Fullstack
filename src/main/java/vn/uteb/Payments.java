package vn.uteb;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static vn.uteb.Domain.*;
import static vn.uteb.ShopService.*;

@Service @Transactional
public class Payments {
 private final VnpayService vnpay;
 private final ShopService shop;
 Payments(ShopService shop,VnpayService vnpay){this.vnpay=vnpay;this.shop=shop;}
 static String sign(String algorithm,String key,String raw){try{Mac m=Mac.getInstance(algorithm);m.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),algorithm));return HexFormat.of().formatHex(m.doFinal(raw.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 static boolean equalsSignature(String a,String b){return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
 static String encode(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
 static String vnRaw(Map<String,String>params){return params.entrySet().stream().filter(e->e.getKey().startsWith("vnp_")&&!e.getKey().equals("vnp_SecureHash")&&!e.getKey().equals("vnp_SecureHashType")&&e.getValue()!=null&&!e.getValue().isEmpty()).sorted(Map.Entry.comparingByKey()).map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));}
 Map<String,Object> start(long id,String ip){
  MealOrder order=shop.accessibleOrder(id);forbidden(order.userId.equals(shop.me().id));
  require("VNPAY".equals(order.paymentMethod),"Đơn hàng này không thanh toán bằng VNPay.");
  return vnpay.start(order,ip);
 }
 boolean vnpayConfigured(){return vnpay.configured();}
 void requireVnpay(){vnpay.requireConfigured();}
 String vnpayReturn(org.springframework.util.MultiValueMap<String,String> fields){return vnpay.returnTarget(VnpayService.singleValues(fields));}
 Map<String,String> vnIpn(Map<String,String> fields){return vnpay.ipn(fields);}
}
