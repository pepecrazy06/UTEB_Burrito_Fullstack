package vn.uteb;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static vn.uteb.Domain.Setting;
import static vn.uteb.ShopService.require;

/** Public business settings only. Credentials never belong in this catalog. */
@Service @Transactional(readOnly=true)
public class BusinessSettings {
    @PersistenceContext EntityManager em;
    record Definition(String name,String label,String initial,String minimum,String maximum) {}
    static final List<Definition> DEFINITIONS=List.of(
        new Definition("brand","Tên thương hiệu","UTEB Burrito",null,null),
        new Definition("brand_short","Tên ngắn cạnh logo","UTEB",null,null),
        new Definition("free_shipping_threshold","Đơn tối thiểu miễn phí giao (đ)","150000","0","100000000"),
        new Definition("points_unit","Số tiền tương ứng 1 điểm (đ)","1000","100","1000000"),
        new Definition("payment_expiry_minutes","Thời hạn thanh toán (phút)","15","5","120"),
        new Definition("schedule_lead_minutes","Thời gian chuẩn bị tối thiểu (phút)","30","1","1440"),
        new Definition("schedule_max_days","Số ngày được đặt trước","7","2","90")
    );
    static Definition definition(String name){return DEFINITIONS.stream().filter(d->d.name.equals(name)).findFirst().orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Tham số cấu hình không được hỗ trợ."));}
    static String validate(String name,String value){
        Definition d=definition(name);require(value!=null&&!value.isBlank(),"Giá trị cấu hình không được trống.");value=value.trim();
        if(d.minimum==null){require(value.length()<=80,"Tên thương hiệu tối đa 80 ký tự.");return value;}
        try{BigDecimal n=new BigDecimal(value);require(n.compareTo(new BigDecimal(d.minimum))>=0&&n.compareTo(new BigDecimal(d.maximum))<=0&&n.stripTrailingZeros().scale()<=0,d.label+": từ "+d.minimum+" đến "+d.maximum+", chỉ nhận số nguyên.");return n.toPlainString();}
        catch(NumberFormatException e){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Giá trị cấu hình phải là số nguyên hợp lệ.");}
    }
    String value(String name){Definition d=definition(name);return em.createQuery("select s.settingValue from Setting s where s.name=:name",String.class).setParameter("name",name).getResultStream().findFirst().orElse(d.initial);}
    BigDecimal decimal(String name){return new BigDecimal(validate(name,value(name)));}
    int integer(String name){return decimal(name).intValueExact();}
    Map<String,Object> publicValues(){Map<String,Object> values=new LinkedHashMap<>();for(Definition d:DEFINITIONS)values.put(d.name,d.minimum==null?value(d.name):decimal(d.name));return values;}
    List<Map<String,Object>> definitions(){return DEFINITIONS.stream().map(d->{Map<String,Object> row=new LinkedHashMap<>();row.put("name",d.name);row.put("label",d.label);row.put("numeric",d.minimum!=null);row.put("minimum",d.minimum);row.put("maximum",d.maximum);row.put("value",value(d.name));return row;}).toList();}
}
