package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.configuration.RagRuntimeProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimePromptBudgeterTest {

    @Test
    void fullCorpus_budget_truncatesAndReturnsMetadata() {
        RagRuntimeProperties props = new RagRuntimeProperties();
        props.getContext().setMaxPromptChars(64);
        props.getContext().setFullCorpusMaxChars(64);
        RuntimePromptBudgeter b = new RuntimePromptBudgeter(props);

        RuntimePromptBudgeter.BudgetResult r = b.budgetForFullCorpus("a".repeat(400));
        assertThat(r.truncated()).isTrue();
        assertThat(r.originalChars()).isGreaterThan(r.finalChars());
        assertThat(r.budgetChars()).isEqualTo(64);
        assertThat(r.textUsed()).contains("...[context truncated]");
    }

    @Test
    void legacy_budget_doesNotTruncateWhenUnderBudget() {
        RagRuntimeProperties props = new RagRuntimeProperties();
        props.getContext().setLegacyContextMaxChars(128);
        RuntimePromptBudgeter b = new RuntimePromptBudgeter(props);

        RuntimePromptBudgeter.BudgetResult r = b.budgetForLegacyContext("short context");
        assertThat(r.truncated()).isFalse();
        assertThat(r.textUsed()).isEqualTo("short context");
    }
}

