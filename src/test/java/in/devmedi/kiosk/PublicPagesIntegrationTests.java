package in.devmedi.kiosk;

import in.devmedi.kiosk.module.ai.provider.AiFailoverService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RD1 public website: the landing page plus the normal informational pages
 * (about, features, privacy, contact). Every page must render anonymously,
 * carry honest capability language, and keep demo-mode claims non-personal.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicPagesIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiFailoverService aiFailoverService;

    @Test
    void landingPageRendersTheFullProductStory() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("MediKiosk")))
                .andExpect(content().string(containsString("Sign in")))
                .andExpect(content().string(containsString("Patient Case-Taking Platform")))
                .andExpect(content().string(containsString("Local demo deployment")))
                .andExpect(content().string(containsString("Application Status:")))
                .andExpect(content().string(containsString("The journey")))
                .andExpect(content().string(containsString("Trust &amp; safety")))
                .andExpect(content().string(containsString("NOT_IMPLEMENTED")));
    }

    @Test
    void aboutPageIsPublicAndNamesTheProblemStatement() throws Exception {
        mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("About MediKiosk")))
                .andExpect(content().string(containsString("SIH26047")))
                .andExpect(content().string(containsString("autonomous diagnostician")));
    }

    @Test
    void featuresPageIsHonestAboutUnavailableCapabilities() throws Exception {
        mockMvc.perform(get("/features"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Implemented today")))
                .andExpect(content().string(containsString("NOT_IMPLEMENTED")))
                .andExpect(content().string(containsString("local-only")));
    }

    @Test
    void privacyPageDescribesActualDataHandling() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Privacy")))
                .andExpect(content().string(containsString("Local kiosk identity")))
                .andExpect(content().string(containsString("No ABDM / HIS transmission")))
                .andExpect(content().string(containsString("no-referrer")));
    }

    @Test
    void contactPageShowsTheOperatorConfiguredSupportEmail() throws Exception {
        mockMvc.perform(get("/contact"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Contact")))
                .andExpect(content().string(containsString("support@medikiosk.local")));
    }

    @Test
    void publicPagesRemainAvailableToAuthenticatedUsers() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(get("/about").session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/features").session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/privacy").session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/contact").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void registeredAuthenticatedUserStillRedirectsHomeAwayFromLanding() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(get("/").session(session))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void landingAiBadgeIsDerivedFromTheBoundProvidersNotHardCoded() throws Exception {
        boolean anyEnabled = aiFailoverService.hasEnabledProviders();
        String expectedBadge = anyEnabled ? "Configured" : "Depending on key";

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Conversational AI (credential-gated, fallback offline)")))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).contains(expectedBadge);
    }

    @Test
    void landingApplicationStatusBadgeCarriesTheIdTheStatusScriptTargets() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"appStatusLive\"")))
                .andExpect(content().string(containsString("Application Status:")));

        // JS contract: the status script targets the id that actually renders.
        String js = classpathAppJs();
        org.assertj.core.api.Assertions.assertThat(js)
                .contains("getElementById('appStatusLive')")
                .doesNotContain("getElementById('appStatus')");
    }

    @Test
    void statusScriptFetchesHealthFromTheApplicationRoot() throws Exception {
        // G2 contract: the health fetch is absolute, so it cannot break if the
        // script is ever served from a nested page.
        String js = classpathAppJs();
        org.assertj.core.api.Assertions.assertThat(js)
                .contains("fetch('/actuator/health'")
                .doesNotContain("fetch('actuator/health'");
    }

    @Test
    void aiCapabilityEndpointMatchesTheLandingDerivation() throws Exception {
        // The anonymous capability API and the landing badge must agree.
        boolean anyEnabled = aiFailoverService.hasEnabledProviders();
        mockMvc.perform(get("/api/capabilities/ai"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.anyProviderEnabled").value(anyEnabled));
    }

    private String classpathAppJs() throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream("static/js/app.js")) {
            org.assertj.core.api.Assertions.assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
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