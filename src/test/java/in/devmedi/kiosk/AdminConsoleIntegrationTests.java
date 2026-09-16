package in.devmedi.kiosk;

import in.devmedi.kiosk.module.admin.config.SystemSetting;
import in.devmedi.kiosk.module.admin.config.SystemSettingRepository;
import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.auth.service.AccountLifecycleService;
import in.devmedi.kiosk.module.profile.entity.PhysicianProfile;
import in.devmedi.kiosk.module.profile.entity.PhysicianProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin console: dashboard, account administration (lock/unlock,
 * activate/deactivate, password reset), physician provisioning and profile
 * maintenance, and the runtime settings store. Only ROLE_ADMIN may reach
 * {@code /admin/**}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminConsoleIntegrationTests {

    private static final String PROBE_USERNAME = "adminprobe";
    private static final String PROBE_PASSWORD = "ProbePass123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PhysicianProfileRepository physicianProfileRepository;
    @Autowired private SystemSettingRepository settingRepository;
    @Autowired private AccountLifecycleService lifecycle;

    private Long probeId;

    @BeforeEach
    void setUp() {
        User probe = userRepository.findByUsername(PROBE_USERNAME).orElseGet(() -> {
            User created = new User(PROBE_USERNAME, "unused", "Probe Physician", Role.PHYSICIAN);
            userRepository.save(created);
            physicianProfileRepository.save(PhysicianProfile.create(created));
            return created;
        });
        probeId = probe.getId();
        lifecycle.unlockAccount(probeId);
        lifecycle.activate(probeId);
        resetProbePassword();
    }

    private void resetProbePassword() {
        User probe = userRepository.findById(probeId).orElseThrow();
        lifecycle.applyNewPasswordInternal(probe, PROBE_PASSWORD);
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

    private MockHttpSession admin() throws Exception {
        return login("admin", "admin123");
    }

    @Test
    void adminDashboardRendersMetrics() throws Exception {
        mockMvc.perform(get("/admin/home").session(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Administration")))
                .andExpect(content().string(containsString("Conversational AI providers")));
    }

    @Test
    void adminDashboardRendersCapabilityStatusAndAuditSections() throws Exception {
        mockMvc.perform(get("/admin/home").session(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Capability &amp; integration status")))
                .andExpect(content().string(containsString("NOT_IMPLEMENTED")))
                .andExpect(content().string(containsString("LocalOnlyExportTransport")))
                .andExpect(content().string(containsString("Recent audit events")));
    }

    @Test
    void nonAdminRolesCannotReachConsole() throws Exception {
        mockMvc.perform(get("/admin/home").session(login("patient", "patient123")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/home").session(login("physician", "physician123")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/accounts").session(login("physician", "physician123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountsListShowsSeededAdminAndProbe() throws Exception {
        mockMvc.perform(get("/admin/accounts").session(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("admin")))
                .andExpect(content().string(containsString(PROBE_USERNAME)));
    }

    @Test
    void lockAndUnlockProbeAccount() throws Exception {
        MockHttpSession session = admin();
        mockMvc.perform(post("/admin/accounts/{id}/lock", probeId).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts"))
                .andExpect(flash().attribute("success", notNullValue()));
        assertThat(userRepository.findById(probeId).orElseThrow().isLocked()).isTrue();

        mockMvc.perform(post("/admin/accounts/{id}/unlock", probeId).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(userRepository.findById(probeId).orElseThrow().isLocked()).isFalse();
    }

    @Test
    void deactivateAndActivateProbeAccount() throws Exception {
        MockHttpSession session = admin();
        mockMvc.perform(post("/admin/accounts/{id}/deactivate", probeId).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(userRepository.findById(probeId).orElseThrow().isEnabled()).isFalse();

        mockMvc.perform(post("/admin/accounts/{id}/activate", probeId).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(userRepository.findById(probeId).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void resetPasswordFlagsMustChangeAndReturnsTemporaryPassword() throws Exception {
        mockMvc.perform(post("/admin/accounts/{id}/reset-password", probeId).session(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts"))
                .andExpect(flash().attribute("temporaryPassword", notNullValue()));

        User probe = userRepository.findById(probeId).orElseThrow();
        assertThat(probe.isMustChangePassword()).isTrue();
    }

    @Test
    void provisionPhysicianCreatesAccountWithForcedPasswordChange() throws Exception {
        String username = "prov-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/admin/physicians/provision")
                        .session(admin()).with(csrf())
                        .param("username", username)
                        .param("displayName", "Dr Provisioned")
                        .param("department", "Cardiology"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts?role=PHYSICIAN"))
                .andExpect(flash().attribute("temporaryPassword", notNullValue()));

        User provisioned = userRepository.findByUsername(username).orElseThrow();
        try {
            assertThat(provisioned.getRole()).isEqualTo(Role.PHYSICIAN);
            assertThat(provisioned.isMustChangePassword()).isTrue();
            Optional<PhysicianProfile> profile = physicianProfileRepository.findById(provisioned.getId());
            assertThat(profile).isPresent();
            assertThat(profile.orElseThrow().getDepartment()).isEqualTo("Cardiology");
        } finally {
            physicianProfileRepository.deleteById(provisioned.getId());
            userRepository.deleteById(provisioned.getId());
        }
    }

    @Test
    void physicianProfileCanBeUpdatedByAdmin() throws Exception {
        MockHttpSession session = admin();
        mockMvc.perform(post("/admin/physicians/{id}/profile", probeId).session(session).with(csrf())
                        .param("qualification", "MBBS, MD")
                        .param("designation", "Consultant")
                        .param("department", "Neurology")
                        .param("organization", "City Hospital"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/physicians/" + probeId + "/profile"));

        PhysicianProfile profile = physicianProfileRepository.findById(probeId).orElseThrow();
        assertThat(profile.getDepartment()).isEqualTo("Neurology");
        assertThat(profile.getOrganization()).isEqualTo("City Hospital");
    }

    @Test
    void settingsListAndUpdateRoundTrips() throws Exception {
        MockHttpSession session = admin();
        mockMvc.perform(get("/admin/settings").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("kiosk.default_language")));

        String original = settingRepository.findByKey("kiosk.support_email")
                .map(SystemSetting::getValue).orElse("support@medikiosk.local");
        try {
            mockMvc.perform(post("/admin/settings").session(session).with(csrf())
                            .param("key", "kiosk.support_email")
                            .param("value", "helpdesk@medikiosk.test"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("success", notNullValue()));
            assertThat(settingRepository.findByKey("kiosk.support_email").orElseThrow().getValue())
                    .isEqualTo("helpdesk@medikiosk.test");
        } finally {
            mockMvc.perform(post("/admin/settings").session(session).with(csrf())
                    .param("key", "kiosk.support_email").param("value", original));
        }
    }

    @Test
    void unsupportedDefaultLanguageIsRejected() throws Exception {
        String original = settingRepository.findByKey("kiosk.default_language")
                .orElseThrow().getValue();
        mockMvc.perform(post("/admin/settings").session(admin()).with(csrf())
                        .param("key", "kiosk.default_language")
                        .param("value", "xx-XX"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", notNullValue()));
        assertThat(settingRepository.findByKey("kiosk.default_language").orElseThrow().getValue())
                .isEqualTo(original);
    }

    // ─── RD2 Phase 6: audit trail + settings auditing ─────────────────

    @Autowired
    private in.devmedi.kiosk.module.audit.repository.AuditEventRepository auditEventRepository;

    @Test
    void auditPageRendersWithFiltersAndPaginationControls() throws Exception {
        mockMvc.perform(get("/admin/audit").session(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Audit trail")))
                .andExpect(content().string(containsString("Event type")))
                .andExpect(content().string(containsString("PATIENT_CORRECTION")));

        // Outcome filter renders and applies.
        mockMvc.perform(get("/admin/audit").session(admin()).param("outcome", "FAILURE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FAILURE")));
    }

    @Test
    void auditPageIsForbiddenForNonAdmins() throws Exception {
        mockMvc.perform(get("/admin/audit").session(login("patient", "patient123")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/audit").session(login("physician", "physician123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditPageFilterByCaseShowsOnlyThatCase() throws Exception {
        String uniqueMarker = "case-rd2-audit-" + UUID.randomUUID();
        mockMvc.perform(get("/admin/audit").session(admin()).param("caseId", uniqueMarker))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("text-bg-danger"));
    }

    @Test
    void settingsChangesAreAuditedWithActorAndKey() throws Exception {
        MockHttpSession session = admin();
        String key = "kiosk.rd2_audit_probe";
        try {
            mockMvc.perform(post("/admin/settings").session(session).with(csrf())
                            .param("key", key).param("value", "probe-value"))
                    .andExpect(status().is3xxRedirection());

            var events = auditEventRepository.findTop10ByOrderByOccurredAtDesc().stream()
                    .filter(e -> e.getEventType().name().equals("SETTINGS_CHANGE"))
                    .filter(e -> e.getOperation() != null && e.getOperation().contains(key))
                    .toList();
            assertThat(events).isNotEmpty();
            assertThat(events.get(0).getActorUsername()).isEqualTo("admin");
            assertThat(events.get(0).getOutcome().name()).isEqualTo("SUCCESS");
            // The setting VALUE must never appear in the audit event.
            assertThat(events.get(0).getOperation() + "|" + events.get(0).getResourceType())
                    .doesNotContain("probe-value");
        } finally {
            settingRepository.findByKey(key).ifPresent(settingRepository::delete);
        }
    }

    @Test
    void settingDeletionIsAudited() throws Exception {
        MockHttpSession session = admin();
        String key = "kiosk.rd2_delete_probe";
        settingRepository.save(new SystemSetting(key, "to-be-removed", null));

        mockMvc.perform(post("/admin/settings/delete").session(session).with(csrf())
                        .param("key", key))
                .andExpect(status().is3xxRedirection());

        var deleteEvents = auditEventRepository.findTop10ByOrderByOccurredAtDesc().stream()
                .filter(e -> e.getEventType().name().equals("SETTINGS_CHANGE"))
                .filter(e -> e.getOperation() != null && e.getOperation().startsWith("DELETE " + key))
                .toList();
        assertThat(deleteEvents).isNotEmpty();
        assertThat(settingRepository.findByKey(key)).isEmpty();
    }
}
