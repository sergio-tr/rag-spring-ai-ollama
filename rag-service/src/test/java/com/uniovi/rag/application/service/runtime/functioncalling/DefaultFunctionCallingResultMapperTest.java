package com.uniovi.rag.application.service.runtime.functioncalling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.runtime.tool.DeterministicToolResultMapper;
import com.uniovi.rag.application.service.runtime.tool.MappedToolOutput;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.tool.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultFunctionCallingResultMapperTest {

    @Mock private DeterministicToolResultMapper deterministicToolResultMapper;
    @Mock private ToolResult raw;

    @Test
    void mapsStableAnswerAndPayloadFromSharedToolMapper() {
        DefaultFunctionCallingResultMapper mapper = new DefaultFunctionCallingResultMapper(deterministicToolResultMapper);
        when(deterministicToolResultMapper.map(raw, DeterministicToolKind.COUNT_DOCUMENTS_TOOL))
                .thenReturn(new MappedToolOutput(DeterministicToolKind.COUNT_DOCUMENTS_TOOL, "42", Map.of("count", 42)));
        assertThat(mapper.stableAnswerText(raw, DeterministicToolKind.COUNT_DOCUMENTS_TOOL)).isEqualTo("42");
        assertThat(mapper.normalizedPayload(raw, DeterministicToolKind.COUNT_DOCUMENTS_TOOL)).containsEntry("count", 42);
    }
}
