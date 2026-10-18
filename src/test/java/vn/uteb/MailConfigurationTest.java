package vn.uteb;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.assertj.core.api.Assertions.*;

class MailConfigurationTest {
    @Test void sqlRuntimeCanStartWithEmailDisabledButCannotPretendToSend() {
        var environment = new MockEnvironment().withProperty("uteb.demo", "false");
        assertThatCode(() -> new MailConfiguration(environment, new JavaMailSenderImpl()).run(null)).doesNotThrowAnyException();
    }

    @Test void enabledMailRejectsPlaceholderHostWithoutDisclosingPassword() {
        var sender = new JavaMailSenderImpl();
        sender.setHost("smtp.example.com"); sender.setPassword("never-show-this-test-secret");
        assertThatThrownBy(() -> new MailConfiguration(enabled(), sender).run(null))
            .hasMessageContaining("SMTP_HOST").hasMessageNotContaining("never-show-this-test-secret");
    }

    @Test void enabledMailRequiresCredentialsInCurrentRunEnvironment() {
        var sender = new JavaMailSenderImpl(); sender.setHost("smtp.test.invalid");
        assertThatThrownBy(() -> new MailConfiguration(enabled(), sender).run(null))
            .hasMessageContaining("SMTP_USER/SMTP_PASSWORD");
    }

    @Test void configurationValidationDoesNotContactExternalSmtp() {
        var sender = new JavaMailSenderImpl(); sender.setHost("smtp.test.invalid");
        sender.setUsername("sender@example.com"); sender.setPassword("private-test-value");
        assertThatCode(() -> new MailConfiguration(enabled(), sender).run(null)).doesNotThrowAnyException();
    }

    private MockEnvironment enabled() {
        return new MockEnvironment().withProperty("uteb.mail.enabled", "true");
    }
}
