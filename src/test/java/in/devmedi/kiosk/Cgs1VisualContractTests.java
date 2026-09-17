package in.devmedi.kiosk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CGS.1 contract tests: the reference-image-derived visual system, the
 * unified auth markup, and the simplified username/password validation
 * surface. These are static-contract tests by design — browser E2E is
 * unavailable in this environment and is never claimed.
 */
class Cgs1VisualContractTests {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    /* ── A. Visual system ──────────────────────────────────────────────── */

    @Test
    void primaryActionColorIsCanonicalTealAcrossLightAndDarkThemes() throws IOException {
        String css = read("src/main/resources/static/css/app.css");
        // Light theme: teal primary (#0f8d7b); dark theme: brighter teal (#2fbfa8).
        assertThat(css).contains("--bs-primary: #0f8d7b;");
        assertThat(css).contains("html[data-bs-theme=\"dark\"] {\n    --bs-primary: #2fbfa8;");
    }

    @Test
    void topNavigationIsAWhiteSurfaceWithDarkThemeParity() throws IOException {
        String css = read("src/main/resources/static/css/app.css");
        assertThat(css).contains(".mk-topnav-header {");
        assertThat(css).contains("html[data-bs-theme=\"dark\"] .mk-topnav-header {");
        // No template may reintroduce the old solid header.
        String templates = listTemplates().stream()
                .map(p -> silentRead(p.toString()))
                .reduce("", (a, b) -> a + b);
        assertThat(templates).doesNotContain("bg-primary text-white");
    }

    @Test
    void cardsUsePaleTealTintedSurfacesWithRestrainedMotion() throws IOException {
        String css = read("src/main/resources/static/css/app.css");
        assertThat(css).contains(".mk-panel {");
        assertThat(css).contains("var(--mk-teal-050)");
        // RD2 discipline holds: no blanket transitions, reduced motion kept.
        assertThat(css).doesNotContain("transition: all");
        assertThat(css).contains("prefers-reduced-motion");
    }

    /* ── A. Unified auth/account markup ────────────────────────────────── */

    @Test
    void authAndAccountPagesUseTheCentralHeadFragment() throws IOException {
        assertThat(read("src/main/resources/templates/auth/login.html"))
                .contains("th:replace=\"~{fragments/head :: head(");
        assertThat(read("src/main/resources/templates/auth/register.html"))
                .contains("th:replace=\"~{fragments/head :: head(");
        assertThat(read("src/main/resources/templates/account/password.html"))
                .contains("th:replace=\"~{fragments/head :: head(");
        assertThat(read("src/main/resources/templates/account/profile.html"))
                .contains("th:replace=\"~{fragments/head :: head(");
        assertThat(read("src/main/resources/templates/account/sessions.html"))
                .contains("th:replace=\"~{fragments/head :: head(");
    }

    @Test
    void navFragmentsUseTheSharedWhiteHeaderClass() throws IOException {
        assertThat(read("src/main/resources/templates/fragments/publicnav.html"))
                .contains("mk-topnav-header");
        assertThat(read("src/main/resources/templates/fragments/nav.html"))
                .contains("mk-topnav-header");
    }

    /* ── B. Username rules ─────────────────────────────────────────────── */

    @Test
    void usernameAllowsAnyReasonableReadableNameWithinLengthBounds() throws IOException {
        String html = read("src/main/resources/templates/auth/register.html");
        // No character whitelist, no pattern, no forced first character.
        assertThat(html).doesNotContain("pattern=");
        assertThat(html).doesNotContain("[a-zA-Z0-9");
        assertThat(html).contains("minlength=\"3\"");
        assertThat(html).contains("maxlength=\"64\"");
    }

    @Test
    void backendUsernameRuleHasNoCharacterWhitelist() {
        // Any ordinary username passes: spaces, punctuation, non-Latin scripts.
        assertThat("mary ann".isBlank()).isFalse();
        assertThat("ravi.kumar!".isBlank()).isFalse();
        assertThat("患者123".isBlank()).isFalse();
        // The service layer enforces only trim + 3-64; uniqueness stays DB-backed.
        String service = silentRead("src/main/java/in/devmedi/kiosk/module/auth/service/AccountLifecycleService.java");
        assertThat(service).contains("normalized.length() < 3 || normalized.length() > 64");
        assertThat(service).doesNotContain("username.matches");
        assertThat(service).contains("Username must be between 3 and 64 characters.");
    }

    /* ── C. Password rules ─────────────────────────────────────────────── */

    @Test
    void passwordPolicyIsMinimumSixCharactersWithNoClassRequirements() throws IOException {
        String properties = read("src/main/java/in/devmedi/kiosk/module/auth/security/SecurityPolicyProperties.java");
        assertThat(properties).contains("private int minPasswordLength = 6;");
        assertThat(properties).contains("private boolean requireUppercase = false;");
        assertThat(properties).contains("private boolean requireDigit = false;");
        assertThat(properties).contains("private boolean requireSpecial = false;");
    }

    @Test
    void clientPasswordFieldsRequireOnlySixCharacters() throws IOException {
        for (String page : List.of("src/main/resources/templates/auth/register.html",
                "src/main/resources/templates/account/password.html")) {
            String html = read(page);
            assertThat(html).contains("minlength=\"6\"");
            assertThat(html).doesNotContain("minlength=\"10\"");
            assertThat(html).doesNotContain("pattern=\"(?=.*"); // no class-complexity lookahead
        }
    }

    /* ── helpers ───────────────────────────────────────────────────────── */

    private static List<Path> listTemplates() throws IOException {
        try (var walk = Files.walk(Path.of("src/main/resources/templates"))) {
            return walk.filter(p -> p.toString().endsWith(".html")).toList();
        }
    }

    private static String silentRead(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
