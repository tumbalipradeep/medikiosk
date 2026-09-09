package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PhysicianReviewControllerIntegrationTests {

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
    void physicianCanAccessReviewPage() throws Exception {
        MockHttpSession session = login("physician", "physician123");
        mockMvc.perform(get("/physician/review").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Physician Review")))
                .andExpect(content().string(containsString("25 captured answers")));
    }

    @Test
    void patientCannotAccessReviewPage() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(get("/physician/review").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedAccessToReviewPageIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/physician/review"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void reviewPageRendersAllSectionsAndRawAnswers() throws Exception {
        MockHttpSession session = login("physician", "physician123");
        mockMvc.perform(get("/physician/review").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("History of Present Illness / SOCRATES")))
                .andExpect(content().string(containsString("Dashavidha Pariksha")))
                .andExpect(content().string(containsString("Ahara-Vihara")))
                .andExpect(content().string(containsString(
                        "Severe headache, mainly on the right side, since the day before yesterday.")))
                .andExpect(content().string(containsString(
                        "Bright light and loud noise make it worse; lying down in a dark, quiet room helps.")))
                .andExpect(content().string(containsString("Occasional evening tea; no smoking and no alcohol.")));
    }

    @Test
    void reviewPageRendersMarkReviewedControlsAndCountIndicator() throws Exception {
        MockHttpSession session = login("physician", "physician123");
        mockMvc.perform(get("/physician/review").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("reviewedCount")))
                .andExpect(content().string(containsString("mark-reviewed-btn")))
                .andExpect(content().string(containsString("Mark reviewed")));
    }
}