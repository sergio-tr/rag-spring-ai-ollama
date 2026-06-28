package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.UserRole;

import java.time.Instant;

/**
 * Factory for {@link UserEntity} construction outside the entity package.
 */
public final class UserEntityFactory {

    private UserEntityFactory() {
    }

    public static UserEntity newRegisteredUser(String email, String name, String passwordHash) {
        UserEntity u = new UserEntity();
        u.setEmail(email);
        u.setName(name);
        u.setPasswordHash(passwordHash);
        u.setRole(UserRole.USER);
        u.setCreatedAt(Instant.now());
        u.setEmailVerified(false);
        u.setEmailVerifiedAt(null);
        return u;
    }

    public static UserEntity newUser(String email, String name, String passwordHash, UserRole role, Instant createdAt) {
        UserEntity u = new UserEntity();
        u.setEmail(email);
        u.setName(name);
        u.setPasswordHash(passwordHash);
        u.setRole(role);
        u.setCreatedAt(createdAt != null ? createdAt : Instant.now());
        u.setEmailVerified(true);
        u.setEmailVerifiedAt(Instant.now());
        return u;
    }
}
