package in.devmedi.kiosk.ai;

import in.devmedi.kiosk.module.ai.provider.AiFailoverService;
import in.devmedi.kiosk.module.ai.provider.ClinicalAiProvider;
import in.devmedi.kiosk.module.ai.provider.GeminiProvider;
import in.devmedi.kiosk.module.ai.provider.GroqProvider;
import in.devmedi.kiosk.module.ai.provider.OpenRouterProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AiProviderConfigurationTests {

    @Autowired
    private List<ClinicalAiProvider> providers;

    @Autowired
    private AiFailoverService failoverService;

    @Autowired
    private GroqProvider groqProvider;

    @Autowired
    private GeminiProvider geminiProvider;

    @Autowired
    private OpenRouterProvider openRouterProvider;

    @Test
    void allThreeProviderBeansExist() {
        assertThat(groqProvider.getName()).isEqualTo("groq");
        assertThat(geminiProvider.getName()).isEqualTo("gemini");
        assertThat(openRouterProvider.getName()).isEqualTo("openrouter");
        assertThat(providers).hasSize(3);
    }

    @Test
    void providersAreDisabledWithoutEnvKeys() {
        // CI/dev machines do not set GROQ_API_KEY etc. Providers must not fail startup
        // and must report disabled until a key is present.
        assertThat(groqProvider.isEnabled()).isFalse();
        assertThat(geminiProvider.isEnabled()).isFalse();
        assertThat(openRouterProvider.isEnabled()).isFalse();
    }

    @Test
    void failoverOrderIsConfigDriven() {
        assertThat(failoverService.getFailoverOrder())
                .containsExactly("groq", "gemini", "openrouter");
    }

    @Test
    void failoverServiceCompletesNormally() {
        // No keys configured -> every call fails fast with a clear message (no HTTP attempted).
        var request = in.devmedi.kiosk.module.ai.conversation.ClinicalAiRequest.builder()
                .language("en")
                .currentQuestion("Describe your problem?")
                .patientAnswer("Headache.")
                .requestedResponse("CAPTURE_STATEMENT")
                .build();
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> failoverService.complete(request)))
                .isInstanceOf(in.devmedi.kiosk.module.ai.provider.AiProviderException.class)
                .hasMessageContaining("no AI provider is enabled");
    }
}