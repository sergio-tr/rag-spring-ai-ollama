package com.uniovi.rag.domain.runtime.tool;

import com.uniovi.rag.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingMinutesToolRawResultTest {

    @Test
    void ok_factory() {
        ToolResult raw = new ToolResult("x", "src");
        MeetingMinutesToolRawResult r =
                MeetingMinutesToolRawResult.ok(DeterministicToolKind.GET_FIELD_TOOL, raw);
        assertEquals(MeetingMinutesToolRawResult.Status.OK, r.status());
        assertEquals(Optional.of(DeterministicToolKind.GET_FIELD_TOOL), r.kind());
        assertEquals(Optional.of(raw), r.raw());
        assertTrue(r.errorDetail().isEmpty());
    }

    @Test
    void missingTool_factory() {
        MeetingMinutesToolRawResult r =
                MeetingMinutesToolRawResult.missingTool(DeterministicToolKind.BOOLEAN_QUERY_TOOL);
        assertEquals(MeetingMinutesToolRawResult.Status.MISSING_TOOL, r.status());
        assertEquals(Optional.of("no_tool_registered"), r.errorDetail());
    }

    @Test
    void runtimeFailure_factory() {
        MeetingMinutesToolRawResult r =
                MeetingMinutesToolRawResult.runtimeFailure(DeterministicToolKind.COUNT_DOCUMENTS_TOOL, "boom");
        assertEquals(MeetingMinutesToolRawResult.Status.RUNTIME_FAILURE, r.status());
        assertEquals(Optional.of("boom"), r.errorDetail());
    }
}
