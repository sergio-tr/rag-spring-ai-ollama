package com.uniovi.rag.tool;

import com.uniovi.rag.domain.model.QueryType;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Tool} and the {@link com.uniovi.rag.infrastructure.observability.Loggable} default {@code log()}.
 */
class ToolTest {

    private static final class EchoTool implements Tool {
        @Override
        public ToolResult execute(ToolExecutionContext context) {
            // Exercise Loggable default logger factory wiring (no logging side effects asserted).
            assertThat(log()).isNotNull();
            assertThat(log().getName()).contains(EchoTool.class.getSimpleName());
            return ToolResult.from(context.query(), EchoTool.class);
        }
    }

    @Test
    void execute_canUseDefaultLoggerFromLoggable() {
        Tool tool = new EchoTool();
        ToolExecutionContext ctx = ToolExecutionContext.of("hello", QueryType.BOOLEAN_QUERY, new JSONObject());
        ToolResult r = tool.execute(ctx);
        assertThat(r.result()).isEqualTo("hello");
        assertThat(r.source()).isEqualTo("EchoTool");
    }
}
