package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.CreateDefinitionCommand;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySpec;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeTraceRegressionSuiteDefinitionDetailDtoToCreateCommandTest {

    @Test
    void t18_toCreateDefinitionCommand_mapsNameDescriptionAndEntries() {
        UUID tid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID conv = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Instant from = Instant.parse("2023-01-01T00:00:00Z");
        Instant to = Instant.parse("2023-02-01T00:00:00Z");
        RuntimeTraceRegressionSuiteDefinitionDetailDto dto =
                new RuntimeTraceRegressionSuiteDefinitionDetailDto(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        "  suite-name  ",
                        "  desc  ",
                        99,
                        Instant.parse("2099-01-01T00:00:00Z"),
                        Instant.parse("2099-01-02T00:00:00Z"),
                        List.of(
                                new RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto(List.of(tid)),
                                new RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto(
                                        conv, from, to, "  wf  ")));

        CreateDefinitionCommand cmd = dto.toCreateDefinitionCommand();
        assertThat(cmd.name()).isEqualTo("suite-name");
        assertThat(cmd.description()).hasValue("desc");
        List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> entries = cmd.entries();
        assertThat(entries).hasSize(2);
        assertThat(entries.getFirst()).isEqualTo(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of(tid)));
        RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByConversation bc =
                (RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByConversation) entries.get(1);
        assertThat(bc.conversationId()).isEqualTo(conv);
        assertThat(bc.createdAtFrom()).isEqualTo(Optional.of(from));
        assertThat(bc.createdAtTo()).isEqualTo(Optional.of(to));
        assertThat(bc.workflowName()).hasValue("wf");
    }

    @Test
    void t18_blankWorkflow_becomesEmptyOptional() {
        UUID conv = UUID.randomUUID();
        RuntimeTraceRegressionSuiteDefinitionDetailDto dto =
                new RuntimeTraceRegressionSuiteDefinitionDetailDto(
                        UUID.randomUUID(),
                        "n",
                        null,
                        1,
                        Instant.now(),
                        Instant.now(),
                        List.of(
                                new RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto(
                                        conv, null, null, "   ")));
        CreateDefinitionCommand cmd = dto.toCreateDefinitionCommand();
        RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByConversation bc =
                (RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByConversation) cmd.entries().getFirst();
        assertThat(bc.workflowName()).isEmpty();
    }
}
