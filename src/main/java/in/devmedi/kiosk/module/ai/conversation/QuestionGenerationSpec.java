package in.devmedi.kiosk.module.ai.conversation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the plain-text prompt pieces for one AI question-generation step.
 *
 * <p>The prompt contract is deliberately minimal and contains no clinical
 * interpretation: the orchestrator supplies the current section, the canonical
 * deterministic next question, the question just answered, the patient's latest
 * verbatim answer, a few previous turns, the already-covered objectives, and the
 * section-constrained follow-up topics. Safety constraints are fixed text embedded
 * here, never taken from the model.</p>
 */
public final class QuestionGenerationSpec {

    /** Hard safety constraints the model must obey for every completion. */
    public static final String SAFETY =
            "You assist a structured clinical health-history interview. "
                    + "You must NOT make any diagnosis, prognosis, or treatment recommendation. "
                    + "You must NOT prescribe, treat, or suggest remedies or lifestyle changes. "
                    + "You must NOT invent or assert any clinical finding, vital sign, lab value, "
                    + "medication, dose, or patient history. "
                    + "You must NOT state as fact anything the patient has not already provided, "
                    + "and you must never fabricate an answer or mark the interview complete. "
                    + "You must strictly stay inside the single objective listed below: do not bring up "
                    + "other symptoms, other facets of the problem, medications, or laboratory interpretation. "
                    + "You must NOT re-ask anything the patient has already been asked or already answered. "
                    + "Ask at most one question.";

    /** Exact structured output contract the model must follow. */
    public static final String OUTPUT_CONTRACT =
            "Reply with exactly one JSON object and nothing else (no markdown, no prose), using exactly this shape: "
                    + "{\"mode\":\"QUESTION\"|\"CLARIFICATION\"|\"CANONICAL\", "
                    + "\"question\": string, \"topic\": string or empty, \"continue\": boolean optional}. "
                    + "\"mode\" meanings: "
                    + "QUESTION = a natural conversational phrasing of the objective, adapted to the conversation; "
                    + "CLARIFICATION = a short question asking the patient to be more specific about their latest "
                    + "answer, still strictly inside the objective; "
                    + "CANONICAL = keep the canonical objective text as-is (omit \"question\" or leave it empty). "
                    + "\"question\" is required for QUESTION and CLARIFICATION and ignored for CANONICAL. "
                    + "\"topic\" must be empty or one of the allowed follow-up topics, never an already-covered one.";

    private QuestionGenerationSpec() {
    }

    /**
     * Builds the full prompt for one generation step. The objective and the
     * already-covered material are supplied by the orchestrator so the model
     * can adapt the wording without changing what is being collected.
     */
    public static String buildPrompt(AiQuestionRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Patient-facing language: ").append(safe(request.language())).append('\n');
        prompt.append("Current deterministic clinical section: ").append(safe(request.section())).append('\n');
        prompt.append("Objective of the next question (stay strictly inside it; collect topic=")
                .append(safe(request.questionTopic())).append("): ").append('\n');
        prompt.append("  canonical text to phrase: ").append(quote(request.targetQuestionText())).append('\n');
        prompt.append("Question the patient just answered (topic=").append(safe(request.currentQuestionTopic()))
                .append("): ").append(quote(request.currentQuestionText())).append('\n');
        prompt.append("Patient's latest answer (verbatim, source of truth): ")
                .append(quote(request.patientAnswer())).append('\n');
        appendPreviousTurns(prompt, request.previousTurns());
        prompt.append("Allowed follow-up topics (only these, or empty): ")
                .append(join(request.allowedFollowUpTopics())).append('\n');
        prompt.append("Topics already covered (never re-ask): ")
                .append(join(request.askedTopics())).append('\n');
        prompt.append("Already asked questions (never re-ask): ")
                .append(joinQuoted(request.alreadyAskedQuestions()));
        return prompt.append('\n').append(SAFETY).append('\n').append(OUTPUT_CONTRACT).toString();
    }

    private static void appendPreviousTurns(StringBuilder prompt, List<ConversationTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            prompt.append("Previously collected answers: (none)").append('\n');
            return;
        }
        prompt.append("Previously collected answers (oldest first):").append('\n');
        for (ConversationTurn turn : turns) {
            prompt.append("  - ").append(quote(turn.question())).append(" -> ").append(quote(turn.answer())).append('\n');
        }
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "(none)";
        }
        return values.stream().map(QuestionGenerationSpec::safe).collect(Collectors.joining(", "));
    }

    private static String joinQuoted(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "(none)";
        }
        return values.stream().map(QuestionGenerationSpec::quote).collect(Collectors.joining("; "));
    }

    private static String quote(String value) {
        return "\"" + (value == null ? "" : value) + "\"";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}