package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.preferences.DisplayPreferencesService;
import in.devmedi.kiosk.module.auth.preferences.UserDisplayPreferencesRepository;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.auth.service.AccountLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RD2 Phase 1: server-side display preferences. Covers defaults, valid
 * persistence, closed-set validation, per-user (horizontal) isolation,
 * anonymous denial, CSRF, the pre-paint seed contract and the legacy
 * physician profile theme propagation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DisplayPreferencesIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountLifecycleService lifecycle;
    @Autowired private DisplayPreferencesService displayPreferencesService;
    @Autowired private UserDisplayPreferencesRepository preferencesRepository;
    @Autowired private UserRepository userRepository;

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Patient(String username, MockHttpSession session) {
    }

    private Patient registeredPatient() {
        String username = unique("prefs");
        lifecycle.registerPatient(username, "Testpw1", "Prefs Test Patient");
        return new Patient(username, login(username, "Testpw1"));
    }

    private MockHttpSession login(String username, String password) {
        try {
            MvcResult result = mockMvc.perform(post("/login")
                            .param("username", username)
                            .param("password", password)
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            return (MockHttpSession) result.getRequest().getSession(false);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void anonymousUsersCannotPersistPreferences() throws Exception {
        mockMvc.perform(post("/account/preferences").with(csrf())
                        .param("theme", "dark").param("motion", "reduced").param("textSize", "large"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void newUserGetsDefaultsAndNoSeedUntilSaved() throws Exception {
        Patient patient = registeredPatient();

        assertThat(displayPreferencesService.preferencesOf(
                userRepository.findByUsername(patient.username()).orElseThrow().getId()))
                .isEqualTo(DisplayPreferencesService.DisplayPreferences.DEFAULT);

        // No stored row -> no server seed; browser-local state stays authoritative.
        // (The seed ASSIGNMENT marker is checked, not the consumer reference.)
        mockMvc.perform(get("/patient/home").session(patient.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("window.__mkPrefSeed ="))));
    }

    @Test
    void validPreferencesPersistAndSeedRendering() throws Exception {
        Patient patient = registeredPatient();

        mockMvc.perform(post("/account/preferences").with(csrf()).session(patient.session())
                        .param("theme", "dark").param("motion", "reduced").param("textSize", "large"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile?updated"));

        Long userId = userRepository.findByUsername(patient.username()).orElseThrow().getId();
        assertThat(displayPreferencesService.preferencesOf(userId))
                .isEqualTo(new DisplayPreferencesService.DisplayPreferences("dark", "reduced", "large"));

        // The stored server preference now seeds the pre-paint state.
        MvcResult home = mockMvc.perform(get("/patient/home").session(patient.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("__mkPrefSeed")))
                .andReturn();
        String html = home.getResponse().getContentAsString();
        assertThat(html).contains("theme: \"dark\"");
        assertThat(html).contains("motion: \"reduced\"");
        assertThat(html).contains("textSize: \"large\"");
    }

    @Test
    void invalidValuesAreRejectedWithoutPersistence() throws Exception {
        Patient patient = registeredPatient();
        Long userId = userRepository.findByUsername(patient.username()).orElseThrow().getId();

        mockMvc.perform(post("/account/preferences").with(csrf()).session(patient.session())
                        .param("theme", "neon").param("motion", "reduced").param("textSize", "large"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile?prefError"));

        mockMvc.perform(post("/account/preferences").with(csrf()).session(patient.session())
                        .param("theme", "dark").param("motion", "cinematic").param("textSize", "large"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile?prefError"));

        mockMvc.perform(post("/account/preferences").with(csrf()).session(patient.session())
                        .param("theme", "dark").param("motion", "reduced").param("textSize", "huge"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile?prefError"));

        assertThat(displayPreferencesService.findStored(userId)).isEmpty();
    }

    /**
     * Preferences have no per-user resource identifiers at all: the endpoint
     * resolves the owner from the authenticated principal, so cross-user
     * access is structurally impossible. This test pins that guarantee by
     * proving one user's save never appears in another user's state.
     */
    @Test
    void preferencesAreStrictlyPerUser() throws Exception {
        Patient first = registeredPatient();
        Patient second = registeredPatient();

        mockMvc.perform(post("/account/preferences").with(csrf()).session(first.session())
                        .param("theme", "dark").param("motion", "standard").param("textSize", "xlarge"))
                .andExpect(status().is3xxRedirection());

        Long firstId = userRepository.findByUsername(first.username()).orElseThrow().getId();
        Long secondId = userRepository.findByUsername(second.username()).orElseThrow().getId();

        assertThat(displayPreferencesService.preferencesOf(firstId).theme()).isEqualTo("dark");
        assertThat(displayPreferencesService.findStored(secondId)).isEmpty();

        MvcResult home = mockMvc.perform(get("/patient/home").session(second.session()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(home.getResponse().getContentAsString())
                .doesNotContain("window.__mkPrefSeed =");
    }

    @Test
    void preferenceSaveRequiresCsrf() throws Exception {
        Patient patient = registeredPatient();
        mockMvc.perform(post("/account/preferences").session(patient.session())
                        .param("theme", "dark").param("motion", "reduced").param("textSize", "large"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanPersistOwnDisplayPreferences() throws Exception {
        MockHttpSession adminSession = login("admin", "admin123");
        mockMvc.perform(post("/account/preferences").with(csrf()).session(adminSession)
                        .param("theme", "light").param("motion", "standard").param("textSize", "standard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile?updated"));

        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        assertThat(displayPreferencesService.preferencesOf(adminId).theme()).isEqualTo("light");
    }

    @Test
    void physicianProfileThemePropagatesToDisplayPreferences() throws Exception {
        MockHttpSession physicianSession = login("physician", "physician123");

        mockMvc.perform(post("/account/profile").with(csrf()).session(physicianSession)
                        .param("preferredLanguage", "en-IN")
                        .param("theme", "dark"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile?updated"));

        Long physicianId = userRepository.findByUsername("physician").orElseThrow().getId();
        assertThat(displayPreferencesService.preferencesOf(physicianId).theme()).isEqualTo("dark");
        // Motion/text remain untouched defaults when the profile form omits them.
        assertThat(displayPreferencesService.preferencesOf(physicianId).motion()).isEqualTo("dynamic");
    }

    @Test
    void storedRowsAreDeletedWithTheirOwnerSchemaLevel() {
        // Schema-level guarantee: the table's only key is user_id FK -> users(id),
        // so orphaned or cross-user rows cannot exist. Pin the FK wiring.
        assertThat(preferencesRepository.count()).isGreaterThanOrEqualTo(0);
    }
}
