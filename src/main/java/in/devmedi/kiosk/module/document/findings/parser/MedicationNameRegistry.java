package in.devmedi.kiosk.module.document.findings.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Curated registry of common drug names and dosage-form prefixes that are
 * recognised at the start of prescription lines.
 *
 * <p>Names are matched longest-first so multi-word drugs (e.g.
 * {@code insulin glargine}, {@code calcium carbonate}) win over their shorter
 * prefixes. A dosage-form prefix (e.g. {@code Tab.}, {@code Syp.}) additionally
 * lets a prescription line be recognised even when the drug itself is not in
 * the registry - the following word(s) are then used as the display name.</p>
 */
@Component
public class MedicationNameRegistry {

    /** Recognised dosage forms, e.g. {@code Tab.}, {@code Syp.}, {@code Inj.}. */
    public static final List<String> DOSAGE_FORMS = List.of(
            "nasal spray", "eye drops", "ear drops", "sublingual",
            "tablet", "tablets", "capsule", "capsules",
            "tab", "tab.", "cap", "cap.", "cap",
            "syr", "syr.", "syrup", "susp", "susp.",
            "inj", "inj.", "injection",
            "drops", "drop", "oin", "oint", "oint.", "ointment",
            "cream", "gel", "lotion", "sachet", "packet",
            "inhaler", "puff", "spray", "lozenges", "syp", "syp.");

    private final List<String> drugNames;
    private final List<String> sortedNames;

    public MedicationNameRegistry() {
        this.drugNames = List.of(
                "metformin", "glimepiride", "glipizide", "gliclazide", "pioglitazone",
                "sitagliptin", "voglibose", "empagliflozin", "dapagliflozin", "linagliptin",
                "insulin glargine", "insulin lispro", "insulin aspart", "insulin human", "insulin",
                "atorvastatin", "rosuvastatin", "simvastatin", "pitavastatin",
                "amlodipine", "telmisartan", "losartan", "valsartan", "olmesartan",
                "ramipril", "enalapril", "lisinopril", "atenolol", "metoprolol",
                "propranolol", "carvedilol", "nifedipine",
                "hydrochlorothiazide", "chlorthalidone", "furosemide", "torsemide", "spironolactone",
                "clopidogrel", "prasugrel", "ticagrelor", "aspirin", "warfarin",
                "dabigatran", "rivaroxaban", "apixaban",
                "digoxin", "glyceryl trinitrate", "nitroglycerin", "isosorbide mononitrate",
                "paracetamol", "acetaminophen", "ibuprofen", "diclofenac", "naproxen",
                "mefenamic acid", "tramadol", "etoricoxib", "nimesulide", "aceclofenac",
                "omeprazole", "esomeprazole", "pantoprazole", "rabeprazole", "lansoprazole",
                "ranitidine", "famotidine", "domperidone", "ondansetron", "metoclopramide", "lactulose",
                "cetirizine", "levocetirizine", "loratadine", "fexofenadine", "montelukast",
                "salbutamol", "albuterol", "levosalbutamol", "budesonide", "fluticasone",
                "prednisolone", "prednisone", "dexamethasone", "hydrocortisone",
                "levothyroxine", "thyroxine", "carbimazole", "methimazole",
                "calcium carbonate", "calcium", "cholecalciferol", "vitamin d3", "vitamin d",
                "folic acid", "ferrous sulfate", "ferrous sulphate", "ferrous ascorbate",
                "iron sucrose", "cyanocobalamin", "vitamin b12", "vitamin b complex", "multivitamin",
                "amitriptyline", "escitalopram", "sertraline", "fluoxetine", "clonazepam",
                "alprazolam", "pregabalin", "gabapentin", "duloxetine",
                "azithromycin", "amoxicillin", "ampicillin", "cloxacillin", "co-amoxiclav",
                "cefixime", "cephalexin", "cefuroxime", "ceftriaxone", "ciprofloxacin",
                "levofloxacin", "norfloxacin", "doxycycline", "metronidazole", "tinidazole",
                "fluconazole", "albendazole", "mebendazole", "ivermectin");
        List<String> sorted = new ArrayList<>(drugNames);
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        this.sortedNames = List.copyOf(sorted);
    }

    /**
     * Returns the display name actually matched (preserving source case via the
     * matched slice), or {@code null} when no registry drug starts the text.
     */
    public String matchAtStart(String text) {
        String t = text.trim();
        for (String name : sortedNames) {
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
        return c == ',' || c == ';' || c == '-' || c == '/' || c == '.';
    }
}