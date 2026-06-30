package com.uniovi.rag.application.config;

import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateValidatorTest {

  private final PromptTemplateValidator validator = new PromptTemplateValidator();

  @Test
  void acceptsDefaultLikeOverrideWithRequiredVariables() {
    String content = ConfigurablePromptGroup.QUERY_REWRITE.defaultContent();
    validator.validateContent(ConfigurablePromptGroup.QUERY_REWRITE, content);
  }

  @Test
  void rejectsMissingRequiredVariables() {
    assertThatThrownBy(
            () -> validator.validateContent(ConfigurablePromptGroup.QUERY_REWRITE, "no placeholders here"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing required variables");
  }

  @Test
  void validatesNestedOverridesMap() {
    String template = ConfigurablePromptGroup.EVALUATION_JUDGE.defaultContent();
    validator.validateOverrides(
        Map.of(
            "promptOverrides",
            Map.of("evaluation_judge", template)));
  }
}
