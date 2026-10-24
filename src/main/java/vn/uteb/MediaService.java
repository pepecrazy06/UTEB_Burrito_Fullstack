package vn.uteb;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;import java.net.http.*;import java.time.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;
import static vn.uteb.ShopService.*;import static vn.uteb.Domain.*;
@Service @Transactional
public class MediaService {
 final ShopService shop;final ObjectMapper json;final String cloud,key,secret;
 MediaService(ShopService shop,ObjectMapper json,@Value("${uteb.cloudinary.cloud}")String cloud,@Value("${uteb.cloudinary.key}")String key,@Value("${uteb.cloudinary.secret}")String secret){this.shop=shop;this.json=json;this.cloud=cloud.strip();this.key=key.strip();this.secret=secret.strip();}
 MediaRecord upload(MultipartFile f)throws Exception{Account a=shop.me();require(f.getSize()>0&&f.getSize()<=10*1024*1024,"Tệp cần nhỏ hơn 10MB.");byte[] bytes=f.getBytes();boolean jpg=bytes.length>3&&(bytes[0]&255)==255&&(bytes[1]&255)==216;boolean png=bytes.length>8&&bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71;boolean webp=bytes.length>12&&new String(bytes,0,4,StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WEBP");boolean mp4=bytes.length>12&&new String(bytes,4,4,StandardCharsets.US_ASCII).equals("ftyp");require(jpg||png||webp||mp4,"Chỉ nhận JPEG, PNG, WebP hoặc MP4.");require(!cloud.isEmpty()&&!key.isEmpty()&&!secret.isEmpty(),"Cloudinary chưa cấu hình. Điền biến môi trường rồi khởi động lại.");String kind=mp4?"video":"image",timestamp=Long.toString(Instant.now().getEpochSecond()),folder="uteb/"+a.id;String raw="folder="+folder+"&timestamp="+timestamp+secret,signature=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(raw.getBytes(StandardCharsets.UTF_8))),boundary="UTEB"+UUID.randomUUID();ByteArrayOutputStream out=new ByteArrayOutputStream();for(var e:Map.of("api_key",key,"timestamp",timestamp,"signature",signature,"folder",folder).entrySet())out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+e.getKey()+"\"\r\n\r\n"+e.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"upload."+(mp4?"mp4":png?"png":webp?"webp":"jpg")+"\"\r\nContent-Type: "+(mp4?"video/mp4":png?"image/png":webp?"image/webp":"image/jpeg")+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));var req=HttpRequest.newBuilder(URI.create("https://api.cloudinary.com/v1_1/"+cloud+"/"+kind+"/upload")).timeout(Duration.ofSeconds(30)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();var res=HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString());if(res.statusCode()!=200)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,uploadError(res.statusCode(),res.body()));Map<?,?>body=json.readValue(res.body(),Map.class);MediaRecord m=new MediaRecord();m.userId=a.id;m.url=body.get("secure_url").toString();m.publicId=body.get("public_id").toString();m.kind=kind;return shop.save(m);}

 void delete(long id){
  Account actor=shop.me();MediaRecord record=shop.get(MediaRecord.class,id);
  forbidden(shop.has(actor,"ADMIN")||Objects.equals(actor.id,record.userId));
  shop.lockMedia(record);
  if(cloud.isEmpty()||key.isEmpty()||secret.isEmpty())
   throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cloudinary chưa được cấu hình.");
  require(record.publicId!=null&&!record.publicId.isBlank()&&("image".equals(record.kind)||"video".equals(record.kind))
   &&record.url!=null&&record.url.startsWith("https://res.cloudinary.com/"+cloud+"/"),"Media Cloudinary không hợp lệ.");
  if(!shop.where(Product.class,"image",record.url).isEmpty())
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Ảnh đang được gắn với món.");
  if(shop.all(Review.class).stream().anyMatch(r->r.media!=null&&Arrays.asList(r.media.split(",")).contains(record.url)))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Media đang được dùng trong đánh giá.");
  if(!shop.where(OrderLine.class,"image",record.url).isEmpty())
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Ảnh thuộc lịch sử đơn hàng, không thể xóa.");
  try{
   HttpResponse<String> response=sendDestroy(destroyRequest(record,Long.toString(Instant.now().getEpochSecond())));
   if(response.statusCode()!=200||response.body()==null||!"ok".equals(json.readTree(response.body()).path("result").asText()))
    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Chưa xóa được media trên Cloudinary. Vui lòng thử lại.");
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Chưa xóa được media trên Cloudinary. Vui lòng thử lại.");}
  catch(IOException e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Chưa xóa được media trên Cloudinary. Vui lòng thử lại.");}
  shop.removeMedia(record);
 }

 String destroyForm(MediaRecord record,String timestamp){
  String parameters="invalidate=true&public_id="+record.publicId+"&timestamp="+timestamp;
  String signature;
  try{signature=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest((parameters+secret).getBytes(StandardCharsets.UTF_8)));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  return "public_id="+URLEncoder.encode(record.publicId,StandardCharsets.UTF_8)+"&timestamp="+timestamp+
   "&invalidate=true&api_key="+URLEncoder.encode(key,StandardCharsets.UTF_8)+"&signature="+signature;
 }

 HttpRequest destroyRequest(MediaRecord record,String timestamp){
  return HttpRequest.newBuilder(URI.create("https://api.cloudinary.com/v1_1/"+cloud+"/"+record.kind+"/destroy"))
   .timeout(Duration.ofSeconds(30)).header("Content-Type","application/x-www-form-urlencoded")
   .POST(HttpRequest.BodyPublishers.ofString(destroyForm(record,timestamp),StandardCharsets.UTF_8)).build();
 }

 HttpResponse<String> sendDestroy(HttpRequest request)throws IOException,InterruptedException{
  return HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
 }

 // Classify the provider response without exposing keys, signatures or raw response content.
 String uploadError(int status,String response){
  String message="";
  try{message=json.readTree(response).path("error").path("message").asText("").toLowerCase(Locale.ROOT);}catch(Exception ignored){}
  String detail;
  if(message.contains("cloud name")||message.contains("cloud_name"))
   detail="Cloud name không hợp lệ. Kiểm tra CLOUDINARY_CLOUD trong Settings → API Keys.";
  else if(message.contains("api_key")||message.contains("api key"))
   detail="API Key không hợp lệ hoặc không thuộc Cloud name đang dùng. Nhập lại cả ba thông tin từ cùng môi trường Cloudinary.";
  else if(message.contains("timestamp")||message.contains("stale")||message.contains("expired"))
   detail="Thời gian yêu cầu không hợp lệ. Kiểm tra ngày giờ và đồng bộ giờ trên máy.";
  else if(message.contains("signature")||message.contains("api_secret"))
   detail="Chữ ký tải lên không hợp lệ. Kiểm tra API Secret có khớp với API Key và Cloud name; sau đó khởi động lại web.";
  else if(message.contains("too large")||message.contains("file size")||status==413)
   detail="Tệp vượt giới hạn dung lượng của Cloudinary. Thử ảnh JPG hoặc PNG nhỏ hơn.";
  else if(message.contains("invalid image")||message.contains("invalid video")||message.contains("unsupported")||message.contains("empty file"))
   detail="Nội dung tệp ảnh/video không hợp lệ hoặc không được hỗ trợ. Thử xuất lại ảnh thành JPG hoặc PNG.";
  else if(message.contains("quota")||message.contains("limit")||message.contains("disabled")||status==429)
   detail="Tài khoản Cloudinary đang bị giới hạn. Kiểm tra trạng thái và hạn mức trong Console.";
  else if(status==401||status==403)
   detail="Thông tin kết nối hoặc quyền tải lên chưa hợp lệ. Kiểm tra Cloud name, API Key và API Secret.";
  else
   detail="Chưa tải được tệp. Kiểm tra cấu hình trong Cloudinary Console hoặc thử lại sau.";
  org.slf4j.LoggerFactory.getLogger(MediaService.class).warn("Cloudinary upload failed (HTTP {}): {}",status,detail);
  return "Cloudinary (HTTP "+status+"): "+detail;
 }
}
