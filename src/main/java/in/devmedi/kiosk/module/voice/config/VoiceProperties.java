package in.devmedi.kiosk.module.voice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the ASR/TTS voice layer, bound from {@code medikiosk.*}.
 *
 * <p>Everything is configured strictly from the environment — never from files
 * or defaults that pretend a provider exists. The provider switches default to
 * {@value #PROVIDER_UNAVAILABLE}; a real provider ("bhashini") is only engaged
 * when its switch is set AND its credentials are complete, otherwise the
 * deterministic {@code UNAVAILABLE} fallback service is used (the patient can
 * simply type).</p>
 *
 * <p>Environment variable mapping:</p>
 * <ul>
 *   <li>{@code MEDIKIOSK_ASR_PROVIDER} &rarr; {@code medikiosk.asr-provider}</li>
 *   <li>{@code MEDIKIOSK_TTS_PROVIDER} &rarr; {@code medikiosk.tts-provider}</li>
 *   <li>{@code MEDIKIOSK_BHASHINI_USER_ID} &rarr; {@code medikiosk.bhashini.user-id}</li>
 *   <li>{@code MEDIKIOSK_BHASHINI_API_KEY} &rarr; {@code medikiosk.bhashini.api-key}</li>
 *   <li>{@code MEDIKIOSK_BHASHINI_PIPELINE_ID} &rarr; {@code medikiosk.bhashini.pipeline-id}</li>
 *   <li>{@code MEDIKIOSK_BHASHINI_API_URL} &rarr; {@code medikiosk.bhashini.api-url}</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "medikiosk")
public class VoiceProperties {

    public static final String PROVIDER_UNAVAILABLE = "unavailable";
    public static final String PROVIDER_BHASHINI = "bhashini";

    /** Every provider switch value the wiring actually understands (closed set). */
    public static final java.util.Set<String> KNOWN_PROVIDERS =
            java.util.Set.of(PROVIDER_UNAVAILABLE, PROVIDER_BHASHINI);

    // Environment variable names, kept as constants so setup hints, docs and
    // tests cite exactly the names the code reads — never a paraphrase.
    public static final String ENV_ASR_PROVIDER = "MEDIKIOSK_ASR_PROVIDER";
    public static final String ENV_TTS_PROVIDER = "MEDIKIOSK_TTS_PROVIDER";
    public static final String ENV_BHASHINI_USER_ID = "MEDIKIOSK_BHASHINI_USER_ID";
    public static final String ENV_BHASHINI_API_KEY = "MEDIKIOSK_BHASHINI_API_KEY";
    public static final String ENV_BHASHINI_PIPELINE_ID = "MEDIKIOSK_BHASHINI_PIPELINE_ID";

    public static final String DEFAULT_BHASHINI_API_URL =
            "https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline";

    private String asrProvider = PROVIDER_UNAVAILABLE;
    private String ttsProvider = PROVIDER_UNAVAILABLE;
    private Bhashini bhashini = new Bhashini();

    public String getAsrProvider() {
        return asrProvider;
    }

    public void setAsrProvider(String asrProvider) {
        this.asrProvider = asrProvider;
    }

    public String getTtsProvider() {
        return ttsProvider;
    }

    public void setTtsProvider(String ttsProvider) {
        this.ttsProvider = ttsProvider;
    }

    public Bhashini getBhashini() {
        return bhashini;
    }

    public void setBhashini(Bhashini bhashini) {
        this.bhashini = bhashini;
    }

    public boolean asrUsesBhashini() {
        return PROVIDER_BHASHINI.equalsIgnoreCase(asrProvider);
    }

    public boolean ttsUsesBhashini() {
        return PROVIDER_BHASHINI.equalsIgnoreCase(ttsProvider);
    }

    /** @return whether the ASR switch names a provider the wiring understands. */
    public boolean asrProviderKnown() {
        return asrProvider != null && KNOWN_PROVIDERS.contains(asrProvider.toLowerCase(java.util.Locale.ROOT));
    }

