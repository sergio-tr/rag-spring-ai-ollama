package com.uniovi.rag.interfaces.rest.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OauthExchangeRequest(@NotBlank String code) {
}

