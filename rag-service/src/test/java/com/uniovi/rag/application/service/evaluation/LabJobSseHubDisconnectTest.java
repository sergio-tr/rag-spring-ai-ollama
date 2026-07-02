package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.uniovi.rag.interfaces.rest.dto.LabJobEventDto;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

class LabJobSseHubDisconnectTest {

    @Test
    void publish_clientDisconnect_removesSubscriberWithoutCompleteWithError() throws Exception {
        LabJobSseHub hub = new LabJobSseHub();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(emitter)
                .send(any(SseEventBuilder.class));

        hub.register(taskId, userId, emitter, event -> {});

        LabJobEventDto event = sampleEvent(taskId, 1L);
        hub.publish(taskId, event);

        verify(emitter, never()).completeWithError(any());
        verify(emitter).complete();
    }

    @Test
    void isClientDisconnected_recognizesIllegalStateException() {
        assertThat(LabJobSseHub.isClientDisconnected(new IllegalStateException("already completed"))).isTrue();
    }

    @Test
    void isClientDisconnected_doesNotTreatGenericRuntimeAsDisconnect() {
        assertThat(LabJobSseHub.isClientDisconnected(new RuntimeException("boom"))).isFalse();
    }

    private static LabJobEventDto sampleEvent(UUID taskId, long eventId) {
        return new LabJobEventDto(
                eventId,
                taskId,
                "PROGRESS",
                "RUNNING",
                "1/3",
                "step",
                Instant.parse("2026-01-01T00:00:01Z"),
                Map.of(),
                null,
                null,
                null,
                1,
                3,
                null,
                null,
                null,
                null);
    }
}
