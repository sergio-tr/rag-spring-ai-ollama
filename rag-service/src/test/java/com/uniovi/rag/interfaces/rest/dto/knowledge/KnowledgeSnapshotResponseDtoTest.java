package com.uniovi.rag.interfaces.rest.dto.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeIndexSnapshot;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeSnapshotResponseDtoTest {

    private static KnowledgeIndexSnapshot snapshot(UUID projId, UUID convId) {
        UUID id = UUID.randomUUID();
        UUID cfgId = UUID.randomUUID();
        Instant created = Instant.parse("2024-01-01T12:00:00Z");
        Instant updated = Instant.parse("2024-01-02T12:00:00Z");
        return new KnowledgeIndexSnapshot(
                id,
                "sig-hash",
                KnowledgeSnapshotScopeType.PROJECT,
                KnowledgeSnapshotOwnerType.PROJECT,
                projId,
                projId,
                convId,
                IndexSnapshotStatus.ACTIVE,
                cfgId,
                "cfg-hash",
                Map.of(),
                "profile-hash",
                null,
                created,
                updated);
    }

    @Test
    void detail_fromDomain_mapsAllFields() {
        KnowledgeIndexSnapshot k = snapshot(UUID.randomUUID(), UUID.randomUUID());
        KnowledgeSnapshotDetailResponse dto = KnowledgeSnapshotDetailResponse.fromDomain(k, 99L);
        assertThat(dto.id()).isEqualTo(k.id());
        assertThat(dto.signatureHash()).isEqualTo(k.signatureHash());
        assertThat(dto.scopeType()).isEqualTo(k.scopeType());
        assertThat(dto.status()).isEqualTo(k.status());
        assertThat(dto.documentCount()).isEqualTo(99L);
        assertThat(dto.createdAt()).isEqualTo(k.createdAt());
        assertThat(dto.updatedAt()).isEqualTo(k.updatedAt());
        assertThat(dto.resolvedConfigSnapshotId()).isEqualTo(k.resolvedConfigSnapshotId());
        assertThat(dto.resolvedConfigHash()).isEqualTo(k.resolvedConfigHash());
    }

    @Test
    void summary_fromDomain_mapsSubset() {
        KnowledgeIndexSnapshot k = snapshot(UUID.randomUUID(), UUID.randomUUID());
        KnowledgeSnapshotSummaryResponse dto = KnowledgeSnapshotSummaryResponse.fromDomain(k);
        assertThat(dto.id()).isEqualTo(k.id());
        assertThat(dto.signatureHash()).isEqualTo(k.signatureHash());
        assertThat(dto.scopeType()).isEqualTo(k.scopeType());
        assertThat(dto.status()).isEqualTo(k.status());
        assertThat(dto.createdAt()).isEqualTo(k.createdAt());
        assertThat(dto.resolvedConfigSnapshotId()).isEqualTo(k.resolvedConfigSnapshotId());
    }
}
