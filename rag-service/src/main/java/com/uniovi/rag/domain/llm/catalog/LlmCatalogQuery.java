package com.uniovi.rag.domain.llm.catalog;

import com.uniovi.rag.domain.llm.LlmProvider;

/** Filter for configured catalog listings. */
public record LlmCatalogQuery(
        LlmProvider provider,
        LlmModelCapability capability,
        Boolean selectableByUser,
        Boolean usableAsDefault) {

    public static LlmCatalogQuery all() {
        return new LlmCatalogQuery(null, null, null, null);
    }

    public static LlmCatalogQuery forProvider(LlmProvider provider) {
        return new LlmCatalogQuery(provider, null, null, null);
    }

    public static LlmCatalogQuery forProviderAndCapability(LlmProvider provider, LlmModelCapability capability) {
        return new LlmCatalogQuery(provider, capability, null, null);
    }
}
