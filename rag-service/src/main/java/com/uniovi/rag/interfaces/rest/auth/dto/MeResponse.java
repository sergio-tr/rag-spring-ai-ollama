package com.uniovi.rag.interfaces.rest.auth.dto;

import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import java.time.Instant;
import java.util.UUID;

public record MeResponse(
        UUID userId,
        String email,
        String name,
        String roleName,
        boolean emailVerified,
        Instant emailVerifiedAt) {

    public static MeResponse fromUser(UserEntity u) {
        String roleName = u.getRole() != null ? u.getRole().name() : null;
        return new MeResponse(
                u.getId(),
                u.getEmail(),
                u.getName(),
                roleName,
                u.isEmailVerified(),
                u.getEmailVerifiedAt());
    }
}

