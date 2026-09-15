package in.devmedi.kiosk;

import in.devmedi.kiosk.module.ocr.OcrCapabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void ocrCapabilityEndpointIsAnonymousAndHonest() throws Exception {
        mockMvc.perform(get("/api/capabilities/ocr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuredProvider").value("local-dev"))
                .andExpect(jsonPath("$.overallStatus").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.engines[0].engineName").value("local-dev (no OCR engine)"))
                .andExpect(jsonPath("$.engines[0].status").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.engines[0].available").value(false))
                .andExpect(jsonPath("$.engines[0].languageStatus.ENGLISH").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.engines[0].languageStatus.HINDI").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.engines[0].languageStatus.TELUGU").value("NOT_IMPLEMENTED"));
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
}