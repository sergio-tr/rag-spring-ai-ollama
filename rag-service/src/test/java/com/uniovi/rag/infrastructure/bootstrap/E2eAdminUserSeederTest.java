package com.uniovi.rag.infrastructure.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class E2eAdminUserSeederTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private E2eAdminUserSeeder seeder;

    @Test
    void run_whenUpdateTouchesRow_skipsInsert() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        seeder.run(new DefaultApplicationArguments());

        verify(jdbcTemplate).update(contains("UPDATE users SET"), any(), any(), any(), any(), any(), any());
        verify(jdbcTemplate, never()).update(contains("INSERT INTO users"), any(), any(), any(), any(), any());
    }

    @Test
    void run_whenUpdateZero_insertsRow() {
        when(jdbcTemplate.update(contains("UPDATE users SET"), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(jdbcTemplate.update(contains("INSERT INTO users"), any(), any(), any(), any(), any()))
                .thenReturn(1);

        seeder.run(new DefaultApplicationArguments());

        verify(jdbcTemplate).update(contains("UPDATE users SET"), any(), any(), any(), any(), any(), any());
        verify(jdbcTemplate).update(contains("INSERT INTO users"), any(), any(), any(), any(), any());
    }

    @Test
    void run_whenInsertRaces_runsSecondUpdate() {
        when(jdbcTemplate.update(contains("UPDATE users SET"), any(), any(), any(), any(), any(), any()))
                .thenReturn(0)
                .thenReturn(1);
        doThrow(new DuplicateKeyException("parallel insert", null))
                .when(jdbcTemplate)
                .update(contains("INSERT INTO users"), any(), any(), any(), any(), any());

        seeder.run(new DefaultApplicationArguments());

        verify(jdbcTemplate, times(2)).update(contains("UPDATE users SET"), any(), any(), any(), any(), any(), any());
        verify(jdbcTemplate).update(contains("INSERT INTO users"), any(), any(), any(), any(), any());
    }

    @Test
    void run_insertUsesExpectedAdminId() {
        when(jdbcTemplate.update(contains("UPDATE users SET"), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(jdbcTemplate.update(contains("INSERT INTO users"), any(), any(), any(), any(), any()))
                .thenReturn(1);

        seeder.run(new DefaultApplicationArguments());

        verify(jdbcTemplate)
                .update(
                        contains("INSERT INTO users"),
                        eq(E2eAdminUserSeeder.E2E_ADMIN_ID),
                        eq(E2eAdminUserSeeder.E2E_ADMIN_EMAIL),
                        eq("{noop}" + E2eAdminUserSeeder.E2E_ADMIN_PASSWORD),
                        any(),
                        any());
    }
}
