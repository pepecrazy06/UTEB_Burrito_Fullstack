package vn.uteb;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.*;
import static org.assertj.core.api.Assertions.*;

class LocalConfigurationTest {
    @TempDir Path directory;
    static final String URL="jdbc:sqlserver://localhost:1433;databaseName=uteb;encrypt=true;trustServerCertificate=true";
    static final String PASSWORD=" p:a=ss\\word !#\t\nMật-khẩu ";
    static final String CLOUD_SECRET="local-test-cloud-secret";
    static final Set<String> VARIABLES=Set.of("DB_AUTH","DB_URL","DB_USER","DB_PASSWORD","JWT_SECRET",
        "CLOUDINARY_CLOUD","CLOUDINARY_KEY","CLOUDINARY_SECRET","BASE_URL","COOKIE_SECURE","PORT",
        "MAIL_ENABLED","SMTP_HOST","SMTP_PORT","SMTP_USER","SMTP_PASSWORD","MAIL_FROM",
        "VNPAY_TMN_CODE","VNPAY_HASH_SECRET","MOMO_PARTNER_CODE","MOMO_ACCESS_KEY","MOMO_SECRET_KEY");

    ApplicationContextRunner configuration(Path file,String profile){
        return new ApplicationContextRunner()
            // These tests must not read a developer's real service credentials.
            .withInitializer(context->{
                context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            })
            .withPropertyValues("spring.profiles.active="+profile,"UTEB_LOCAL_CONFIG="+file.toUri())
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withInitializer(context->new SqlServerEnvironment().postProcessEnvironment(context.getEnvironment(),new SpringApplication()));
    }
    Path saveSample()throws IOException{
        Properties properties=new Properties();
        properties.setProperty("DB_AUTH","sql");properties.setProperty("DB_URL",URL);
        properties.setProperty("DB_USER","sa");properties.setProperty("DB_PASSWORD",PASSWORD);
        properties.setProperty("CLOUDINARY_CLOUD","local-test-cloud");properties.setProperty("CLOUDINARY_KEY","123456789");
        properties.setProperty("CLOUDINARY_SECRET",CLOUD_SECRET);properties.setProperty("JWT_SECRET","a".repeat(64));
        properties.setProperty("COOKIE_SECURE","false");
        Path file=directory.resolve(".env.local.properties");
        try(OutputStream output=Files.newOutputStream(file)){properties.store(output,"Test values only");}
        return file;
    }
    @Test void sqlProfileAutomaticallyImportsSavedCredentialsWithoutEnvironmentVariables()throws Exception{
        configuration(saveSample(),"sqlserver").run(context->{
            assertThat(context).hasNotFailed();var env=context.getEnvironment();
            assertThat(env.getProperty("spring.datasource.url")).isEqualTo(URL);
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("sa");
            assertThat(env.getProperty("spring.datasource.password")).isEqualTo(PASSWORD);
            assertThat(env.getProperty("uteb.cloudinary.cloud")).isEqualTo("local-test-cloud");
            assertThat(env.getProperty("uteb.cloudinary.key")).isEqualTo("123456789");
            assertThat(env.getProperty("uteb.cloudinary.secret")).isEqualTo(CLOUD_SECRET);
            assertThat(env.getProperty("server.servlet.session.cookie.secure")).isEqualTo("false");
            assertThat(env.getProperty("uteb.seed")).isEqualTo("false");
        });
    }
    @Test void environmentOverridesSavedCredentialsWithoutRewritingTheFile()throws Exception{
        Path file=saveSample();byte[] before=Files.readAllBytes(file);
        configuration(file,"sqlserver").withInitializer(context->context.getEnvironment().getPropertySources()
            .addFirst(new SystemEnvironmentPropertySource("testRuntimeEnvironment",Map.of("DB_PASSWORD","runtime-override","CLOUDINARY_SECRET","cloud-override"))))
            .run(context->{
                assertThat(context).hasNotFailed();var env=context.getEnvironment();
                assertThat(env.getProperty("spring.datasource.password")).isEqualTo("runtime-override");
                assertThat(env.getProperty("uteb.cloudinary.secret")).isEqualTo("cloud-override");
            });
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
    }
    @Test void demoTestsNeverImportSavedSqlOrCloudinarySecrets()throws Exception{
        configuration(saveSample(),"demo").run(context->{
            assertThat(context).hasNotFailed();var env=context.getEnvironment();
            assertThat(env.getProperty("DB_PASSWORD")).isNull();
            assertThat(env.getProperty("uteb.cloudinary.secret")).isEmpty();
            assertThat(env.getProperty("spring.datasource.url")).startsWith("jdbc:h2:");
        });
    }
    @Test void absentLocalFileStillAllowsEnvironmentOnlyDeployments(){
        configuration(directory.resolve("not-created.properties"),"sqlserver")
            .withPropertyValues("DB_URL="+URL,"DB_USER=runtime-user","DB_PASSWORD=runtime-password")
            .run(context->assertThat(context).hasNotFailed());
    }

