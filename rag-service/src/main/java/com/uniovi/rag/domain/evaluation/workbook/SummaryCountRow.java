package com.uniovi.rag.domain.evaluation.workbook;

/** Row from {@code summary_counts} sheet. */
public record SummaryCountRow(String dataset, String rows, String purpose, String primaryBranch) {}
