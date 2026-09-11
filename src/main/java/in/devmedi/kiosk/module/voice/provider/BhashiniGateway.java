package in.devmedi.kiosk.module.voice.provider;

import tools.jackson.databind.JsonNode;
import in.devmedi.kiosk.module.voice.config.VoiceProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP bridge to the Bhashini/ULCA pipeline API.
 *
 * <p>Two-round-trip flow, exactly as documented by Bhashini:
 * <ol>
 *   <li><b>Config call</b> — {@code POST getModelsPipeline} with {@code userID}
 *       and {@code ulcaApiKey} headers returns the inference endpoint
 *       ({@code callbackUrl} + {@code inferenceApiKey} name/value header) and
 *       the per-task/per-language {@code serviceId}.</li>
 *   <li><b>Compute call</b> — {@code POST callbackUrl} with the returned
 *       authorization header; ASR sends base64 {@code audioContent} and returns
 *       a text transcript, TTS sends {@code source} text and returns base64
 *       {@code audioContent}.</li>
 * </ol>
 *
 * <p>The config response for each (task, language) pair is cached in-process
 * for the lifetime of the gateway, so every patient clip only performs the
 * compute round trip. Failing responses are classified into
 * {@link BhashiniFailure} kinds so the service layer can map them to
 * conservative public statuses.</p>
 *
 * <p><strong>Privacy:</strong> this gateway never logs credentials, patient
 * text, or audio content — only the failure classification (see the service
 * layer, which logs language + duration + failure kind).</p>
 */
public final class BhashiniGateway {

    public static final String TASK_ASR = "asr";
    public static final String TASK_TTS = "tts";

    record Pipeline(String callbackUrl, String authName, String authValue, String serviceId) {
    }

    private final VoiceProperties.Bhashini settings;
    private final RestClient restClient;
    private final ConcurrentHashMap<String, Pipeline> pipelineCache = new ConcurrentHashMap<>();

    public BhashiniGateway(VoiceProperties.Bhashini settings, RestClient.Builder builder) {
        this.settings = settings;
        this.restClient = builder.clone().build();
    }

    /**
     * Recognizes speech in the given audio clip.
     *
     * @param sourceLanguage ISO-639 base language code (e.g. {@code hi})
     * @param audio          the raw audio bytes
     * @return the recognized transcript, or an empty string when the provider
     *         produced no speech segment
     * @throws BhashiniException classified on any provider/transport failure
     */
    public String recognize(String sourceLanguage, byte[] audio) {
        Pipeline pipeline = pipeline(TASK_ASR, sourceLanguage);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("language", Map.of("sourceLanguage", sourceLanguage));
        config.put("serviceId", pipeline.serviceId());
        config.put("audioFormat", settings.getAsrAudioFormat());
        config.put("samplingRate", settings.getAsrSamplingRate());
        config.put("preProcessors", List.of("vad"));

        Map<String, Object> inputData = new LinkedHashMap<>();
        Map<String, String> input = new LinkedHashMap<>();
        input.put("source", null);
        inputData.put("input", List.of(input));
        inputData.put("audio", List.of(Map.of("audioContent", Base64.getEncoder().encodeToString(audio))));

        JsonNode response = compute(pipeline, "asr", config, inputData);
        JsonNode output = firstOutput(response);
        if (output == null || !output.hasNonNull("source")) {
            throw new BhashiniException(BhashiniFailure.MALFORMED,
                    "ASR response missing pipelineResponse[0].output[0].source");
        }
        return output.path("source").asText("");
    }

    /**
     * Synthesizes speech for the given text.
     *
     * @param sourceLanguage ISO-639 base language code (e.g. {@code hi})
     * @param text           the text to speak
     * @return the synthesized audio bytes
     * @throws BhashiniException classified on any provider/transport failure
     */
    public byte[] synthesize(String sourceLanguage, String text) {
        Pipeline pipeline = pipeline(TASK_TTS, sourceLanguage);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("language", Map.of("sourceLanguage", sourceLanguage));
        config.put("serviceId", pipeline.serviceId());
        config.put("audioFormat", settings.getTtsAudioFormat());
        config.put("samplingRate", settings.getTtsSamplingRate());
        config.put("encoding", "base64");

        Map<String, Object> inputData = new LinkedHashMap<>();
        inputData.put("input", List.of(Map.of("source", text)));

        JsonNode response = compute(pipeline, "tts", config, inputData);
        JsonNode audio = firstAudio(response);
        if (audio == null || !audio.hasNonNull("audioContent")) {
            throw new BhashiniException(BhashiniFailure.MALFORMED,
                    "TTS response missing pipelineResponse[0].audio[0].audioContent");
        }
        try {
            return Base64.getDecoder().decode(audio.path("audioContent").asText(""));
        } catch (IllegalArgumentException ex) {
            throw new BhashiniException(BhashiniFailure.MALFORMED, "TTS response carried invalid base64 audio", ex);
        }
    }

