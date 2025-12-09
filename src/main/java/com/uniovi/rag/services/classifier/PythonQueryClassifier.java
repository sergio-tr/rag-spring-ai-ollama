package com.uniovi.rag.services.classifier;

import java.io.BufferedReader;
import java.io.InputStreamReader;


public class PythonQueryClassifier implements QueryClassifier {

    private static final String PYTHON_EXECUTABLE = "C:\\Users\\eii\\Desktop\\SergioLLMS\\Python\\rag\\env\\Scripts\\activate.bat";
    private static final String SCRIPT_PATH = "C:\\Users\\eii\\Desktop\\SergioLLMS\\Python\\classifier\\classify_question.py";


    @Override
    public String classifyWithText(String query) {
        return classifyWithPython(query);
    }

    @Override
    public QueryType classify(String query) {
        String result = classifyWithPython(query);

        QueryType queryType;
        try {
            queryType = QueryType.valueOf(result);
        } catch (IllegalArgumentException e) {
            queryType = null;
        }

        log().info("[CLASSIFIER] Query type: " + queryType);

        return queryType;
    }

    public String classifyWithPython(String question) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/c",
                    "call %s && python %s %s".formatted(PYTHON_EXECUTABLE, SCRIPT_PATH, question)
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

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
                throw new RuntimeException("Classifier response error");
            }

        } catch (Exception e) {
            throw new RuntimeException("Error while executing Python script", e);
        }
    }
}
