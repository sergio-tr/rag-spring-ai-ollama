package com.uniovi.rag.infrastructure.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionAdminSeederTest {

    @Test
    void run_whenAdminsExist_doesNothing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PasswordEncoder enc = mock(PasswordEncoder.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(2);

        ProductionAdminSeeder seeder =
                new ProductionAdminSeeder(jdbc, enc, "admin@example.com", "pw", "Admin");
        seeder.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbc, never()).update(anyString(), any(), any(), any(), any());
    }

    @Test
    void run_whenNoAdminsAndMissingCreds_throws() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PasswordEncoder enc = mock(PasswordEncoder.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

        ProductionAdminSeeder seeder = new ProductionAdminSeeder(jdbc, enc, "", "", "Admin");
        assertThatThrownBy(() -> seeder.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production requires an initial admin user");
    }

    @Test
    void run_whenNoAdminsAndCredsProvided_insertsAdmin() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PasswordEncoder enc = mock(PasswordEncoder.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        when(enc.encode(anyString())).thenReturn("hash");

        ProductionAdminSeeder seeder =
                new ProductionAdminSeeder(jdbc, enc, "Admin@Example.com", "pw", "Admin");
        seeder.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbc).update(anyString(), any(), any(), any(), any());
    }
}

