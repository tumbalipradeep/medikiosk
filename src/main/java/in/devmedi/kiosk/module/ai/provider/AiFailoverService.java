package in.devmedi.kiosk.module.ai.provider;

import in.devmedi.kiosk.module.ai.config.AiProviderProperties;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiRequest;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tries providers strictly in the configured order, one at a time.
 * On timeout, HTTP 429, HTTP 5xx, or any provider failure it moves to the next
 * enabled provider. Throws a single {@link AiProviderException} only when every
 * enabled provider has failed.
 */
public class AiFailoverService {

    private static final Logger log = LoggerFactory.getLogger(AiFailoverService.class);

    private final List<ClinicalAiProvider> orderedProviders;
    private final List<String> failoverOrder;

    public AiFailoverService(AiProviderProperties properties, List<ClinicalAiProvider> providers) {
        this.failoverOrder = List.copyOf(properties.getFailoverOrder());
        Map<String, ClinicalAiProvider> byName = providers.stream()
                .collect(Collectors.toMap(ClinicalAiProvider::getName, Function.identity()));
        List<ClinicalAiProvider> ordered = new ArrayList<>();
        for (String name : this.failoverOrder) {
            ClinicalAiProvider provider = byName.get(name);
            if (provider != null) {
                ordered.add(provider);
            } else {
                log.warn("AI provider '{}' declared in failover-order but no bean exists", name);
            }
        }
        this.orderedProviders = List.copyOf(ordered);
    }

    /**
     * Returns the first successful response, or fails after all enabled providers.
     */
    public ClinicalAiResponse complete(ClinicalAiRequest request) throws AiProviderException {
        List<String> failures = new ArrayList<>();
        for (ClinicalAiProvider provider : orderedProviders) {
            if (!provider.isEnabled()) {
                log.debug("AI provider '{}' skipped (not enabled)", provider.getName());
                continue;
            }
            try {
                ClinicalAiResponse response = provider.complete(request);
                log.info("AI provider '{}' answered", provider.getName());
                return response;
            } catch (RuntimeException ex) {
                log.warn("AI provider '{}' failed: {}", provider.getName(), ex.getMessage());
                failures.add(provider.getName() + ": " + ex.getMessage());
            }
        }
        String reason = failures.isEmpty()
                ? "no AI provider is enabled (check API keys and config)"
                : "all providers failed: " + String.join(" | ", failures);
        throw new AiProviderException("AI service unavailable — " + reason);
    }

    public List<String> getFailoverOrder() {
        return failoverOrder;
    }
}