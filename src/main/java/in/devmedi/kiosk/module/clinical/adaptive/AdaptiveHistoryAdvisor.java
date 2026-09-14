package in.devmedi.kiosk.module.clinical.adaptive;

import in.devmedi.kiosk.module.ai.conversation.ClinicalAiRequest;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiResponse;
import in.devmedi.kiosk.module.ai.conversation.ConversationTurn;
import in.devmedi.kiosk.module.ai.provider.AiFailoverService;
import in.devmedi.kiosk.module.ai.provider.AiProviderException;
import in.devmedi.kiosk.module.ai.provider.AiProviderFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Asks a validated AI provider which question to ask next in the
 * complete-clinical-history conversation.
 *
 * <p>The provider is only ever allowed to <em>choose between</em> candidates
 * computed deterministically by the planner — it can never invent a question
 * or leave the coverage plan. If the response does not clearly resolve to one
 * candidate, or no provider is enabled, the advisory is null and the caller
 * uses the deterministic planner. This keeps the LLM as the conversational
 * engine while bounds are enforced by code.</p>
 */
@Service
public class AdaptiveHistoryAdvisor {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveHistoryAdvisor.class);

    private final AiFailoverService failoverService;

    public AdaptiveHistoryAdvisor(AiFailoverService failoverService) {
        this.failoverService = failoverService;
    }

    /** @return the selected candidate id, or {@code null} when not resolvable. */
    public String adviseNext(List<AdaptiveHistoryQuestion> candidates,
                             List<ConversationTurn> turns) {
        if (!failoverService.hasEnabledProviders() || candidates.isEmpty()) {
            return null;
        }
        long start = System.nanoTime();
        try {
            ClinicalAiRequest request = ClinicalAiRequest.builder()
                    .language("en")
                    .currentQuestion(candidates.get(0).text())
                    .patientAnswer(turns.isEmpty() ? "" : turns.get(turns.size() - 1).answer())
                    .conversation(turns.size() > 6 ? turns.subList(turns.size() - 6, turns.size()) : turns)
                    .requestedResponse(prompt(candidates, turns))
                    .build();
            ClinicalAiResponse response = failoverService.complete(request);
            String selected = resolve(response.getContent(), candidates);
            if (selected == null) {
                log.info("Adaptive advisor: no resolvable selection provider={} model={} failure=UNRESOLVABLE durationMs={}",
                        response.getProvider(), response.getModel(), elapsedMs(start));
                return null;
            }
            log.info("Adaptive advisor: chose={} provider={} model={} durationMs={}",
                    selected, response.getProvider(), response.getModel(), elapsedMs(start));
            return selected;
        } catch (AiProviderException ex) {
            log.info("Adaptive advisor: fallback failure={} durationMs={}", AiProviderFailure.classify(ex), elapsedMs(start));
            return null;
        } catch (RuntimeException ex) {
            log.warn("Adaptive advisor aborted unexpectedly; using deterministic fallback", ex);
            return null;
        }
    }

    private String prompt(List<AdaptiveHistoryQuestion> candidates, List<ConversationTurn> turns) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are helping a clinical history consultant decide which question to ask next.\n");
        sb.append("You must ONLY pick a single next question from the provided candidate list based on what is most\n");
        sb.append("clinically useful to complete the missing information. Do not diagnose, prescribe, or speculate\n");
        sb.append("on disease. Respond with exactly one candidate id.\n\n");
        sb.append("Conversation so far:\n");
        for (ConversationTurn turn : turns) {
            sb.append("- Q: ").append(turn.question()).append("\n  A: ").append(turn.answer()).append("\n");
        }
        sb.append("\nCandidates (id | category | priority | text):\n");
        for (AdaptiveHistoryQuestion q : candidates) {
            sb.append("- ").append(q.id()).append(" | ").append(q.category().readableName())
                    .append(" | ").append(q.priority()).append(" | ").append(q.text()).append("\n");
        }
        sb.append("\nRespond with exactly one candidate id, nothing else.");
        return sb.toString();
    }

    private String resolve(String content, List<AdaptiveHistoryQuestion> candidates) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String text = content.trim().toLowerCase(Locale.ROOT);
        // Exact id match anywhere in the reply wins.
        for (AdaptiveHistoryQuestion q : candidates) {
            if (text.contains(q.id().toLowerCase(Locale.ROOT))) {
                return q.id();
            }
        }
        // Category readable-name or concept-key mention.
        for (AdaptiveHistoryQuestion q : candidates) {
            if (text.contains(q.category().readableName().toLowerCase(Locale.ROOT))) {
                return q.id();
            }
            if (text.contains(q.conceptKey().toLowerCase(Locale.ROOT))) {
                return q.id();
            }
        }
        return null;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}