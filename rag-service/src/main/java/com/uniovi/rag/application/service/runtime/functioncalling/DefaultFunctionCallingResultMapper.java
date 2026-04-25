package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.application.service.runtime.tool.DeterministicToolResultMapper;
import com.uniovi.rag.application.service.runtime.tool.MappedToolOutput;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DefaultFunctionCallingResultMapper implements FunctionCallingResultMapper {

    private final DeterministicToolResultMapper deterministicToolResultMapper;

    public DefaultFunctionCallingResultMapper(DeterministicToolResultMapper deterministicToolResultMapper) {
        this.deterministicToolResultMapper = deterministicToolResultMapper;
    }

    @Override
    public Map<String, Object> normalizedPayload(ToolResult raw, DeterministicToolKind kind) {
        MappedToolOutput m = deterministicToolResultMapper.map(raw, kind);
        return m != null ? m.normalizedPayload() : Map.of("error", "mapping_failed");
    }

    @Override
    public String stableAnswerText(ToolResult raw, DeterministicToolKind kind) {
        MappedToolOutput m = deterministicToolResultMapper.map(raw, kind);
        return m != null ? m.answerText() : "";
    }
}
