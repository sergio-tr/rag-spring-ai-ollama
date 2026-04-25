package com.uniovi.rag.infrastructure.bootstrap;

import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class E2eAdminUserSeederTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private E2eAdminUserSeeder seeder;

    @Test
    void run_whenAdminExists_doesNothing() {
        when(userRepository.findByEmailIgnoreCase(E2eAdminUserSeeder.E2E_ADMIN_EMAIL))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(UserEntity.class)));

        seeder.run(new DefaultApplicationArguments());

        verify(userRepository).findByEmailIgnoreCase(E2eAdminUserSeeder.E2E_ADMIN_EMAIL);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void run_seedsAdminWhenMissing() {
        when(userRepository.findByEmailIgnoreCase(E2eAdminUserSeeder.E2E_ADMIN_EMAIL))
                .thenReturn(Optional.empty());

        seeder.run(new DefaultApplicationArguments());

        ArgumentCaptor<UserEntity> cap = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(cap.capture());
        assertEquals(E2eAdminUserSeeder.E2E_ADMIN_ID, cap.getValue().getId());
    }
}
