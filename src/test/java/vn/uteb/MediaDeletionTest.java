package vn.uteb;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static vn.uteb.Domain.*;

class MediaDeletionTest {
 static class FakeMediaService extends MediaService {
  HttpRequest request;
  HttpResponse<String> response=reply(200,"{\"result\":\"ok\"}");
  IOException failure;
  FakeMediaService(ShopService shop){super(shop,new ObjectMapper(),"unit-cloud","unit-key","unit-secret");}
  @Override HttpResponse<String> sendDestroy(HttpRequest request)throws IOException {
   this.request=request;if(failure!=null)throw failure;return response;
  }
 }

 static class Fixture {
  final ShopService shop=mock(ShopService.class);
  final Account owner=new Account();
  final MediaRecord record=new MediaRecord();
  final FakeMediaService media=new FakeMediaService(shop);
  Fixture(){
   owner.id=1L;owner.roles="CUSTOMER";
   record.id=7L;record.userId=owner.id;record.kind="image";
   record.url="https://res.cloudinary.com/unit-cloud/image/upload/v1/uteb/1/photo.jpg";
   record.publicId="uteb/1/photo";
   when(shop.me()).thenReturn(owner);
   when(shop.get(MediaRecord.class,7L)).thenReturn(record);
  }
 }

 @SuppressWarnings("unchecked")
 static HttpResponse<String> reply(int status,String body){
  HttpResponse<String> response=mock(HttpResponse.class);
  when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(body);
  return response;
 }

 @Test void ownerCanDeleteUnusedImage(){
  Fixture f=new Fixture();f.media.delete(7L);
  assertThat(f.media.request.uri().toString()).isEqualTo("https://api.cloudinary.com/v1_1/unit-cloud/image/destroy");
  verify(f.shop).lockMedia(f.record);
  verify(f.shop).removeMedia(f.record);
 }

 @Test void videoUsesVideoDestroyEndpoint(){
  Fixture f=new Fixture();f.record.kind="video";
  f.record.url="https://res.cloudinary.com/unit-cloud/video/upload/v1/uteb/1/clip.mp4";
  f.media.delete(7L);
  assertThat(f.media.request.uri().toString()).endsWith("/video/destroy");
 }

 @Test void destroyRequestSignsTheExactCloudinaryParameters(){
  Fixture f=new Fixture();String form=f.media.destroyForm(f.record,"173719931");
  assertThat(form).contains("public_id=uteb%2F1%2Fphoto","timestamp=173719931","invalidate=true","api_key=unit-key")
   .contains("signature=ccc690bb350027c34e7c7f68dc81dfc0eb3ae9cb")
   .doesNotContain("unit-secret");
 }

 @Test void otherUserCannotDeleteMedia(){
  Fixture f=new Fixture();Account other=new Account();other.id=2L;other.roles="CUSTOMER";
  when(f.shop.me()).thenReturn(other);
  ResponseStatusException error=catchThrowableOfType(()->f.media.delete(7L),ResponseStatusException.class);
  assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  assertThat(f.media.request).isNull();verify(f.shop,never()).removeMedia(any());
 }

 @Test void adminCanDeleteUnusedMediaFromAnotherAccount(){
  Fixture f=new Fixture();Account admin=new Account();admin.id=3L;admin.roles="ADMIN";
  when(f.shop.me()).thenReturn(admin);when(f.shop.has(admin,"ADMIN")).thenReturn(true);
  f.media.delete(7L);verify(f.shop).removeMedia(f.record);
 }

 @Test void inactiveProductStillProtectsItsImage(){
  Fixture f=new Fixture();Product product=new Product();product.available=false;product.image=f.record.url;
  when(f.shop.where(Product.class,"image",f.record.url)).thenReturn(List.of(product));
  ResponseStatusException error=catchThrowableOfType(()->f.media.delete(7L),ResponseStatusException.class);
  assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  assertThat(error.getReason()).contains("gắn với món");
  assertThat(f.media.request).isNull();verify(f.shop,never()).removeMedia(any());
 }

 @Test void reviewAttachmentCannotBeDeleted(){
  Fixture f=new Fixture();Review review=new Review();review.media="https://res.cloudinary.com/unit-cloud/image/upload/other.jpg,"+f.record.url;
  when(f.shop.all(Review.class)).thenReturn(List.of(review));
  ResponseStatusException error=catchThrowableOfType(()->f.media.delete(7L),ResponseStatusException.class);
  assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  assertThat(error.getReason()).contains("đánh giá");
  assertThat(f.media.request).isNull();verify(f.shop,never()).removeMedia(any());
 }

 @Test void similarReviewUrlDoesNotBlockUnusedImage(){
  Fixture f=new Fixture();Review review=new Review();review.media=f.record.url+"-other";
  when(f.shop.all(Review.class)).thenReturn(List.of(review));
  f.media.delete(7L);verify(f.shop).removeMedia(f.record);
 }

 @Test void orderHistoryProtectsImageEvenAfterProductChanges(){
  Fixture f=new Fixture();OrderLine line=new OrderLine();line.image=f.record.url;
  when(f.shop.where(OrderLine.class,"image",f.record.url)).thenReturn(List.of(line));
  ResponseStatusException error=catchThrowableOfType(()->f.media.delete(7L),ResponseStatusException.class);
  assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  assertThat(error.getReason()).contains("lịch sử đơn hàng");
  assertThat(f.media.request).isNull();verify(f.shop,never()).removeMedia(any());
 }

 @Test void providerFailureKeepsMediaRecord(){
  Fixture f=new Fixture();f.media.response=reply(500,"{}");
  ResponseStatusException error=catchThrowableOfType(()->f.media.delete(7L),ResponseStatusException.class);
  assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
  verify(f.shop,never()).removeMedia(any());
 }

 @Test void unexpectedProviderResultKeepsMediaRecord(){
  Fixture f=new Fixture();f.media.response=reply(200,"{\"result\":\"not found\"}");
  ResponseStatusException error=catchThrowableOfType(()->f.media.delete(7L),ResponseStatusException.class);
  assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
  verify(f.shop,never()).removeMedia(any());
 }

 @Test void networkFailureKeepsMediaRecord(){
  Fixture f=new Fixture();f.media.failure=new IOException("connection failed");
  ResponseStatusException error=catchThrowableOfType(()->f.media.delete(7L),ResponseStatusException.class);
  assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
  verify(f.shop,never()).removeMedia(any());
 }

 @Test void localAssetIsNotACloudinaryDeletionTarget(){
  Fixture f=new Fixture();f.record.url="/assets/menu/burrito-grilled-chicken.jpg";
  ResponseStatusException error=catchThrowableOfType(()->f.media.delete(7L),ResponseStatusException.class);
  assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  assertThat(f.media.request).isNull();verify(f.shop,never()).removeMedia(any());
 }
}
