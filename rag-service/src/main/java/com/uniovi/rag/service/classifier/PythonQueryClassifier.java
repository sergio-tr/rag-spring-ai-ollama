package com.uniovi.rag.service.classifier;

import com.uniovi.rag.model.QueryType;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Legacy classifier implementation that runs a Python script via {@link ProcessBuilder}.
 * <p>
 * The current backend configuration ({@link com.uniovi.rag.configuration.RagQueryConfiguration})
 * uses {@code ClassifierServiceClient} talking over HTTP to the running {@code classifier-service}.
 * This class is kept only as a legacy fallback / reference implementation.
 */
@Deprecated
public class PythonQueryClassifier implements QueryClassifier {

    private final String pythonExecutable;
    private final String scriptPath;

    /**
     * No-arg constructor for use when configuration is not available (e.g. factory).
     * Behaves as "script not configured": classify will return null so LLM fallback is used.
     */
    public PythonQueryClassifier() {
        this("", "");
    }

    /**
     * @param pythonExecutable optional path to Python executable or activate script (e.g. venv)
     * @param scriptPath       path to classify_question.py; if empty or file does not exist, returns null without throwing
     */
    public PythonQueryClassifier(String pythonExecutable, String scriptPath) {
        this.pythonExecutable = pythonExecutable != null ? pythonExecutable.trim() : "";
        this.scriptPath = scriptPath != null ? scriptPath.trim() : "";
    }

    @Override
    public String classifyWithText(String query) {
        return classifyWithPython(query);
    }

    @Override
    public QueryType classify(String query) {
        String result = classifyWithPython(query);

        QueryType queryType;
        try {
            queryType = result != null ? QueryType.valueOf(result) : null;
        } catch (IllegalArgumentException e) {
            queryType = null;
        }

        log().info("[CLASSIFIER] Query type: " + queryType);

        return queryType;
    }

    /**
     * Returns null if Python is not configured, script file does not exist, or execution fails.
     * Does not throw so that PythonQueryClassifier can use LLM fallback.
     */
    public String classifyWithPython(String question) {
        if (scriptPath.isEmpty()) {
            log().debug("[CLASSIFIER] Python script path not configured, returning null (LLM fallback will be used)");
            return null;
        }
        File scriptFile = new File(scriptPath);
        if (!scriptFile.exists()) {
            log().warn("[CLASSIFIER] Python script file does not exist: {}, returning null (LLM fallback will be used)", scriptPath);
            return null;
        }

        try {
            String command;
            if (pythonExecutable != null && !pythonExecutable.isEmpty()) {
                command = "call " + pythonExecutable + " && python " + scriptPath + " " + escapeArg(question);
            } else {
                command = "python " + scriptPath + " " + escapeArg(question);
            }
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/c",
                    command
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            log().info("[CLASSIFIER] Salida de Python");
            String lastLine = null;
            String line;

            while ((line = reader.readLine()) != null) {
                log().info("[CLASSIFIER] " + line);
                lastLine = line;
            }

            int exitCode = process.waitFor();

            if (exitCode == 0 && lastLine != null) {
                return lastLine.trim();
            } else {
                log().warn("[CLASSIFIER] Python script exited with code {} or empty output, returning null", exitCode);
                return null;
            }

        } catch (Exception e) {
            log().warn("[CLASSIFIER] Error executing Python script (LLM fallback will be used): {}", e.getMessage());
            return null;
        }
    }

    private static String escapeArg(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
