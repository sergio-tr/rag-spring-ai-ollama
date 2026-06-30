package com.uniovi.rag.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBundleFingerprintCustomOverridesTest {

  @Test
  void bundleHash_changesWhenPromptOverrideMaterialAdded() {
    PromptBundleFingerprint.Result frozen = PromptBundleFingerprint.computeFrozen();
    PromptBundleFingerprint.Result withOverrides =
        PromptBundleFingerprint.computeForRuntime(
            null, "", "llm-system", "query_rewrite=custom-template\n");

    assertThat(withOverrides.bundleHashSha256()).isNotEqualTo(frozen.bundleHashSha256());
    assertThat(withOverrides.includedGroups())
        .anyMatch(g -> PromptBundleFingerprint.GROUP_PROMPT_OVERRIDES.equals(g.groupId()));
  }
}
