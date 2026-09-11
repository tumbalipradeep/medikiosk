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