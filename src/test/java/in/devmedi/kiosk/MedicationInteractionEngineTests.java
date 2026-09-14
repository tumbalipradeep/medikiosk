package in.devmedi.kiosk;

import in.devmedi.kiosk.module.medication.entity.MedicationInteractionRule;
import in.devmedi.kiosk.module.medication.entity.MedicationProfile;
import in.devmedi.kiosk.module.medication.enums.MedicationInteractionSeverity;
import in.devmedi.kiosk.module.medication.repository.MedicationInteractionRuleRepository;
import in.devmedi.kiosk.module.medication.repository.MedicationProfileRepository;
import in.devmedi.kiosk.module.medication.service.DetectedMedication;
import in.devmedi.kiosk.module.medication.service.MedicationInteractionEngine;
import in.devmedi.kiosk.module.medication.service.MedicationInteractionView;
import in.devmedi.kiosk.module.medication.service.MedicationNormalizer;
import in.devmedi.kiosk.module.medication.enums.MedicationSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the medication interaction engine and normalizer against the
 * seeded profile and rule data. The seeded set is the production truth: any
 * new rule or profile added in {@code V22} must still pass these assertions
 * or be explicitly covered by new tests. This is a screening aid, not a
 * prescription or diagnosis.
 */
@SpringBootTest
class MedicationInteractionEngineTests {

    @Autowired private MedicationInteractionEngine engine;
    @Autowired private MedicationNormalizer normalizer;
    @Autowired private MedicationProfileRepository profileRepository;
    @Autowired private MedicationInteractionRuleRepository ruleRepository;

    private List<MedicationProfile> profiles;
    private List<MedicationInteractionRule> rules;

    @BeforeEach
    void setUp() {
        profiles = profileRepository.findAll();
        rules = ruleRepository.findAll();
    }

    private DetectedMedication med(String name) {
        return normalizer.resolveSingle(name, profiles);
    }

    @Test
    void seededProfilesLoaded() {
        assertThat(profiles).isNotEmpty();
        assertThat(profiles).anyMatch(p -> "Warfarin".equals(p.getCanonicalName()));
        assertThat(profiles).anyMatch(p -> "NSAID".equals(p.getClassKey()));
    }

