package in.devmedi.kiosk.module.document.findings.parser;

import in.devmedi.kiosk.module.document.findings.model.Medication;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic prescription decoder.
 *
 * <p>A line is recognised as a medication when it starts with a known dosage
 * form (e.g. {@code Tab.}, {@code Syp.}) or a curated drug name, and provides
 * at least one of strength/dose/frequency/duration/route. Field values are
 * captured only when explicitly present.</p>
 */
public class MedicationsParser {

    private final MedicationNameRegistry registry;

    private static final Pattern FORM = Pattern.compile(
            "(?i)^\\s*(?:nasal\\s+spray|eye\\s+drop(?:s)?|ear\\s+drop(?:s)?|tablet(?:s)?|capsule(?:s)?|sublingual"
                    + "|suspension|injection|ointment|lozenge(?:s)?|syrup|tab(?:\\.)?|cap(?:\\.)?|syr(?:\\.)?|syp(?:\\.)?"
                    + "|susp(?:\\.)?|inj(?:\\.)?|oint(?:\\.)?|cream|gel|lotion|drop(?:s)?|sachet|packet|inhaler"
                    + "|puff(?:s)?|spray)(?=\\s|[\\-:;]|$)");
    private static final Pattern NAME_AFTER_FORM = Pattern.compile(
            "^([A-Z][A-Za-z0-9'&./-]+(?:\\s+[A-Z][A-Za-z0-9'&./-]+){0,2})");

    private static final Pattern STRENGTH = Pattern.compile(
            "\\b(\\d+(?:[.,]\\d+)?)\\s*(mcg|\u00B5g|mg|g|grams?|grammes?|kg|units?)\\b");
    private static final Pattern DOSE = Pattern.compile(
            "\\b(\\d+(?:[.,]\\d+)?)\\s*(ml|drop|drops|puff|puffs|tab\\.?|tablet|tablets|cap\\.?|capsule|capsules"
                    + "|sachet|packet|spoonful|spoon|inhalation|inhalations?)\\b");
    private static final Pattern BARE_DOSE = Pattern.compile(
            "\\b(\\d+(?:[.,]\\d+)?)\\s*(?:tab\\.?|tablet|tablets|cap\\.?|capsule|capsules|drop|drops|puff|puffs)?"
                    + "\\s*(?=(?:OD|OAD|QD|QOD|BD|BID|TD|TID|TDS|QID|QDS|HS|OM|ON|PRN|SOS|STAT)\\b)");
    private static final Pattern FREQ_CODE = Pattern.compile(
            "\\b(OD|OAD|QD|QOD|BD|BID|TD|TID|TDS|QID|QDS|HS|OM|ON|PRN|SOS|STAT)\\b");
    private static final Pattern FREQ_PHRASE = Pattern.compile(
            "(?i)\\b(once(?:\\s*a\\s*day|\\s*daily|\\s*per\\s*day)?|twice(?:\\s*a\\s*day|\\s*daily|\\s*per\\s*day)?"
                    + "|thrice(?:\\s*a\\s*day|\\s*daily|\\s*per\\s*day)?|three\\s*times(?:\\s*a\\s*day)?"
                    + "|two\\s*times(?:\\s*a\\s*day)?|four\\s*times(?:\\s*a\\s*day)?|every\\s*other\\s*day"
                    + "|at\\s*bed\\s*time|bedtime|in\\s*the\\s*morning|in\\s*the\\s*evening|at\\s*night"
                    + "|as\\s*needed|when\\s*required|after\\s*food|before\\s*food)\\b");
    private static final Pattern DURATION = Pattern.compile(
            "\\b(\\d+(?:[.,]\\d+)?)\\s*(days?|weeks?|months?)\\b");
    private static final Pattern ROUTE = Pattern.compile(
            "(?i)\\b(by\\s*mouth|orally|oral|intravenous|iv|intramuscular|im|subcutaneous|subcut|sc|sublingual"
                    + "|topical|inhaled|inhalational)\\b");

    private static final Map<String, String> FREQUENCY_MAP = Map.ofEntries(
            Map.entry("OD", "Once a day"), Map.entry("OAD", "Once a day"), Map.entry("QD", "Once a day"),
            Map.entry("QOD", "Every other day"),
            Map.entry("BD", "Twice a day"), Map.entry("BID", "Twice a day"),
            Map.entry("TD", "Three times a day"), Map.entry("TID", "Three times a day"), Map.entry("TDS", "Three times a day"),
            Map.entry("QID", "Four times a day"), Map.entry("QDS", "Four times a day"),
            Map.entry("HS", "At bedtime"), Map.entry("OM", "In the morning"), Map.entry("ON", "At night"),
            Map.entry("PRN", "As needed"), Map.entry("SOS", "When required"), Map.entry("STAT", "Immediately"));

