package vn.uteb;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

/** Real SMTP transport against a local test server; never contacts a mail provider. */
class OtpSmtpTransportTest {
    @Test void otpIsSentOverSmtpAndOnlyHashIsKeptOnAccount() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(5000);
            Future<String> received = executor.submit(() -> receiveMail(server));
            var sender = new JavaMailSenderImpl();
            sender.setHost(server.getInetAddress().getHostAddress()); sender.setPort(server.getLocalPort());
            sender.setDefaultEncoding("UTF-8");
            sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "3000");
            sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "3000");
            var encoder = new BCryptPasswordEncoder();
            var service = new ShopService(encoder, null, sender, false, true, "sender@example.com");
            var account = new Domain.Account(); account.email = "recipient@example.com";
            service.sendOtp(account, "VERIFY");
            String raw = received.get(5, TimeUnit.SECONDS);
            var message = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
            assertThat(message.getFrom()[0].toString()).isEqualTo("sender@example.com");
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo(account.email);
            assertThat(message.getSubject()).contains("xác thực tài khoản");
            String body = message.getContent().toString();
            var code = Pattern.compile("\\b[0-9]{6}\\b").matcher(body);
            assertThat(code.find()).isTrue();
            assertThat(encoder.matches(code.group(), account.otpHash)).isTrue();
            assertThat(account.otpHash).isNotEqualTo(code.group());
            assertThat(account.otpExpires).isEqualTo(account.otpSent.plusMinutes(5));
        }
    }

    @Test void sqlModeCannotFallBackToLocalMailboxWhenEmailIsDisabled() {
        var service = new ShopService(new BCryptPasswordEncoder(), null, new JavaMailSenderImpl(), false, false, "sender@example.com");
        var account = new Domain.Account(); account.email = "recipient@example.com";
        assertThatThrownBy(() -> service.sendOtp(account, "VERIFY"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
    }

    private String receiveMail(ServerSocket server) throws Exception {
        try (var socket = server.accept()) {
            socket.setSoTimeout(5000);
            var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            var output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            reply(output, "220 localhost test SMTP");
            StringBuilder message = new StringBuilder();
            for (String line; (line = input.readLine()) != null;) {
                if (line.startsWith("EHLO") || line.startsWith("HELO")) reply(output, "250 localhost");
                else if (line.startsWith("MAIL FROM:") || line.startsWith("RCPT TO:")) reply(output, "250 OK");
                else if (line.equals("DATA")) {
                    reply(output, "354 End with dot");
                    while ((line = input.readLine()) != null && !line.equals("."))
                        message.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                    reply(output, "250 Accepted");
                } else if (line.equals("QUIT")) {reply(output, "221 Bye"); break;}
                else throw new IOException("Unexpected SMTP command in test");
            }
            return message.toString();
        }
    }

    private void reply(BufferedWriter output, String text) throws IOException {
        output.write(text + "\r\n"); output.flush();
    }
}
