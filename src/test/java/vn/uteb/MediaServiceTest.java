package vn.uteb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MediaServiceTest {
    final MediaService media = new MediaService(null, new ObjectMapper(), " cloud ", " key ", " private-secret ");

    @Test void copiedConfigurationRemovesSurroundingWhitespace() {
        assertThat(media.cloud).isEqualTo("cloud");
        assertThat(media.key).isEqualTo("key");
        assertThat(media.secret).isEqualTo("private-secret");
    }

    @Test void providerAuthenticationErrorsGiveGuidanceWithoutEchoingCredentials() throws Exception {
        assertThat(error("Unknown API key private-secret"))
            .contains("API Key", "Cloud name").doesNotContain("private-secret");
        assertThat(error("Invalid Signature private-secret. String to sign - folder=uteb/1"))
            .contains("API Secret").doesNotContain("private-secret", "folder=uteb/1");
        assertThat(error("Unknown cloud name private-secret"))
            .contains("CLOUDINARY_CLOUD").doesNotContain("private-secret");
    }

    @Test void distinguishesClockAndDamagedImageFromCredentials() throws Exception {
        assertThat(error("Stale request - timestamp is too old" )).contains("đồng bộ giờ");
        assertThat(error("Invalid image file")).contains("Nội dung tệp");
    }

    @Test void unexpectedOrNonJsonErrorsStillHaveSafeHttpDiagnostics() {
        assertThat(media.uploadError(502, "<html>private-secret</html>"))
            .contains("HTTP 502").doesNotContain("private-secret");
        assertThat(media.uploadError(429, "{}" )).contains("hạn mức");
    }

    private String error(String message) throws Exception {
        return media.uploadError(400, new ObjectMapper().writeValueAsString(
            java.util.Map.of("error", java.util.Map.of("message", message))));
    }
}