    /** @return whether the TTS switch names a provider the wiring understands. */
    public boolean ttsProviderKnown() {
        return ttsProvider != null && KNOWN_PROVIDERS.contains(ttsProvider.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Honest, resolved ASR provider name for status reporting: the real provider
     * only when the wiring would actually engage it (switch set AND credentials
     * complete); the deterministic fallback name otherwise. An unknown switch
     * value is never echoed as a provider — the wiring binds the fallback.
     */
    public String resolvedAsrProvider() {
        return asrUsesBhashini() && bhashini.isComplete() ? PROVIDER_BHASHINI : PROVIDER_UNAVAILABLE;
    }

    /** @see #resolvedAsrProvider() */
    public String resolvedTtsProvider() {
        return ttsUsesBhashini() && bhashini.isComplete() ? PROVIDER_BHASHINI : PROVIDER_UNAVAILABLE;
    }

    /**
     * Actionable, secret-free setup guidance derived from the actual voice
     * configuration state: which switch is off and which credentials are
     * missing. Null exactly when both ASR and TTS are live — an operational
     * capability needs no setup instructions. Environment-variable names are
     * cited from the constants above, never paraphrased, and no credential
     * value is ever included.
     */
    public String setupHint() {
        boolean asrLive = asrUsesBhashini() && bhashini.isComplete();
        boolean ttsLive = ttsUsesBhashini() && bhashini.isComplete();
        if (asrLive && ttsLive) {
            return null;
        }
        StringBuilder hint = new StringBuilder("Not live. ");
        boolean asrSwitchOff = !asrUsesBhashini();
        boolean ttsSwitchOff = !ttsUsesBhashini();
        if (asrSwitchOff || ttsSwitchOff) {
            hint.append("Set ");
            boolean needAnd = false;
            if (asrSwitchOff) {
                hint.append(ENV_ASR_PROVIDER);
                needAnd = true;
            }
            if (ttsSwitchOff) {
                if (needAnd) {
                    hint.append(" and ");
                }
                hint.append(ENV_TTS_PROVIDER);
            }
            hint.append(" to 'bhashini'");
            if (!asrSwitchOff || !ttsSwitchOff) {
                hint.append(" (the other direction already is)");
            }
            hint.append(".");
        }
        if (!bhashini.isComplete()) {
            hint.append(asrSwitchOff || ttsSwitchOff ? " Then provide " : " Provide ")
                    .append(ENV_BHASHINI_USER_ID).append(", ")
                    .append(ENV_BHASHINI_API_KEY).append(" and ")
                    .append(ENV_BHASHINI_PIPELINE_ID)
                    .append(" from the Bhashini/ULCA registration, then restart.");
        }
        return hint.toString();
    }

    /** Bhashini/ULCA settings; a real provider only activates when credentials are complete. */
    public static class Bhashini {

        private String userId;
        private String apiKey;
        private String pipelineId;
        private String apiUrl = DEFAULT_BHASHINI_API_URL;
        private String asrAudioFormat = "wav";
        private int asrSamplingRate = 16_000;
        private String ttsAudioFormat = "wav";
        private int ttsSamplingRate = 22_050;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getPipelineId() {
            return pipelineId;
        }

        public void setPipelineId(String pipelineId) {
            this.pipelineId = pipelineId;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getAsrAudioFormat() {
            return asrAudioFormat;
        }

        public void setAsrAudioFormat(String asrAudioFormat) {
            this.asrAudioFormat = asrAudioFormat;
        }

        public int getAsrSamplingRate() {
            return asrSamplingRate;
        }

        public void setAsrSamplingRate(int asrSamplingRate) {
            this.asrSamplingRate = asrSamplingRate;
        }

        public String getTtsAudioFormat() {
            return ttsAudioFormat;
        }

        public void setTtsAudioFormat(String ttsAudioFormat) {
            this.ttsAudioFormat = ttsAudioFormat;
        }

        public int getTtsSamplingRate() {
            return ttsSamplingRate;
        }

        public void setTtsSamplingRate(int ttsSamplingRate) {
            this.ttsSamplingRate = ttsSamplingRate;
        }

        /**
         * @return whether a real Bhashini integration can be engaged. Credentials
         *         are only ever read from the environment; no placeholder value is
         *         ever treated as configured.
         */
        public boolean isComplete() {
            return isNotBlank(userId) && isNotBlank(apiKey) && isNotBlank(pipelineId);
        }

        private static boolean isNotBlank(String value) {
            return value != null && !value.isBlank();
        }
    }
}