package com.uniovi.rag.infrastructure.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSecurityValidatorTest {

    private static final ApplicationArguments NO_ARGS = new DefaultApplicationArguments(new String[0]);

    @Test
    void run_shortSecret_throws() {
        ProductionSecurityValidator v = new ProductionSecurityValidator("short");
        assertThrows(IllegalStateException.class, () -> v.run(NO_ARGS));
    }

    @Test
    void run_placeholderSecret_throws() {
        String secret = "a".repeat(32) + "CHANGE-ME";
        ProductionSecurityValidator v = new ProductionSecurityValidator(secret);
        assertThrows(IllegalStateException.class, () -> v.run(NO_ARGS));
    }

    @Test
    void run_strongSecret_passes() {
        String secret = "a".repeat(40);
        ProductionSecurityValidator v = new ProductionSecurityValidator(secret);
        assertDoesNotThrow(() -> v.run(NO_ARGS));
    }

    @Test
    void constructor_nullSecret_treatedAsEmpty() {
        ProductionSecurityValidator v = new ProductionSecurityValidator(null);
        assertThrows(IllegalStateException.class, () -> v.run(NO_ARGS));
    }
}