    Properties read(Path file)throws IOException{
        Properties properties=new Properties();try(InputStream input=Files.newInputStream(file)){properties.load(input);}return properties;
    }
    void runHelper(Path file,String section,Map<String,String> values,boolean success)throws Exception{
        Path powershell=Path.of(System.getenv("SystemRoot"),"System32","WindowsPowerShell","v1.0","powershell.exe");
        Path script=Path.of("sql","Save-LocalConfig.ps1").toAbsolutePath();
        ProcessBuilder builder=new ProcessBuilder(powershell.toString(),"-NoProfile","-NonInteractive","-ExecutionPolicy","RemoteSigned","-File",script.toString(),
            "-Section",section,"-ConfigPath",file.toString());
        builder.environment().keySet().removeIf(key->VARIABLES.contains(key.toUpperCase(Locale.ROOT)));
        builder.environment().putAll(values);builder.redirectErrorStream(true);
        Process process=builder.start();
        try{
            assertThat(process.waitFor(20,TimeUnit.SECONDS)).isTrue();
            String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            assertThat(output).doesNotContain(PASSWORD,CLOUD_SECRET);
            if(success)assertThat(process.exitValue()).as(output).isZero();
            else {assertThat(process.exitValue()).isNotZero();assertThat(output).contains("Enter the Cloud name");}
        }finally{process.destroyForcibly();}
    }
    Map<String,String> values(){return Map.of("DB_PASSWORD",PASSWORD,"CLOUDINARY_CLOUD","local-test-cloud",
        "CLOUDINARY_KEY","123456789","CLOUDINARY_SECRET",CLOUD_SECRET);}
    @Test @EnabledOnOs(OS.WINDOWS)
    void powershellHelperSavesEscapedValuesThatSpringCanReadAfterRestart()throws Exception{
        Path file=directory.resolve("config with spaces/.env.local.properties");
        Map<String,String> supplied=new HashMap<>(values());supplied.put("BASE_URL","https://local-config.example.test");
        runHelper(file,"all",supplied,true);
        Properties properties=read(file);
        assertThat(properties.getProperty("DB_PASSWORD")).isEqualTo(PASSWORD);
        assertThat(properties.getProperty("DB_USER")).isEqualTo("sa");
        assertThat(properties.getProperty("DB_URL")).isEqualTo(URL);
        assertThat(properties.getProperty("JWT_SECRET")).hasSizeGreaterThanOrEqualTo(32);
        assertThat(properties.getProperty("COOKIE_SECURE")).isEqualTo("true");
        configuration(file,"sqlserver").run(context->{
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.datasource.password")).isEqualTo(PASSWORD);
            assertThat(context.getEnvironment().getProperty("uteb.cloudinary.secret")).isEqualTo(CLOUD_SECRET);
        });
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void changingCloudinaryKeepsSqlSmtpVnpayAndJwt()throws Exception{
        Path file=saveSample();
        Files.writeString(file,"\nSMTP_PASSWORD=keep-smtp\nVNPAY_HASH_SECRET=keep-vnpay\ncustom.note=keep-note\n",StandardOpenOption.APPEND);
        runHelper(file,"cloudinary",Map.of("CLOUDINARY_CLOUD","changed-cloud","CLOUDINARY_KEY","987654321","CLOUDINARY_SECRET","changed-secret"),true);
        Properties properties=read(file);
        assertThat(properties.getProperty("DB_PASSWORD")).isEqualTo(PASSWORD);
        assertThat(properties.getProperty("CLOUDINARY_CLOUD")).isEqualTo("changed-cloud");
        assertThat(properties.getProperty("JWT_SECRET")).isEqualTo("a".repeat(64));
        assertThat(properties.getProperty("SMTP_PASSWORD")).isEqualTo("keep-smtp");
        assertThat(properties.getProperty("VNPAY_HASH_SECRET")).isEqualTo("keep-vnpay");
        assertThat(properties.getProperty("custom.note")).isEqualTo("keep-note");
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void invalidUpdateLeavesPreviouslySavedCredentialsUntouched()throws Exception{
        Path file=saveSample();byte[] before=Files.readAllBytes(file);
        runHelper(file,"cloudinary",Map.of("CLOUDINARY_CLOUD","https://wrong.example","CLOUDINARY_KEY","123","CLOUDINARY_SECRET",CLOUD_SECRET),false);
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
        try(var files=Files.list(directory)){
            assertThat(files.map(p->p.getFileName().toString())).containsExactly(".env.local.properties");
        }
    }
}
