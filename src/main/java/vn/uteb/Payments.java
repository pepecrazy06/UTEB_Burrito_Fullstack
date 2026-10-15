package vn.uteb;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static vn.uteb.Domain.*;
import static vn.uteb.ShopService.*;

@Service @Transactional
public class Payments {
 final ShopService shop;final ObjectMapper json;final String base,vnCode,vnSecret,partner,access,secret;
 Payments(ShopService shop,ObjectMapper json,@Value("${uteb.base-url}")String base,@Value("${uteb.vnpay.code}")String vnCode,@Value("${uteb.vnpay.secret}")String vnSecret,@Value("${uteb.momo.partner}")String partner,@Value("${uteb.momo.access}")String access,@Value("${uteb.momo.secret}")String secret){this.shop=shop;this.json=json;this.base=base;this.vnCode=vnCode;this.vnSecret=vnSecret;this.partner=partner;this.access=access;this.secret=secret;}
 static String sign(String algorithm,String key,String raw){try{Mac m=Mac.getInstance(algorithm);m.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),algorithm));return HexFormat.of().formatHex(m.doFinal(raw.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 static boolean equalsSignature(String a,String b){return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
 static String encode(String s){return URLEncoder.encode(s,StandardCharsets.US_ASCII);}
 static String vnRaw(Map<String,String>params){return params.entrySet().stream().filter(e->e.getKey().startsWith("vnp_")&&!e.getKey().equals("vnp_SecureHash")&&!e.getKey().equals("vnp_SecureHashType")&&!e.getValue().isEmpty()).sorted(Map.Entry.comparingByKey()).map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));}
 Map<String,Object> start(long id,String ip)throws Exception{MealOrder o=shop.accessibleOrder(id);forbidden(o.userId.equals(shop.me().id));require(!o.paymentMethod.equals("COD")&&!o.paymentStatus.equals("PAID")&&!o.status.equals("CANCELLED"),"Đơn không cần thanh toán.");require(o.paymentExpires.isAfter(LocalDateTime.now()),"Đơn đã hết thời gian thanh toán.");if(shop.demoMode()&&((o.paymentMethod.equals("VNPAY")&&vnCode.isEmpty())||(o.paymentMethod.equals("MOMO")&&partner.isEmpty())))return Map.of("simulation",true,"orderId",id,"message","Thanh toán giả lập local; không chuyển tiền thật.");
  if(o.paymentMethod.equals("VNPAY")){require(!vnCode.isEmpty()&&!vnSecret.isEmpty(),"Chưa cấu hình VNPay Sandbox.");Map<String,String>p=new TreeMap<>();p.put("vnp_Version","2.1.0");p.put("vnp_Command","pay");p.put("vnp_TmnCode",vnCode);p.put("vnp_Amount",o.total.multiply(new BigDecimal("100")).toPlainString());p.put("vnp_CurrCode","VND");p.put("vnp_TxnRef",o.id.toString());p.put("vnp_OrderInfo","Thanh toan "+o.code);p.put("vnp_OrderType","other");p.put("vnp_Locale","vn");p.put("vnp_ReturnUrl",base+"/payments/return");p.put("vnp_IpAddr",ip);p.put("vnp_CreateDate",ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));p.put("vnp_ExpireDate",ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusMinutes(15).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));String raw=vnRaw(p);return Map.of("url","https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"+raw+"&vnp_SecureHash="+sign("HmacSHA512",vnSecret,raw),"simulation",false);}
  require(!partner.isEmpty()&&!access.isEmpty()&&!secret.isEmpty(),"Chưa cấu hình MoMo Sandbox.");String requestId=UUID.randomUUID().toString(),orderId=o.id.toString(),amount=o.total.toPlainString(),info="UTEB "+o.code,redirect=base+"/payments/return",ipn=base+"/payments/momo/ipn";
  String raw="accessKey="+access+"&amount="+amount+"&extraData=&ipnUrl="+ipn+"&orderId="+orderId+"&orderInfo="+info+"&partnerCode="+partner+"&redirectUrl="+redirect+"&requestId="+requestId+"&requestType=captureWallet";
  Map<String,Object>p=new LinkedHashMap<>();p.put("partnerCode",partner);p.put("requestId",requestId);p.put("amount",Long.parseLong(amount));p.put("orderId",orderId);p.put("orderInfo",info);p.put("redirectUrl",redirect);p.put("ipnUrl",ipn);p.put("lang","vi");p.put("requestType","captureWallet");p.put("extraData","");p.put("signature",sign("HmacSHA256",secret,raw));
  var req=HttpRequest.newBuilder(URI.create("https://test-payment.momo.vn/v2/gateway/api/create")).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(p))).build();var response=HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString());Map<?,?>result=json.readValue(response.body(),Map.class);require(response.statusCode()==200&&((Number)result.getOrDefault("resultCode",null)).intValue()==0&&result.get("payUrl")!=null,"MoMo không tạo được giao dịch; kiểm tra cấu hình Sandbox.");return Map.of("url",result.get("payUrl"),"simulation",false);
 }
 Map<String,String> vnIpn(Map<String,String>p){if(vnSecret.isEmpty()||!equalsSignature(sign("HmacSHA512",vnSecret,vnRaw(p)),p.get("vnp_SecureHash")))return Map.of("RspCode","97","Message","Invalid signature");try{long id=Long.parseLong(p.get("vnp_TxnRef"));MealOrder o=shop.get(MealOrder.class,id);if(!"VNPAY".equals(o.paymentMethod)||o.total.multiply(new BigDecimal("100")).compareTo(new BigDecimal(p.get("vnp_Amount")))!=0)return Map.of("RspCode","04","Message","Invalid amount");if("PAID".equals(o.paymentStatus))return Map.of("RspCode","02","Message","Already confirmed");shop.paymentResult(id,p.get("vnp_TransactionNo"),"00".equals(p.get("vnp_ResponseCode"))&&"00".equals(p.get("vnp_TransactionStatus")));return Map.of("RspCode","00","Message","Confirm success");}catch(Exception e){return Map.of("RspCode","01","Message","Order invalid or expired; reconcile manually");}}
 void momoIpn(Map<String,Object>p){require(!secret.isEmpty(),"MoMo chưa cấu hình.");String raw="accessKey="+access+"&amount="+str(p,"amount")+"&extraData="+str(p,"extraData")+"&message="+str(p,"message")+"&orderId="+str(p,"orderId")+"&orderInfo="+str(p,"orderInfo")+"&orderType="+str(p,"orderType")+"&partnerCode="+str(p,"partnerCode")+"&payType="+str(p,"payType")+"&requestId="+str(p,"requestId")+"&responseTime="+str(p,"responseTime")+"&resultCode="+str(p,"resultCode")+"&transId="+str(p,"transId");require(partner.equals(str(p,"partnerCode"))&&equalsSignature(sign("HmacSHA256",secret,raw),str(p,"signature")),"Chữ ký MoMo không hợp lệ.");MealOrder o=shop.get(MealOrder.class,number(p,"orderId"));require("MOMO".equals(o.paymentMethod)&&o.total.compareTo(new BigDecimal(str(p,"amount")))==0,"Số tiền MoMo không khớp.");shop.paymentResult(o.id,str(p,"transId"),"0".equals(str(p,"resultCode")));}
}
