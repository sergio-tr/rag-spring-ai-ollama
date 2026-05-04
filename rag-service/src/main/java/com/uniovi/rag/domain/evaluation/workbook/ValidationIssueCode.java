package com.uniovi.rag.domain.evaluation.workbook;

/** Stable codes for UI / logs (machine-readable). */
public enum ValidationIssueCode {
    MISSING_SHEET,
    MISSING_COLUMN,
    DUPLICATE_ID,
    EMPTY_REQUIRED_CELL,
    INVALID_QUERY_TYPE,
    INVALID_DIFFICULTY,
    INVALID_PRESET_CODE,
    UNKNOWN_CHUNK_REF,
    EMPTY_ROW_SKIPPED,
    PARSE_ERROR,
    WORKBOOK_IO_ERROR
}
