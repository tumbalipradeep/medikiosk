package in.devmedi.kiosk.module.voice.language;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageServiceTests {

    private final LanguageService service = new LanguageService();

    @Test
    void supportsExactlyTheFivePatientLanguagesInCanonicalOrder() {
        List<SupportedLanguage> languages = service.supportedLanguages();

        assertThat(languages).extracting(SupportedLanguage::code)
                .containsExactly("en-IN", "hi-IN", "te-IN", "ta-IN", "kn-IN");
        assertThat(languages).extracting(SupportedLanguage::label)
                .containsExactly("English", "Hindi", "Telugu", "Tamil", "Kannada");
    }

    @Test
    void eachLanguageExposesCorrectBcp47Tag() {
        assertThat(service.bcp47("en-IN")).isEqualTo("en-IN");
        assertThat(service.bcp47("hi-IN")).isEqualTo("hi-IN");
        assertThat(service.bcp47("te-IN")).isEqualTo("te-IN");
        assertThat(service.bcp47("ta-IN")).isEqualTo("ta-IN");
        assertThat(service.bcp47("kn-IN")).isEqualTo("kn-IN");
    }

    @Test
    void englishIsTheDeterministicDefault() {
        assertThat(service.resolve(null)).isEqualTo(SupportedLanguage.ENGLISH);
        assertThat(service.resolve("")).isEqualTo(SupportedLanguage.ENGLISH);
        assertThat(service.resolve("   ")).isEqualTo(SupportedLanguage.ENGLISH);
        assertThat(service.bcp47(null)).isEqualTo("en-IN");
    }

    @Test
    void codesAreMatchedCaseInsensitivelyAndTrimmed() {
        assertThat(service.resolve("TE-IN")).isEqualTo(SupportedLanguage.TELUGU);
        assertThat(service.resolve(" kn-in ")).isEqualTo(SupportedLanguage.KANNADA);
        assertThat(service.isSupported("ta-in")).isTrue();
    }

    @Test
    void unsupportedLanguageIsRejectedNotSilentlyAccepted() {
        assertThat(service.isSupported("xx")).isFalse();
        assertThat(service.isSupported("en")).isFalse();
        assertThat(service.find("xx")).isEmpty();

        assertThatThrownBy(() -> service.resolve("xx"))
                .isInstanceOf(UnsupportedLanguageException.class)
                .hasMessageContaining("xx");
    }

    @Test
    void findReturnsTheLanguageForSupportedCodes() {
        assertThat(service.find("hi-IN")).contains(SupportedLanguage.HINDI);
        assertThat(service.find(null)).isEmpty();
        assertThat(service.find("")).isEmpty();
    }
}