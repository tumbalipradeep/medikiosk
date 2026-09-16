package in.devmedi.kiosk.module.patient.correction;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Patient-facing correction endpoints for the review step of the kiosk journey.
 *
 * <p>Mounted under {@code /patient/**} (role {@code PATIENT} + CSRF by the
 * global security configuration). Ownership is enforced in
 * {@link PatientCorrectionService#requireOwnedCase}: a patient can only ever
 * read or correct answers of their OWN case, and foreign cases yield 404
 * without revealing existence.</p>
 */
@RestController
@RequestMapping("/patient/cases/{caseId}/corrections")
@Validated
public class PatientCorrectionController {

    private final PatientCorrectionService correctionService;

    public PatientCorrectionController(PatientCorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    /** Lists the patient's current corrections for their own case. */
    @GetMapping
    public Map<String, Object> list(@PathVariable("caseId") String caseId,
                                    @AuthenticationPrincipal ApplicationUserDetails principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        Map<Integer, PatientAnswerCorrection> byOrder =
                correctionService.correctionsByOrder(caseId, principal.getId());
        List<Map<String, Object>> items = byOrder.values().stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("answerOrder", c.getAnswerOrder());
                    m.put("originalAnswer", c.getOriginalAnswer());
                    m.put("correctedAnswer", c.getCorrectedAnswer());
                    m.put("reason", c.getReason());
                    m.put("correctedBy", c.getCorrectedBy());
                    m.put("correctedAt", c.getCorrectedAt());
                    m.put("status", c.getStatus().name());
                    return m;
                })
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("caseId", caseId);
        body.put("corrections", items);
        return body;
    }

    /** Submits (or replaces) the patient's correction for one answer. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> correct(@PathVariable("caseId") String caseId,
                                       @Valid @RequestBody CorrectionRequest request,
                                       @AuthenticationPrincipal ApplicationUserDetails principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        PatientAnswerCorrection saved = correctionService.correct(
                caseId,
                principal.getId(),
                principal.getUsername(),
                request.answerOrder(),
                request.correctedAnswer(),
                request.reason(),
                "/patient/cases/" + caseId + "/corrections");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("caseId", caseId);
        body.put("correction", PatientCorrectionService.CorrectionView.from(saved));
        return body;
    }

    /** Correction submission payload. */
    public record CorrectionRequest(
            @NotNull(message = "Answer order is required.")
            Integer answerOrder,

            @NotBlank(message = "Corrected answer must not be empty.")
            @Size(max = 4000, message = "Corrected answer is too long.")
            String correctedAnswer,

            @Size(max = 1000, message = "Correction reason is too long.")
            String reason) {
    }
}
