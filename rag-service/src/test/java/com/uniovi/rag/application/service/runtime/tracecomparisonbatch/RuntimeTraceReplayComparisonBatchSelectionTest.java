package com.uniovi.rag.application.service.runtime.tracecomparisonbatch;

import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeTraceReplayComparisonBatchSelectionTest {

    @Test
    void by_trace_ids_rejects_null_element() {
        List<UUID> ids = new ArrayList<>();
        ids.add(UUID.randomUUID());
        ids.add(null);
        assertThatThrownBy(() -> RuntimeTraceReplayComparisonBatchRequest.byTraceIds(UUID.randomUUID(), ids))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dedupe_preserves_first_seen_order() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<UUID> raw = List.of(b, a, b, c, a);
        assertThat(RuntimeTraceReplayComparisonBatchService.dedupePreserveOrder(raw)).containsExactly(b, a, c);
    }

    @Test
    void raw_list_over_50_is_valid_request_object_but_service_returns_not_attempted() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            ids.add(UUID.randomUUID());
        }
        RuntimeTraceReplayComparisonBatchRequest req =
                RuntimeTraceReplayComparisonBatchRequest.byTraceIds(UUID.randomUUID(), ids);
        var batch = new RuntimeTraceReplayComparisonBatchService(null, null);
        var result = batch.execute(req);
        assertThat(result.batchOutcome()).isEqualTo(RuntimeTraceReplayComparisonBatchOutcome.NOT_ATTEMPTED);
        assertThat(result.requestedCount()).isEqualTo(51);
        assertThat(result.selectedCount()).isEqualTo(0);
        assertThat(result.summary().processedCount()).isZero();
    }
}
