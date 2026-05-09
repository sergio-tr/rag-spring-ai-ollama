package com.uniovi.rag.application.service.evaluation.lab;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Verifies that classpath corpus fixtures under {@code src/test/resources/docs} resolve the same way as production
 * {@code src/main/resources/docs} after Maven packaging (both trees land under {@code docs/} on the classpath).
 */
class ClasspathDocsResourcePackagingTest {

    @Test
    void testClasspath_fixture_pdf_is_readable() throws IOException {
        var url = Thread.currentThread().getContextClassLoader().getResource("docs/bootstrap-acta.pdf");
        assertThat(url).isNotNull();
        try (InputStream in = url.openStream()) {
            byte[] head = in.readNBytes(8);
            assertThat(new String(head, StandardCharsets.US_ASCII)).startsWith("%PDF");
        }
    }

    @Test
    void classpathPattern_matches_pdf_under_docs_tree() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        var resources = resolver.getResources("classpath*:docs/**/*.pdf");
        boolean found = false;
        for (var r : resources) {
            if (r.exists()
                    && r.isReadable()
                    && "bootstrap-acta.pdf".equals(r.getFilename())) {
                found = true;
                break;
            }
        }
        assertThat(found).as("Expected bootstrap-acta.pdf from test/resources/docs").isTrue();
    }
}
