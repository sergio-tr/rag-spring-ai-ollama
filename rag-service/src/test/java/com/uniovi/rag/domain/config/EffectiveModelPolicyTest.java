package com.uniovi.rag.domain.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EffectiveModelPolicyTest {

    @Test
    void emptyAllowlist_acceptsAny() {
        assertEquals("m1", EffectiveModelPolicy.validateChatModelOverride("m1", Set.of()));
    }

    @Test
    void nonEmptyAllowlist_rejectsUnknown() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EffectiveModelPolicy.validateChatModelOverride("bad", Set.of("good")));
    }

    @Test
    void nonEmptyAllowlist_acceptsListed() {
        assertEquals("good", EffectiveModelPolicy.validateChatModelOverride("good", Set.of("good", "other")));
    }
}
