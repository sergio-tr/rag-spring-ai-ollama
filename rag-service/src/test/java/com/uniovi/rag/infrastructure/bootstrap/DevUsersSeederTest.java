package com.uniovi.rag.infrastructure.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevUsersSeederTest {

    @Test
    void run_whenBothUsersExist_updatesOnly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PasswordEncoder enc = mock(PasswordEncoder.class);
        when(enc.encode(anyString())).thenReturn("hash");
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        DevUsersSeeder seeder =
                new DevUsersSeeder(
                        jdbc,
                        enc,
                        "Admin@Dev.Local",
                        "pw1",
                        "A",
                        "User@Dev.Local",
                        "pw2",
                        "U");
        seeder.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).update(sql.capture(), any(), any(), any(), any(), any(), any());
        List<String> sqls = sql.getAllValues();
        // Both calls should be UPDATE statements.
        org.junit.jupiter.api.Assertions.assertTrue(sqls.stream().allMatch(s -> s.contains("UPDATE users SET")));
    }

    @Test
    void run_whenNoUsersExist_insertsBoth() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PasswordEncoder enc = mock(PasswordEncoder.class);
        when(enc.encode(anyString())).thenReturn("hash");
        // First call (admin update) returns 0, second (user update) returns 0.
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        DevUsersSeeder seeder =
                new DevUsersSeeder(
                        jdbc,
                        enc,
                        "admin@dev.local",
                        "pw1",
                        "A",
                        "user@dev.local",
                        "pw2",
                        "U");
        seeder.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbc, times(2)).update(anyString(), any(), any(), any(), any(), any(), any());

        ArgumentCaptor<String> insertSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).update(insertSql.capture(), any(), any(), any(), any(), any());
        org.junit.jupiter.api.Assertions.assertTrue(
                insertSql.getAllValues().stream().allMatch(s -> s.contains("INSERT INTO users")));
    }
}

