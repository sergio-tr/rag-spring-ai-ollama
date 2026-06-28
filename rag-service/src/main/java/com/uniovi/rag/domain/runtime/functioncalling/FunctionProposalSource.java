package com.uniovi.rag.domain.runtime.functioncalling;

/** Origin of a function-call proposal signal. */
public enum FunctionProposalSource {
    QUERY_SHAPE,
    QUERY_PLAN_ENTITIES,
    LAB_ORACLE,
    MODEL_LLM,
    NATIVE_PROVIDER,
    NONE
}
