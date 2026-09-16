package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
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
 * Regression coverage for the simplified password policy: the only mandatory
 * rule is minimum length (6). No character-class requirement is enforced, and
 * authentication/role-assignment behavior must be unaffected by the change.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordPolicyIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void register(String username, String password) throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", username)
                        .param("displayName", "Password Policy Test")
                        .param("password", password)
                        .param("confirmPassword", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    @Test
    void sixCharacterPasswordIsAccepted() throws Exception {
        String username = unique("pw6");
        register(username, "abcdef");
        User user = userRepository.findByUsername(username).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.PATIENT);
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void fiveCharacterPasswordIsRejected() throws Exception {
        String username = unique("pw5");
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", username)
                        .param("displayName", "Five Char")
                        .param("password", "abcde")
                        .param("confirmPassword", "abcde"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("must be at least 6")));

        assertThat(userRepository.findByUsername(username)).isEmpty();
    }

    @Test
    void lettersOnlyPasswordIsAccepted() throws Exception {
        String username = unique("pwletters");
        register(username, "ghijkl");
        assertThat(userRepository.findByUsername(username)).isPresent();
    }

    @Test
    void digitsOnlyPasswordIsAccepted() throws Exception {
        String username = unique("pwdigits");
        register(username, "123456");
        assertThat(userRepository.findByUsername(username)).isPresent();
    }

    @Test
    void mixedSimplePasswordsAreAccepted() throws Exception {
        String first = unique("pwmix1");
        String second = unique("pwmix2");
        register(first, "hello1");
        register(second, "a1b2c3");
        assertThat(userRepository.findByUsername(first)).isPresent();
        assertThat(userRepository.findByUsername(second)).isPresent();
    }

    @Test
    void specialOnlyPasswordIsAccepted() throws Exception {
        String username = unique("pwspecial");
        register(username, "!!!!!!");
        assertThat(userRepository.findByUsername(username)).isPresent();
    }

    @Test
    void sixCharacterPasswordCanAuthenticate() throws Exception {
        String username = unique("pwauth");
        register(username, "abcdef");

        MockHttpSession session = login(username, "abcdef");
        mockMvc.perform(get("/patient/home").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordAcceptsSixCharacterPassword() throws Exception {
        String username = unique("pwchange");
        register(username, "oldpass1");
        MockHttpSession session = login(username, "oldpass1");

        mockMvc.perform(post("/account/password").with(csrf()).session(session)
                        .param("currentPassword", "oldpass1")
                        .param("newPassword", "newpw1")
                        .param("confirmPassword", "newpw1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/sessions?passwordUpdated=true"));

        // The new 6-character password must authenticate.
        login(username, "newpw1");
    }

    @Test
    void changePasswordStillRejectsTooShortPassword() throws Exception {
        String username = unique("pwshort");
        register(username, "oldpass1");
        MockHttpSession session = login(username, "oldpass1");

        mockMvc.perform(post("/account/password").with(csrf()).session(session)
                        .param("currentPassword", "oldpass1")
                        .param("newPassword", "abc")
                        .param("confirmPassword", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("must be at least 6")));
    }

    @Test
    void simplePasswordCannotEscalateRole() throws Exception {
        String username = unique("pwrole");
        register(username, "abcdef");
        assertThat(userRepository.findByUsername(username).orElseThrow().getRole())
                .isEqualTo(Role.PATIENT);
    }
}
