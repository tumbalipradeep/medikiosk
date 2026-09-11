package in.devmedi.kiosk.module.ai.conversation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates structured AI question output before it may be shown to a patient.
 *
 * <p>The rules are deliberately strict and conservative. Any failure returns an
 * invalid {@link AiQuestionValidation} and the deterministic clinical backbone
 * provides the question text instead. The validator never inspects the patient's
 * raw answer for safety: the answer is the source of truth, is never modified,
 * and is never part of what is being validated.</p>
 *
 * <p>Adaptive scope (M2 Phase 2): the AI may rephrase the current deterministic
 * objective as a natural {@link AiQuestionMode#QUESTION} or as a
 * {@link AiQuestionMode#CLARIFICATION} of the patient's latest answer. Both must
 * remain inside the objective: the structured {@code topic} must be one of the
 * allowed topics and not already covered, the question must not duplicate an
 * already-asked question, and when no topic anchor is provided the wording must
 * still lexically connect to the objective (or to the latest answer for a
 * clarification). {@link AiQuestionMode#CANONICAL} is accepted without a
 * question — the deterministic text is used unchanged.</p>
 */
@Component
public class AiQuestionValidator {

    /** Safety floor/ceiling for question text length (characters). */
    public static final int MIN_QUESTION_LENGTH = 3;
    public static final int MAX_QUESTION_LENGTH = 300;

    /**
     * High-signal patterns that indicate a model is inventing clinical facts,
     * vital signs, lab values, medications, doses, or offering treatment/diagnosis.
     * A question text never needs these tokens.
     */
    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            // imperative treatment/diagnosis advice
            Pattern.compile("(?i)\\brecommend\\b"),
            Pattern.compile("(?i)\\bprescrib\\w*\\b"),
            Pattern.compile("(?i)\\byou (should|must|need to|have to)\\b"),
            Pattern.compile("(?i)\\bi (recommend|suggest|advise|would give)\\b"),
            Pattern.compile("(?i)\\bit is (important|essential|better) that you\\b"),
            Pattern.compile("(?i)\\bbecause you have\\b"),
            Pattern.compile("(?i)\\byou have been (diagnosed|told)\\b"),
            Pattern.compile("(?i)\\btake (a |the )?\\w+ (tablets?|capsules?|doses?|injections?)\\b"),
            // invented lab / vital / dosage values
            Pattern.compile("(?i)\\b\\d+(\\s*(mg|mcg|ml|kg|mm|cm|mmol|l\\/dl|g\\/dl|mmhg|bpm|kpa))\\b"),
            Pattern.compile("(?i)\\b(blood sugar|glucose level|blood pressure is|cholesterol level|lab reports?)\\b"));

    private static final List<String> PROMPT_STARTERS = List.of(
            "please", "tell me", "could you", "can you", "would you", "will you",
            "how ", "what ", "when ", "where ", "which ", "why ", "who ",
            "describe", "share ", "is it ", "is there ", "are you ", "are there ",
            "do you ", "does it ", "did you ", "have you ", "has it ");

    private final QuestionDuplicateDetector duplicateDetector;

    public AiQuestionValidator() {
        this(new QuestionDuplicateDetector());
    }

    public AiQuestionValidator(QuestionDuplicateDetector duplicateDetector) {
        this.duplicateDetector = duplicateDetector;
    }

    /**
     * Validates an AI-proposed next question in the context of the current
     * deterministic objective.
     *
     * @param response structured AI output (may be {@code null})
     * @param context  the current deterministic objective constraints
     * @return valid only when the question is safe to present inside the objective
     */
    public AiQuestionValidation validate(AiQuestionResponse response, AiQuestionValidationContext context) {
        if (response == null) {
            return AiQuestionValidation.rejected(AiRejectionReason.BLANK_QUESTION, "no AI response");
        }
        AiQuestionMode mode = response.mode();
        if (mode == null) {
            return AiQuestionValidation.rejected(AiRejectionReason.MODE_MISSING_OR_INVALID, "no question mode");
        }
        if (mode == AiQuestionMode.CANONICAL) {
            return AiQuestionValidation.accepted();
        }

        String question = response.question();
        if (question == null || question.isBlank()) {
            return AiQuestionValidation.rejected(AiRejectionReason.BLANK_QUESTION, "question text is blank");
        }
        String trimmed = question.trim();
        if (trimmed.length() < MIN_QUESTION_LENGTH) {
            return AiQuestionValidation.rejected(AiRejectionReason.QUESTION_TOO_SHORT,
                    "question shorter than " + MIN_QUESTION_LENGTH + " characters");
        }
        if (trimmed.length() > MAX_QUESTION_LENGTH) {
            return AiQuestionValidation.rejected(AiRejectionReason.QUESTION_TOO_LONG,
                    "question longer than " + MAX_QUESTION_LENGTH + " characters");
        }
        if (containsForbiddenClinicalClaim(trimmed)) {
            return AiQuestionValidation.rejected(AiRejectionReason.FORBIDDEN_CLINICAL_CLAIM,
                    "output asserts clinical facts the model may not claim");
        }
        if (!looksLikeFollowUpPrompt(trimmed)) {
            return AiQuestionValidation.rejected(AiRejectionReason.NOT_QUESTION_LIKE,
                    "output does not read as a follow-up question");
        }

        String topic = response.followUpTopic();
        boolean topicAnchored = topic != null && !topic.isBlank();
        if (topicAnchored) {
            if (context == null
                    || context.allowedFollowUpTopics() == null
                    || !context.allowedFollowUpTopics().contains(topic)) {
                return AiQuestionValidation.rejected(AiRejectionReason.TOPIC_OUT_OF_SECTION,
                        "follow-up topic is outside the current deterministic objective");
            }
            if (context.askedTopics() != null && context.askedTopics().contains(topic)) {
                return AiQuestionValidation.rejected(AiRejectionReason.TOPIC_ALREADY_COVERED,
                        "follow-up topic restates an already-covered objective");
            }
        }

        if (duplicatesAlreadyAsked(trimmed, context)) {
            return AiQuestionValidation.rejected(AiRejectionReason.DUPLICATE_QUESTION,
                    "question repeats a question that has already been asked");
        }

        if (!topicAnchored && !withinObjective(trimmed, mode, context)) {
            return AiQuestionValidation.rejected(AiRejectionReason.OBJECTIVE_OUT_OF_BOUNDS,
                    "question leaves the current deterministic clinical objective");
        }

        return AiQuestionValidation.accepted();
    }

    private boolean duplicatesAlreadyAsked(String question, AiQuestionValidationContext context) {
        if (context == null || context.alreadyAskedQuestions() == null || context.alreadyAskedQuestions().isEmpty()) {
            return false;
        }
        return duplicateDetector.isDuplicate(question, context.alreadyAskedQuestions());
    }

    private boolean withinObjective(String question, AiQuestionMode mode, AiQuestionValidationContext context) {
        if (context == null || context.targetQuestionText() == null || context.targetQuestionText().isBlank()) {
            return true;
        }
        Set<String> candidate = QuestionTokens.tokensOf(question);
        Set<String> objective = QuestionTokens.tokensOf(context.targetQuestionText());
        for (String token : candidate) {
            if (objective.contains(token)) {
                return true;
            }
        }
        if (mode == AiQuestionMode.CLARIFICATION && overlapsLatestAnswer(question, context)) {
            return true;
        }
        return false;
    }

    private boolean overlapsLatestAnswer(String question, AiQuestionValidationContext context) {
        if (context.latestAnswer() == null || context.latestAnswer().isBlank()) {
            return false;
        }
        Set<String> candidate = QuestionTokens.tokensOf(question);
        Set<String> answer = QuestionTokens.tokensOf(context.latestAnswer());
        for (String token : candidate) {
            if (answer.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeFollowUpPrompt(String question) {
        if (question.endsWith("?")) {
            return true;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        for (String starter : PROMPT_STARTERS) {
            if (lower.startsWith(starter)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsForbiddenClinicalClaim(String question) {
        for (Pattern pattern : FORBIDDEN_PATTERNS) {
            if (pattern.matcher(question).find()) {
                return true;
            }
        }
        return false;
    }
}