package in.devmedi.kiosk.module.document.findings.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Curated registry of laboratory tests with their canonical display names and
 * the spellings a document may use for each.
 *
 * <p>{@link #matchers()} returns one entry per (canonical name, alias) pair,
 * ordered by decreasing alias length so the most specific alias always wins on
 * ambiguous lines (e.g. {@code Free T4} before {@code T4}, {@code HDL
 * Cholesterol} before {@code HDL}). The ordering is stable and deterministic.</p>
 */
@Component
public class LabTestRegistry {

    /** A canonical test name plus the aliases recognised for it. */
    public record LabTest(String canonicalName, List<String> aliases) {
    }

    /** One flattened (canonical name, alias) pair. */
    public record Matcher(String canonicalName, String alias) {
    }

    private final List<LabTest> tests;
    private final List<Matcher> matchers;

    public LabTestRegistry() {
        this.tests = List.of(
                new LabTest("Hemoglobin", List.of("Hemoglobin", "Total Hemoglobin", "Hb (Total)", "Hemoglobin (Hb)", "Hb")),
                new LabTest("Total WBC Count", List.of("Total WBC Count", "Total White Blood Cell Count", "Total Leukocyte Count",
                        "White Blood Cell Count", "Total WBCs", "WBC Count", "Total WBC", "TLC")),
                new LabTest("Neutrophils", List.of("Neutrophils", "Neutrophils (%)", "Absolute Neutrophil Count")),
                new LabTest("Lymphocytes", List.of("Lymphocytes", "Lymphocytes (%)", "Absolute Lymphocyte Count")),
                new LabTest("Monocytes", List.of("Monocytes", "Monocytes (%)", "Absolute Monocyte Count")),
                new LabTest("Eosinophils", List.of("Eosinophils", "Eosinophils (%)", "Absolute Eosinophil Count")),
                new LabTest("Basophils", List.of("Basophils", "Basophils (%)", "Absolute Basophil Count")),
                new LabTest("Platelet Count", List.of("Platelet Count", "Total Platelet Count", "Platelets", "Platelet", "TRC")),
                new LabTest("RBC Count", List.of("RBC Count", "Red Blood Cell Count", "Total RBC Count", "Erythrocyte Count", "RBCs")),
                new LabTest("PCV / Hematocrit", List.of("PCV", "Hematocrit", "Packed Cell Volume")),
                new LabTest("MCV", List.of("MCV", "Mean Corpuscular Volume")),
                new LabTest("MCH", List.of("MCH", "Mean Corpuscular Hemoglobin")),
                new LabTest("MCHC", List.of("MCHC", "Mean Corpuscular Hemoglobin Concentration")),
                new LabTest("RDW", List.of("RDW", "RDW-CV", "Red Cell Distribution Width")),
                new LabTest("ESR", List.of("ESR", "Erythrocyte Sedimentation Rate", "Westergren ESR")),
                new LabTest("Total Cholesterol", List.of("Total Cholesterol", "Serum Cholesterol", "Cholesterol Total")),
                new LabTest("Triglycerides", List.of("Triglycerides", "Serum Triglycerides", "Triglyceride")),
                new LabTest("HDL Cholesterol", List.of("HDL Cholesterol", "HDL-C", "High Density Lipoprotein", "HDL")),
                new LabTest("LDL Cholesterol", List.of("LDL Cholesterol", "LDL-C", "Low Density Lipoprotein", "LDL")),
                new LabTest("VLDL Cholesterol", List.of("VLDL Cholesterol", "VLDL")),
                new LabTest("Fasting Blood Sugar", List.of("Fasting Blood Sugar", "Fasting Blood Glucose", "Fasting Plasma Glucose",
                        "Fasting Glucose", "Fasting Sugar", "Blood Sugar (Fasting)", "FBS")),
                new LabTest("Post Prandial Blood Sugar", List.of("Post Prandial Blood Sugar", "2 Hr Post Prandial Blood Sugar",
                        "2-Hr Post Prandial", "Post Meal Blood Sugar", "PP Blood Sugar", "Postprandial Blood Sugar", "PPBS")),
                new LabTest("Random Blood Sugar", List.of("Random Blood Sugar", "Random Blood Glucose", "Random Plasma Glucose",
                        "Random Sugar", "RBS")),
                new LabTest("HbA1C", List.of("HbA1C", "HbA1c", "Hemoglobin A1C", "Glycated Hemoglobin", "Glycosylated Hemoglobin", "A1C")),
                new LabTest("Serum Creatinine", List.of("Serum Creatinine", "Plasma Creatinine", "S. Creatinine", "Creatinine", "Creat")),
                new LabTest("Blood Urea", List.of("Blood Urea", "Serum Urea", "B. Urea", "Urea")),
                new LabTest("Blood Urea Nitrogen", List.of("Blood Urea Nitrogen", "BUN")),
                new LabTest("Uric Acid", List.of("Uric Acid", "Serum Uric Acid", "Urate")),
                new LabTest("Sodium", List.of("Serum Sodium", "Sodium", "Na")),
                new LabTest("Potassium", List.of("Serum Potassium", "Potassium", "K")),
                new LabTest("Chloride", List.of("Serum Chloride", "Chloride", "Cl")),
                new LabTest("Calcium", List.of("Serum Calcium", "Total Calcium", "Calcium")),
                new LabTest("Total Protein", List.of("Total Protein", "Serum Total Protein", "Total Proteins")),
                new LabTest("Albumin", List.of("Serum Albumin", "Albumin")),
                new LabTest("Globulin", List.of("Serum Globulin", "Globulin")),
                new LabTest("A/G Ratio", List.of("Albumin Globulin Ratio", "A/G Ratio", "A: G Ratio", "AG Ratio")),
                new LabTest("Total Bilirubin", List.of("Total Bilirubin", "Serum Bilirubin (Total)", "Bilirubin (Total)", "T. Bilirubin")),
                new LabTest("Direct Bilirubin", List.of("Direct Bilirubin", "Serum Bilirubin (Direct)", "Bilirubin (Direct)",
                        "Conjugated Bilirubin", "D. Bilirubin")),
                new LabTest("Indirect Bilirubin", List.of("Indirect Bilirubin", "Bilirubin (Indirect)", "Unconjugated Bilirubin")),
                new LabTest("SGOT (AST)", List.of("Serum Glutamic Oxaloacetic Transaminase", "SGOT", "Aspartate Aminotransferase", "AST")),
                new LabTest("SGPT (ALT)", List.of("Serum Glutamic Pyruvic Transaminase", "SGPT", "Alanine Aminotransferase", "ALT")),
                new LabTest("Alkaline Phosphatase", List.of("Alkaline Phosphatase", "Serum Alkaline Phosphatase", "ALP")),
                new LabTest("Gamma GT", List.of("Gamma Glutamyl Transferase", "Gamma GT", "GGTP", "GGT")),
                new LabTest("LDH", List.of("Lactate Dehydrogenase", "LDH")),
                new LabTest("TSH", List.of("Thyroid Stimulating Hormone", "TSH")),
                new LabTest("Free T3", List.of("Free T3", "FT3", "Free Triiodothyronine")),
                new LabTest("Free T4", List.of("Free T4", "FT4", "Free Thyroxine")),
                new LabTest("Total T3", List.of("Total T3", "Triiodothyronine", "T3")),
                new LabTest("Total T4", List.of("Total T4", "Thyroxine", "T4")),
                new LabTest("Serum Amylase", List.of("Serum Amylase", "Amylase")),
                new LabTest("Serum Lipase", List.of("Serum Lipase", "Lipase")),
                new LabTest("Serum Iron", List.of("Serum Iron", "Iron")),
                new LabTest("TIBC", List.of("Total Iron Binding Capacity", "TIBC")),
                new LabTest("Ferritin", List.of("Serum Ferritin", "Ferritin")),
                new LabTest("Troponin I", List.of("High Sensitive Troponin I", "hs-Troponin I", "Troponin I")),
                new LabTest("Troponin T", List.of("Troponin T", "hs-Troponin T")),
                new LabTest("CK-MB", List.of("CK-MB", "CPK-MB", "Creatine Kinase MB")),
                new LabTest("CPK", List.of("Creatine Phosphokinase", "Creatine Kinase (Total)", "Total CPK", "Creatine Kinase", "CPK")),
                new LabTest("NT-proBNP", List.of("N-terminal Pro B-Type Natriuretic Peptide", "NT-proBNP", "Pro-BNP", "BNP")),
                new LabTest("Vitamin D", List.of("25 Hydroxy Vitamin D", "25-OH Vitamin D", "25-Hydroxy Vitamin D",
                        "Vitamin D (25-OH)", "Vitamin D")),
                new LabTest("Vitamin B12", List.of("Vitamin B12", "Vitamin B-12", "Serum Vitamin B12", "Cobalamin")),
                new LabTest("Folate", List.of("Folate", "Serum Folate", "Folic Acid")),
                new LabTest("CRP", List.of("C Reactive Protein", "C-Reactive Protein", "hs-CRP", "High Sensitivity CRP", "CRP")),
                new LabTest("Homocysteine", List.of("Homocysteine", "Serum Homocysteine")),
                new LabTest("Prolactin", List.of("Prolactin", "Serum Prolactin")),
                new LabTest("Cortisol", List.of("Cortisol", "Serum Cortisol")),
                new LabTest("PTH", List.of("Parathyroid Hormone", "PTH")),
                new LabTest("PSA", List.of("Prostate Specific Antigen", "PSA")));

        List<Matcher> flattened = new ArrayList<>();
        for (LabTest test : tests) {
            for (String alias : test.aliases()) {
                flattened.add(new Matcher(test.canonicalName(), alias));
            }
        }
        flattened.sort(Comparator
                .comparingInt((Matcher m) -> m.alias().length())
                .reversed()
                .thenComparing(m -> m.canonicalName().toLowerCase(Locale.ROOT))
                .thenComparing(Matcher::alias));
        this.matchers = List.copyOf(flattened);
    }

    /**
     * All (canonical name, alias) matchers ordered longest-alias first. The
     * order is fully deterministic.
     */
    public List<Matcher> matchers() {
        return matchers;
    }
}