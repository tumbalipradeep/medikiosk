package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.consent.entity.Consent;
import in.devmedi.kiosk.module.consent.entity.ConsentState;
import in.devmedi.kiosk.module.consent.entity.ConsentType;
import in.devmedi.kiosk.module.consent.repository.ConsentRepository;
import in.devmedi.kiosk.module.patientsession.entity.PatientSession;
import in.devmedi.kiosk.module.patientsession.entity.PatientSessionStatus;
import in.devmedi.kiosk.module.patientsession.repository.PatientSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConsentPatientSessionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private PatientSessionRepository patientSessionRepository;

    @BeforeEach
    void resetPatientData() {
        Long patientId = userRepository.findByUsername("patient").orElseThrow().getId();
        patientSessionRepository.deleteAll(patientSessionRepository.findByUserId(patientId));
        consentRepository.deleteAll(consentRepository.findByUserId(patientId));
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void grantClinical(Long patientId) {
        consentRepository.save(new Consent(
                userRepository.getReferenceById(patientId),
                ConsentType.CLINICAL_CASE_TAKING,
                "test"));
    }

    @Test
    void consentPageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/patient/consent"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void intakePageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/patient/intake"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void nonPatientRolesCannotAccessConsentPage() throws Exception {
        for (String[] creds : new String[][]{
                {"physician", "physician123"},
                {"admin", "admin123"}}) {
            MockHttpSession session = login(creds[0], creds[1]);
            mockMvc.perform(get("/patient/consent").session(session))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void nonPatientRolesCannotStartSession() throws Exception {
        for (String[] creds : new String[][]{
                {"physician", "physician123"},
                {"admin", "admin123"}}) {
            MockHttpSession session = login(creds[0], creds[1]);
            mockMvc.perform(post("/patient/session/start").session(session).with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void nonPatientRolesCannotAccessIntakePage() throws Exception {
        for (String[] creds : new String[][]{
                {"physician", "physician123"},
                {"admin", "admin123"}}) {
            MockHttpSession session = login(creds[0], creds[1]);
            mockMvc.perform(get("/patient/intake").session(session))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void patientCanViewConsentPage() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(get("/patient/consent").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Consent &amp; Privacy Choices")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Clinical Case-Taking")));
    }

    @Test
    void patientCanGrantClinicalConsent() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(post("/patient/consent/grant")
                        .session(session)
                        .param("type", "CLINICAL_CASE_TAKING")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/patient/consent"));

        Long patientId = userRepository.findByUsername("patient").orElseThrow().getId();
        var consent = consentRepository.findByUserIdAndConsentType(patientId, ConsentType.CLINICAL_CASE_TAKING);
        assertThat(consent).isPresent();
        assertThat(consent.get().getState()).isEqualTo(ConsentState.GRANTED);
        assertThat(consent.get().getGrantedAt()).isNotNull();
    }

    @Test
    void patientCanStartSessionAfterConsent() throws Exception {
        Long patientId = userRepository.findByUsername("patient").orElseThrow().getId();
        grantClinical(patientId);

        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(post("/patient/session/start").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/patient/intake"));

        assertThat(patientSessionRepository.existsByUserIdAndStatus(patientId, PatientSessionStatus.ACTIVE)).isTrue();
    }

    @Test
    void patientCannotStartSessionWithoutConsent() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(post("/patient/session/start").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/patient/consent"));

        Long patientId = userRepository.findByUsername("patient").orElseThrow().getId();
        assertThat(patientSessionRepository.existsByUserIdAndStatus(patientId, PatientSessionStatus.ACTIVE)).isFalse();
    }

    @Test
    void duplicateActiveSessionIsPrevented() throws Exception {
        Long patientId = userRepository.findByUsername("patient").orElseThrow().getId();
        grantClinical(patientId);

        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(post("/patient/session/start").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/patient/intake"));

        MockHttpSession session2 = login("patient", "patient123");
        mockMvc.perform(post("/patient/session/start").session(session2).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/patient/consent"));

        assertThat(patientSessionRepository.countByUserIdAndStatus(patientId, PatientSessionStatus.ACTIVE)).isEqualTo(1);
    }

    @Test
    void patientCanRevokeConsent() throws Exception {
        Long patientId = userRepository.findByUsername("patient").orElseThrow().getId();
        grantClinical(patientId);

        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(post("/patient/consent/revoke")
                        .session(session)
                        .param("type", "CLINICAL_CASE_TAKING")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/patient/consent"));

        var consent = consentRepository.findByUserIdAndConsentType(patientId, ConsentType.CLINICAL_CASE_TAKING);
        assertThat(consent).isPresent();
        assertThat(consent.get().getState()).isEqualTo(ConsentState.REVOKED);
        assertThat(consent.get().getRevokedAt()).isNotNull();
    }

    @Test
    void revokedConsentPreventsNewSession() throws Exception {
        Long patientId = userRepository.findByUsername("patient").orElseThrow().getId();
        grantClinical(patientId);
        MockHttpSession revokeSession = login("patient", "patient123");
        mockMvc.perform(post("/patient/consent/revoke")
                        .session(revokeSession)
                        .param("type", "CLINICAL_CASE_TAKING")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(post("/patient/session/start").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/patient/consent"));

        assertThat(patientSessionRepository.existsByUserIdAndStatus(patientId, PatientSessionStatus.ACTIVE)).isFalse();
    }

    @Test
    void csrfIsEnforcedOnConsentAndSessionEndpoints() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(post("/patient/consent/grant").session(session).param("type", "CLINICAL_CASE_TAKING"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/patient/consent/revoke").session(session).param("type", "CLINICAL_CASE_TAKING"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/patient/session/start").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void intakeConversationPageIsVisibleToAuthenticatedPatient() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(get("/patient/intake").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"chatWindow\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"chatMessages\"")));
    }
}
