package vn.uteb;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import static vn.uteb.ShopService.*;

@RestController
@RequestMapping({"/shop-api/public/ai","/api/public/ai"})
class AiMenuController {
    private final AiMenuService service;
    private final int dailyLimit;
    private final Semaphore concurrency=new Semaphore(1);
    private final Map<String,Long> recent=new HashMap<>();
    private LocalDate day=LocalDate.now(ZoneOffset.UTC);
    private int dailyCount;
    AiMenuController(AiMenuService service,@Value("${uteb.ai.daily-limit:1000}") int dailyLimit) {
        this.service=service; this.dailyLimit=dailyLimit;
    }
    @GetMapping("/status") Map<String,Object> status() { return Map.of("available",dailyLimit>0&&service.ready()); }

    @PostMapping("/recommend") Map<String,Object> recommend(@RequestBody Map<String,Object> body,HttpServletRequest request) {
        String message=str(body,"message");
        require(!message.isBlank()&&message.length()<=1000,"Hãy mô tả nhu cầu ăn uống trong 1–1.000 ký tự.");
        long storeId=number(body,"storeId");
        require(storeId>0,"Vui lòng chọn chi nhánh.");
        List<String> previous=new ArrayList<>();
        Object context=body.get("previousMessages");
        if(context!=null) {
            require(context instanceof List<?>,"Nội dung trao đổi không hợp lệ.");
            List<?> values=(List<?>)context;
            require(values.size()<=4,"Tối đa 4 yêu cầu trước đó.");
            for(Object value:values) {
                require(value instanceof String&&!((String)value).isBlank()&&((String)value).length()<=1000,"Nội dung trao đổi không hợp lệ.");
                previous.add((String)value);
            }
        }
        List<AiConversationTurn> conversation=new ArrayList<>();
        Object history=body.get("conversation");
        if(history!=null){
            require(history instanceof List<?> && ((List<?>)history).size()<=4,"Tối đa 4 lượt trao đổi gần nhất.");
            for(Object raw:(List<?>)history){
                require(raw instanceof Map<?,?>,"Lịch sử trò chuyện không hợp lệ.");Map<?,?> turn=(Map<?,?>)raw;
                require(turn.get("user") instanceof String&&turn.get("answer") instanceof String,"Lịch sử trò chuyện không hợp lệ.");
                String user=(String)turn.get("user"),answer=(String)turn.get("answer");
                require(!user.isBlank()&&user.length()<=1000&&!answer.isBlank()&&answer.length()<=2000,"Lượt trò chuyện quá dài hoặc trống.");
                Object names=turn.get("dishes");List<String> dishes=new ArrayList<>();
                if(names!=null){require(names instanceof List<?> && ((List<?>)names).size()<=3,"Tối đa 3 món trong lượt trước.");for(Object name:(List<?>)names){require(name instanceof String&&!((String)name).isBlank()&&((String)name).length()<=255,"Tên món trong lịch sử không hợp lệ.");dishes.add((String)name);}}
                conversation.add(new AiConversationTurn(user,answer,dishes));
            }
        }
        if(!service.configured()) throw MenuAiClient.unavailable("Trợ lý AI chưa sẵn sàng. Vui lòng chọn món từ thực đơn hoặc quay lại sau.");
        if(!concurrency.tryAcquire()) throw busy();
        try { reserve(request.getRemoteAddr()); return service.recommend(storeId,message,previous,conversation); }
        finally { concurrency.release(); }
    }
    synchronized void reserve(String address) {
        long now=System.currentTimeMillis();
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        if(!today.equals(day)) { day=today; dailyCount=0; }
        recent.entrySet().removeIf(e->now-e.getValue()>=1000);
        if(dailyCount>=dailyLimit||recent.size()>=10000||recent.containsKey(address)) throw busy();
        recent.put(address,now); dailyCount++;
    }
    static ResponseStatusException busy() { return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Trợ lý đang bận hoặc đã đạt hạn mức. Bạn hãy đợi một lúc rồi thử lại."); }
}
