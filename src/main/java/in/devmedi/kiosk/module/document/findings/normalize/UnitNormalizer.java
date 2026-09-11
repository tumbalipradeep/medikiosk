package in.devmedi.kiosk.module.document.findings.normalize;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, prescriptive unit normalisation.
 *
 * <p>Only well-known equivalent spellings and case/symbol forms are mapped
 * (e.g. {@code g/dl} -&gt; {@code g/dL}, {@code kg/m2} -&gt; {@code kg/m²},
 * {@code deg F} -&gt; {@code °F}). Units are never invented where the source
 * text did not state one, and semantically different units are never merged.
 * An unrecognised unit is returned unchanged.</p>
 */
public final class UnitNormalizer {

    private static final Pattern[] TOKENS = new Pattern[]{
            Pattern.compile("(?i)\\bdegree(?:s)?( )?([CF])\\b", Pattern.DOTALL),
            Pattern.compile("(?i)\\bdeg\\.?(?:ree)?(?:rees)?( )?([CF])\\b", Pattern.DOTALL),
            Pattern.compile("(?i)\\b(c)elsius\\b", Pattern.DOTALL),
            Pattern.compile("(?i)\\b(f)ahrenheit\\b", Pattern.DOTALL),
    };

    private UnitNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        t = replaceUnit(t);
        t = t.replaceAll("(?i)\\b(kg/m2|kg/m\\^2)\\b", "kg/m\u00B2");
        t = t.replaceAll("(?i)\\b(g/dl)\\b", "g/dL");
        t = t.replaceAll("(?i)\\b(mg/dl)\\b", "mg/dL");
        t = t.replaceAll("(?i)\\b(\u00B5g/dl|mcg/dl)\\b", "\u00B5g/dL");
        t = t.replaceAll("(?i)\\b(mmol/l)\\b", "mmol/L");
        t = t.replaceAll("(?i)\\b(\u00B5mol/l|mcmol/l)\\b", "\u00B5mol/L");
        t = t.replaceAll("(?i)\\b(nmol/l)\\b", "nmol/L");
        t = t.replaceAll("(?i)\\b(pmol/l)\\b", "pmol/L");
        t = t.replaceAll("(?i)\\b(ng/dl)\\b", "ng/dL");
        t = t.replaceAll("(?i)\\b(pg/ml)\\b", "pg/mL");
        t = t.replaceAll("(?i)\\b(ng/ml)\\b", "ng/mL");
        t = t.replaceAll("(?i)\\b(ng/l)\\b", "ng/L");
        t = t.replaceAll("(?i)\\b(pg/dl)\\b", "pg/dL");
        t = t.replaceAll("(?i)\\b(mmhg)\\b", "mmHg");
        t = t.replaceAll("(?i)\\b(beats\\s*per\\s*minute|beats?/min|per\\s*minute)\\b", "bpm");
        t = t.replaceAll("(?i)\\b(breaths\\s*per\\s*minute)\\b", "breaths/min");
        t = t.replaceAll("(?i)/(?:[\\u00B5\\u03BCu]|mc)l\\b", "/\u00B5L");
        t = t.replaceAll("(?i)[\u00B5\u03BC]l\\b", "\u00B5L");
        t = t.replaceAll("\\s*/\\s*", "/");
        return t;
    }

    private static String replaceUnit(String input) {
        String t = input;
        for (Pattern p : TOKENS) {
            Matcher m = p.matcher(t);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String letter = m.group(2) != null ? m.group(2) : m.group(1);
                m.appendReplacement(sb, "\u00B0" + letter.toUpperCase(Locale.ROOT));
            }
            m.appendTail(sb);
            t = sb.toString();
        }
        return t;
    }
}