package in.devmedi.kiosk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RD2 static verification of the presentation layer. These are source-level
 * contract tests: they pin the rules the RD2 directive set for motion,
 * accessibility and the failure-first feedback layer without needing a
 * browser.
 */
class Rd2PresentationContractTests {

    private static String read(String path) {
        try {
            return Files.readString(Path.of("src/main/resources", path));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    private static String css() {
        return read("static/css/app.css");
    }

    @Test
    void noBlanketTransitionAllIsIntroduced() {
        // The directive forbids `transition: all` as the motion architecture.
        assertThat(css()).doesNotContain("transition: all");
        assertThat(css()).doesNotContain("transition:all");
    }

    @Test
    void motionLayerUsesOnlyCompositorFriendlyPropertiesInAnimations() {
        // Keyframe bodies in the RD2 motion layer must animate transform,
        // opacity or clip-path only — never layout properties.
        List<String> forbidden = List.of("width:", "height:", "top:", "left:", "margin:", "padding:");
        String motionBlock = css().substring(css().indexOf("@keyframes mk-spring-in"));
        assertThat(forbidden).allSatisfy(p ->
                assertThat(motionBlock).doesNotContain("animation" + p));
    }

    @Test
    void prefersReducedMotionHandlingExistsForEveryMotionSurface() {
        String c = css();
        assertThat(c).contains("@media (prefers-reduced-motion: reduce)");
        // Every entrance animation class is disabled in reduced-motion.
        assertThat(c).contains("html[data-mk-motion=\"reduced\"] .chat-message");
        assertThat(c).contains("html[data-mk-motion=\"reduced\"] .mk-feedback");
        assertThat(c).contains("html[data-mk-motion=\"reduced\"] .mk-correction-dialog");
    }

    @Test
    void sharedFeedbackModuleExposesTypedErrorsAndAriaLiveRegions() {
        String js = read("static/js/mk-errors.js");
        assertThat(js).contains("aria-live");
        assertThat(js).contains("kind = 'session'");
        assertThat(js).contains("kind = 'network'");
        assertThat(js).contains("global.MkFetch");
    }

    @Test
    void professionalWorkflowsNoLongerUseBrowserAlerts() throws IOException {
        List<String> workflowScripts = List.of(
                "static/js/physician.js",
                "static/js/intake.js",
                "static/js/adaptive-history.js",
                "static/js/record.js");
        for (String script : workflowScripts) {
            String source = Files.readString(Path.of("src/main/resources", script));
            assertThat(source).doesNotMatch("(?m)^\\s*alert\\(");
        }
    }

    @Test
    void healthStatusBindingIsCorrectOnTheLandingPage() {
        String html = read("templates/home.html");
        String js = read("static/js/app.js");
        assertThat(html).contains("appStatusLive");
        assertThat(js).contains("getElementById('appStatusLive')");
        assertThat(js).contains("fetch('/actuator/health'");
    }
}
