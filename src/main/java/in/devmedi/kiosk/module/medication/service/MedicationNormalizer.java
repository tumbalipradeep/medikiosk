package in.devmedi.kiosk.module.medication.service;

import in.devmedi.kiosk.module.document.findings.parser.MedicationNameRegistry;
import in.devmedi.kiosk.module.medication.entity.MedicationProfile;
import in.devmedi.kiosk.module.medication.enums.MedicationSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure, deterministic resolver that turns raw medication text into detected
 * medicines. Free-text entries (patient history) are split into chunks and each
 * chunk is scanned for the earliest known drug name; document-finding medicine
 * names are resolved the same way against the same vocabulary.
 *
 * <p>Recognition vocabulary is data-driven: canonical profile names and their
 * aliases are merged with the curated {@link MedicationNameRegistry} drug
 * list, longest-first, so multi-word drugs win over their prefixes. This
 * component never guesses a class or a rule — it only maps text onto known
 * identities; everything downstream (interactions, guidance) lives in the
 * seeded rule data.</p>
 */
@Component
public class MedicationNormalizer {

    private final MedicationNameRegistry registry;

    public MedicationNormalizer(MedicationNameRegistry registry) {
        this.registry = registry;
    }

    /**
     * @return all recognizable drug names (profile canonicals + aliases +
     *         registry names), longest first
     */
    public List<String> mergedNames(List<MedicationProfile> profiles) {
        Set<String> names = new LinkedHashSet<>();
        if (profiles != null) {
            for (MedicationProfile profile : profiles) {
                addProfileNames(names, profile);
            }
        }
        names.addAll(registry.sortedNames());
        return new ArrayList<>(names);
    }

    private void addProfileNames(Set<String> names, MedicationProfile profile) {
        if (profile == null || profile.getCanonicalName() == null || profile.getCanonicalName().isBlank()) {
            return;
        }
        names.add(profile.getCanonicalName().trim());
        if (profile.getAliases() != null) {
            for (String alias : profile.getAliases().split(",")) {
                if (alias != null && !alias.isBlank()) {
                    names.add(alias.trim());
                }
            }
        }
    }

    /**
     * Resolves free-text medication lines (e.g. a patient history entry) into
     * zero or more detected medicines.
     */
    public List<DetectedMedication> resolveFreeText(String text, List<MedicationProfile> profiles) {
        List<DetectedMedication> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        List<String> names = mergedNames(profiles);
        for (String chunk : splitChunks(text)) {
            DetectedMedication detected = resolveSingle(chunk, profiles, names);
            if (detected != null) {
                out.add(detected);
            }
        }
        return out;
    }

    /**
     * Resolves a single medicine name (document finding or physician overlay).
     * Returns {@code null} only when the input is blank or cleans to nothing.
     */
    public DetectedMedication resolveSingle(String name, List<MedicationProfile> profiles) {
        return resolveSingle(name, profiles, mergedNames(profiles));
    }

    private DetectedMedication resolveSingle(String raw, List<MedicationProfile> profiles, List<String> names) {
        if (raw == null) {
            return null;
        }
        String cleaned = stripLeadingJunk(raw);
        if (cleaned.isBlank()) {
            return null;
        }
        Match match = findFirstMatch(cleaned, names);
        if (match == null) {
            String display = cleanUnmatched(cleaned);
            if (display.isBlank()) {
                return null;
            }
            return new DetectedMedication(raw.trim(), display, null, null, MedicationSource.PATIENT_HISTORY);
        }
        MedicationProfile profile = profileFor(match.name(), profiles);
        String display = match.slice();
        return new DetectedMedication(
                raw.trim(),
                display,
                profile == null ? null : profile.getCanonicalName(),
                profile == null ? null : profile.getClassKey(),
                MedicationSource.PATIENT_HISTORY);
    }

    /**
     * Splits free text on common separators so each chunk carries a single
     * medicine: newlines, commas, semicolons, slashes, plus, ampersand and the
     * words {@code and}/{@code plus}, plus the sentence full-stop.
     */
    static List<String> splitChunks(String text) {
        List<String> chunks = new ArrayList<>();
        String normalized = text
                .replaceAll("(?i)\\b and \\b", ",")
                .replaceAll("(?i)\\b plus \\b", ",")
                .replaceAll("&", ",")
                .replaceAll("[\\n,;/+.]", ",");
        for (String part : normalized.split(",")) {
            if (part != null && !part.isBlank()) {
                chunks.add(part);
            }
        }
        return chunks;
    }

