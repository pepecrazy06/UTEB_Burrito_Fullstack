package vn.uteb;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static vn.uteb.Domain.Store;
import static vn.uteb.ShopService.str;

/** Existing stores share Vietnam business time and one recurring daily interval. */
@Component
public class SchedulingPolicy {
    static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern HOURS=Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)\\s*[-–—]\\s*([01]?\\d|2[0-3]):([0-5]\\d)$");
    @org.springframework.beans.factory.annotation.Autowired BusinessSettings rules;
    private final Clock clock;
    private int rule(String name){return rules==null?Integer.parseInt(BusinessSettings.definition(name).initial()):rules.integer(name);}
    public SchedulingPolicy(){this(Clock.system(ZONE));}
    SchedulingPolicy(Clock clock){this.clock=clock.withZone(ZONE);}
    record Hours(LocalTime opens,LocalTime closes){
        boolean contains(LocalTime time){
            return opens.isBefore(closes)?!time.isBefore(opens)&&time.isBefore(closes):!time.isBefore(opens)||time.isBefore(closes);
        }
    }
    private ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}

    /** Legacy clients may omit scheduleMode and submit scheduledAt directly. */
    String value(Map<String,Object> body){
        String mode=str(body,"scheduleMode"),value=str(body,"scheduledAt");
        if(!mode.isEmpty()&&!mode.equals("ASAP")&&!mode.equals("SCHEDULE"))throw invalid("Lựa chọn thời gian nhận món không hợp lệ.");
        if(mode.equals("ASAP"))return "ASAP";
        if(mode.equals("SCHEDULE")&&(value.isEmpty()||value.equals("ASAP")))throw invalid("Vui lòng chọn ngày và giờ hẹn theo giờ Việt Nam.");
        return value.isEmpty()?"ASAP":value;
    }
    LocalDateTime scheduledTime(String value){
        if("ASAP".equals(value))return null;
        LocalDateTime time;
        try{time=LocalDateTime.parse(value);}catch(DateTimeParseException e){throw invalid("Ngày giờ hẹn không hợp lệ. Dùng định dạng YYYY-MM-DDTHH:mm theo giờ Việt Nam, không kèm múi giờ.");}
        LocalDateTime now=LocalDateTime.now(clock);
        if(!time.isAfter(now))throw invalid("Không thể đặt món vào thời gian đã qua. Vui lòng chọn thời gian mới.");
        if(!time.isAfter(now.plusMinutes(rule("schedule_lead_minutes"))))throw invalid("Giờ hẹn cần sau thời điểm hiện tại hơn "+rule("schedule_lead_minutes")+" phút để chuẩn bị món. Vui lòng chọn lại.");
        if(!time.isBefore(now.plusDays(rule("schedule_max_days"))))throw invalid("Giờ hẹn phải trong vòng "+rule("schedule_max_days")+" ngày tới, không nhận đúng mốc giới hạn. Vui lòng chọn ngày gần hơn.");
        return time;
    }
    Hours hours(String value){
        var match=HOURS.matcher(value==null?"":value.trim());
        if(!match.matches())throw invalid("Giờ hoạt động cần có dạng HH:mm–HH:mm (ví dụ 08:00–21:00), có thể qua nửa đêm.");
        LocalTime opens=LocalTime.of(Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2)));
        LocalTime closes=LocalTime.of(Integer.parseInt(match.group(3)),Integer.parseInt(match.group(4)));
        if(opens.equals(closes))throw invalid("Giờ mở cửa và đóng cửa phải khác nhau; chưa hỗ trợ cấu hình 24 giờ.");
        return new Hours(opens,closes);
    }
    void validate(Store store,LocalDateTime time){
        if(time==null)return; // Preserve the existing ASAP rules.
        if(!store.active)throw invalid("Chi nhánh “"+store.name+"” đang ngừng nhận đơn. Vui lòng chọn chi nhánh khác.");
        Hours hours;
        try{hours=hours(store.hours);}catch(ResponseStatusException e){throw invalid("Chi nhánh “"+store.name+"” chưa có giờ hoạt động hợp lệ. Chưa thể đặt hẹn; vui lòng chọn chi nhánh khác hoặc liên hệ cửa hàng.");}
        if(!hours.contains(time.toLocalTime())){
            String reason=hours.opens.isBefore(hours.closes)&&time.toLocalTime().isBefore(hours.opens)?" chưa mở cửa vào giờ bạn chọn.":" không nhận đơn vào giờ bạn chọn.";
            throw invalid("Chi nhánh “"+store.name+"”"+reason+" Giờ nhận đơn: "+hours.opens+"–"+hours.closes
                    +(hours.opens.isAfter(hours.closes)?" ngày hôm sau":"")+" (giờ Việt Nam), không nhận đúng giờ đóng cửa. Vui lòng chọn lại giờ hẹn.");
        }
    }
}
