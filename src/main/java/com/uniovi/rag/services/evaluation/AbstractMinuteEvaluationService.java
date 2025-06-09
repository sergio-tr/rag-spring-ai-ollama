package com.uniovi.rag.services.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.services.document.DocumentService;
import com.uniovi.rag.services.query.QueryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
public abstract class AbstractMinuteEvaluationService extends AbstractEvaluationService {

    public AbstractMinuteEvaluationService(
            RagFeatureConfiguration featureConfig,
            ChatClient chatClient,
            DocumentService documentService,
            QueryService queryService) {
        super(featureConfig, chatClient, documentService, queryService);
    }

    @Override
    protected void loadSpecificData() {
        try {
            ClassPathResource resource = new ClassPathResource("docs/actas");
            File directory = resource.getFile();

            if (directory.exists() && directory.isDirectory()) {
                File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));

                if (files != null) {
                    for (File file : files) {
                        try {
                            sendFileToEndpoint(file);
                        } catch (Exception e) {
                            System.err.println("Error enviando el archivo: " + file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al cargar archivos desde la carpeta actas", e);
        }
    }

    private void sendFileToEndpoint(File file) throws IOException {
        String endpoint = "http://localhost:9000/api/v3/documents";
        String boundary = "===" + System.currentTimeMillis() + "===";

        HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (
                OutputStream output = connection.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
                FileInputStream inputStream = new FileInputStream(file)
        ) {
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"\r\n");
            writer.append("Content-Type: application/pdf\r\n");
            writer.append("\r\n").flush();

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.flush();
            writer.append("\r\n").flush();

            writer.append("--").append(boundary).append("--").append("\r\n").flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Error al enviar archivo: " + file.getName() + ". Código: " + responseCode);
        } else {
            System.out.println("Archivo enviado correctamente: " + file.getName());
        }
    }

}

