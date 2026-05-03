package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;

public record ProjectListResponseDto(List<ProjectSummaryDto> items, long total) {
}
