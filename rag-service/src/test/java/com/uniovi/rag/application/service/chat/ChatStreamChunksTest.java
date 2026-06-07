package com.uniovi.rag.application.service.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatStreamChunksTest {

    @Test
    void chunkForStream_nullOrEmpty_returnsSingleEmptyString() {
        assertEquals(List.of(""), ChatStreamChunks.chunkForStream(null));
        assertEquals(List.of(""), ChatStreamChunks.chunkForStream(""));
    }

    @Test
    void chunkForStream_whitespaceOnly_fallsBackToOriginal() {
        assertEquals(List.of("   "), ChatStreamChunks.chunkForStream("   "));
    }

    @Test
    void chunkForStream_splitsIntoWordAlignedChunks() {
        String s = "one two three four five six seven eight";
        List<String> parts = ChatStreamChunks.chunkForStream(s);
        assertEquals(s, String.join("", parts));
        for (String p : parts) {
            assertTrue(p.length() <= 32);
        }
    }
}
