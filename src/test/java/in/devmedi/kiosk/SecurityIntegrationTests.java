package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.repository.UserRepository;
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
class SecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

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
    void contextLoads() {
    }

    @Test
    void publicHomePageIsAccessibleAnonymously() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MediKiosk")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sign in")));
    }

    @Test
    void anonymousAccessToPatientAreaIsBlocked() throws Exception {
        mockMvc.perform(get("/patient/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void anonymousAccessToPhysicianAreaIsBlocked() throws Exception {
        mockMvc.perform(get("/physician/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void patientLoginRedirectsToPatientHome() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "patient")
                        .param("password", "patient123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/patient/home"));
    }

    @Test
    void physicianLoginRedirectsToPhysicianHome() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "physician")
                        .param("password", "physician123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/physician/home"));
    }

    @Test
    void adminLoginRedirectsToAdminHome() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"));
    }

    @Test
    void invalidLoginShowsError() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "patient")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    @Test
    void invalidKnownUsernameShowsError() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "no-such-user")
                        .param("password", "whatever")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "patient")
                        .param("password", "patient123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validPatientCanAccessPatientHome() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(get("/patient/home").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Demo Patient")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Patient Home")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Patient")));
    }

    @Test
    void validPhysicianCanAccessPhysicianHome() throws Exception {
        MockHttpSession session = login("physician", "physician123");
        mockMvc.perform(get("/physician/home").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dr. Demo Physician")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Physician Dashboard")));
    }

    @Test
    void validAdminCanAccessAdminHome() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(get("/admin/home").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MediKiosk Administrator")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Administration")));
    }

    @Test
    void patientCannotAccessPhysicianHome() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(get("/physician/home").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void physicianCannotAccessPatientHome() throws Exception {
        MockHttpSession session = login("physician", "physician123");
        mockMvc.perform(get("/patient/home").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotAccessPatientHome() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(get("/patient/home").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotAccessPhysicianHome() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(get("/physician/home").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        MvcResult logout = mockMvc.perform(post("/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andReturn();
        assertThat(logout.getRequest().getSession(false)).isNull();

        mockMvc.perform(get("/patient/home").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void passwordIsNotStoredAsPlaintext() {
        var patient = userRepository.findByUsername("patient").orElseThrow();
        assertThat(patient.getPassword()).isNotEqualTo("patient123");
        assertThat(patient.getPassword()).startsWith("$2");

        var physician = userRepository.findByUsername("physician").orElseThrow();
        assertThat(physician.getPassword()).isNotEqualTo("physician123");
        assertThat(physician.getPassword()).startsWith("$2");

        var admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.getPassword()).isNotEqualTo("admin123");
        assertThat(admin.getPassword()).startsWith("$2");
    }

    @Test
    void healthEndpointStillWorks() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"UP\"")));
    }
}