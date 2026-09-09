package in.devmedi.kiosk.module.ai.config;

import in.devmedi.kiosk.module.ai.provider.AiFailoverService;
import in.devmedi.kiosk.module.ai.provider.ClinicalAiProvider;
import in.devmedi.kiosk.module.ai.provider.GeminiProvider;
import in.devmedi.kiosk.module.ai.provider.GroqProvider;
import in.devmedi.kiosk.module.ai.provider.OpenRouterProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the AI provider adapters. API keys are read strictly from environment
 * variables (never from files or defaults). Each adapter builds its {@link RestClient}
 * from the auto-configured {@link RestClient.Builder}, overriding the request
 * factory with short per-provider connect/read timeouts.
 */
@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiProviderConfig {

    public static final String GROQ_API_KEY_ENV = "GROQ_API_KEY";
    public static final String GEMINI_API_KEY_ENV = "GEMINI_API_KEY";
    public static final String OPENROUTER_API_KEY_ENV = "OPENROUTER_API_KEY";

    @Bean
    public GroqProvider groqProvider(AiProviderProperties props, RestClient.Builder restClientBuilder) {
        return new GroqProvider(props.settingsFor(GroqProvider.NAME), System.getenv(GROQ_API_KEY_ENV), restClientBuilder);
    }

    @Bean
    public GeminiProvider geminiProvider(AiProviderProperties props, RestClient.Builder restClientBuilder) {
        return new GeminiProvider(props.settingsFor(GeminiProvider.NAME), System.getenv(GEMINI_API_KEY_ENV), restClientBuilder);
    }

    @Bean
    public OpenRouterProvider openRouterProvider(AiProviderProperties props, RestClient.Builder restClientBuilder) {
        return new OpenRouterProvider(props.settingsFor(OpenRouterProvider.NAME), System.getenv(OPENROUTER_API_KEY_ENV), restClientBuilder);
    }

    @Bean
    public AiFailoverService aiFailoverService(AiProviderProperties props,
                                               java.util.List<ClinicalAiProvider> providers) {
        return new AiFailoverService(props, providers);
    }
}