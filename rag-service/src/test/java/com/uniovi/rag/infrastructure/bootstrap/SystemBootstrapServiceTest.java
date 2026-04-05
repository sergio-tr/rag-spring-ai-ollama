package com.uniovi.rag.infrastructure.bootstrap;

import com.uniovi.rag.infrastructure.persistence.jpa.DefaultSystemConfigurationEntity;
import com.uniovi.rag.infrastructure.persistence.DefaultSystemConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemBootstrapServiceTest {

    @Test
    void run_whenNoRow_savesDefault() {
        DefaultSystemConfigurationRepository repo = mock(DefaultSystemConfigurationRepository.class);
        when(repo.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());

        SystemBootstrapService svc = new SystemBootstrapService(repo);
        svc.run(new DefaultApplicationArguments(new String[0]));

        verify(repo).save(any());
    }

    @Test
    void run_whenRowExists_doesNotSave() {
        DefaultSystemConfigurationRepository repo = mock(DefaultSystemConfigurationRepository.class);
        when(repo.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(mock(DefaultSystemConfigurationEntity.class)));

        SystemBootstrapService svc = new SystemBootstrapService(repo);
        svc.run(new DefaultApplicationArguments(new String[0]));

        verify(repo, never()).save(any());
    }
}
