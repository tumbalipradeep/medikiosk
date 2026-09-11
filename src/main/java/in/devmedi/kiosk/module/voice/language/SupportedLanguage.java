package in.devmedi.kiosk.module.voice.language;

/**
 * The fixed set of patient-facing languages MediKiosk supports.
 *
 * <p>Every supported language is identified by a stable, explicit code and a
 * BCP-47 language tag. Codes and tags are intentionally identical for the
 * current set, but they are kept as separate fields so the model stays
 * explicit and future additions (for example regional variants) do not blur
 * the two concepts.</p>
 *
 * <p>English is the deterministic default; no other value is ever assumed.
 * Values are a closed set — arbitrary language codes are rejected by
 * {@link LanguageService} rather than silently accepted.</p>
 */
public enum SupportedLanguage {

    ENGLISH("en-IN", "English", "English", "en-IN"),
    HINDI("hi-IN", "Hindi", "हिन्दी", "hi-IN"),
    TELUGU("te-IN", "Telugu", "తెలుగు", "te-IN"),
    TAMIL("ta-IN", "Tamil", "தமிழ்", "ta-IN"),
    KANNADA("kn-IN", "Kannada", "ಕನ್ನಡ", "kn-IN");

    private final String code;
    private final String label;
    private final String nativeName;
    private final String bcp47;

    SupportedLanguage(String code, String label, String nativeName, String bcp47) {
        this.code = code;
        this.label = label;
        this.nativeName = nativeName;
        this.bcp47 = bcp47;
    }

    /** Stable, machine-readable language code (also used as the session key). */
    public String code() {
        return code;
    }

    /** Human-readable display label, safe for the patient UI. */
    public String label() {
        return label;
    }

    /** The language's name written in its own script (for example "हिन्दी"). */
    public String nativeName() {
        return nativeName;
    }

    /** BCP-47 language tag (for example for TTS voices or ASR language hints). */
    public String bcp47() {
        return bcp47;
    }
}