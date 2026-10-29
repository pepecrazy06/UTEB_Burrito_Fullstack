package vn.uteb;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.text.Normalizer;
import static vn.uteb.Domain.*;

/** Public facts are answered from the catalog, not guessed from conversation history. */
final class AiPublicKnowledge {
    private AiPublicKnowledge(){}
    static String plain(String value){return AiMenuService.plain(value);}
    static boolean words(String text,String alternatives){return Pattern.compile("\\b(?:"+alternatives+")\\b").matcher(text).find();}
    static List<String> requests(List<String> previous,List<AiConversationTurn> conversation,String message){
        List<String> requests=new ArrayList<>(conversation.isEmpty()?previous:conversation.stream().map(AiConversationTurn::user).toList());requests.add(message);return requests;
    }
    static boolean hasAllergy(List<String> requests){
        return !allergyReports(requests).isEmpty();
    }
    private static List<String> allergyReports(List<String> requests){
        List<String> reports=new ArrayList<>();
        Pattern denial=Pattern.compile("(?iuU)\\b(?:(?:không|khong) (?:(?:bị|bi|có|co) )?(?:dị ứng|di ung)|(?:không phải|khong phai) (?:dị ứng|di ung)|not allergic|no allergy)\\b");
        Pattern declaration=Pattern.compile("(?iuU)\\b(?:dị ứng|di ung|không dung nạp|khong dung nap|bất dung nạp|bat dung nap|allergic(?: to)?|allergy|intolerance(?: to)?)\\b");
        for(String request:requests){
            String original=Normalizer.normalize(request,Normalizer.Form.NFC);
            if(denial.matcher(original).find()){reports.clear();original=denial.matcher(original).replaceAll("");}
            var matcher=declaration.matcher(original);boolean declared=false;
            while(matcher.find()){
                declared=true;String target=original.substring(matcher.end()).split("(?iuU)[.,;!?\\n]|\\b(?:nhưng|nhung|tôi muốn|toi muon|thèm|muốn ăn|muon an)\\b",2)[0].strip();reports.add(target);
            }
            if(!declared&&reports.size()==1&&reports.getFirst().isBlank()&&original.strip().split("\\s+").length<=3)reports.add(original.strip());
        }
        return reports;
    }
    static String answer(ShopService shop,Store selected,List<Ingredient> ingredients,String message,List<String> previous,List<AiConversationTurn> conversation){
        String text=plain(message);
        List<Store> stores=shop.all(Store.class).stream().filter(s->s.active).sorted(Comparator.comparing(s->s.id)).toList();
        boolean branches=words(text,"chi nhanh|cua hang|store|shop|uteb");
        if(branches&&words(text,"gan (?:toi|day|nhat)|gan day|gan nha|gan vi tri"))
            return "Bạn bấm Đổi cửa hàng trong cửa sổ chat hoặc mục Cửa hàng trên menu, rồi chọn Dùng vị trí của tôi để xem các chi nhánh gần vị trí được bạn cho phép. Danh sách vẫn dùng được nếu bạn không cấp vị trí. Khoảng cách trên bản đồ là đường thẳng.\n\n"+storeInfo(selected);
        if(branches&&words(text,"danh sach|liet ke|tat ca|cac|nhung|bao nhieu|co may|chi nhanh khac|cua hang khac"))return storeList(stores);
        if(words(text,"voucher|ma giam|khuyen mai|uu dai|giam gia"))return promotions(shop,selected,text);
        if(words(text,"thanh toan|vnpay|momo|cod|tra tien"))
            return "UTEB hiện có COD (trả tiền khi nhận món) và VNPay Sandbox. Checkout không còn MoMo. Bạn chọn phương thức ở bước Thanh toán; VNPay cần thông tin kết nối sẵn sàng và xác nhận từ cổng thanh toán. Với giỏ nhiều chi nhánh, mỗi đơn được thanh toán riêng.\n\nNếu hỏi đơn của mình đã trả tiền chưa, hãy đăng nhập và mở Đơn của tôi → Xem chi tiết để đọc trạng thái thực tế.";
        if(words(text,"phi giao|phi van chuyen|mien phi giao|freeship")){
            List<String> carriers=shop.all(ShippingCarrier.class).stream().filter(c->c.active&&Objects.equals(c.storeId,selected.id)).map(c->c.name+" — "+money(c.fee)).toList();
            return "Phí giao được tính riêng theo từng chi nhánh và nhà vận chuyển bạn chọn. Đơn có tiền món từ 150.000đ tại một chi nhánh được miễn phí giao của chi nhánh đó. Đến lấy không có phí giao.\n\n"+selected.name+" hiện có phí giao nội bộ "+money(selected.deliveryFee)+"."+(carriers.isEmpty()?"":" Các dịch vụ đang hoạt động: "+String.join("; ",carriers)+".")+" Phí và ưu đãi cuối cùng hiển thị ở bước Thanh toán.";
        }
        if(words(text,"cach dat|dat mon the nao|dat hang the nao|dat mon nhu the nao|huong dan dat|lich su (?:mua|don)|don (?:cua )?toi"))
            return "Bạn chọn cửa hàng và Đến lấy hoặc Giao tận nơi, chọn món và nguyên liệu rồi thêm vào Giỏ hàng. Đăng nhập, kiểm tra người nhận/địa chỉ, thời gian nhận, nhà vận chuyển và mã ưu đãi trước khi xác nhận COD hoặc VNPay.\n\nĐể theo dõi đơn, mở Tài khoản → Đơn của tôi → Xem chi tiết. Trợ lý không tự đặt đơn hoặc thay đổi giỏ của bạn.";
        if(words(text,"dang ky|dang nhap|quen mat khau|doi mat khau|otp|xac thuc")){
            if(words(text,"shop|cua hang|ban hang|vendor"))return "Bạn đăng ký và xác thực tài khoản khách trước, vào Tài khoản → Đăng ký mở cửa hàng, nhập thông tin shop rồi gửi. Admin duyệt tại Duyệt cửa hàng. Sau khi được duyệt, đăng nhập lại để mở trang quản trị shop của mình.";
            return "Bạn bấm Đăng nhập → Tạo tài khoản, nhập thông tin và xác thực mã OTP gửi tới email. OTP gồm 6 chữ số, có hiệu lực 5 phút; mỗi lần gửi lại cách ít nhất 60 giây. Nếu quên mật khẩu, chọn Quên mật khẩu ở trang đăng nhập rồi làm theo email. Khi đã đăng nhập, có thể đổi mật khẩu trong Tài khoản.";
        }
        if(words(text,"dia chi|o dau|mo cua|dong cua|may gio|gio (?:mo|dong|hoat dong|phuc vu)|hotline|so dien thoai|lien he|giao hang|giao tan noi|den lay|thong tin (?:cua hang|chi nhanh)|ten cua hang")){
            String compact=text.replaceAll("\\s+","");
            List<Store> named=stores.stream().filter(s->{String key=plain(s.name).replaceAll("\\b(uteb|burrito|chi nhanh|cua hang)\\b","").replaceAll("\\s+","");return key.length()>=3&&compact.contains(key);}).toList();
            if(!named.isEmpty())return String.join("\n\n",named.stream().map(AiPublicKnowledge::storeInfo).toList());
            if(words(text,"chi nhanh")&&!words(text,"chi nhanh (?:nay|dang chon)|dang chon|hien tai"))return storeList(stores)+"\nBạn muốn hỏi thêm chi nhánh nào trong danh sách này?";
            return storeInfo(selected);
        }
        List<String> requests=requests(previous,conversation,message);
        if(hasAllergy(requests))return allergy(shop,selected,ingredients,requests);
        return null;
    }
    static String storeInfo(Store store){
        return store.name+"\nĐịa chỉ: "+Objects.toString(store.address,"Chưa cập nhật")+"\nGiờ phục vụ: "+Objects.toString(store.hours,"Chưa cập nhật")
            +(store.phone==null||store.phone.isBlank()?"":"\nĐiện thoại: "+store.phone)
            +"\nĐến lấy: "+(store.pickup?"có":"không")+"; giao tận nơi: "+(store.delivery?"có":"không")+".";
    }
    private static String storeList(List<Store> stores){
        if(stores.isEmpty())return "Hiện chưa có chi nhánh đang hoạt động trong dữ liệu cửa hàng.";
        StringBuilder result=new StringBuilder("UTEB hiện có "+stores.size()+" chi nhánh đang hoạt động:\n");int count=0;
        for(Store store:stores){String row="\n"+(count+1)+". "+storeInfo(store)+"\n";if(result.length()+row.length()>1750)break;result.append(row);count++;}
        if(count<stores.size())result.append("\nCòn ").append(stores.size()-count).append(" chi nhánh; mở mục Cửa hàng để xem đầy đủ.");
        return result+"\nBấm Đổi cửa hàng hoặc mục Cửa hàng để xem bản đồ và chọn chi nhánh. Việc hỏi danh sách không tự đổi cửa hàng đang chọn.";
    }
    private static String promotions(ShopService shop,Store selected,String text){
        if(words(text,"cua toi|cua minh|da doi|diem thuong"))return "Mã cá nhân và mã đổi điểm nằm trong Tài khoản hoặc Rewards sau khi bạn đăng nhập. Hãy mở các mục đó để xem ưu đãi thuộc tài khoản của mình; mã công khai được kiểm tra tại bước Thanh toán.";
        LocalDateTime now=LocalDateTime.now();
        List<Voucher> active=shop.all(Voucher.class).stream().filter(v->v.active&&v.userId==null&&(v.storeId==null||Objects.equals(v.storeId,selected.id))&&v.used<v.usageLimit&&v.startsAt.isBefore(now)&&v.endsAt.isAfter(now))
            .filter(v->v.productId==null||shop.all(Product.class).stream().anyMatch(p->p.id.equals(v.productId)&&p.available&&Objects.equals(p.storeId,selected.id))).sorted(Comparator.comparing(v->v.endsAt)).toList();
        if(active.isEmpty())return "Hiện chưa có mã ưu đãi công khai còn hiệu lực cho "+selected.name+" trong dữ liệu cửa hàng. Bạn có thể xem Rewards để kiểm tra mã đã đổi bằng điểm. Mã và số tiền giảm được kiểm tra lại ở bước Thanh toán.";
        StringBuilder result=new StringBuilder("Ưu đãi công khai hiện có tại "+selected.name+":\n");
        for(Voucher v:active.stream().limit(6).toList()){
            String discount="PERCENT".equals(v.type)?v.value.stripTrailingZeros().toPlainString()+"% tiền món":"SHIPPING".equals(v.type)?money(v.value)+" phí giao":money(v.value)+" tiền món";
            result.append("\n• ").append(v.code).append(": giảm ").append(discount).append("; tối thiểu ").append(money(v.minimum)).append("; giảm tối đa ").append(money(v.maxDiscount));
            if(v.productId!=null)result.append("; chỉ món ").append(shop.get(Product.class,v.productId).name);
            result.append(".\n");
        }
        return result+"\nMỗi checkout dùng một mã. Hãy chọn mã tại Thanh toán để xem số tiền thực tế và các điều kiện còn lại.";
    }
    static String allergy(ShopService shop,Store selected,List<Ingredient> ingredients,List<String> requests){
        List<String> relevant=allergyReports(requests);
        String original=String.join("\n",relevant).toLowerCase(Locale.ROOT),text=plain(original);List<String> labels=new ArrayList<>();
        if(Pattern.compile("(?iuU)\\b(?:bò|bo|beef)\\b").matcher(original).find())labels.add("bò");
        if(words(text,"sua|lactose|milk|dairy"))labels.add("sữa");
        if(words(text,"dau nanh|soy|tofu"))labels.add("đậu nành");
        if(words(text,"trung|egg"))labels.add("trứng");
        if(words(text,"dau phong|lac|peanut"))labels.add("đậu phộng");
        if(words(text,"lua mi|bot mi|gluten|wheat"))labels.add("lúa mì hoặc gluten");
        if(original.contains("bơ")||words(text,"avocado|guacamole"))labels.add("bơ");
        if(labels.isEmpty()){
            String supplied=relevant.stream().filter(s->!s.isBlank()).reduce((first,last)->last).orElse("");
            if(!supplied.isBlank())labels.add(supplied.substring(0,Math.min(supplied.length(),80)));
            else return "Bạn dị ứng hoặc không dung nạp nguyên liệu nào? Cho mình biết tên nguyên liệu để đối chiếu các mục đã được ghi trên thực đơn. Dữ liệu chưa đầy đủ về phần nền và cách chuẩn bị chung, nên cần xác nhận trực tiếp với cửa hàng trước khi chọn món.";
        }
        List<String> knownIngredients=ingredients.stream().filter(i->i.available&&labels.stream().anyMatch(label->allergenMatches(i.name+" "+i.allergens,label))).map(i->i.name).distinct().toList();
        List<String> knownMeals=shop.all(Product.class).stream().filter(p->p.available&&Objects.equals(p.storeId,selected.id)&&labels.stream().anyMatch(label->allergenMatches(p.name+" "+p.description,label))).map(p->p.name).distinct().limit(6).toList();
        String answer="Mình ghi nhận bạn dị ứng hoặc không dung nạp "+String.join(", ",labels)+". Tại "+selected.name+", những mục có ghi rõ liên quan trong dữ liệu hiện có là:";
        answer+=knownIngredients.isEmpty()?"\n• Nhân/nguyên liệu: chưa có nhãn đủ rõ để xác định.":"\n• Nhân/nguyên liệu: "+String.join(", ",knownIngredients)+".";
        if(!knownMeals.isEmpty())answer+="\n• Tên món hoặc mô tả: "+String.join(", ",knownMeals)+".";
        return answer+"\n\nĐây chưa phải danh sách chất gây dị ứng đầy đủ. Mình chưa thể khẳng định món khác phù hợp với bạn vì phần nền và cách chuẩn bị chung chưa được xác nhận. Bạn hãy xác nhận trực tiếp với cửa hàng"+(selected.phone==null||selected.phone.isBlank()?".":" qua số "+selected.phone+".");
    }
    private static boolean allergenMatches(String original,String label){String text=plain(original);
        return switch(label){
            case "bò"->Pattern.compile("(?iuU)\\b(?:bò|bo|beef)\\b").matcher(original).find();
            case "bơ"->original.toLowerCase(Locale.ROOT).contains("bơ")||words(text,"avocado|guacamole");
            case "sữa"->words(text,"sua|pho mai|kem chua|cheese|milk|lactose");
            case "đậu nành"->words(text,"dau nanh|dau hu|soy|tofu");
            case "trứng"->words(text,"trung|egg");
            case "đậu phộng"->words(text,"dau phong|lac|peanut");
            case "lúa mì hoặc gluten"->words(text,"lua mi|bot mi|gluten|wheat");
            default->words(text,Pattern.quote(plain(label)));
        };
    }
    private static String money(BigDecimal amount){return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount)+"đ";}
}