    private record Match(int start, String name) {
        String slice() {
            return name;
        }
    }

    /**
     * Finds the earliest known drug name within a chunk. Only word-boundary
     * positions are considered so {@code Aspirin} is not matched inside
     * {@code hisaspirin} or {@code antiplatelet aspirin} handled later.
     */
    private Match findFirstMatch(String chunk, List<String> names) {
        for (int start = 0; start < chunk.length(); start++) {
            if (start > 0 && Character.isLetterOrDigit(chunk.charAt(start - 1))) {
                continue;
            }
            String suffix = chunk.substring(start);
            String matched = matchAtStart(suffix, names);
            if (matched != null) {
                return new Match(start, matched);
            }
        }
        return null;
    }

    private String matchAtStart(String text, List<String> names) {
        String t = text.trim();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (t.regionMatches(true, 0, name, 0, name.length())) {
                int end = name.length();
                if (end == t.length() || Character.isWhitespace(t.charAt(end)) || isBoundary(t.charAt(end))) {
                    return t.substring(0, end).trim();
                }
            }
        }
        return null;
    }

    private boolean isBoundary(char c) {
        return c == ',' || c == ';' || c == '-' || c == '/' || c == '.' || c == '(' || c == ')';
    }

    private MedicationProfile profileFor(String matchedName, List<MedicationProfile> profiles) {
        if (profiles == null || matchedName == null) {
            return null;
        }
        for (MedicationProfile profile : profiles) {
            if (profile.getCanonicalName() != null
                    && profile.getCanonicalName().equalsIgnoreCase(matchedName.trim())) {
                return profile;
            }
            if (profile.getAliases() != null) {
                for (String alias : profile.getAliases().split(",")) {
                    if (alias != null && alias.trim().equalsIgnoreCase(matchedName.trim())) {
                        return profile;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Removes leading dosage-form and numeric junk so the drug name can start
     * the chunk ({@code Tab. Aspirin} → {@code Aspirin}).
     */
    private String stripLeadingJunk(String raw) {
        String t = raw.trim();
        boolean changed;
        do {
            changed = false;
            for (String form : MedicationNameRegistry.DOSAGE_FORMS) {
                if (t.regionMatches(true, 0, form, 0, form.length())
                        && form.length() < t.length()
                        && (Character.isWhitespace(t.charAt(form.length()))
                        || t.charAt(form.length()) == ',' || t.charAt(form.length()) == '.')) {
                    t = t.substring(form.length()).trim();
                    changed = true;
                    break;
                }
            }
            String stripped = t.replaceFirst("^[\\s,./;:()\\-]*", "");
            if (!stripped.equals(t)) {
                t = stripped;
                changed = true;
            }
        } while (changed);
        return t;
    }

    /**
     * Cleans an unrecognized chunk for display: drops trailing dose/frequency
     * words ({@code mg}, {@code mcg}, {@code ml}, counts, unit markers) and
     * stray separators so the physician sees the medicine name, not the whole
     * line.
     */
    private String cleanUnmatched(String chunk) {
        String t = chunk.trim();
        boolean changed;
        do {
            changed = false;
            String trimmed = t;
            while (trimmed.endsWith(",") || trimmed.endsWith(";") || trimmed.endsWith(".")
                    || trimmed.endsWith(")") || trimmed.endsWith("(")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            }
            String[] words = trimmed.split("\\s+");
            if (words.length > 1) {
                String last = words[words.length - 1].toLowerCase(Locale.ROOT);
                if (last.matches("(\\d+(\\.\\d+)?|\\d+\\s*(mg|mcg|g|ml|unit|units|gtt|drop|drops|tab|caps|capsule|capsules|tablet|tablets|sachet|puff)s?)")) {
                    trimmed = trimmed.substring(0, trimmed.length() - words[words.length - 1].length()).trim();
                    changed = true;
                }
            }
            t = trimmed;
        } while (changed);
        return t;
    }
}