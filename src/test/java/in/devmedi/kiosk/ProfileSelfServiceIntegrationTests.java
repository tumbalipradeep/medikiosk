package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.auth.service.AccountLifecycleService;
import in.devmedi.kiosk.module.profile.entity.PatientProfile;
import in.devmedi.kiosk.module.profile.entity.PatientProfileRepository;
import in.devmedi.kiosk.module.profile.entity.PhysicianProfile;
import in.devmedi.kiosk.module.profile.entity.PhysicianProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Self-service profile editing for patients and physicians: the editable
 * demographic/contact/preference fields, validation boundaries, ownership
 * isolation of profile pictures, and the read-only admin treatment.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileSelfServiceIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private PhysicianProfileRepository physicianProfileRepository;
    @Autowired private AccountLifecycleService lifecycle;

    private MockHttpSession patientSession;
    private MockHttpSession physicianSession;
    private MockHttpSession adminSession;

    @BeforeEach
    void setUp() throws Exception {
        patientSession = login("patient", "patient123");
        physicianSession = login("physician", "physician123");
        adminSession = login("admin", "admin123");
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

    @Test
    void patientProfilePageRendersEditableFields() throws Exception {
        mockMvc.perform(get("/account/profile").session(patientSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Preferred language")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Emergency contact name")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Accessibility preferences")));
    }

    @Test
    void patientCanUpdateFullProfileAndRoundTrips() throws Exception {
        mockMvc.perform(multipart("/account/profile")
                        .file(picture("me.png", "image/png", new byte[]{1, 2, 3, 4}))
                        .session(patientSession).with(csrf())
                        .param("dateOfBirth", "1990-06-15")
                        .param("gender", "FEMALE")
                        .param("phone", "+91 90000 00000")
                        .param("email", "demo.patient@example.org")
                        .param("addressLine1", "12 Nehru Nagar")
                        .param("city", "Mysuru")
                        .param("state", "Karnataka")
                        .param("postalCode", "570001")
                        .param("country", "India")
                        .param("emergencyContactName", "Relative One")
                        .param("emergencyContactPhone", "+91 91111 11111")
                        .param("bloodGroup", "O+")
                        .param("preferredLanguage", "kn-IN")
                        .param("accessibilityPrefs", "Large text preferred")
                        .param("notificationPrefs", "SMS only"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile?updated"));

        User demoPatient = userRepository.findByUsername("patient").orElseThrow();
        PatientProfile profile = patientProfileRepository.findByUserId(demoPatient.getId()).orElseThrow();
        assertThat(profile.getDateOfBirth()).isEqualTo(java.time.LocalDate.of(1990, 6, 15));
        assertThat(profile.getGender()).isEqualTo(PatientProfile.Gender.FEMALE);
        assertThat(profile.getPhone()).isEqualTo("+91 90000 00000");
        assertThat(profile.getEmail()).isEqualTo("demo.patient@example.org");
        assertThat(profile.getAddressLine1()).isEqualTo("12 Nehru Nagar");
        assertThat(profile.getCity()).isEqualTo("Mysuru");
        assertThat(profile.getEmergencyContactName()).isEqualTo("Relative One");
        assertThat(profile.getPreferredLanguage()).isEqualTo("kn-IN");
        assertThat(profile.getAccessibilityPrefs()).isEqualTo("Large text preferred");
        assertThat(profile.getProfilePicturePath()).isNotNull();
    }

    @Test
    void patientCannotSaveUnsupportedLanguageOrFutureDateOfBirth() throws Exception {
        mockMvc.perform(post("/account/profile")
                        .session(patientSession).with(csrf())
                        .param("preferredLanguage", "xx-XX"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Unsupported language code")));

        mockMvc.perform(post("/account/profile")
                        .session(patientSession).with(csrf())
                        .param("preferredLanguage", "en-IN")
                        .param("dateOfBirth", "2099-01-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Date of birth cannot be in the future")));

        mockMvc.perform(post("/account/profile")
                        .session(patientSession).with(csrf())
                        .param("preferredLanguage", "en-IN")
                        .param("gender", "UNDEFINED"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid gender")));
    }

    @Test
    void physicianCanUpdateOwnPreferencesAndTheyAreIsolatedFromPatientData() throws Exception {
        mockMvc.perform(post("/account/profile")
                        .session(physicianSession).with(csrf())
                        .param("preferredLanguage", "te-IN")
                        .param("theme", "dark")
                        .param("notificationPrefs", "Email digest")
                        .param("phone", "+91 98000 00000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile?updated"));

        User demoPhysician = userRepository.findByUsername("physician").orElseThrow();
        PhysicianProfile profile = physicianProfileRepository.findByUserId(demoPhysician.getId()).orElseThrow();
        assertThat(profile.getPreferredLanguage()).isEqualTo("te-IN");
        assertThat(profile.getTheme()).isEqualTo("dark");
        assertThat(profile.getNotificationPrefs()).isEqualTo("Email digest");
        assertThat(profile.getPhone()).isEqualTo("+91 98000 00000");

        assertThat(patientProfileRepository.existsByUserId(demoPhysician.getId())).isFalse();
    }

    @Test
    void physiciansCannotEditProfessionalLinesThroughSelfService() throws Exception {
        User demoPhysician = userRepository.findByUsername("physician").orElseThrow();
        mockMvc.perform(post("/account/profile")
                        .session(physicianSession).with(csrf())
                        .param("preferredLanguage", "en-IN")
                        .param("theme", "system")
                        .param("qualification", "MBBS")
                        .param("department", "Cardiology"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile?updated"));

        PhysicianProfile profile = physicianProfileRepository.findByUserId(demoPhysician.getId()).orElseThrow();
        assertThat(profile.getQualification()).isNull();
        assertThat(profile.getDepartment()).isNull();
    }

    @Test
    void onlyTheOwnerCanReadTheirProfilePicture() throws Exception {
        mockMvc.perform(multipart("/account/profile")
                        .file(picture("avatar.png", "image/png", new byte[]{9, 8, 7, 6}))
                        .session(patientSession).with(csrf())
                        .param("preferredLanguage", "en-IN"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/account/profile/picture").session(patientSession))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[]{9, 8, 7, 6}));

        mockMvc.perform(get("/account/profile/picture").session(physicianSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminSeesReadOnlyAccountAndCannotMutateProfile() throws Exception {
        mockMvc.perform(get("/account/profile").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("account console")));

        mockMvc.perform(post("/account/profile")
                        .session(adminSession).with(csrf())
                        .param("preferredLanguage", "en-IN"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Administrator accounts are managed through the console")));
    }

    @Test
    void seededPatientWithoutProfileRendersDefaultsThenPersistsOnSave() throws Exception {
        User demoPatient = userRepository.findByUsername("patient").orElseThrow();
        patientProfileRepository.findByUserId(demoPatient.getId())
                .ifPresent(patientProfileRepository::delete);

        mockMvc.perform(get("/account/profile").session(patientSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Preferred language")));

        mockMvc.perform(post("/account/profile")
                        .session(patientSession).with(csrf())
                        .param("preferredLanguage", "en-IN"))
                .andExpect(status().is3xxRedirection());

        assertThat(patientProfileRepository.findByUserId(demoPatient.getId())
                .orElseThrow().getPreferredLanguage()).isEqualTo("en-IN");
    }

    @Test
    void registeredPatientCanSaveTheirProfile() throws Exception {
        String username = "prof-" + UUID.randomUUID().toString().substring(0, 8);
        lifecycle.registerPatient(username, "ProfilePw123!", "Profile Test Patient");
        MockHttpSession session = login(username, "ProfilePw123!");
        try {
            mockMvc.perform(post("/account/profile")
                            .session(session).with(csrf())
                            .param("preferredLanguage", "hi-IN")
                            .param("gender", "OTHER")
                            .param("city", "Delhi"))
                    .andExpect(status().is3xxRedirection());
            User user = userRepository.findByUsername(username).orElseThrow();
            PatientProfile profile = patientProfileRepository.findByUserId(user.getId()).orElseThrow();
            assertThat(profile.getPreferredLanguage()).isEqualTo("hi-IN");
            assertThat(profile.getGender()).isEqualTo(PatientProfile.Gender.OTHER);
            assertThat(profile.getCity()).isEqualTo("Delhi");
        } finally {
            userRepository.findByUsername(username).ifPresent(u -> {
                patientProfileRepository.deleteById(u.getId());
                userRepository.deleteById(u.getId());
            });
        }
    }

    private static MockMultipartFile picture(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("profilePicture", name, contentType, bytes);
    }
}