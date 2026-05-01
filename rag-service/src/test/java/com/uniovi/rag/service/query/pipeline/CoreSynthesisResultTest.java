package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.application.model.DraftAndContext;
import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.domain.model.QueryType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoreSynthesisResultTest {

    @Test
    void toDirectQueryResponse_mapsToolKinds() {
        CoreSynthesisResult r =
                new CoreSynthesisResult(
                        new DraftAndContext("a", "a"), CoreSynthesisResult.Kind.TOOL, "MetadataCount");

        QueryResponse qr = r.toDirectQueryResponse(QueryType.COUNT_DOCUMENTS);

        assertThat(qr.isUsedTool()).isTrue();
        assertThat(qr.getAnswer()).isEqualTo("a");
        assertThat(qr.getToolUsed()).isEqualTo("MetadataCount");
    }

    @Test
    void toDirectQueryResponse_mapsMetadataGuard_withDefaultToolName() {
        CoreSynthesisResult r =
                new CoreSynthesisResult(new DraftAndContext("g", "g"), CoreSynthesisResult.Kind.METADATA_GUARD, null);

        QueryResponse qr = r.toDirectQueryResponse(QueryType.GET_DURATION);

        assertThat(qr.isUsedTool()).isTrue();
        assertThat(qr.getToolUsed()).isEqualTo("tool");
    }

    @Test
    void toDirectQueryResponse_mapsLlmKind() {
        CoreSynthesisResult r =
                new CoreSynthesisResult(new DraftAndContext("l", "l"), CoreSynthesisResult.Kind.LLM, null);

        QueryResponse qr = r.toDirectQueryResponse(QueryType.FIND_PARAGRAPH);

        assertThat(qr.isUsedTool()).isFalse();
        assertThat(qr.getAnswer()).isEqualTo("l");
    }
}
