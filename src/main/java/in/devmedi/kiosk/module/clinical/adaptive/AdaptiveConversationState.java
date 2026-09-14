package in.devmedi.kiosk.module.clinical.adaptive;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Session-scoped conversational state for the adaptive complete-history
 * conversation. Holds only the raw answers and ordering; all interpretation is
 * done by the planner and service.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION,
        proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AdaptiveConversationState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean started;
    private boolean completed;
    private Instant startedAt;
    private Instant completedAt;
    private String currentQuestionId;
    private String aiAdvisorUsed;
    /** questionId -> patient's free-text (or picked) answer, insertion ordered. */
    private final LinkedHashMap<String, String> answers = new LinkedHashMap<>();

    public void begin() {
        started = true;
        completed = false;
        startedAt = Instant.now();
        completedAt = null;
        currentQuestionId = null;
        aiAdvisorUsed = null;
        answers.clear();
    }

    public void reset() {
        begin();
    }

    public void answer(String questionId, String answer) {
        answers.put(questionId, answer == null ? "" : answer.trim());
    }

    public void complete() {
        completed = true;
        completedAt = Instant.now();
        currentQuestionId = null;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getCurrentQuestionId() {
        return currentQuestionId;
    }

    public void setCurrentQuestionId(String currentQuestionId) {
        this.currentQuestionId = currentQuestionId;
    }

    public String getAiAdvisorUsed() {
        return aiAdvisorUsed;
    }

    public void setAiAdvisorUsed(String aiAdvisorUsed) {
        this.aiAdvisorUsed = aiAdvisorUsed;
    }

    public Map<String, String> getAnswers() {
        return answers;
    }

    public boolean hasAnswer(String questionId) {
        return answers.containsKey(questionId);
    }
}