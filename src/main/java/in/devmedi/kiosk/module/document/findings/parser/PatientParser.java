package in.devmedi.kiosk.module.document.findings.parser;

import in.devmedi.kiosk.module.document.findings.model.PatientIdentifiers;
import in.devmedi.kiosk.module.document.findings.normalize.DateParser;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic decoder of the patient-identity block (name, date of birth,
 * sex, medical record number) from the header of a clinical document.
 */
public class PatientParser {

    private static final Pattern NAME = Pattern.compile(
            "(?i)(?:patient(?:'?s)?|name\\s+of\\s+(?:the\\s+)?patient|pt\\.?)\\s*(?:name)?\\s*[:.\\-]\\s*([A-Za-z][A-Za-z .'-]{2,60})");
    private static final Pattern DOB = Pattern.compile(
            "(?i)\\b(?:date\\s*of\\s*birth|dob|d\\.o\\.b\\.?|\\.?birth\\s*date)\\s*[:.=\\-]?\\s*"
                    + "(?<date>\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}|\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/. ]+\\p{L}{3,9}[-/., ]*\\d{2,4})");
    private static final Pattern SEX = Pattern.compile(
            "(?i)\\b(?:gender|sex)\\s*[:.\\-]?\\s*(male|female|\\bm\\b|\\bf\\b)\\b");
    private static final Pattern MRN = Pattern.compile(
            "(?i)\\b(?:mrn|mr\\.?\\s*no\\.?|medical\\s*record\\s*(?:number|no\\.?)?|reg(?:istration)?\\.?\\s*no\\.?"
                    + "|patient\\s*(?:id|id\\.?|no\\.?|number)|cr\\.?\\s*no\\.?|uhid)\\s*[:.\\-]?\\s*([A-Za-z0-9][A-Za-z0-9/._-]{1,30})");

    public PatientIdentifiers parse(List<String> lines) {
        String name = null;
        String dob = null;
        String sex = null;
        String mrn = null;

        for (String line : lines) {
            if (name == null) {
                Matcher m = NAME.matcher(line);
                if (m.find()) {
                    String candidate = m.group(1).trim().replaceAll("[\\],;]$", "");
                    if (isName(candidate)) {
                        name = candidate;
                    }
                }
            }
            if (dob == null) {
                Matcher m = DOB.matcher(line);
                if (m.find()) {
                    dob = DateParser.parse(m.group("date")).orElse(null);
                }
            }
            if (sex == null) {
                Matcher m = SEX.matcher(line);
                if (m.find()) {
                    sex = normalizeSex(m.group(1));
                }
            }
            if (mrn == null) {
                Matcher m = MRN.matcher(line);
                if (m.find()) {
                    String candidate = m.group(1).trim().replaceAll("[\\],;.]$", "");
                    if (!candidate.isBlank()) {
                        mrn = candidate;
                    }
                }
            }
        }
        if (name == null && dob == null && sex == null && mrn == null) {
            return null;
        }
        return new PatientIdentifiers(name, dob, sex, mrn);
    }

    private static boolean isName(String candidate) {
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (List.of("male", "female", "m", "f", "n/a", "na").contains(lower)) {
            return false;
        }
        return candidate.chars().filter(Character::isAlphabetic).count() >= 3;
    }

    private static String normalizeSex(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        return (lower.equals("male") || lower.equals("m")) ? "Male" : "Female";
    }
}