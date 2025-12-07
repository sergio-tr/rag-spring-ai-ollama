package com.uniovi.rag.services.tools;

import com.uniovi.rag.model.Loggable;

public interface Tool extends Loggable {

    ToolResult execute(ToolExecutionContext context);

}
