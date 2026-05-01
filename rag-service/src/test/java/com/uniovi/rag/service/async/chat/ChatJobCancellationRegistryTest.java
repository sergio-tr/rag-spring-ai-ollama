package com.uniovi.rag.service.async.chat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatJobCancellationRegistryTest {

    @Test
    void signalCancel_thenIsCancelled_andClear() {
        ChatJobCancellationRegistry reg = new ChatJobCancellationRegistry();
        UUID id = UUID.randomUUID();

        assertFalse(reg.isCancelled(id));
        reg.signalCancel(id);
        assertTrue(reg.isCancelled(id));
        reg.clear(id);
        assertFalse(reg.isCancelled(id));
    }

    @Test
    void unknownTask_notCancelled() {
        assertFalse(new ChatJobCancellationRegistry().isCancelled(UUID.randomUUID()));
    }
}
