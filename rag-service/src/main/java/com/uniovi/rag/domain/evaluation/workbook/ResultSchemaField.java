package com.uniovi.rag.domain.evaluation.workbook;

/** Row from {@code result_schema} sheet. */
public record ResultSchemaField(String field, String type, boolean required, String description) {}
