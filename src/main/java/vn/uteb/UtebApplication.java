package vn.uteb;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class UtebApplication { @jakarta.annotation.PostConstruct void applicationTimeZone(){java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));} public static void main(String[] args) { SpringApplication.run(UtebApplication.class,args); } }
