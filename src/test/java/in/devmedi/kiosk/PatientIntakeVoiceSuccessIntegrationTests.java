package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionRequest;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionService;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisRequest;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the audio transport endpoints when a real provider IS active: audio
 * bytes reach the ASR service untouched, the language is threaded through, the
 * provider's transcript is returned verbatim, and TTS audio returns as an
 * opaque byte body. Stub services stand in for Bhashini so the request/response
 * contract of the clinical transport layer is verified independently of the
 * provider HTTP layer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PatientIntakeVoiceSuccessIntegrationTests.StubVoiceConfig.class)
class PatientIntakeVoiceSuccessIntegrationTests {

    private static final byte[] WAV_BYTES = new byte[]{0x52, 0x49, 0x46, 0x46, 9, 8, 7, 6};

    @Autowired
    private MockMvc mockMvc;

    @Test
    void asrReturnsTheProviderTranscriptVerbatimAndLanguage() throws Exception {
        MockHttpSession session = loginAsPatient();
        session.setAttribute("patientIntakeLanguageCode", "te-IN");

        MvcResult result = mockMvc.perform(multipart("/patient/intake/voice/asr")
                        .file(new MockMultipartFile("audio", "clip.webm", "audio/webm",
                                new byte[]{1, 2, 3, 4, 5}))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("TRANSCRIBED");
        assertThat((String) JsonPath.read(body, "$.transcript")).isEqualTo("my exact spoken words");
        assertThat((String) JsonPath.read(body, "$.language")).isEqualTo("te-IN");

        SpeechRecognitionRequest received = StubVoiceConfig.LAST_ASR_REQUEST.get();
        assertThat(received.language()).isEqualTo("te-IN");
        assertThat(received.audio()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void asrHonoursAnExplicitLanguageParameterOverTheSession() throws Exception {
        MockHttpSession session = loginAsPatient();
        session.setAttribute("patientIntakeLanguageCode", "en-IN");

        mockMvc.perform(multipart("/patient/intake/voice/asr")
                        .file(new MockMultipartFile("audio", "clip.webm", "audio/webm",
                                new byte[]{1, 2, 3, 4, 5}))
                        .param("language", "hi-IN")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("hi-IN"));

        assertThat(StubVoiceConfig.LAST_ASR_REQUEST.get().language()).isEqualTo("hi-IN");
    }

    @Test
    void asrReportsEmptyStatusWhenTheProviderHearsNoSpeech() throws Exception {
        MockHttpSession session = loginAsPatient();
        StubVoiceConfig.ASR_EMPTY.set(true);
        try {
            mockMvc.perform(multipart("/patient/intake/voice/asr")
                            .file(new MockMultipartFile("audio", "clip.webm", "audio/webm",
                                    new byte[]{1, 2, 3, 4, 5}))
                            .header("X-Requested-With", "XMLHttpRequest")
                            .session(session)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("EMPTY"))
                    .andExpect(jsonPath("$.transcript").value(""));
        } finally {
            StubVoiceConfig.ASR_EMPTY.set(false);
        }
    }

    @Test
    void ttsReturnsTheSynthesizedAudioAsAnOpaqueByteBody() throws Exception {
        MockHttpSession session = loginAsPatient();

        mockMvc.perform(post("/patient/intake/voice/tts")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"What is your main problem?\",\"language\":\"en-IN\"}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/wav"))
                .andExpect(content().bytes(WAV_BYTES));

        SpeechSynthesisRequest received = StubVoiceConfig.LAST_TTS_REQUEST.get();
        assertThat(received.text()).isEqualTo("What is your main problem?");
        assertThat(received.language()).isEqualTo("en-IN");
    }

    @Test
    void ttsDefaultsToTheSessionLanguage() throws Exception {
        MockHttpSession session = loginAsPatient();
        session.setAttribute("patientIntakeLanguageCode", "kn-IN");

        mockMvc.perform(post("/patient/intake/voice/tts")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"ಸುಪ್ರಭಾತ\"}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/wav"));

        assertThat(StubVoiceConfig.LAST_TTS_REQUEST.get().language()).isEqualTo("kn-IN");
    }

    private MockHttpSession loginAsPatient() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", "patient")
                        .param("password", "patient123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @TestConfiguration
    static class StubVoiceConfig {

        static final AtomicReference<SpeechRecognitionRequest> LAST_ASR_REQUEST =
                new AtomicReference<>();
        static final AtomicReference<SpeechSynthesisRequest> LAST_TTS_REQUEST =
                new AtomicReference<>();
        static final java.util.concurrent.atomic.AtomicBoolean ASR_EMPTY =
                new java.util.concurrent.atomic.AtomicBoolean();

        @Bean
        @Primary
        SpeechRecognitionService fakeAsr() {
            return request -> {
                LAST_ASR_REQUEST.set(request);
                if (ASR_EMPTY.get()) {
                    return in.devmedi.kiosk.module.voice.speech.SpeechRecognitionResult.empty(request.language());
                }
                return in.devmedi.kiosk.module.voice.speech.SpeechRecognitionResult
                        .transcribed("my exact spoken words", request.language());
            };
        }

        @Bean
        @Primary
        SpeechSynthesisService fakeTts() {
            return request -> {
                LAST_TTS_REQUEST.set(request);
                return in.devmedi.kiosk.module.voice.speech.SpeechSynthesisResult.synthesized(WAV_BYTES, request.language());
            };
        }
    }
}