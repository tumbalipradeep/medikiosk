package in.devmedi.kiosk.ai;

import in.devmedi.kiosk.module.ai.config.AiProviderProperties;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiRequest;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiResponse;
import in.devmedi.kiosk.module.ai.provider.AiFailoverService;
import in.devmedi.kiosk.module.ai.provider.AiProviderException;
import in.devmedi.kiosk.module.ai.provider.AiProviderTimeoutException;
import in.devmedi.kiosk.module.ai.provider.ClinicalAiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiFailoverServiceTests {

    @Mock
    private ClinicalAiProvider groq;

    @Mock
    private ClinicalAiProvider gemini;

    @Mock
    private ClinicalAiProvider openRouter;

    private AiProviderProperties properties;

    @BeforeEach
    void setUp() {
        when(groq.getName()).thenReturn("groq");
        when(gemini.getName()).thenReturn("gemini");
        when(openRouter.getName()).thenReturn("openrouter");
        properties = new AiProviderProperties();
        properties.setFailoverOrder(List.of("groq", "gemini", "openrouter"));
    }

    private AiFailoverService service() {
        return new AiFailoverService(properties, List.of(groq, gemini, openRouter));
    }

    private ClinicalAiRequest request() {
        return ClinicalAiRequest.builder()
                .language("en")
                .currentQuestion("Please describe your problem.")
                .patientAnswer("I have a headache.")
                .requestedResponse("CAPTURE_STATEMENT")
                .build();
    }

    @Test
    void providerSelectionUsesConfiguredOrder() {
        when(groq.isEnabled()).thenReturn(true);
        when(groq.complete(any())).thenReturn(response("groq"));

        ClinicalAiResponse result = service().complete(request());

        assertThat(result.getProvider()).isEqualTo("groq");
        verify(groq).complete(any());
        verify(gemini, never()).complete(any());
        verify(openRouter, never()).complete(any());
    }

    @Test
    void failoverFromProviderOneToProviderTwo() {
        when(groq.isEnabled()).thenReturn(true);
        when(gemini.isEnabled()).thenReturn(true);
        when(groq.complete(any())).thenThrow(new AiProviderException("groq unavailable"));
        when(gemini.complete(any())).thenReturn(response("gemini"));

        ClinicalAiResponse result = service().complete(request());

        assertThat(result.getProvider()).isEqualTo("gemini");
        InOrder order = inOrder(groq, gemini);
        order.verify(groq).complete(any());
        order.verify(gemini).complete(any());
        verify(openRouter, never()).complete(any());
    }

    @Test
    void failoverThroughAllProviders() {
        when(groq.isEnabled()).thenReturn(true);
        when(gemini.isEnabled()).thenReturn(true);
        when(openRouter.isEnabled()).thenReturn(true);
        when(groq.complete(any())).thenThrow(new AiProviderException("groq down"));
        when(gemini.complete(any())).thenThrow(new AiProviderTimeoutException("gemini", Duration.ofSeconds(1), null));
        when(openRouter.complete(any())).thenThrow(new AiProviderException("openrouter 503"));

        assertThatThrownBy(() -> service().complete(request()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("groq")
                .hasMessageContaining("gemini")
                .hasMessageContaining("openrouter");

        InOrder order = inOrder(groq, gemini, openRouter);
        order.verify(groq).complete(any());
        order.verify(gemini).complete(any());
        order.verify(openRouter).complete(any());
    }

    @Test
    void failsToThirdWhenFirstTwoFail() {
        when(groq.isEnabled()).thenReturn(true);
        when(gemini.isEnabled()).thenReturn(true);
        when(openRouter.isEnabled()).thenReturn(true);
        when(groq.complete(any())).thenThrow(new AiProviderException("groq 429"));
        when(gemini.complete(any())).thenThrow(new AiProviderException("gemini timeout"));
        when(openRouter.complete(any())).thenReturn(response("openrouter"));

        ClinicalAiResponse result = service().complete(request());

        assertThat(result.getProvider()).isEqualTo("openrouter");
        verify(openRouter).complete(any());
    }

    @Test
    void disabledProvidersAreSkipped() {
        when(groq.isEnabled()).thenReturn(false);
        when(gemini.isEnabled()).thenReturn(false);
        when(openRouter.isEnabled()).thenReturn(true);
        when(openRouter.complete(any())).thenReturn(response("openrouter"));

        ClinicalAiResponse result = service().complete(request());

        assertThat(result.getProvider()).isEqualTo("openrouter");
        verify(openRouter).complete(any());
        verify(groq, never()).complete(any());
        verify(gemini, never()).complete(any());
    }

    @Test
    void missingApiKeysLeadsToUnavailable() {
        when(groq.isEnabled()).thenReturn(false);
        when(gemini.isEnabled()).thenReturn(false);
        when(openRouter.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service().complete(request()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("no AI provider is enabled");
    }

    @Test
    void timeoutExceptionFailsOverToNextProvider() {
        when(groq.isEnabled()).thenReturn(true);
        when(gemini.isEnabled()).thenReturn(true);
        when(groq.complete(any())).thenThrow(new AiProviderTimeoutException(
                "groq", Duration.ofSeconds(2), new java.net.http.HttpTimeoutException("timed out")));
        when(gemini.complete(any())).thenReturn(response("gemini"));

        ClinicalAiResponse result = service().complete(request());

        assertThat(result.getProvider()).isEqualTo("gemini");
    }

    @Test
    void providersAreTriedSequentially() {
        when(groq.isEnabled()).thenReturn(true);
        when(gemini.isEnabled()).thenReturn(true);
        when(groq.complete(any())).thenThrow(new AiProviderException("boom"));
        when(gemini.complete(any())).thenReturn(response("gemini"));

        service().complete(request());

        verify(groq, times(1)).complete(any());
        verify(gemini, times(1)).complete(any());
    }

    private ClinicalAiResponse response(String provider) {
        return ClinicalAiResponse.of(provider, "model-" + provider, "ok", "en");
    }
}