    public MedicationsParser(MedicationNameRegistry registry) {
        this.registry = registry;
    }

    public List<Medication> parse(List<String> lines) {
        List<Medication> result = new ArrayList<>();
        for (String line : lines) {
            Matcher form = FORM.matcher(line);
            String text = line;
            boolean hasForm = form.find();
            if (hasForm) {
                text = line.substring(form.end()).trim();
            }

            String name = registry.matchAtStart(text);
            String rest = text;
            if (name != null) {
                rest = text.substring(name.length()).trim();
            } else if (hasForm) {
                Matcher nameMatcher = NAME_AFTER_FORM.matcher(text);
                if (nameMatcher.find()) {
                    name = nameMatcher.group(1).trim();
                    rest = text.substring(nameMatcher.end()).trim();
                }
            }
            if (name == null) {
                continue;
            }

            String strength = match(STRENGTH, rest);
            String dose = match(DOSE, rest);
            if (dose == null) {
                Matcher bare = BARE_DOSE.matcher(rest);
                if (bare.find()) {
                    dose = bare.group(1);
                }
            }
            String frequency = frequency(rest);
            String duration = match(DURATION, rest);
            String route = route(rest);

            if (strength == null && dose == null && frequency == null && duration == null && route == null) {
                continue;
            }
            result.add(new Medication(name, strength, dose, route, frequency, duration, line, result.size(),
                    null, null, 1));
        }
        return List.copyOf(result);
    }

    private String match(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group(0).trim();
        }
        return null;
    }

    private String frequency(String text) {
        Matcher code = FREQ_CODE.matcher(text);
        Matcher phrase = FREQ_PHRASE.matcher(text);
        int codeStart = code.find() ? code.start() : Integer.MAX_VALUE;
        int phraseStart = phrase.find() ? phrase.start() : Integer.MAX_VALUE;
        String selected;
        boolean useCode;
        if (codeStart <= phraseStart) {
            selected = codeStart == Integer.MAX_VALUE ? null : code.group(0).toUpperCase(Locale.ROOT);
            useCode = true;
        } else {
            selected = phrase.group(0);
            useCode = false;
        }
        if (selected == null) {
            return null;
        }
        if (useCode) {
            return FREQUENCY_MAP.get(selected);
        }
        return phraseLabel(selected);
    }

    private String phraseLabel(String phrase) {
        String lower = phrase.toLowerCase(Locale.ROOT);
        if (lower.startsWith("once")) {
            return "Once a day";
        }
        if (lower.startsWith("twice") || lower.startsWith("two times")) {
            return "Twice a day";
        }
        if (lower.startsWith("thrice") || lower.startsWith("three times")) {
            return "Three times a day";
        }
        if (lower.startsWith("four times")) {
            return "Four times a day";
        }
        if (lower.contains("every other day")) {
            return "Every other day";
        }
        if (lower.contains("bed")) {
            return "At bedtime";
        }
        if (lower.contains("morning")) {
            return "In the morning";
        }
        if (lower.contains("evening")) {
            return "In the evening";
        }
        if (lower.contains("night")) {
            return "At night";
        }
        if (lower.contains("as needed")) {
            return "As needed";
        }
        if (lower.contains("when required")) {
            return "When required";
        }
        if (lower.contains("after food")) {
            return "After food";
        }
        if (lower.contains("before food")) {
            return "Before food";
        }
        return phrase;
    }

    private String route(String text) {
        Matcher m = ROUTE.matcher(text);
        if (!m.find()) {
            return null;
        }
        String raw = m.group(0).toLowerCase(Locale.ROOT);
        if (raw.contains("mouth") || raw.equals("oral") || raw.equals("orally")) {
            return "Oral";
        }
        if (raw.equals("iv") || raw.equals("intravenous")) {
            return "Intravenous";
        }
        if (raw.equals("im") || raw.equals("intramuscular")) {
            return "Intramuscular";
        }
        if (raw.equals("sc") || raw.equals("subcut") || raw.equals("subcutaneous")) {
            return "Subcutaneous";
        }
        if (raw.equals("sublingual")) {
            return "Sublingual";
        }
        if (raw.equals("topical")) {
            return "Topical";
        }
        if (raw.equals("inhaled") || raw.equals("inhalational")) {
            return "Inhalation";
        }
        return null;
    }
}