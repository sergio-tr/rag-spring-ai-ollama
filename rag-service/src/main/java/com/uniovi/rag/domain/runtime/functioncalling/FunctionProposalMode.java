package com.uniovi.rag.domain.runtime.functioncalling;

/** How a function-call proposal was produced. */
public enum FunctionProposalMode {
    BACKEND_DETERMINISTIC,
    MODEL_JSON,
    NATIVE_PROVIDER,
    NONE
}
