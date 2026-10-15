package vn.uteb;
import org.springframework.stereotype.Component;
import org.springframework.boot.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.*;
import org.springframework.context.annotation.Configuration;
import java.time.*;
import jakarta.persistence.LockModeType;
import static vn.uteb.Domain.*;
@Configuration @EnableScheduling class Scheduling {}
@Component class BootstrapAdmin implements ApplicationRunner {
 final ShopService s;final String email,password;BootstrapAdmin(ShopService s,@Value("${ADMIN_EMAIL:}")String email,@Value("${ADMIN_PASSWORD:}")String password){this.s=s;this.email=email;this.password=password;}
 @Transactional public void run(ApplicationArguments args){if(!s.demoMode()&&s.all(Account.class).isEmpty()){ShopService.require(!email.isBlank()&&!password.isBlank(),"DB mới cần ADMIN_EMAIL và ADMIN_PASSWORD để tạo quản trị viên.");ShopService.passwordValid(password);Account a=new Account();a.email=email.toLowerCase(java.util.Locale.ROOT);a.name="Quản trị UTEB";a.roles="ADMIN";a.verified=true;a.passwordHash=s.hashPassword(password);s.save(a);}}
}
@Component class PaymentExpiry {
 final ShopService s;PaymentExpiry(ShopService s){this.s=s;}
 @Scheduled(fixedDelay=60000,initialDelay=60000) @Transactional public void expire(){s.expirePayments();}
}
