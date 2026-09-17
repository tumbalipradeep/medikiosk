package in.devmedi.kiosk;

import in.devmedi.kiosk.module.ai.provider.AiFailoverService;
import in.devmedi.kiosk.module.ocr.OcrCapabilityService;
import in.devmedi.kiosk.module.voice.config.VoiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CapabilityStatusIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OcrCapabilityService capabilityService;

    @Autowired
    private AiFailoverService aiFailoverService;

    @Autowired
    private VoiceProperties voiceProperties;

    @Test
    void ocrCapabilityEndpointIsAnonymousAndHonest() throws Exception {
        // Two engines are bound: the credential-gated Bhashini printed-text OCR
        // (implemented, NOT credential-verified) and the honest local-dev seam.
        // The overall status follows the CONFIGURED provider (local-dev), so the
        // default deployment still reports NOT_IMPLEMENTED — never live.
        mockMvc.perform(get("/api/capabilities/ocr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuredProvider").value("local-dev"))
                .andExpect(jsonPath("$.overallStatus").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.engines.length()").value(2))
                .andExpect(jsonPath("$.engines[0].engineName").value("bhashini (printed-text OCR)"))
                .andExpect(jsonPath("$.engines[0].status").value("IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED"))
                .andExpect(jsonPath("$.engines[0].available").value(false))
                .andExpect(jsonPath("$.engines[1].engineName").value("local-dev (no OCR engine)"))
                .andExpect(jsonPath("$.engines[1].status").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.engines[1].available").value(false))
                .andExpect(jsonPath("$.engines[1].languageStatus.ENGLISH").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.engines[1].languageStatus.HINDI").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.engines[1].languageStatus.TELUGU").value("NOT_IMPLEMENTED"));
    }

    @Test
    void hwrCapabilityIsNeverPresentedAsImplemented() throws Exception {
        mockMvc.perform(get("/api/capabilities/hwr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("none"))
                .andExpect(jsonPath("$.status").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void ocrCapabilityResolutionIsDeterministic() {
        var first = capabilityService.ocrCapabilities();
        var second = capabilityService.ocrCapabilities();
        assertThat(second).isEqualTo(first);
        assertThat(first.overallStatus().name()).isEqualTo("NOT_IMPLEMENTED");
    }

    @Test
    void aiCapabilityEndpointIsAnonymousAndReflectsTheBoundProviders() throws Exception {
        List<AiFailoverService.AiProviderEnabled> expected = aiFailoverService.providerStatuses();

        String body = mockMvc.perform(get("/api/capabilities/ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.length()").value(expected.size()))
                .andExpect(jsonPath("$.providers[0].name").value(expected.get(0).name()))
                .andExpect(jsonPath("$.providers[0].enabled").value(expected.get(0).enabled()))
                .andExpect(jsonPath("$.providers[1].name").value(expected.get(1).name()))
                .andExpect(jsonPath("$.providers[1].enabled").value(expected.get(1).enabled()))
                .andExpect(jsonPath("$.providers[2].name").value(expected.get(2).name()))
                .andExpect(jsonPath("$.providers[2].enabled").value(expected.get(2).enabled()))
                .andExpect(jsonPath("$.anyProviderEnabled").value(aiFailoverService.hasEnabledProviders()))
                .andReturn().getResponse().getContentAsString();

        // Configuration-level only: no credential material may ever appear.
        assertThat(body).doesNotContain("apiKey").doesNotContain("Bearer");
    }

    /**
     * RD2: honest voice (ASR/TTS) capability. The endpoint must mirror the
     * exact predicate {@code VoiceConfig} uses to engage a real provider, and
     * must never leak credential material. This environment has no Bhashini
     * credentials, so the honest state is UNAVAILABLE on both channels.
     */
    @Test
    void voiceCapabilityEndpointIsAnonymousAndMirrorsTheWiringDecision() throws Exception {
        boolean asrLive = voiceProperties.asrUsesBhashini() && voiceProperties.getBhashini().isComplete();
        boolean ttsLive = voiceProperties.ttsUsesBhashini() && voiceProperties.getBhashini().isComplete();
        // Environment honesty precondition: CI runs without voice credentials.
        // If that ever changes, update this test consciously, not silently.
        org.assertj.core.api.Assumptions.assumeThat(asrLive).isFalse();
        org.assertj.core.api.Assumptions.assumeThat(ttsLive).isFalse();

        String body = mockMvc.perform(get("/api/capabilities/voice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asrProvider").value("unavailable"))
                .andExpect(jsonPath("$.asrAvailable").value(false))
                .andExpect(jsonPath("$.ttsProvider").value("unavailable"))
                .andExpect(jsonPath("$.ttsAvailable").value(false))
                .andReturn().getResponse().getContentAsString();

        // Configuration-level only: no credential material may ever appear.
        assertThat(body).doesNotContain("apiKey").doesNotContain("Bearer").doesNotContain("pipelineId");
    }

    @Test
    void voiceCapabilityEndpointCarriesActionableSecretFreeSetupHint() throws Exception {
        boolean asrLive = voiceProperties.resolvedAsrProvider()
                .equals(in.devmedi.kiosk.module.voice.config.VoiceProperties.PROVIDER_BHASHINI);
        boolean ttsLive = voiceProperties.resolvedTtsProvider()
                .equals(in.devmedi.kiosk.module.voice.config.VoiceProperties.PROVIDER_BHASHINI);
        org.assertj.core.api.Assumptions.assumeThat(asrLive && ttsLive).isFalse();

        String body = mockMvc.perform(get("/api/capabilities/voice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupHint").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // The hint must cite the exact environment-variable names the code reads
        // (constants from VoiceProperties), so an operator can act on it directly.
        assertThat(body)
                .contains(in.devmedi.kiosk.module.voice.config.VoiceProperties.ENV_ASR_PROVIDER)
                .contains(in.devmedi.kiosk.module.voice.config.VoiceProperties.ENV_TTS_PROVIDER)
                .contains(in.devmedi.kiosk.module.voice.config.VoiceProperties.ENV_BHASHINI_USER_ID);
        // Secret-free by construction: variable names, never values.
        assertThat(body).doesNotContain("=").doesNotContain("Bearer");
    }

    @Test
    void ocrCapabilityEndpointCarriesSetupHintWhileNotOperational() throws Exception {
        var ocr = capabilityService.ocrCapabilities();
        org.assertj.core.api.Assumptions.assumeThat(ocr.overallStatus().isOperational()).isFalse();

        mockMvc.perform(get("/api/capabilities/ocr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupHint").isNotEmpty());
    }
}