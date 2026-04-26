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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class E2eAdminUserSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private E2eAdminUserSeeder seeder;

    @Test
    void run_whenAdminExists_doesNothing() {
        when(transactionTemplate.execute(any()))
                .thenAnswer(
                        inv -> {
                            // execute callback in-place (mocked tx); callback will call findByEmailIgnoreCase.
                            var cb = inv.getArgument(0);
                            return ((org.springframework.transaction.support.TransactionCallback<?>) cb).doInTransaction(null);
                        });
        when(userRepository.findByEmailIgnoreCase(E2eAdminUserSeeder.E2E_ADMIN_EMAIL))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(UserEntity.class)));

        seeder.run(new DefaultApplicationArguments());

        verify(transactionTemplate).execute(any());
        verify(userRepository).findByEmailIgnoreCase(E2eAdminUserSeeder.E2E_ADMIN_EMAIL);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void run_seedsAdminWhenMissing() {
        when(transactionTemplate.execute(any()))
                .thenAnswer(
                        inv -> {
                            var cb = inv.getArgument(0);
                            return ((org.springframework.transaction.support.TransactionCallback<?>) cb).doInTransaction(null);
                        });
        when(userRepository.findByEmailIgnoreCase(E2eAdminUserSeeder.E2E_ADMIN_EMAIL))
                .thenReturn(Optional.empty());

        seeder.run(new DefaultApplicationArguments());

        ArgumentCaptor<UserEntity> cap = ArgumentCaptor.forClass(UserEntity.class);
        verify(transactionTemplate).execute(any());
        verify(userRepository).saveAndFlush(cap.capture());
        assertEquals(E2eAdminUserSeeder.E2E_ADMIN_ID, cap.getValue().getId());
    }
}
