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
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

class LabJobSseHubBrokenPipeTest {

    @Test
    void publish_brokenPipe_removesSubscriberAndCompletesQuietly() throws Exception {
        LabJobSseHub hub = new LabJobSseHub();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        IOException brokenPipe = new IOException("Broken pipe");
        doThrow(new AsyncRequestNotUsableException("ServletOutputStream failed to flush", brokenPipe))
                .when(emitter)
                .send(any(SseEventBuilder.class));

        hub.register(taskId, userId, emitter, event -> {});

        hub.publish(taskId, sampleEvent(taskId, 2L));

        verify(emitter, never()).completeWithError(any());
        verify(emitter).complete();
    }

    @Test
    void isClientDisconnected_recognizesAsyncRequestNotUsableWithBrokenPipeCause() {
        IOException brokenPipe = new IOException("Broken pipe");
        AsyncRequestNotUsableException ex =
                new AsyncRequestNotUsableException("ServletOutputStream failed to flush", brokenPipe);
        assertThat(LabJobSseHub.isClientDisconnected(ex)).isTrue();
    }

    @Test
    void publish_brokenPipe_doesNotInvokeTerminalCallbackForNonTerminalEvent() throws Exception {
        LabJobSseHub hub = new LabJobSseHub();
        UUID taskId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("Broken pipe")).when(emitter).send(any(SseEventBuilder.class));
        var terminalSeen = new boolean[] {false};
        hub.register(taskId, UUID.randomUUID(), emitter, event -> terminalSeen[0] = true);

        hub.publish(taskId, sampleEvent(taskId, 3L));

        assertThat(terminalSeen[0]).isFalse();
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
