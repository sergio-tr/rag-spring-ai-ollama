package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.application.service.runtime.validation.ResponseValidator;
import com.uniovi.rag.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class DefaultDeterministicToolResultMapper implements DeterministicToolResultMapper {

    private final ResponseValidator responseValidator;

    public DefaultDeterministicToolResultMapper(ResponseValidator responseValidator) {
        this.responseValidator = responseValidator;
    }

    @Override
    public MappedToolOutput map(ToolResult raw, DeterministicToolKind kind) {
        if (raw == null || raw.result() == null || raw.result().isBlank()) {
            return null;
        }
        String validated =
                responseValidator.validateAndClean(raw.result(), "DeterministicTool-" + kind.name());
        if (validated == null || validated.isBlank()) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", Optional.ofNullable(raw.source()).orElse(""));
        payload.put("toolKind", kind.name());
        return new MappedToolOutput(kind, validated, payload);
    }
}