    private Pipeline pipeline(String taskType, String sourceLanguage) {
        String key = taskType + "|" + sourceLanguage;
        return pipelineCache.computeIfAbsent(key, k -> {
            if (!settings.isComplete()) {
                throw new BhashiniException(BhashiniFailure.UNCONFIGURED,
                        "Bhashini credentials are not configured");
            }
            return fetchPipeline(taskType, sourceLanguage);
        });
    }

    private Pipeline fetchPipeline(String taskType, String sourceLanguage) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pipelineTasks", List.of(Map.of(
                "taskType", taskType,
                "config", Map.of("language", Map.of("sourceLanguage", sourceLanguage)))));
        body.put("pipelineRequestConfig", Map.of("pipelineId", settings.getPipelineId()));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("userID", settings.getUserId());
        headers.put("ulcaApiKey", settings.getApiKey());

        JsonNode response = post(settings.getApiUrl(), headers, body);
        JsonNode endpoint = response.path("pipelineInferenceAPIEndPoint");
        if (!endpoint.hasNonNull("callbackUrl")) {
            throw new BhashiniException(BhashiniFailure.MALFORMED,
                    "Pipeline config response missing callbackUrl");
        }
        String authName = endpoint.path("inferenceApiKey").path("name").asText("Authorization");
        String authValue = endpoint.path("inferenceApiKey").path("value").asText("");
        String serviceId = findServiceId(response, taskType, sourceLanguage);
        if (serviceId == null) {
            throw new BhashiniException(BhashiniFailure.MALFORMED,
                    "Pipeline config lists no service for task=" + taskType
                            + " language=" + sourceLanguage);
        }
        return new Pipeline(endpoint.path("callbackUrl").asText(), authName, authValue, serviceId);
    }

    private String findServiceId(JsonNode response, String taskType, String sourceLanguage) {
        JsonNode pipelineConfigs = response.path("pipelineResponseConfig");
        if (!pipelineConfigs.isArray()) {
            return null;
        }
        for (JsonNode entry : pipelineConfigs) {
            if (!taskType.equalsIgnoreCase(entry.path("taskType").asText())) {
                continue;
            }
            JsonNode configs = entry.path("config");
            if (!configs.isArray()) {
                continue;
            }
            for (JsonNode cfg : configs) {
                String lang = cfg.path("language").path("sourceLanguage").asText("");
                if (lang.equalsIgnoreCase(sourceLanguage)) {
                    return cfg.path("serviceId").asText(null);
                }
            }
        }
        return null;
    }

    private JsonNode compute(Pipeline pipeline, String taskType,
                             Map<String, Object> config, Map<String, Object> inputData) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pipelineTasks", List.of(Map.of("taskType", taskType, "config", config)));
        body.put("inputData", inputData);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(pipeline.authName(), pipeline.authValue());
        return post(pipeline.callbackUrl(), headers, body);
    }

    private JsonNode post(String url, Map<String, String> headers, Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri(url)
                    .headers(h -> headers.forEach(h::set))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            int code = ex.getStatusCode().value();
            if (code == 429) {
                throw new BhashiniException(BhashiniFailure.RATE_LIMITED,
                        "Provider rate-limited the request");
            }
            throw new BhashiniException(BhashiniFailure.SERVER,
                    "Provider rejected the request (HTTP " + code + ")");
        } catch (ResourceAccessException ex) {
            throw transportFailure(ex);
        }
    }

    private BhashiniException transportFailure(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof SocketTimeoutException) {
            return new BhashiniException(BhashiniFailure.TIMEOUT,
                    "Provider did not answer within the configured timeout", ex);
        }
        return new BhashiniException(BhashiniFailure.TRANSPORT,
                "Provider could not be reached", ex);
    }

    private static JsonNode firstOutput(JsonNode response) {
        JsonNode responses = response.path("pipelineResponse");
        if (!responses.isArray() || responses.isEmpty()) {
            return null;
        }
        JsonNode outputs = responses.get(0).path("output");
        if (!outputs.isArray() || outputs.isEmpty()) {
            return null;
        }
        return outputs.get(0);
    }

    private static JsonNode firstAudio(JsonNode response) {
        JsonNode responses = response.path("pipelineResponse");
        if (!responses.isArray() || responses.isEmpty()) {
            return null;
        }
        JsonNode audios = responses.get(0).path("audio");
        if (!audios.isArray() || audios.isEmpty()) {
            return null;
        }
        return audios.get(0);
    }
}