package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountPersistenceAdapterTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserAccountPersistenceAdapter adapter;

    @Test
    void findByEmailIgnoreCase_delegates() {
        UserEntity u = Mockito.mock(UserEntity.class);
        when(userRepository.findByEmailIgnoreCase("A@b.c")).thenReturn(Optional.of(u));
        assertSame(u, adapter.findByEmailIgnoreCase("A@b.c").orElseThrow());
        verify(userRepository).findByEmailIgnoreCase("A@b.c");
    }

    @Test
    void findById_delegates() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        assertEquals(Optional.empty(), adapter.findById(id));
    }

    @Test
    void save_delegates() {
        UserEntity u = Mockito.mock(UserEntity.class);
        when(userRepository.save(u)).thenReturn(u);
        assertSame(u, adapter.save(u));
    }
}
