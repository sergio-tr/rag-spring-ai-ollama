package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.config.validation.CompatibilityViolation;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityResultDtoTest {

    @Test
    void fromDomain_mapsMessagesAndSeverity() {
        CompatibilityResult domain = new CompatibilityResult(
                List.of(CompatibilityViolation.of("R1", "bad")),
                List.of(CompatibilityViolation.of("R2", "warn")),
                List.of("hint"));
        CompatibilityResultDto dto = CompatibilityResultDto.fromDomain(domain);
        assertThat(dto.valid()).isFalse();
        assertThat(dto.severity()).isEqualTo("ERROR");
        assertThat(dto.errors()).containsExactly("bad");
        assertThat(dto.warnings()).containsExactly("warn");
        assertThat(dto.fallbackSuggestions()).containsExactly("hint");
    }

    @Test
    void toDomain_treatsNullListsAsEmpty() {
        CompatibilityResultDto dto = new CompatibilityResultDto(true, "OK", null, null, null);
        CompatibilityResult back = dto.toDomain();
        assertThat(back.valid()).isTrue();
        assertThat(back.errors()).isEmpty();
        assertThat(back.warnings()).isEmpty();
        assertThat(back.fallbackSuggestions()).isEmpty();
    }

    @Test
    void toDomain_preservesSuggestionsWhenPresent() {
        CompatibilityResultDto dto =
                new CompatibilityResultDto(true, "OK", List.of(), List.of(), List.of("x"));
        assertThat(dto.toDomain().fallbackSuggestions()).containsExactly("x");
    }
}
