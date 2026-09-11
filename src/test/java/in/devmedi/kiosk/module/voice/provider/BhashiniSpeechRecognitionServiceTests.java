package in.devmedi.kiosk.module.voice.provider;

import in.devmedi.kiosk.module.voice.config.VoiceProperties;
import in.devmedi.kiosk.module.voice.language.LanguageService;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionRequest;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionResult;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionStatus;
import in.devmedi.kiosk.module.voice.speech.VoiceLimits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies the real Bhashini/ULCA ASR adapter against the documented
 * config-then-compute pipeline contract, including the fallback statuses when
 * recognition cannot complete. Never an invented transcript.
 */
class BhashiniSpeechRecognitionServiceTests {

    private static final String CONFIG_URL =
            "https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline";
    private static final String COMPUTE_URL = "https://compute.example/asr";

    private static final String CONFIG_RESPONSE = """
            {
              "pipelineResponseConfig": [
                {
                  "taskType": "asr",
                  "config": [
                    {"language": {"sourceLanguage": "en"}, "serviceId": "svc-asr-en"}
                  ]
                }
              ],
              "pipelineInferenceAPIEndPoint": {
                "callbackUrl": "%s",
                "inferenceApiKey": {"name": "Authorization", "value": "tok123"}
              }
            }
            """.formatted(COMPUTE_URL);

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private VoiceProperties.Bhashini settings;
    private BhashiniSpeechRecognitionService service;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        settings = new VoiceProperties.Bhashini();
        settings.setUserId("u");
        settings.setApiKey("k");
        settings.setPipelineId("p");
        service = new BhashiniSpeechRecognitionService(settings, new LanguageService(), builder);
    }

    @AfterEach
    void verifyMocks() {
        server.verify();
    }

    private void expectConfigCall() {
        server.expect(requestTo(CONFIG_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("userID", "u"))
                .andExpect(header("ulcaApiKey", "k"))
                .andRespond(withSuccess(CONFIG_RESPONSE, MediaType.APPLICATION_JSON));
    }

    private void expectComputeCall(String transcript) {
        server.expect(requestTo(COMPUTE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "tok123"))
                .andExpect(jsonPath("$.pipelineTasks[0].taskType").value("asr"))
                .andExpect(jsonPath("$.pipelineTasks[0].config.language.sourceLanguage").value("en"))
                .andExpect(jsonPath("$.pipelineTasks[0].config.serviceId").value("svc-asr-en"))
                .andExpect(jsonPath("$.pipelineTasks[0].config.audioFormat").value("wav"))
                .andExpect(jsonPath("$.pipelineTasks[0].config.samplingRate").value(16000))
                .andExpect(jsonPath("$.inputData.audio[0].audioContent")
                        .value(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4})))
                .andRespond(withSuccess("""
                        {"pipelineResponse": [{"output": [{"source": "%s"}]}]}
                        """.formatted(transcript), MediaType.APPLICATION_JSON));
    }

    @Test
    void transcribedResultPreservesTheExactProviderTranscript() {
        expectConfigCall();
        expectComputeCall("chest pain, started yesterday evening");

        SpeechRecognitionResult result = service.transcribe(
                new SpeechRecognitionRequest(new byte[]{1, 2, 3, 4}, "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.TRANSCRIBED);
        assertThat(result.transcript()).isEqualTo("chest pain, started yesterday evening");
        assertThat(result.language()).isEqualTo("en-IN");
    }

    @Test
    void pipelineConfigIsFetchedOnceAndCachedPerLanguage() {
        expectConfigCall();
        server.expect(requestTo(COMPUTE_URL)).andRespond(withSuccess(
                "{\"pipelineResponse\": [{\"output\": [{\"source\": \"first\"}]}]}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(COMPUTE_URL)).andRespond(withSuccess(
                "{\"pipelineResponse\": [{\"output\": [{\"source\": \"second\"}]}]}",
                MediaType.APPLICATION_JSON));

        SpeechRecognitionResult first = service.transcribe(
                new SpeechRecognitionRequest(new byte[]{1, 2, 3, 4}, "en-IN", "s-1"));
        SpeechRecognitionResult second = service.transcribe(
                new SpeechRecognitionRequest(new byte[]{1, 2, 3, 4}, "en-IN", "s-1"));

        assertThat(first.transcript()).isEqualTo("first");
        assertThat(second.transcript()).isEqualTo("second");
    }

    @Test
    void blankProviderOutputMapsToEmptyStatus() {
        expectConfigCall();
        expectComputeCall("   ");

        SpeechRecognitionResult result = service.transcribe(
                new SpeechRecognitionRequest(new byte[]{1, 2, 3, 4}, "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.EMPTY);
        assertThat(result.transcript()).isBlank();
    }

    @Test
    void emptyAudioReturnsEmptyWithoutAnyProviderCall() {
        SpeechRecognitionResult result = service.transcribe(
                new SpeechRecognitionRequest(new byte[0], "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.EMPTY);
    }

    @Test
    void oversizedAudioReturnsFailedWithoutAnyProviderCall() {
        byte[] huge = new byte[VoiceLimits.ASR_MAX_INPUT_BYTES + 1];

        SpeechRecognitionResult result = service.transcribe(
                new SpeechRecognitionRequest(huge, "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.FAILED);
    }

    @Test
    void unsupportedLanguageReturnsUnsupportedWithoutAnyProviderCall() {
        SpeechRecognitionResult result = service.transcribe(
                new SpeechRecognitionRequest(new byte[]{1}, "fr-FR", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.UNSUPPORTED);
    }

    @Test
    void missingCredentialsReportUnavailableNeverTranscript() {
        settings = new VoiceProperties.Bhashini();
        service = new BhashiniSpeechRecognitionService(settings, new LanguageService(), builder);

        SpeechRecognitionResult result = service.transcribe(
                new SpeechRecognitionRequest(new byte[]{1, 2, 3}, "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.UNAVAILABLE);
        assertThat(result.transcript()).isNullOrEmpty();
    }

    @Test
    void serverErrorMapsToFailed() {
        expectConfigCall();
        server.expect(requestTo(COMPUTE_URL))
                .andRespond(withServerError());

        SpeechRecognitionResult result = service.transcribe(
                new SpeechRecognitionRequest(new byte[]{1, 2, 3, 4}, "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.FAILED);
        assertThat(result.transcript()).isNullOrEmpty();
    }

    @Test
    void rateLimitMapsToFailed() {
        expectConfigCall();
        server.expect(requestTo(COMPUTE_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        SpeechRecognitionResult result = service.transcribe(
                new SpeechRecognitionRequest(new byte[]{1, 2, 3, 4}, "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.FAILED);
        assertThat(result.transcript()).isNullOrEmpty();
    }

    @Test
    void timeoutMapsToFailed() {
        expectConfigCall();
        server.expect(requestTo(COMPUTE_URL))
                .andRespond(transportFailure());

        SpeechRecognitionResult result = service.transcribe(
                new SpeechRecognitionRequest(new byte[]{1, 2, 3, 4}, "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.FAILED);
        assertThat(result.transcript()).isNullOrEmpty();
    }

    @Test
    void malformedResponseWithoutPipelineMapsToFailed() {
        expectConfigCall();
        server.expect(requestTo(COMPUTE_URL))
                .andRespond(withSuccess("{\"unexpected\": true}", MediaType.APPLICATION_JSON));

        SpeechRecognitionResult result = service.transcribe(
                new SpeechRecognitionRequest(new byte[]{1, 2, 3, 4}, "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.FAILED);
    }

    @Test
    void configResponseWithoutServiceIdMapsToFailed() {
        String badConfig = """
                {
                  "pipelineResponseConfig": [
                    {"taskType": "asr", "config": [
                      {"language": {"sourceLanguage": "hi"}, "serviceId": "svc-asr-hi"}]}
                  ],
                  "pipelineInferenceAPIEndPoint": {
                    "callbackUrl": "%s",
                    "inferenceApiKey": {"name": "Authorization", "value": "tok123"}
                  }
                }
                """.formatted(COMPUTE_URL);
        server.expect(requestTo(CONFIG_URL))
                .andRespond(withSuccess(badConfig, MediaType.APPLICATION_JSON));

        SpeechRecognitionResult result = service.transcribe(
                new SpeechRecognitionRequest(new byte[]{1, 2, 3, 4}, "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.FAILED);
        assertThat(result.transcript()).isNullOrEmpty();
    }

    private ResponseCreator transportFailure() {
        return request -> {
            throw new ResourceAccessException("boom", new SocketTimeoutException("read timed out"));
        };
    }
}