package com.uniovi.rag.tool;

import com.uniovi.rag.model.Loggable;

public interface Tool extends Loggable {

    ToolResult execute(ToolExecutionContext context);

}
