package com.uniovi.rag.domain.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic snapshot signature over index signature + sorted document ids and checksums.
 */
public final class SnapshotSignatureHasher {

    private SnapshotSignatureHasher() {}

    private record DocPair(UUID id, String checksum) {}

    /**
     * Stable SHA-256 hex: indexSigHex | sorted (documentId|checksum) pairs.
     */
    public static String computeSnapshotSignatureHex(
            String indexSignatureHashHex, List<UUID> documentIds, List<String> contentChecksums) {
        if (documentIds.size() != contentChecksums.size()) {
            throw new IllegalArgumentException("documentIds and checksums size mismatch");
        }
        List<DocPair> pairs = new ArrayList<>();
        for (int i = 0; i < documentIds.size(); i++) {
            pairs.add(new DocPair(
                    documentIds.get(i),
                    contentChecksums.get(i) != null ? contentChecksums.get(i) : ""));
        }
        pairs.sort(Comparator.comparing(DocPair::id));
        StringBuilder sb = new StringBuilder(indexSignatureHashHex != null ? indexSignatureHashHex : "");
        sb.append("|");
        for (DocPair p : pairs) {
            sb.append(p.id()).append("|").append(p.checksum()).append(";");
        }
        if (pairs.isEmpty()) {
            sb.append("EMPTY");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
