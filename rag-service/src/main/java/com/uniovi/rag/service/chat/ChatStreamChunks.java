package com.uniovi.rag.service.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Word-aligned chunks for SSE-style streaming of a completed answer (same strategy as {@code MessageStreamController}).
 */
public final class ChatStreamChunks {

    private static final Pattern WORD_CHUNK_PATTERN = Pattern.compile("\\S+\\s*");

    private ChatStreamChunks() {}

    public static List<String> chunkForStream(String answer) {
        if (answer == null || answer.isEmpty()) {
            return List.of("");
        }
        List<String> tokens = new ArrayList<>();
        Matcher m = WORD_CHUNK_PATTERN.matcher(answer);
        while (m.find()) {
            tokens.add(m.group());
        }
        if (tokens.isEmpty()) {
            return List.of(answer);
        }
        int maxChunk = 32;
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String t : tokens) {
            if (cur.length() > 0 && cur.length() + t.length() > maxChunk) {
                parts.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(t);
        }
        if (cur.length() > 0) {
            parts.add(cur.toString());
        }
        return parts;
    }
}
