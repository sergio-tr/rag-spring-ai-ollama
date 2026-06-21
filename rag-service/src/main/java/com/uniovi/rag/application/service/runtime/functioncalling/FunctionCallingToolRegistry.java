package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/** Whitelist registry for function-calling tool exposure. */
public interface FunctionCallingToolRegistry {

    /**
     * Returns callbacks for the given subset, in stable enum order.
     */
    List<ToolCallback> callbacksFor(Iterable<DeterministicToolKind> kinds);
}
