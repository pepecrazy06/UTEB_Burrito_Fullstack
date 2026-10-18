package vn.uteb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.support.TestPropertySourceUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SqlServerEnvironmentTest {
    private final SqlServerEnvironment validator = new SqlServerEnvironment();

    private MockEnvironment configured() {
        MockEnvironment env = new MockEnvironment();
        TestPropertySourceUtils.addPropertiesFilesToEnvironment(env, new org.springframework.core.io.DefaultResourceLoader(),
                "classpath:application.properties", "classpath:application-sqlserver.properties");
        // Model environment/command-line overrides above the properties files.
        env.getPropertySources().addFirst(env.getPropertySources().get("mockProperties"));
        env.setActiveProfiles("sqlserver");
        return env.withProperty("DB_URL", "jdbc:sqlserver://localhost:1433;databaseName=uteb")
                .withProperty("DB_USER", "test-user").withProperty("DB_PASSWORD", "test-password");
    }

    @Test void sharedDefaultsAndRegistrationArePresent() {
        MockEnvironment env = configured();
        assertThat(env.getProperty("spring.profiles.active")).isEqualTo("sqlserver");
        assertThat(SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class, getClass().getClassLoader()))
                .contains(SqlServerEnvironment.class.getName());
        assertThatCode(() -> validator.postProcessEnvironment(env, new SpringApplication()))
                .doesNotThrowAnyException();
    }

    private MockEnvironment windowsConfigured() {
        return configured().withProperty("DB_AUTH", "windows")
                .withProperty("DB_URL", "jdbc:sqlserver://localhost:1433;databaseName=UTE_Burrito;integratedSecurity=true;authenticationScheme=NativeAuthentication;encrypt=true;trustServerCertificate=true")
                .withProperty("DB_USER", "").withProperty("DB_PASSWORD", "");
    }

    @Test void windowsAuthenticationDoesNotRequireSqlCredentials() {
        assertThatCode(() -> validator.postProcessEnvironment(windowsConfigured(), new SpringApplication()))
                .doesNotThrowAnyException();
    }

    @Test void windowsAuthenticationRequiresIntegratedSecurity() {
        var env = windowsConfigured().withProperty("DB_URL", "jdbc:sqlserver://localhost:1433;databaseName=UTE_Burrito");
        assertThatThrownBy(() -> validator.postProcessEnvironment(env, new SpringApplication()))
                .hasMessageContaining("integratedSecurity=true");
    }

    @Test void windowsAuthenticationRejectsLeftoverSqlCredentials() {
        var env = windowsConfigured().withProperty("DB_PASSWORD", "secret-not-to-log");
        assertThatThrownBy(() -> validator.postProcessEnvironment(env, new SpringApplication()))
                .hasMessageContaining("DB_PASSWORD").hasMessageNotContaining("secret-not-to-log");
    }

    @Test void unknownAuthenticationModeFailsClearly() {
        var env = configured().withProperty("DB_AUTH", "unknown");
        assertThatThrownBy(() -> validator.postProcessEnvironment(env, new SpringApplication()))
                .hasMessageContaining("DB_AUTH");
    }

    @ParameterizedTest @ValueSource(strings = {"DB_URL", "DB_USER", "DB_PASSWORD"})
    void missingOrBlankConnectionVariablesFailBeforeDatabaseAccess(String key) {
        MockEnvironment env = configured().withProperty(key, " ");
        assertThatThrownBy(() -> validator.postProcessEnvironment(env, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(key)
                .hasMessageNotContaining("test-password");
    }

    @ParameterizedTest @ValueSource(strings = {"jdbc:h2:mem:forbidden", "jdbc:mysql://localhost/test"})
    void sqlServerProfileRejectsAnotherDatabase(String url) {
        MockEnvironment env = configured().withProperty("DB_URL", url);
        assertThatThrownBy(() -> validator.postProcessEnvironment(env, new SpringApplication()))
                .hasMessageContaining("jdbc:sqlserver://");
    }

    @ParameterizedTest @ValueSource(strings = {"spring.datasource.url", "spring.datasource.username",
            "spring.datasource.password", "spring.datasource.driver-class-name", "spring.jpa.hibernate.ddl-auto",
            "spring.sql.init.mode", "uteb.demo", "uteb.seed"})
    void unsafeOverridesCannotChangeTheSharedRuntime(String key) {
        MockEnvironment env = configured().withProperty(key, "unsafe-override");
        assertThatThrownBy(() -> validator.postProcessEnvironment(env, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(key)
                .hasMessageNotContaining("test-password").hasMessageNotContaining("unsafe-override");
    }

    @Test void demoCannotBeCombinedWithSqlServer() {
        MockEnvironment env = configured();
        env.setActiveProfiles("demo", "sqlserver");
        assertThatThrownBy(() -> validator.postProcessEnvironment(env, new SpringApplication()))
                .hasMessageContaining("đồng thời");
    }

    @Test void explicitDemoTestsDoNotRequireSqlServerCredentials() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("demo");
        assertThatCode(() -> validator.postProcessEnvironment(env, new SpringApplication()))
                .doesNotThrowAnyException();
    }

    @Test void blankProfileCannotFallBackToAnEmbeddedDatabase() {
        assertThatThrownBy(() -> validator.postProcessEnvironment(new MockEnvironment(), new SpringApplication()))
                .hasMessageContaining("profile sqlserver");
    }

    @Test void misspelledProfileCannotFallBackToAnEmbeddedDatabase() {
        var env = new MockEnvironment();
        env.setActiveProfiles("sqlsever");
        assertThatThrownBy(() -> validator.postProcessEnvironment(env, new SpringApplication()))
                .hasMessageContaining("profile sqlserver");
    }

    @Test void nonDemoSeedDoesNotEvenReadOrWriteDatabase() {
        ShopService shop = mock(ShopService.class);
        new SeedData(shop, true, false).run(null);
        verifyNoInteractions(shop);
    }

    @Test void emptyDatabaseRequiresExplicitAdminCredentials() {
        ShopService shop = mock(ShopService.class);
        assertThatThrownBy(() -> new BootstrapAdmin(shop, "", "").run(null))
                .hasMessageContaining("ADMIN_EMAIL");
        verify(shop, never()).save(any());
    }

    @Test void bootstrapCreatesOnlyOneVerifiedAdminWithHashedPassword() {
        ShopService shop = mock(ShopService.class);
        when(shop.hashPassword("Admin-test-123")).thenReturn("encoded-password");
        new BootstrapAdmin(shop, "ADMIN@example.com", "Admin-test-123").run(null);
        var account = org.mockito.ArgumentCaptor.forClass(Domain.Account.class);
        verify(shop).save(account.capture());
        assertThat(account.getValue().email).isEqualTo("admin@example.com");
        assertThat(account.getValue().roles).isEqualTo("ADMIN");
        assertThat(account.getValue().verified).isTrue();
        assertThat(account.getValue().passwordHash).isEqualTo("encoded-password");
    }

    @Test void existingAccountsAreNeverReplacedByBootstrap() {
        ShopService shop = mock(ShopService.class);
        when(shop.all(Domain.Account.class)).thenReturn(java.util.List.of(new Domain.Account()));
        new BootstrapAdmin(shop, "admin@example.com", "Admin-test-123").run(null);
        verify(shop, never()).save(any());
        verify(shop, never()).hashPassword(any());
    }

    @Test void realStartupRejectsMissingCredentialsBeforeContextCreation() {
        assertThatThrownBy(() -> SpringApplication.run(UtebApplication.class,
                "--spring.profiles.active=sqlserver", "--DB_URL=", "--DB_USER=", "--DB_PASSWORD="))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DB_URL");
    }
}
