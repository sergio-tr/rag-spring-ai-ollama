package com.uniovi.rag.domain.runtime.tool;

import com.uniovi.rag.tool.ToolResult;

import java.util.Optional;

/**
 * Raw outcome of {@link com.uniovi.rag.application.service.runtime.tool.MeetingMinutesToolExecutionCore}
 * before deterministic or FC mappers.
 */
public record MeetingMinutesToolRawResult(
        Status status,
        Optional<DeterministicToolKind> kind,
        Optional<ToolResult> raw,
        Optional<String> errorDetail) {

    public enum Status {
        OK,
        MISSING_TOOL,
        RUNTIME_FAILURE
    }

    public static MeetingMinutesToolRawResult ok(DeterministicToolKind kind, ToolResult raw) {
        return new MeetingMinutesToolRawResult(Status.OK, Optional.of(kind), Optional.of(raw), Optional.empty());
    }

    public static MeetingMinutesToolRawResult missingTool(DeterministicToolKind kind) {
        return new MeetingMinutesToolRawResult(
                Status.MISSING_TOOL, Optional.of(kind), Optional.empty(), Optional.of("no_tool_registered"));
    }

    public static MeetingMinutesToolRawResult runtimeFailure(DeterministicToolKind kind, String message) {
        return new MeetingMinutesToolRawResult(
                Status.RUNTIME_FAILURE,
                Optional.of(kind),
                Optional.empty(),
                Optional.ofNullable(message));
    }
}
