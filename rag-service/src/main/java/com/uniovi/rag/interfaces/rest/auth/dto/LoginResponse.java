package com.uniovi.rag.interfaces.rest.auth.dto;

public record LoginResponse(String accessToken, String refreshToken, AuthUserDto user) {
}
