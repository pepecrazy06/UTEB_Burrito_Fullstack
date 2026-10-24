package vn.uteb;

import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.uteb.Domain.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:mediaDeletionApi;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
 "spring.jpa.hibernate.ddl-auto=create-drop",
 "uteb.cloudinary.cloud=unit-cloud", "uteb.cloudinary.key=unit-key", "uteb.cloudinary.secret=unit-secret",
 "uteb.mail.enabled=false"
})
@ActiveProfiles("demo") @AutoConfigureMockMvc
class MediaDeletionApiTest {
 @Autowired ShopService shop;
 @Autowired MockMvc mvc;
 @Autowired JwtService jwt;
 @MockitoSpyBean MediaService media;
 @MockitoBean JavaMailSender mail;

 @SuppressWarnings("unchecked")
 @BeforeEach void fakeCloudinary(){
  HttpResponse<String> response=mock(HttpResponse.class);
  when(response.statusCode()).thenReturn(200);
  when(response.body()).thenReturn("{\"result\":\"ok\"}");
  try{doReturn(response).when(media).sendDestroy(any());}
  catch(Exception e){throw new AssertionError(e);}
 }

 MediaRecord uploadedBy(String email){
  Account owner=shop.byEmail(email);String publicId="uteb/"+owner.id+"/"+UUID.randomUUID();
  MediaRecord record=new MediaRecord();record.userId=owner.id;record.kind="image";record.publicId=publicId;
  record.url="https://res.cloudinary.com/unit-cloud/image/upload/v1/"+publicId+".jpg";
  return shop.save(record);
 }

 @Test void ownerDeletesUnusedMediaWithSessionAndCsrf()throws Exception{
  MediaRecord record=uploadedBy("customer@uteb.vn");
  mvc.perform(delete("/shop-api/media/"+record.id).with(user("customer@uteb.vn")).with(csrf()))
   .andExpect(status().isNoContent());
  assertThatThrownBy(()->shop.get(MediaRecord.class,record.id))
   .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
  verify(media).sendDestroy(any());
 }

 @Test void missingAuthenticationOrCsrfIsRejectedBeforeDeletion()throws Exception{
  MediaRecord record=uploadedBy("customer@uteb.vn");
  mvc.perform(delete("/shop-api/media/"+record.id).with(csrf())).andExpect(status().isUnauthorized());
  mvc.perform(delete("/shop-api/media/"+record.id).with(user("customer@uteb.vn")))
   .andExpect(status().isForbidden());
  assertThat(shop.get(MediaRecord.class,record.id)).isNotNull();
  verify(media,never()).sendDestroy(any());
 }

 @Test void anotherCustomerCannotDeleteMedia()throws Exception{
  MediaRecord record=uploadedBy("customer@uteb.vn");
  Account other=new Account();other.email="media-other-"+UUID.randomUUID()+"@example.com";
  other.name="Other Customer";other.passwordHash=shop.hashPassword("Password@123");other.verified=true;
  shop.save(other);
  mvc.perform(delete("/shop-api/media/"+record.id).with(user(other.email)).with(csrf()))
   .andExpect(status().isForbidden());
  assertThat(shop.get(MediaRecord.class,record.id)).isNotNull();
  verify(media,never()).sendDestroy(any());
 }

 @Test void productReferenceReturnsConflictWithoutCallingCloudinary()throws Exception{
  MediaRecord record=uploadedBy("customer@uteb.vn");
  Product product=new Product();product.name="Referenced image";product.storeId=1L;product.image=record.url;shop.save(product);
  mvc.perform(delete("/shop-api/media/"+record.id).with(user("customer@uteb.vn")).with(csrf()))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Ảnh đang được gắn với món."));
  assertThat(shop.get(MediaRecord.class,record.id)).isNotNull();
  verify(media,never()).sendDestroy(any());
 }

 @Test void reviewAttachmentReturnsConflictWithoutCallingCloudinary()throws Exception{
  MediaRecord record=uploadedBy("customer@uteb.vn");
  Review review=new Review();review.userId=record.userId;review.media=record.url;review.text="Existing review";
  shop.save(review);
  mvc.perform(delete("/shop-api/media/"+record.id).with(user("customer@uteb.vn")).with(csrf()))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Media đang được dùng trong đánh giá."));
  assertThat(shop.get(MediaRecord.class,record.id)).isNotNull();
  verify(media,never()).sendDestroy(any());
 }

 @Test void historicalOrderImageReturnsConflictEvenWhenNoProductUsesIt()throws Exception{
  MediaRecord record=uploadedBy("customer@uteb.vn");
  OrderLine line=new OrderLine();line.orderId=shop.all(MealOrder.class).getFirst().id;
  line.productId=shop.all(Product.class).getFirst().id;line.name="Historical dish";line.image=record.url;
  shop.save(line);
  mvc.perform(delete("/shop-api/media/"+record.id).with(user("customer@uteb.vn")).with(csrf()))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Ảnh thuộc lịch sử đơn hàng, không thể xóa."));
  assertThat(shop.get(MediaRecord.class,record.id)).isNotNull();
  verify(media,never()).sendDestroy(any());
 }

 @Test void jwtEndpointDeletesWithValidBearerWithoutCsrf()throws Exception{
  MediaRecord record=uploadedBy("customer@uteb.vn");
  String token=jwt.issue(shop.byEmail("customer@uteb.vn"));
  mvc.perform(delete("/api/media/"+record.id).header("Authorization","Bearer "+token))
   .andExpect(status().isNoContent());
  assertThatThrownBy(()->shop.get(MediaRecord.class,record.id))
   .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
 }

 @Test void cloudinaryFailureDoesNotDiscardMediaRecord()throws Exception{
  MediaRecord record=uploadedBy("customer@uteb.vn");
  HttpResponse<String> failed=mock(HttpResponse.class);
  when(failed.statusCode()).thenReturn(503);when(failed.body()).thenReturn("{}");
  doReturn(failed).when(media).sendDestroy(any());
  mvc.perform(delete("/shop-api/media/"+record.id).with(user("customer@uteb.vn")).with(csrf()))
   .andExpect(status().isBadGateway());
  assertThat(shop.get(MediaRecord.class,record.id)).isNotNull();
 }
}
