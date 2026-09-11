package in.devmedi.kiosk.module.voice.provider;

import in.devmedi.kiosk.module.voice.config.VoiceProperties;
import in.devmedi.kiosk.module.voice.language.LanguageService;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisRequest;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisResult;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisStatus;
import in.devmedi.kiosk.module.voice.speech.VoiceLimits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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
 * Verifies the real Bhashini/ULCA TTS adapter against the documented
 * config-then-compute pipeline contract, including fallback statuses. Synthesis
 * only ever returns audio the provider actually produced.
 */
class BhashiniSpeechSynthesisServiceTests {

    private static final String CONFIG_URL =
            "https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline";
    private static final String COMPUTE_URL = "https://compute.example/tts";

    private static final byte[] WAV_BYTES = new byte[]{0x52, 0x49, 0x46, 0x46, 1, 2, 3, 4};

    private static final String CONFIG_RESPONSE = """
            {
              "pipelineResponseConfig": [
                {
                  "taskType": "tts",
                  "config": [
                    {"language": {"sourceLanguage": "en"}, "serviceId": "svc-tts-en"},
                  {"language": {"sourceLanguage": "hi"}, "serviceId": "svc-tts-hi"}
                  ]
                }
              ],
              "pipelineInferenceAPIEndPoint": {
                "callbackUrl": "%s",
                "inferenceApiKey": {"name": "Authorization", "value": "tok456"}
              }
            }
            """.formatted(COMPUTE_URL);

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private VoiceProperties.Bhashini settings;
    private BhashiniSpeechSynthesisService service;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        settings = new VoiceProperties.Bhashini();
        settings.setUserId("u");
        settings.setApiKey("k");
        settings.setPipelineId("p");
        service = new BhashiniSpeechSynthesisService(settings, new LanguageService(), builder);
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

    @Test
    void synthesizedAudioReturnsTheProvidersActualBytes() {
        expectConfigCall();
        server.expect(requestTo(COMPUTE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "tok456"))
                .andExpect(jsonPath("$.pipelineTasks[0].taskType").value("tts"))
                .andExpect(jsonPath("$.pipelineTasks[0].config.language.sourceLanguage").value("hi"))
                .andExpect(jsonPath("$.pipelineTasks[0].config.serviceId").value("svc-tts-hi"))
                .andExpect(jsonPath("$.inputData.input[0].source").value("आपकी समस्या क्या है?"))
                .andRespond(withSuccess("""
                        {"pipelineResponse": [{"audio": [{
                          "audioContent": "%s"
                        }]}]}
                        """.formatted(Base64.getEncoder().encodeToString(WAV_BYTES)),
                        MediaType.APPLICATION_JSON));

        SpeechSynthesisResult result = service.synthesize(
                new SpeechSynthesisRequest("आपकी समस्या क्या है?", "hi-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.SYNTHESIZED);
        assertThat(result.audio()).isEqualTo(WAV_BYTES);
        assertThat(result.language()).isEqualTo("hi-IN");
    }

    @Test
    void blankTextReturnsFailedWithoutAnyProviderCall() {
        SpeechSynthesisResult result = service.synthesize(
                new SpeechSynthesisRequest("   ", "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.FAILED);
        assertThat(result.audio()).isEmpty();
    }

    @Test
    void overlyLongTextReturnsFailedWithoutAnyProviderCall() {
        SpeechSynthesisResult result = service.synthesize(
                new SpeechSynthesisRequest("x".repeat(VoiceLimits.TTS_MAX_TEXT_CHARS + 1), "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.FAILED);
        assertThat(result.audio()).isEmpty();
    }

    @Test
    void unsupportedLanguageReturnsUnsupportedWithoutAnyProviderCall() {
        SpeechSynthesisResult result = service.synthesize(
                new SpeechSynthesisRequest("bonjour", "fr-FR", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.UNSUPPORTED);
        assertThat(result.audio()).isEmpty();
    }

    @Test
    void missingCredentialsReportUnavailableNeverAudio() {
        settings = new VoiceProperties.Bhashini();
        service = new BhashiniSpeechSynthesisService(settings, new LanguageService(), builder);

        SpeechSynthesisResult result = service.synthesize(
                new SpeechSynthesisRequest("hello", "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.UNAVAILABLE);
        assertThat(result.audio()).isEmpty();
    }

    @Test
    void serverErrorMapsToFailed() {
        expectConfigCall();
        server.expect(requestTo(COMPUTE_URL)).andRespond(withServerError());

        SpeechSynthesisResult result = service.synthesize(
                new SpeechSynthesisRequest("hello", "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.FAILED);
        assertThat(result.audio()).isEmpty();
    }

    @Test
    void rateLimitMapsToFailed() {
        expectConfigCall();
        server.expect(requestTo(COMPUTE_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        SpeechSynthesisResult result = service.synthesize(
                new SpeechSynthesisRequest("hello", "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.FAILED);
    }

    @Test
    void malformedResponseWithoutAudioMapsToFailed() {
        expectConfigCall();
        server.expect(requestTo(COMPUTE_URL)).andRespond(withSuccess(
                "{\"pipelineResponse\": []}", MediaType.APPLICATION_JSON));

        SpeechSynthesisResult result = service.synthesize(
                new SpeechSynthesisRequest("hello", "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.FAILED);
        assertThat(result.audio()).isEmpty();
    }

    @Test
    void invalidBase64AudioMapsToFailed() {
        expectConfigCall();
        server.expect(requestTo(COMPUTE_URL)).andRespond(withSuccess("""
                {"pipelineResponse": [{"audio": [{"audioContent": "!!!not-base64!!!"}]}]}
                """, MediaType.APPLICATION_JSON));

        SpeechSynthesisResult result = service.synthesize(
                new SpeechSynthesisRequest("hello", "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.FAILED);
        assertThat(result.audio()).isEmpty();
    }
}