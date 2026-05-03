package com.uniovi.rag.application.service.runtime.tracereplaybatch;

import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchOutcome;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceReplayBatchSelectionTest {

    @Mock
    private RuntimeTraceReplayService replayService;

    @Mock
    private RuntimeTraceQueryService traceQueryService;

    @Test
    void dedupe_preserves_first_seen_order() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<UUID> raw = List.of(b, a, b, c, a);
        assertThat(RuntimeTraceReplayBatchService.dedupePreserveOrder(raw)).containsExactly(b, a, c);
    }

    @Test
    void raw_list_over_50_is_valid_request_object_but_service_returns_not_attempted() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            ids.add(UUID.randomUUID());
        }
        RuntimeTraceReplayBatchRequest req = RuntimeTraceReplayBatchRequest.byTraceIds(UUID.randomUUID(), ids);
        var batch = new RuntimeTraceReplayBatchService(replayService, traceQueryService);
        var result = batch.execute(req);
        assertThat(result.batchOutcome()).isEqualTo(RuntimeTraceReplayBatchOutcome.NOT_ATTEMPTED);
        assertThat(result.requestedCount()).isEqualTo(51);
        assertThat(result.selectedCount()).isZero();
        assertThat(result.summary().processedCount()).isZero();
    }

    @Test
    void by_trace_ids_accepts_null_element_in_list_service_returns_not_attempted() {
        List<UUID> ids = new ArrayList<>();
        ids.add(UUID.randomUUID());
        ids.add(null);
        RuntimeTraceReplayBatchRequest req = RuntimeTraceReplayBatchRequest.byTraceIds(UUID.randomUUID(), ids);
        var batch = new RuntimeTraceReplayBatchService(replayService, traceQueryService);
        var result = batch.execute(req);
        assertThat(result.batchOutcome()).isEqualTo(RuntimeTraceReplayBatchOutcome.NOT_ATTEMPTED);
        assertThat(result.requestedCount()).isEqualTo(2);
    }
}
