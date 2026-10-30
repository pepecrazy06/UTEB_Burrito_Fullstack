package vn.uteb;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Bounded, per-account and per-address login budget. No proxy headers are trusted. */
@Service
public class LoginThrottle {
    private final Map<String,Bucket> buckets=new HashMap<>();
    private final Clock clock;private final int accountLimit,ipLimit,minutes;
    private record Bucket(int count,Instant expires){}
    @org.springframework.beans.factory.annotation.Autowired
    public LoginThrottle(@Value("${uteb.security.login.account-limit:5}")int accountLimit,@Value("${uteb.security.login.ip-limit:30}")int ipLimit,@Value("${uteb.security.login.window-minutes:15}")int minutes){this(Clock.systemUTC(),accountLimit,ipLimit,minutes);}
    LoginThrottle(Clock clock,int accountLimit,int ipLimit,int minutes){if(accountLimit<1||ipLimit<accountLimit||minutes<1)throw new IllegalArgumentException("Cấu hình giới hạn đăng nhập không hợp lệ.");this.clock=clock;this.accountLimit=accountLimit;this.ipLimit=ipLimit;this.minutes=minutes;}
    private String account(String email){return "account:"+email.trim().toLowerCase(Locale.ROOT);}
    private ResponseStatusException blocked(){return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau "+minutes+" phút.");}
    synchronized void reserve(String email,String ip){
        Instant now=clock.instant();buckets.values().removeIf(b->!b.expires.isAfter(now));
        String user=account(email),address="ip:"+ip;
        if(buckets.size()>=10000&&!buckets.containsKey(user))throw blocked();
        for(String key:List.of(user,address)){Bucket b=buckets.get(key);if(b!=null&&b.count>=(key.equals(user)?accountLimit:ipLimit))throw blocked();}
        for(String key:List.of(user,address)){Bucket b=buckets.get(key);buckets.put(key,new Bucket(b==null?1:b.count+1,b==null?now.plus(Duration.ofMinutes(minutes)):b.expires));}
    }
    synchronized void success(String email,String ip){buckets.remove(account(email));String key="ip:"+ip;Bucket b=buckets.get(key);if(b!=null){if(b.count<=1)buckets.remove(key);else buckets.put(key,new Bucket(b.count-1,b.expires));}}
    synchronized void clear(){buckets.clear();}
}
