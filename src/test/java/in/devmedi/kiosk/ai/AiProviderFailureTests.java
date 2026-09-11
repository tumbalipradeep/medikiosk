package in.devmedi.kiosk.ai;

import in.devmedi.kiosk.module.ai.provider.AiProviderException;
import in.devmedi.kiosk.module.ai.provider.AiProviderFailure;
import in.devmedi.kiosk.module.ai.provider.AiProviderRateLimitedException;
import in.devmedi.kiosk.module.ai.provider.AiProviderServerException;
import in.devmedi.kiosk.module.ai.provider.AiProviderTimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderFailureTests {

    @Test
    void timeoutMapsToTimeoutCategory() {
        assertThat(AiProviderFailure.classify(
                new AiProviderTimeoutException("groq", Duration.ofSeconds(2), null)))
                .isEqualTo(AiProviderFailure.TIMEOUT);
    }

    @Test
    void rateLimitMapsToRateLimitedCategory() {
        assertThat(AiProviderFailure.classify(
                new AiProviderRateLimitedException("groq", 5)))
                .isEqualTo(AiProviderFailure.RATE_LIMITED);
    }

    @Test
    void serverErrorMapsToServerErrorCategory() {
        assertThat(AiProviderFailure.classify(
                new AiProviderServerException("gemini", 503)))
                .isEqualTo(AiProviderFailure.SERVER_ERROR);
    }

    @Test
    void emptyCompletionMapsToEmptyResponseCategory() {
        assertThat(AiProviderFailure.classify(
                new AiProviderException("groq returned an empty completion")))
                .isEqualTo(AiProviderFailure.EMPTY_RESPONSE);
    }

    @Test
    void genericProviderFailureMapsToUnspecified() {
        assertThat(AiProviderFailure.classify(new AiProviderException("groq exploded")))
                .isEqualTo(AiProviderFailure.UNSPECIFIED_FAILURE);
    }

    @Test
    void unrelatedExceptionMapsToUnspecified() {
        assertThat(AiProviderFailure.classify(new IllegalStateException("boom")))
                .isEqualTo(AiProviderFailure.UNSPECIFIED_FAILURE);
    }

    @Test
    void nullMapsToUnspecified() {
        assertThat(AiProviderFailure.classify(null)).isEqualTo(AiProviderFailure.UNSPECIFIED_FAILURE);
    }
}