package com.uniovi.rag.application.service;

import com.uniovi.rag.infrastructure.persistence.AuditLogRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditApplicationServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuditApplicationService service;

    @Test
    void record_persistsWithActor() {
        UUID actorId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UserEntity actor = Mockito.mock(UserEntity.class);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));

        service.persistAuditEntry(actorId, "UPDATE", "RagConfiguration", resourceId, Map.of("k", 1));

        verify(userRepository).findById(actorId);
        verify(auditLogRepository).save(any());
    }

    @Test
    void record_nullActor_savesWithoutUser() {
        UUID resourceId = UUID.randomUUID();
        service.persistAuditEntry(null, "DELETE", "Preset", resourceId, Map.of());

        verify(userRepository, never()).findById(any());
        verify(auditLogRepository).save(any());
    }
}
