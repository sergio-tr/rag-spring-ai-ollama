package com.uniovi.rag.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

class InlineFullyQualifiedJavaReferencePolicyTest {

    private static final Pattern INLINE_FQCN_PATTERN =
            Pattern.compile("\\b((?:java|javax|jakarta|org|com)\\.[A-Za-z_][\\w$]*(?:\\.[A-Za-z_][\\w$]*)+)");
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");

    private static final Map<String, Set<String>> ACCEPTED_EXCEPTIONS = acceptedExceptions();

    @Test
    void javaExecutableCodeMustNotContainInlineFullyQualifiedReferences() throws IOException {
        List<String> violations = new ArrayList<>();
        scanTree(Path.of("src/main/java"), violations);
        scanTree(Path.of("src/test/java"), violations);

        if (!violations.isEmpty()) {
            fail("Inline fully qualified Java references found (non-exempt):\n" + String.join("\n", violations));
        }
    }

    private static void scanTree(Path root, List<String> violations) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> scanFile(path, violations));
        }
    }

    private static void scanFile(Path file, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            boolean inBlockComment = false;
            for (int i = 0; i < lines.size(); i++) {
                String rawLine = lines.get(i);
                String trimmed = rawLine.stripLeading();

                if (inBlockComment) {
                    if (rawLine.contains("*/")) {
                        inBlockComment = false;
                    }
                    continue;
                }
                if (trimmed.startsWith("/*")) {
                    if (!rawLine.contains("*/")) {
                        inBlockComment = true;
                    }
                    continue;
                }
                if (trimmed.startsWith("//")
                        || trimmed.startsWith("*")
                        || trimmed.startsWith("import ")
                        || trimmed.startsWith("package ")) {
                    continue;
                }

                String withoutStrings = STRING_LITERAL_PATTERN.matcher(rawLine).replaceAll("\"\"");
                Matcher matcher = INLINE_FQCN_PATTERN.matcher(withoutStrings);
                while (matcher.find()) {
                    String symbol = matcher.group(1);
                    String relPath = file.toString().replace('\\', '/');
                    if (!isAcceptedException(relPath, symbol)) {
                        violations.add(relPath + ":" + (i + 1) + " -> " + symbol);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file " + file, e);
        }
    }

    private static boolean isAcceptedException(String relPath, String symbol) {
        Set<String> allowed = ACCEPTED_EXCEPTIONS.get(relPath);
        return allowed != null && allowed.contains(symbol);
    }

    private static Map<String, Set<String>> acceptedExceptions() {
        Map<String, Set<String>> map = new HashMap<>();
        map.put(
                "src/main/java/com/uniovi/rag/tool/MeetingMinutesToolsAdapter.java",
                new HashSet<>(Set.of("com.uniovi.rag.tool.Tool")));
        return map;
    }
}
