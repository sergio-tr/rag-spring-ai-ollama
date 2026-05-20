package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.infrastructure.web.SimpleMultipartFile;
import com.uniovi.rag.application.service.knowledge.document.DocumentService;
import com.uniovi.rag.application.service.runtime.execution.QueryExecutionService;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public abstract class AbstractMinuteEvaluationService extends AbstractEvaluationService {

    protected AbstractMinuteEvaluationService(
        RagFeatureConfiguration featureConfig,
        RagImplementationProperties implementationProperties,
        ChatClient chatClient,
        DocumentService documentService,
        QueryExecutionService queryService,
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
            // Best-effort batch load: continue with remaining PDFs; failure is recorded above.
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

    /**
     * Ingests a PDF via {@link DocumentService} (same path as classpath loading; no HTTP ingest loopback).
     */
    protected void sendFileToEndpoint(File file) throws IOException {
        MultipartFile multipartFile = fileToMultipartFile(file);
        documentService.processDocument(multipartFile);
        log().info("File sent to document service: {}", file.getName());
    }
}
