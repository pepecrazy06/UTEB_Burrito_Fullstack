package vn.uteb;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/** Diagnose configuration without printing credentials or connecting at startup. */
@Component
class MailConfiguration implements ApplicationRunner {
    private final Environment environment;
    private final JavaMailSender sender;

    MailConfiguration(Environment environment, JavaMailSender sender) {
        this.environment = environment;
        this.sender = sender;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.getProperty("uteb.mail.enabled", Boolean.class, false)) {
            if (!environment.getProperty("uteb.demo", Boolean.class, false)) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "Email disabled: set MAIL_ENABLED=true and SMTP_HOST/SMTP_PORT/SMTP_USER/SMTP_PASSWORD. See SMTP_SETUP.md; .env is not loaded automatically.");
            }
            return;
        }
        if (sender instanceof JavaMailSenderImpl smtp) {
            if (smtp.getHost() == null || smtp.getHost().isBlank() || smtp.getHost().equals("smtp.example.com")) {
                throw new IllegalStateException("Email enabled but SMTP_HOST is missing or still a placeholder. See SMTP_SETUP.md.");
            }
            if (environment.getProperty("spring.mail.properties.mail.smtp.auth", Boolean.class, true)
                    && (smtp.getUsername() == null || smtp.getUsername().isBlank()
                        || smtp.getPassword() == null || smtp.getPassword().isBlank())) {
                throw new IllegalStateException("Email enabled but SMTP_USER/SMTP_PASSWORD is missing. Set them in the SAME terminal or IntelliJ Run Configuration. See SMTP_SETUP.md.");
            }
        }
    }
}
