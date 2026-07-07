package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.application.service.runtime.ResponseSourcesBackfill;
import com.uniovi.rag.configuration.ToolDescriptor;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolDecision;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import com.uniovi.rag.domain.runtime.tool.MeetingMinutesToolRawResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class DefaultDeterministicToolExecutor implements DeterministicToolExecutor {

    private static final int MAX_INPUT_SUMMARY_CHARS = 256;

    private final MeetingMinutesToolExecutionCore meetingMinutesToolExecutionCore;
    private final DeterministicToolResultMapper resultMapper;

    public DefaultDeterministicToolExecutor(
            MeetingMinutesToolExecutionCore meetingMinutesToolExecutionCore,
            DeterministicToolResultMapper resultMapper) {
        this.meetingMinutesToolExecutionCore = meetingMinutesToolExecutionCore;
        this.resultMapper = resultMapper;
    }

    @Override
    public DeterministicToolExecutionResult execute(
            DeterministicToolDecision decision, ExecutionContext ctx, QueryPlan plan) {
        Optional<DeterministicToolKind> kindOpt = decision.selectedToolKind();
        if (!decision.selected() || kindOpt.isEmpty()) {
            return DeterministicToolExecutionResult.skipped(
                    decision.outcome(), decision.reasons(), Optional.empty());
        }
        DeterministicToolKind kind = kindOpt.get();
        QueryType queryType = DeterministicToolKindMappings.toQueryType(kind);
        String toolName = ToolDescriptor.getName(queryType);
        String toolInputSummary = buildInputSummary(plan, kind);
        List<String> baseNotes = new ArrayList<>(List.of("toolName=" + toolName, "toolInputSummary=" + toolInputSummary));
        MeetingMinutesToolRawResult raw = meetingMinutesToolExecutionCore.execute(kind, ctx, plan);
        if (raw.status() == MeetingMinutesToolRawResult.Status.MISSING_TOOL) {
            return failed(
                    kind,
                    baseNotes,
                    Map.of("error", "no_tool_registered_for_query_type", "queryType", queryType.name()),
                    List.of("no_tool_registered", "queryType=" + queryType));
        }
        if (raw.status() == MeetingMinutesToolRawResult.Status.RUNTIME_FAILURE) {
            return failed(
                    kind,
                    baseNotes,
                    Map.of(
                            "error",
                            "tool_execution_exception",
                            "message",
                            raw.errorDetail().orElse("")),
                    List.of("tool_execution_exception", raw.errorDetail().orElse("")));
        }
        MappedToolOutput mapped = resultMapper.map(raw.raw().orElseThrow(), kind);
        if (mapped == null) {
            return failed(
                    kind,
                    baseNotes,
                    Map.of("error", "tool_output_validation_failed"),
                    List.of("tool_output_validation_failed", "kind=" + kind));
        }
        Map<String, Object> payload = new LinkedHashMap<>(mapped.normalizedPayload());
        payload.put("toolName", toolName);
        payload.put("toolInputSummary", toolInputSummary);
        payload.put("toolOutputHash", hashText(mapped.answerText()));
        List<Map<String, Object>> sources =
                ResponseSourcesBackfill.fromToolExecution(payload, mapped.answerText());
        if (!sources.isEmpty()) {
            payload.put("responseSources", sources);
        }
        List<String> notes = new ArrayList<>(baseNotes);
        notes.add("executed=" + kind);
        notes.add("toolOutputHash=" + payload.get("toolOutputHash"));
        return new DeterministicToolExecutionResult(
                Optional.of(kind),
                DeterministicToolOutcome.EXECUTED_SUCCESS,
                true,
                mapped.answerText(),
                payload,
                List.copyOf(notes));
    }

    private static DeterministicToolExecutionResult failed(
            DeterministicToolKind kind,
            List<String> baseNotes,
            Map<String, Object> payload,
            List<String> failureNotes) {
        List<String> notes = new ArrayList<>(baseNotes);
        notes.addAll(failureNotes);
        return new DeterministicToolExecutionResult(
                Optional.of(kind),
                DeterministicToolOutcome.EXECUTED_FAILED_INFRA,
                false,
                "",
                payload,
                List.copyOf(notes));
    }

    private static String buildInputSummary(QueryPlan plan, DeterministicToolKind kind) {
        StringBuilder sb = new StringBuilder();
        sb.append("kind=").append(kind.name());
        sb.append(";query=").append(truncate(plan.rewrittenQueryText(), MAX_INPUT_SUMMARY_CHARS));
        if (!plan.slots().isEmpty()) {
            sb.append(";slots=")
                    .append(
                            plan.slots().entrySet().stream()
                                    .map(e -> e.getKey() + ":" + e.getValue())
                                    .collect(Collectors.joining(",")));
        }
        return truncate(sb.toString(), MAX_INPUT_SUMMARY_CHARS);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max) + "...";
    }

    private static String hashText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            return "";
        }
    }
}
