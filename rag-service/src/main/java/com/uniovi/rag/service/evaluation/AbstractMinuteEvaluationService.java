package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.model.SimpleMultipartFile;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.query.QueryService;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
public abstract class AbstractMinuteEvaluationService extends AbstractEvaluationService {

    protected AbstractMinuteEvaluationService(
        RagFeatureConfiguration featureConfig,
        RagImplementationProperties implementationProperties,
        ChatClient chatClient,
        DocumentService documentService,
        QueryService queryService,
        boolean cleanBeforeLoad
    ) {
        super(featureConfig, implementationProperties, chatClient, documentService, queryService, cleanBeforeLoad);
    }

    @Override
    protected void loadSpecificData() {
        // Default implementation: use HTTP endpoint (for default configuration)
        loadSpecificDataWithService(documentService);
    }
    
    @Override
    protected void loadSpecificDataWithService(DocumentService docService) {
        try {
            ClassPathResource resource = new ClassPathResource("docs");
            File directory = resource.getFile();

            if (directory.exists() && directory.isDirectory()) {
                File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));

                if (files != null) {
                    log().info("Loading {} PDF files from docs", files.length);
                    for (File file : files) {
                        loadSinglePdfFromDocs(file, docService);
                    }
                    log().info("Finished loading documents");
                }
            } else {
                log().warn("Directory docs does not exist or is not a directory");
            }
        } catch (Exception e) {
            log().error("Error loading files from docs directory", e);
            throw new IllegalStateException("Failed to load files from docs directory", e);
        }
    }
    
    private void loadSinglePdfFromDocs(File file, DocumentService docService) {
        try {
            MultipartFile multipartFile = fileToMultipartFile(file);
            docService.processDocument(multipartFile);
            log().info("Successfully loaded file: {}", file.getName());
        } catch (Exception e) {
            log().error("Error loading file: {}", file.getName(), e);
        }
    }

    /**
     * Converts a File to a MultipartFile for processing.
     */
    private MultipartFile fileToMultipartFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] content = input.readAllBytes();
            return new SimpleMultipartFile(
                    "file",
                    file.getName(),
                    "application/pdf",
                    content
            );
        }
    }

    protected void sendFileToEndpoint(File file) throws IOException {
        String endpoint = "http://localhost:9000/api/v4/documents";
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
            log().info("Archivo enviado correctamente: " + file.getName());
        }
    }

}

