package com.uniovi.rag.configuration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DevProfileJwtTtlPropertiesTest {

    @Test
    void applicationDevProperties_setsExtendedAccessTtl() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application-dev.properties")) {
            assertThat(in).isNotNull();
            props.load(in);
        }
        assertThat(props.getProperty("rag.jwt.access-ttl-seconds")).contains("14400");
    }

    @Test
    void applicationDockerProperties_setsExtendedAccessTtl() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application-docker.properties")) {
            assertThat(in).isNotNull();
            props.load(in);
        }
        assertThat(props.getProperty("rag.jwt.access-ttl-seconds")).contains("14400");
    }
}
