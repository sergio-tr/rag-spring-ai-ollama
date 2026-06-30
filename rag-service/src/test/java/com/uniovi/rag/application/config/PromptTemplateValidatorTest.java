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
        .isInstanceOf(PromptTemplateValidationException.class)
        .hasMessageContaining("missing required variables");
  }

  @Test
  void rejectsInvalidPlaceholder() {
    String template = ConfigurablePromptGroup.EVALUATION_JUDGE.defaultContent() + " {badToken}";
    assertThatThrownBy(() -> validator.validateContent(ConfigurablePromptGroup.EVALUATION_JUDGE, template))
        .isInstanceOf(PromptTemplateValidationException.class)
        .satisfies(
            ex -> {
              PromptTemplateValidationException p = (PromptTemplateValidationException) ex;
              assertThat(p.field()).contains("evaluation_judge");
              assertThat(p.invalidPlaceholder()).isEqualTo("{badToken}");
              assertThat(p.allowedPlaceholders()).contains("{question}");
            });
  }

  @Test
  void rejectsSimilarityThresholdOutOfRange() {
    assertThatThrownBy(() -> validator.validateOverrides(Map.of("similarityThreshold", 1.5)))
        .isInstanceOf(PromptTemplateValidationException.class)
        .hasMessageContaining("between 0 and 1");
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
