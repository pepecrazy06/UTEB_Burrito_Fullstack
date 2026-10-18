package vn.uteb;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Validate shared-runtime configuration before any DataSource or runner is created. */
public class SqlServerEnvironment implements EnvironmentPostProcessor, Ordered {
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("sqlserver")) {
            if (Arrays.asList(environment.getActiveProfiles()).contains("demo")) return;
            throw new IllegalStateException("Bản chạy chung cần profile sqlserver; không để trống SPRING_PROFILES_ACTIVE. Demo chỉ chạy khi chọn rõ profile demo.");
        }
        if (Arrays.asList(environment.getActiveProfiles()).contains("demo")) {
            throw new IllegalStateException("Không chạy đồng thời profile sqlserver và demo.");
        }
        String auth = environment.getProperty("DB_AUTH", "sql");
        if (!auth.equals("sql") && !auth.equals("windows")) {
            throw new IllegalStateException("DB_AUTH phải là sql hoặc windows.");
        }
        boolean windows = auth.equals("windows");
        for (String key : windows ? new String[]{"DB_URL"} : new String[]{"DB_URL", "DB_USER", "DB_PASSWORD"}) {
            if (environment.getProperty(key, "").isBlank()) {
                throw new IllegalStateException("SQL Server thiếu " + key
                        + ". Đặt biến trong IntelliJ Run Configuration hoặc terminal; .env không tự được nạp.");
            }
        }
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:sqlserver://")
                || !url.equals(environment.getProperty("DB_URL"))) {
            throw new IllegalStateException("spring.datasource.url phải dùng DB_URL với jdbc:sqlserver://.");
        }
        if (windows) {
            if (!java.util.regex.Pattern.compile("(?i);\\s*integratedSecurity\\s*=\\s*true\\s*(;|$)").matcher(url).find()
                    || !java.util.regex.Pattern.compile("(?i);\\s*authenticationScheme\\s*=\\s*NativeAuthentication\\s*(;|$)").matcher(url).find()) {
                throw new IllegalStateException("DB_AUTH=windows cần integratedSecurity=true;authenticationScheme=NativeAuthentication trong DB_URL.");
            }
            for (String key : new String[]{"DB_USER", "DB_PASSWORD"}) {
                if (!environment.getProperty(key, "").isEmpty()) {
                    throw new IllegalStateException("DB_AUTH=windows: bỏ biến " + key + " để dùng tài khoản Windows hiện tại.");
                }
            }
        }
        expect(environment, "spring.datasource.username", environment.getProperty("DB_USER", ""));
        expect(environment, "spring.datasource.password", environment.getProperty("DB_PASSWORD", ""));
        expect(environment, "spring.datasource.driver-class-name", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        expect(environment, "spring.jpa.hibernate.ddl-auto", "validate");
        expect(environment, "spring.sql.init.mode", "never");
        expect(environment, "uteb.demo", "false");
        expect(environment, "uteb.seed", "false");
    }

    private static void expect(ConfigurableEnvironment environment, String key, String expected) {
        if (!expected.equals(environment.getProperty(key))) {
            // Only the property name is reported, never credential values.
            throw new IllegalStateException("Cấu hình SQL Server không hợp lệ: " + key
                    + ". Xem SQLSERVER_RUNTIME.md.");
        }
    }
}
