package com.uniovi.rag.application.service.model;

import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.domain.product.ProductDemoModel;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Filters embedding model tags for Lab comparison against the fixed {@code vector_store.embedding} width.
 * Uses curated dimensions when known; excludes tags that commonly mismatch the default store on Ollama builds.
 */
@Component
public class EmbeddingModelStoreCompatibility {

    private static final Pattern INCOMPATIBLE_PATTERN =
            Pattern.compile("nomic-embed|qwen3-embedding", Pattern.CASE_INSENSITIVE);

    private final RagVectorProperties ragVectorProperties;

    public EmbeddingModelStoreCompatibility(RagVectorProperties ragVectorProperties) {
        this.ragVectorProperties = ragVectorProperties;
    }

    public boolean isSelectableForLabEmbeddingBenchmark(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        String trimmed = modelName.trim();
        if (INCOMPATIBLE_PATTERN.matcher(trimmed).find()) {
            return false;
        }
        Optional<ProductDemoModel> curated = resolveCurated(trimmed);
        if (curated.isPresent() && curated.get().modelType() == AllowedModelType.EMBEDDING) {
            return curated.get().fitsStoreEmbeddingDimension(ragVectorProperties.storeEmbeddingDimension());
        }
        return true;
    }

    private static Optional<ProductDemoModel> resolveCurated(String raw) {
        Optional<ProductDemoModel> direct = ProductDemoModel.resolve(raw);
        if (direct.isPresent()) {
            return direct;
        }
        int colon = raw.lastIndexOf(':');
        if (colon > 0) {
            return ProductDemoModel.resolve(raw.substring(0, colon));
        }
        return Optional.empty();
    }
}
