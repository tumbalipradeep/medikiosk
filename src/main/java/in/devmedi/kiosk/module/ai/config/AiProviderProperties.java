package in.devmedi.kiosk.module.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI provider failover + per-provider settings, bound from {@code medikiosk.ai.*}.
 */
@ConfigurationProperties(prefix = "medikiosk.ai")
public class AiProviderProperties {

    /** Provider names in failover order, primary first. */
    private List<String> failoverOrder = List.of("groq", "gemini", "openrouter");

    /** Per-provider settings keyed by provider name. */
    private Map<String, ProviderSettings> providers = new LinkedHashMap<>();

    public List<String> getFailoverOrder() {
        return failoverOrder;
    }

    public void setFailoverOrder(List<String> failoverOrder) {
        this.failoverOrder = failoverOrder;
    }

    public Map<String, ProviderSettings> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderSettings> providers) {
        this.providers = providers;
    }

    public ProviderSettings settingsFor(String name) {
        return providers.get(name);
    }

    public static class ProviderSettings {
        /** Config toggle; independently of the API key presence. */
        private boolean enabled = true;
        private String model;
        private String baseUrl;
        private long timeoutMs = 12_000;
        private int maxTokens = 1024;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }
}