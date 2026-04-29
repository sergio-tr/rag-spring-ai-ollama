package com.uniovi.rag.interfaces.rest.support.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiValidationError(String field, String message) {}

