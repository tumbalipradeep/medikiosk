package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.auth.service.AccountLifecycleService;
import in.devmedi.kiosk.module.profile.entity.PatientProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for the patient registration / self-service path and the
 * {@code CK_USERS_ROLE} check constraint that previously surfaced as a 500
 * "Check constraint invalid" on the live endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationIntegrationTests {

    private static final String PASSWORD = "RegHx9!kqA2";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private AccountLifecycleService lifecycle;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String validPassword() {
        return PASSWORD;
    }

    @Test
    void registerPageRenders() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create your account")));
    }

    @Test
    void patientRegistrationSucceedsAndPersistsPatientRole() throws Exception {
        String username = unique("reg");
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", username)
                        .param("displayName", "Reg Test Patient")
                        .param("password", validPassword())
                        .param("confirmPassword", validPassword()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        User user = userRepository.findByUsername(username).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.PATIENT);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(patientProfileRepository.findByUserId(user.getId())).isPresent();
    }

    @Test
    void usernameWithSpacesAndOrdinaryCharactersRegistersAndLogsIn() throws Exception {
        String username = unique("mary ann");
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", username)
                        .param("displayName", "Mary Ann")
                        .param("password", validPassword())
                        .param("confirmPassword", validPassword()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        assertThat(userRepository.findByUsername(username)).isPresent();
        MockHttpSession session = login(username, validPassword());
        assertThat(session).isNotNull();
    }

    @Test
    void usernameIsTrimmedOnceAndStoredWithoutSurroundingWhitespace() throws Exception {
        String username = unique("trim me");
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "  " + username + "  ")
                        .param("displayName", "Trim Test")
                        .param("password", validPassword())
                        .param("confirmPassword", validPassword()))
                .andExpect(status().is3xxRedirection());

        assertThat(userRepository.findByUsername(username)).isPresent();
        assertThat(userRepository.findByUsername("  " + username + "  ")).isEmpty();
        MockHttpSession session = login(username, validPassword());
        assertThat(session).isNotNull();
    }

    @Test
    void shortAndOverlongUsernamesAreRejected() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "ab")
                        .param("displayName", "Short Name")
                        .param("password", validPassword())
                        .param("confirmPassword", validPassword()))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("between 3 and 64")));

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "a".repeat(65))
                        .param("displayName", "Long Name")
                        .param("password", validPassword())
                        .param("confirmPassword", validPassword()))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("between 3 and 64")));
    }

    @Test
    void blankUsernameIsRejected() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "   ")
                        .param("displayName", "Blank Name")
                        .param("password", validPassword())
                        .param("confirmPassword", validPassword()))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("between 3 and 64")));
    }

    @Test
    void roleParamCannotEscalateRegistrationToAdminOrPhysician() throws Exception {
        String adminAttempt = unique("esc-admin");
        String physicianAttempt = unique("esc-phys");

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", adminAttempt)
                        .param("displayName", "Escalation Attempt Admin")
                        .param("password", validPassword())
                        .param("confirmPassword", validPassword())
                        .param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", physicianAttempt)
                        .param("displayName", "Escalation Attempt Physician")
                        .param("password", validPassword())
                        .param("confirmPassword", validPassword()))
                .andExpect(status().is3xxRedirection());

        assertThat(userRepository.findByUsername(adminAttempt).orElseThrow().getRole())
                .isEqualTo(Role.PATIENT);
        assertThat(userRepository.findByUsername(physicianAttempt).orElseThrow().getRole())
                .isEqualTo(Role.PATIENT);

        MockHttpSession session = login(physicianAttempt, validPassword());
        mockMvc.perform(get("/patient/home").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateUsernameRendersErrorWithoutServerFailure() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "patient")
                        .param("displayName", "Duplicate Attempt")
                        .param("password", validPassword())
                        .param("confirmPassword", validPassword()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("is already taken")));
    }

    @Test
    void passwordMismatchRendersError() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", unique("mismatch"))
                        .param("displayName", "Mismatch Attempt")
                        .param("password", validPassword())
                        .param("confirmPassword", validPassword() + "x"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Passwords do not match")));
    }

    @Test
    void weakPasswordRendersErrorAndPersistsNothing() throws Exception {
        String username = unique("weak");
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", username)
                        .param("displayName", "Weak Attempt")
                        .param("password", "short")
                        .param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("must be at least")));
        assertThat(userRepository.findByUsername(username)).isEmpty();
    }

    @Test
    void physicianProvisioningAndSeedRolesRemainIntact() {
        String username = unique("prov");
        AccountLifecycleService.ProvisionedPhysician provisioned =
                lifecycle.provisionPhysician(username, "Dr. Provisioned", "MBBS", "Consultant",
                        "Cardiology", "TestOrg", "REG-1", "prov@example.com", "9876543210");
        assertThat(provisioned.user().getRole()).isEqualTo(Role.PHYSICIAN);
        assertThat(provisioned.user().isMustChangePassword()).isTrue();

        assertThat(userRepository.findByUsername("physician").orElseThrow().getRole())
                .isEqualTo(Role.PHYSICIAN);
        assertThat(userRepository.findByUsername("admin").orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
        assertThat(userRepository.findByUsername("patient").orElseThrow().getRole())
                .isEqualTo(Role.PATIENT);
    }

    @Test
    void schemaConstraintStillRejectsUnknownRoleValue() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE users SET role = 'ANONYMOUS' WHERE username = 'patient'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(userRepository.findByUsername("patient").orElseThrow().getRole())
                .isEqualTo(Role.PATIENT);
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
}