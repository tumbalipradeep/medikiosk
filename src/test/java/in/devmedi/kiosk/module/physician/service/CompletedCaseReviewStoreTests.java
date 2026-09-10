package in.devmedi.kiosk.module.physician.service;

import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompletedCaseReviewStoreTests {

    @Test
    void registerAssignsAStableCaseIdentity() {
        CompletedCaseReviewStore store = new CompletedCaseReviewStore();
        ClinicalConversationResult result = new ClinicalConversationResult();

        CompletedCase completed = store.register(result);

        assertThat(completed).isNotNull();
        assertThat(completed.id()).startsWith("case-");
        assertThat(completed.result()).isSameAs(result);
    }

    @Test
    void caseIdentityRemainsAvailableWhileHeldInTheStore() {
        CompletedCaseReviewStore store = new CompletedCaseReviewStore();
        store.register(new ClinicalConversationResult());

        CompletedCase firstLookup = store.latest().orElseThrow();
        CompletedCase secondLookup = store.latest().orElseThrow();

        assertThat(secondLookup.id()).isEqualTo(firstLookup.id());
        assertThat(secondLookup.result()).isSameAs(firstLookup.result());
    }

    @Test
    void latestReturnsTheMostRecentlyRegisteredCase() {
        CompletedCaseReviewStore store = new CompletedCaseReviewStore();
        CompletedCase first = store.register(new ClinicalConversationResult());
        CompletedCase second = store.register(new ClinicalConversationResult());

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(store.latest().orElseThrow().id()).isEqualTo(second.id());
    }

    @Test
    void everyRegisteredCaseGetsANewIdentity() {
        CompletedCaseReviewStore store = new CompletedCaseReviewStore();
        ClinicalConversationResult result = new ClinicalConversationResult();

        assertThat(store.register(result).id()).isNotEqualTo(store.register(result).id());
    }

    @Test
    void clearRemovesTheHeldCase() {
        CompletedCaseReviewStore store = new CompletedCaseReviewStore();
        store.register(new ClinicalConversationResult());

        store.clear();

        assertThat(store.latest()).isEmpty();
    }
}