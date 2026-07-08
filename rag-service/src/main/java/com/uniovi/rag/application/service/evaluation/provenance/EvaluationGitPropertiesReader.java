package com.uniovi.rag.application.service.evaluation.provenance;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/** Reads optional {@code git.properties} from the classpath (e.g. git-commit-id-plugin). */
final class EvaluationGitPropertiesReader {

    private static final String[] LOCATIONS = {"git.properties", "META-INF/git.properties"};

    private EvaluationGitPropertiesReader() {}

    static Optional<String> readProperty(String key) {
        for (String location : LOCATIONS) {
            Optional<String> value = readFromClasspath(location, key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> readFromClasspath(String location, String key) {
        try (InputStream in =
                EvaluationGitPropertiesReader.class.getClassLoader().getResourceAsStream(location)) {
            if (in == null) {
                return Optional.empty();
            }
            Properties props = new Properties();
            props.load(in);
            String value = props.getProperty(key);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(value.trim());
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
