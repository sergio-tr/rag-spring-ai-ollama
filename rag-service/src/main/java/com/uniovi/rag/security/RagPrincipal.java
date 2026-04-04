package com.uniovi.rag.security;

import java.util.UUID;

/**
 * Authenticated API user resolved from JWT (subject = user id).
 */
public record RagPrincipal(UUID userId, String email, String roleName) {
}