    @Test
    void seededRulesLoaded() {
        assertThat(rules).isNotEmpty();
        assertThat(rules.size()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void warfarinAspirinDetectedAsMajorInteraction() {
        List<DetectedMedication> meds = List.of(med("Warfarin"), med("Aspirin"));
        List<MedicationInteractionView> interactions = engine.analyze(meds, rules);

        assertThat(interactions).isNotEmpty();
        assertThat(interactions).anyMatch(i -> i.severity() == MedicationInteractionSeverity.MAJOR
                && (i.drugA().equalsIgnoreCase("Warfarin") || i.drugA().equalsIgnoreCase("Aspirin"))
                && (i.drugB().equalsIgnoreCase("Warfarin") || i.drugB().equalsIgnoreCase("Aspirin")));
    }

    @Test
    void sildenafilNitratesDetectedAsContraindicated() {
        List<DetectedMedication> meds = List.of(med("Sildenafil"), med("Glyceryl trinitrate"));
        List<MedicationInteractionView> interactions = engine.analyze(meds, rules);

        assertThat(interactions).isNotEmpty();
        assertThat(interactions).anyMatch(i -> i.severity() == MedicationInteractionSeverity.CONTRAINDICATED);
    }

    @Test
    void simvastatinClarithromycinDetectedAsContraindicated() {
        List<DetectedMedication> meds = List.of(med("Simvastatin"), med("Clarithromycin"));
        List<MedicationInteractionView> interactions = engine.analyze(meds, rules);

        assertThat(interactions).anyMatch(i -> i.severity() == MedicationInteractionSeverity.CONTRAINDICATED
                && i.title().toLowerCase().contains("simvastatin"));
    }

    @Test
    void amiodaroneWarfarinDetectedAsMajor() {
        List<DetectedMedication> meds = List.of(med("Warfarin"), med("Amiodarone"));
        List<MedicationInteractionView> interactions = engine.analyze(meds, rules);

        assertThat(interactions).anyMatch(i -> i.severity() == MedicationInteractionSeverity.MAJOR
                && i.title().toLowerCase().contains("amiodarone"));
    }

    @Test
    void clopidogrelOmeprazoleDetectedAsModerate() {
        List<DetectedMedication> meds = List.of(med("Clopidogrel"), med("Omeprazole"));
        List<MedicationInteractionView> interactions = engine.analyze(meds, rules);

        assertThat(interactions).anyMatch(i -> i.severity() == MedicationInteractionSeverity.MODERATE);
    }

    @Test
    void aceInhibitorPlusKSparingDiureticDetectedAsModerate() {
        DetectedMedication ace = normalizer.resolveSingle("Ramipril", profiles);
        DetectedMedication kDiur = normalizer.resolveSingle("Spironolactone", profiles);
        List<DetectedMedication> meds = List.of(ace, kDiur);
        List<MedicationInteractionView> interactions = engine.analyze(meds, rules);

        assertThat(interactions).anyMatch(i -> i.severity() == MedicationInteractionSeverity.MODERATE
                && i.title().toLowerCase().contains("potassium"));
    }

    @Test
    void noInteractionBetweenUnrelatedDrugs() {
        List<DetectedMedication> meds = List.of(
                normalizer.resolveSingle("Amlodipine", profiles),
                normalizer.resolveSingle("Metformin", profiles));
        List<MedicationInteractionView> interactions = engine.analyze(meds, rules);

        assertThat(interactions).isEmpty();
    }

    @Test
    void interactionsSortedMostSevereFirst() {
        // Sildenafil + glyceryl trinitrate => CONTRAINDICATED (PDE5 + NITRATE)
        // Warfarin + Aspirin            => MAJOR (ANTICOAGULANT + ANTIPLATELET)
        List<DetectedMedication> meds = List.of(
                normalizer.resolveSingle("Sildenafil", profiles),
                normalizer.resolveSingle("Glyceryl trinitrate", profiles),
                normalizer.resolveSingle("Warfarin", profiles),
                normalizer.resolveSingle("Aspirin", profiles));
        List<MedicationInteractionView> interactions = engine.analyze(meds, rules);

        assertThat(interactions.size()).isGreaterThanOrEqualTo(2);
        assertThat(interactions.get(0).severity())
                .isGreaterThanOrEqualTo(interactions.get(1).severity());
        assertThat(interactions.get(0).severity())
                .isEqualTo(MedicationInteractionSeverity.CONTRAINDICATED);
    }

    @Test
    void overallSeverityTakesHighest() {
        List<MedicationInteractionView> interactions = List.of(
                new MedicationInteractionView("A", "B", MedicationInteractionSeverity.MODERATE,
                        "Mod", "desc", "guidance"),
                new MedicationInteractionView("C", "D", MedicationInteractionSeverity.MAJOR,
                        "Maj", "desc", "guidance"));
        MedicationInteractionSeverity overall = MedicationInteractionEngine.overall(interactions);
        assertThat(overall).isEqualTo(MedicationInteractionSeverity.MAJOR);
    }

    @Test
    void overallSeverityNullWhenNoInteractions() {
        MedicationInteractionSeverity overall = MedicationInteractionEngine.overall(List.of());
        assertThat(overall).isNull();
    }

    @Test
    void normalizerResolvesDrugNamesIgnoringDosageFormPrefix() {
        DetectedMedication dm = normalizer.resolveSingle("Tab. Aspirin", profiles);
        assertThat(dm).isNotNull();
        assertThat(dm.displayName()).isEqualToIgnoringCase("Aspirin");
        assertThat(dm.classKey()).isEqualTo("ANTIPLATELET");
    }

    @Test
    void normalizerResolvesFreeTextWithMultipleMedications() {
        var results = normalizer.resolveFreeText("Warfarin, Aspirin, Paracetamol", profiles);
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        assertThat(results).anyMatch(d -> d.classKey() != null && d.displayName() != null
                && d.displayName().equalsIgnoreCase("Warfarin"));
    }

    @Test
    void normalizerHandlesUnrecognizedMedication() {
        var results = normalizer.resolveFreeText("Bogus drug 12345", profiles);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).classKey()).isNull();
        assertThat(results.get(0).canonicalName()).isNull();
    }
}