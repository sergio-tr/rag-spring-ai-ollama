package com.uniovi.rag.interfaces.rest.auth.dto;

import java.util.UUID;

public record AuthUserDto(UUID id, String email, String name, String role) {
}
