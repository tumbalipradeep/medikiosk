package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import in.devmedi.kiosk.module.voice.speech.VoiceLimits;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the audio transport endpoints on the LIVE configuration (no ASR/TTS
 * provider credentials in this deployment). Every request must resolve
 * gracefully: a clear recoverable status is returned, never a crash and never
 * invented speech, so the patient simply types. Also verifies the request
 * boundary validation and the unchanged /patient security roles.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PatientIntakeVoiceControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void asrReportsUnavailableWithoutAnyProviderConfigured() throws Exception {
        MockHttpSession session = loginAsPatient();
        mockMvc.perform(multipart("/patient/intake/voice/asr")
                        .file(new MockMultipartFile("audio", "clip.webm", "audio/webm", new byte[]{1, 2, 3}))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.language").value("en-IN"));
    }

    @Test
    void asrThreadsTheSessionLanguageThroughToTheService() throws Exception {
        MockHttpSession session = loginAsPatient();
        session.setAttribute("patientIntakeLanguageCode", "te-IN");
        MvcResult result = mockMvc.perform(multipart("/patient/intake/voice/asr")
                        .file(new MockMultipartFile("audio", "clip.webm", "audio/webm", new byte[]{1, 2, 3}))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andReturn();
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.language"))
                .isEqualTo("te-IN");
    }

    @Test
    void asrRejectsAnEmptyRecording() throws Exception {
        MockHttpSession session = loginAsPatient();
        mockMvc.perform(multipart("/patient/intake/voice/asr")
                        .file(new MockMultipartFile("audio", "clip.webm", "audio/webm", new byte[0]))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void asrRejectsANonAudioUpload() throws Exception {
        MockHttpSession session = loginAsPatient();
        mockMvc.perform(multipart("/patient/intake/voice/asr")
                        .file(new MockMultipartFile("audio", "notes.txt", "text/plain", new byte[]{1, 2, 3}))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void asrRejectsAnOversizedRecording() throws Exception {
        MockHttpSession session = loginAsPatient();
        mockMvc.perform(multipart("/patient/intake/voice/asr")
                        .file(new MockMultipartFile("audio", "clip.webm", "audio/webm",
                                new byte[VoiceLimits.ASR_MAX_INPUT_BYTES + 1]))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void asrRejectsAnUnsupportedExplicitLanguage() throws Exception {
        MockHttpSession session = loginAsPatient();
        mockMvc.perform(multipart("/patient/intake/voice/asr")
                        .file(new MockMultipartFile("audio", "clip.webm", "audio/webm", new byte[]{1, 2, 3}))
                        .param("language", "fr-FR")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ttsReportsUnavailableWithoutAnyProviderConfigured() throws Exception {
        MockHttpSession session = loginAsPatient();
        mockMvc.perform(post("/patient/intake/voice/tts")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"What is your main problem?\",\"language\":\"en-IN\"}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
    }

    @Test
    void ttsRejectsBlankText() throws Exception {
        MockHttpSession session = loginAsPatient();
        mockMvc.perform(post("/patient/intake/voice/tts")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \"}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ttsRejectsOverlyLongText() throws Exception {
        MockHttpSession session = loginAsPatient();
        String longText = "x".repeat(VoiceLimits.TTS_MAX_TEXT_CHARS + 1);
        mockMvc.perform(post("/patient/intake/voice/tts")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + longText + "\"}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedVoiceRequestsAreRejected() throws Exception {
        mockMvc.perform(multipart("/patient/intake/voice/asr")
                        .file(new MockMultipartFile("audio", "clip.webm", "audio/webm", new byte[]{1, 2, 3}))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/patient/intake/voice/tts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void physicianRoleCannotAccessPatientVoiceEndpoints() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", "physician")
                        .param("password", "physician123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession physician = (MockHttpSession) result.getRequest().getSession(false);

        mockMvc.perform(multipart("/patient/intake/voice/asr")
                        .file(new MockMultipartFile("audio", "clip.webm", "audio/webm", new byte[]{1, 2, 3}))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .session(physician)
                        .with(csrf()))
                .andExpect(status().isForbidden());
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
}