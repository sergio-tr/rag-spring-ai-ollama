package com.uniovi.rag.tool;

import com.uniovi.rag.infrastructure.observability.Loggable;

public interface Tool extends Loggable {

    ToolResult execute(ToolExecutionContext context);

}
