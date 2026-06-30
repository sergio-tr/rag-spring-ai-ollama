package com.uniovi.rag.infrastructure.vector;

import com.uniovi.rag.configuration.RagVectorProperties;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validates embedding output width against the physical {@code vector_store.embedding} column.
 */
@Service
public class EmbeddingSpaceGuard {

    private static final String PROBE_A = "rag-embedding-dimension-probe-a";
    private static final String PROBE_B = "rag-embedding-dimension-probe-b";

    private final ProviderAwareEmbeddingModelFactory embeddingModelFactory;
    private final RagVectorProperties ragVectorProperties;

    public EmbeddingSpaceGuard(
            ProviderAwareEmbeddingModelFactory embeddingModelFactory, RagVectorProperties ragVectorProperties) {
        this.embeddingModelFactory = embeddingModelFactory;
        this.ragVectorProperties = ragVectorProperties;
    }

    public int probeOutputDimensions(String modelId) {
        String effectiveModelId = embeddingModelFactory.effectiveModelId(modelId);
        EmbeddingModel model = embeddingModelFactory.forModel(modelId);
        int dims = AbstractEmbeddingModel.dimensions(model, PROBE_A, PROBE_B);
        if (dims <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "EMBEDDING_DIMENSION_UNAVAILABLE: could not determine output dimension for model '"
                            + effectiveModelId
                            + "'");
        }
        return dims;
    }

    /**
     * @return embedding width after validating it matches {@link RagVectorProperties#storeEmbeddingDimension()}
     */
    public int assertFitsPhysicalVectorColumnReturning(String modelId) {
        String effectiveModelId = embeddingModelFactory.effectiveModelId(modelId);
        int dims = probeOutputDimensions(modelId);
        int expected = ragVectorProperties.storeEmbeddingDimension();
        if (dims != expected) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "EMBEDDING_DIMENSION_MISMATCH: model '"
                            + effectiveModelId
                            + "' outputs "
                            + dims
                            + " dimensions but this deployment's vector_store.embedding column is fixed to "
                            + expected
                            + " (reindex with a compatible model or migrate the schema).");
        }
        return dims;
    }

    public void assertFitsPhysicalVectorColumn(String modelId) {
        assertFitsPhysicalVectorColumnReturning(modelId);
    }
}
