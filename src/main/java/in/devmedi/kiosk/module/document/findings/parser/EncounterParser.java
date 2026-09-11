package in.devmedi.kiosk.module.document.findings.parser;

import in.devmedi.kiosk.module.document.findings.model.EncounterMetadata;
import in.devmedi.kiosk.module.document.findings.normalize.DateParser;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic decoder of encounter/report metadata (report date, encounter
 * date, facility, clinician, report type) from the header of a clinical
 * document.
 */
public class EncounterParser {

    private static final String DATE_GROUP =
            "(?<date>\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}|\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/. ]+\\p{L}{3,9}[-/., ]*\\d{2,4})";

    private static final Pattern REPORT_DATE = Pattern.compile(
            "(?i)\\b(?:report(?:ed)?\\s*(?:date|on|dated)?|result\\s*(?:date|dated)|date\\s*of\\s*report|test(?:ed)?\\s*(?:date|on)?)"
                    + "\\s*[:.\\-]?\\s*" + DATE_GROUP);
    private static final Pattern ENCOUNTER_DATE = Pattern.compile(
            "(?i)\\b(?:encounter\\s*date|visit\\s*date|consultation\\s*date|registration\\s*date|admission\\s*date"
                    + "|date\\s*of\\s*(?:visit|encounter|consultation|admission|recording)|specimen\\s*(?:date|collected\\s*on)"
                    + "|sample\\s*(?:date|collected\\s*on)|blood\\s*drawn\\s*on|collected\\s*on|drawn\\s*on)"
                    + "\\s*[:.\\-]?\\s*" + DATE_GROUP);
    private static final Pattern FACILITY = Pattern.compile(
            "(?i)\\b(?:facility|hospital|clinic|pathology(?:\\s*lab)?|diagnostic\\s*(?:center|centre)|laborator(?:y|ies)|centre)"
                    + "\\s*[:.\\-]\\s*([A-Za-z][A-Za-z0-9 .,'&()\\-]{4,80})");
    private static final Pattern CLINICIAN = Pattern.compile(
            "(?i)\\b(?:referring\\s*(?:doctor|physician|consultant|practitioner)|treating\\s*(?:doctor|physician)"
                    + "|attending\\s*(?:doctor|physician|consultant)|consultant|doctor|physician|practitioner"
                    + "|reporting\\s*pathologist|pathologist|radiologist)"
                    + "\\s*[:.\\-]?\\s*((?:dr\\.?\\s*)?[A-Za-z][A-Za-z .'-]{2,70})");
    private static final Pattern REPORT_TYPE = Pattern.compile(
            "(?i)\\b(?:report\\s*type|type\\s*of\\s*report|test\\s*(?:requested|name)?|investigation(?:s)?\\s*(?:requested|performed|done)?)"
                    + "\\s*[:.\\-]\\s*([A-Za-z][A-Za-z0-9 .,()&\\-]{2,80})");

    private static final List<String> REPORT_HEADINGS = List.of(
            "label report", "blood test report", "cbc report", "complete blood count",
            "diabetic profile", "thyroid profile", "liver function test", "renal function test",
            "kidney function test", "lipid profile", "lipid panel", "cardiac profile",
            "iron profile", "biochemical profile", "hba1c report", "hormone profile",
            "urine routine", "urine examination", "complete urine examination", "cbc",
            "fasting blood sugar", "cardiac marker", "troponin", "lft", "rft", "semen analysis");

    private static final Pattern FACILITY_HEADING = Pattern.compile("^[A-Z][A-Z0-9 .,&'()\\-]{8,100}$");

    public EncounterMetadata parse(List<String> lines) {
        String reportDate = null;
        String encounterDate = null;
        String facility = null;
        String clinician = null;
        String reportType = null;

        for (String line : lines) {
            if (reportDate == null) {
                Matcher m = REPORT_DATE.matcher(line);
                if (m.find()) {
                    reportDate = DateParser.parse(m.group("date")).orElse(null);
                }
            }
            if (encounterDate == null) {
                Matcher m = ENCOUNTER_DATE.matcher(line);
                if (m.find()) {
                    encounterDate = DateParser.parse(m.group("date")).orElse(null);
                }
            }
            if (facility == null) {
                Matcher m = FACILITY.matcher(line);
                if (m.find()) {
                    String candidate = m.group(1).trim().replaceAll("[\\],;]$", "");
                    if (!candidate.isBlank()) {
                        facility = candidate;
                    }
                }
            }
            if (clinician == null) {
                Matcher m = CLINICIAN.matcher(line);
                if (m.find()) {
                    String candidate = m.group(1).trim().replaceAll("[\\],;]$", "");
                    if (isClinicianName(candidate)) {
                        clinician = candidate;
                    }
                }
            }
            if (reportType == null) {
                Matcher m = REPORT_TYPE.matcher(line);
                if (m.find()) {
                    reportType = m.group(1).trim();
                }
            }
        }

        if (facility == null) {
            facility = fallbackFacilityHeading(lines);
        }
        if (reportType == null) {
            reportType = fallbackReportHeading(lines);
        }

        if (reportDate == null && encounterDate == null && facility == null
                && clinician == null && reportType == null) {
            return null;
        }
        return new EncounterMetadata(reportDate, encounterDate, facility, clinician, reportType);
    }

    private static boolean isClinicianName(String candidate) {
        if (!(candidate.startsWith("Dr") || Character.isUpperCase(candidate.charAt(0)))) {
            return false;
        }
        String lower = candidate.toLowerCase();
        return !(lower.contains("on duty") || lower.contains("in charge") || lower.contains("on call")
                || lower.contains("on leave") || lower.startsWith("at ") || lower.startsWith("in "));
    }

    private static String fallbackFacilityHeading(List<String> lines) {
        for (String line : lines) {
            if (FACILITY_HEADING.matcher(line).matches()) {
                String lower = line.toLowerCase();
                if (containsAny(lower, "hospital", "clinic", "pathology", "laboratory", "laboratories",
                        "institute", "center", "centre", "nursing", "care")) {
                    if (!containsAny(lower, "report", "vital", "result", "medication", "patient",
                            "investigation", "record", "address")) {
                        return line;
                    }
                }
            }
        }
        return null;
    }

    private static String fallbackReportHeading(List<String> lines) {
        for (String line : lines) {
            String lower = line.trim().toLowerCase().replaceAll("\\s+", " ");
            if (containsAny(lower, REPORT_HEADINGS)) {
                return line.trim();
            }
        }
        return null;
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, List<String> tokens) {
        for (String token : tokens) {
            if (text.equals(token) || text.startsWith(token) || text.contains(" " + token)) {
                return true;
            }
        }
        return false;
    }
}