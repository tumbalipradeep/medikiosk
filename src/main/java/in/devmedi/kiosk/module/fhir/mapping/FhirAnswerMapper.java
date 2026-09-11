package in.devmedi.kiosk.module.fhir.mapping;

import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.model.FhirAnnotation;
import in.devmedi.kiosk.module.fhir.model.FhirCodeableConcept;
import in.devmedi.kiosk.module.fhir.model.FhirCoding;
import in.devmedi.kiosk.module.fhir.model.FhirObservation;
import in.devmedi.kiosk.module.fhir.model.FhirReference;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseAnswerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps one persisted {@link CompletedCaseAnswerEntity} (a deterministic
 * question/answer pair of the clinical conversation) to a FHIR R4
 * {@code Observation}.
 *
 * <p>The question is carried verbatim as the observation code: the stable
 * question id under the MediKiosk question-code system, the section under the
 * section system, and the (possibly AI-reworded) displayed wording as the code
 * text. The patient's verbatim answer becomes the observation value; a blank
 * persisted answer produces no value element rather than a fabricated one.</p>
 */
public final class FhirAnswerMapper {

    public static final String QUESTION_SYSTEM = "urn:medikiosk:question-code";
    public static final String SECTION_SYSTEM = "urn:medikiosk:section";

    private FhirAnswerMapper() {
    }

    public static FhirObservation toFhir(CompletedCaseAnswerEntity answer,
                                         String caseId,
                                         FhirContextRefs refs) {
        List<FhirCoding> codings = new ArrayList<>();
        codings.add(FhirCoding.of(QUESTION_SYSTEM, answer.getQuestionId(), answer.getQuestionText()));
        if (answer.getSection() != null && !answer.getSection().isBlank()) {
            codings.add(FhirCoding.of(SECTION_SYSTEM, answer.getSection(), null));
        }
        FhirCodeableConcept code = new FhirCodeableConcept(codings, answer.getDisplayedQuestionText());

        String valueString = (answer.getAnswer() != null && !answer.getAnswer().isBlank())
                ? answer.getAnswer() : null;

        return FhirObservation.of(
                FhirIds.answer(caseId, answer.getAnswerOrder()),
                code,
                refs.subjectReference() == null ? null
                        : FhirReference.of(refs.subjectReference(), null),
                refs.encounterReference() == null ? null
                        : FhirReference.of(refs.encounterReference(), null),
                FhirDateTimes.instant(answer.getCreatedAt()),
                valueString,
                null,
                null,
                List.of(FhirAnnotation.of("answerSource=" + answer.getAnswerSource()
                        + "; questionSource=" + answer.getQuestionSource()
                        + "; language=" + answer.getAnswerLanguage())));
    }
}