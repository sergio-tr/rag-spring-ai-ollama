package com.uniovi.rag.services.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public abstract class AbstractDocumentService<T> implements DocumentService {

    protected final PgVectorStore vectorStore;
    protected final ChatClient chatClient;
    protected final JdbcTemplate jdbcTemplate;

    public AbstractDocumentService(PgVectorStore vectorStore, ChatClient chatClient, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void add(List<Document> documents) {
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
    }
    
    @Override
    public void clearDatabase() {
        try {
            log().info("Clearing vector_store and documents tables");
            jdbcTemplate.update("DELETE FROM vector_store");
            jdbcTemplate.update("DELETE FROM documents");
            log().info("Database cleared successfully");
        } catch (Exception e) {
            log().error("Error clearing database", e);
            throw new RuntimeException("Failed to clear database", e);
        }
    }
    
    @Override
    public boolean hasDocuments() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            log().warn("Error checking if database has documents", e);
            return false;
        }
    }

    protected String extractContent(MultipartFile file) {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();

        if (contentType == null) {
            throw new IllegalArgumentException("File type not specified");
        }

        if (fileName == null) {
            throw new IllegalArgumentException("File name not specified");
        }

        try {
            if (contentType.contentEquals("application/pdf")) {
                return extractFromPdf(file);
            } else if (contentType.contentEquals("text/plain")) {
                return extractFromTxt(file);
            } else if (contentType.contains("spreadsheet") || fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                return extractFromExcel(file);
            } else if ("text/csv".contentEquals(contentType) || "application/octet-stream".contentEquals(contentType) && fileName.endsWith(".csv")) {
                return extractFromCsv(file);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error processing the file", e);
        }


        throw new IllegalArgumentException("Not supported file type");
    }

    protected String extractFromPdf(MultipartFile file) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("File is null");
        }
        
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(document);
            
            // Validate that the text is not empty
            if (rawText == null || rawText.trim().isEmpty()) {
                log().error("PDF extraction returned empty text for file: " + filename);
                throw new IllegalArgumentException("The PDF does not contain extractable text. It may be protected or an image.");
            }
            
            log().debug("PDF extracted " + rawText.length() + " characters from file: " + filename);
            
            // Normalize the extracted text to improve subsequent extraction
            String normalized = normalizeExtractedText(rawText);
            
            log().debug("After normalization: " + normalized.length() + " characters for file: " + filename);
            
            // Validate minimum length
            if (normalized.length() < 20) {
                log().warn("Normalized text is very short (" + normalized.length() + " chars) for file: " + filename);
            }
            
            return normalized;
        } catch (IllegalArgumentException e) {
            // Re-lanzar IllegalArgumentException tal cual
            throw e;
        } catch (Exception e) {
            log().error("Error processing PDF file: " + filename, e);
            e.printStackTrace();
            throw new RuntimeException("Error processing the PDF " + filename + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Normalize the extracted text from PDFs to improve subsequent extraction.
     * Clean multiple spaces, normalize line breaks and special characters.
     */
    protected String normalizeExtractedText(String text) {
        if (text == null) return "";
        
        return text
            .replaceAll("\\r\\n", "\n")
            .replaceAll("\\r", "\n")
            .replaceAll("\\u00A0", " ")  // NBSP (Non-breaking space)
            .replaceAll("\\u2007", " ")  // Figure space
            .replaceAll("\\u202F", " ")  // Narrow NBSP
            .replaceAll("\\u2009", " ")  // Thin space
            // Normalize multiple spaces to one space
            .replaceAll("\\s+", " ")
            // Normalize multiple line breaks to one
            .replaceAll("\\n\\s*\\n+", "\n")
            // Normalize spaces around colons
            .replaceAll("\\s*:\\s*", ": ")
            // Normalize spaces around parentheses
            .replaceAll("\\s*\\(\\s*", " (")
            .replaceAll("\\s*\\)", ")")
            // Normalizar viñetas (diferentes tipos a •)
            .replaceAll("[•·▪▫◦‣⁃]", "•")
            // Clean control characters except line breaks
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
            .replaceAll("\\uFFFD", "")  // Replacement character
            .trim();
    }

    protected String extractFromTxt(MultipartFile file) throws Exception {
        return getString(file);
    }

    protected String extractFromExcel(MultipartFile file) throws Exception {
        StringBuilder content = new StringBuilder();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        content.append(cell.toString()).append(" ");
                    }
                    content.append("\n");
                }
            }
        }
        return content.toString();
    }

    protected String extractFromCsv(MultipartFile file) throws Exception {
        return getString(file);
    }

    protected String getString(MultipartFile file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    protected Document createDocumentFromFile(MultipartFile file) {
        String content = extractContent(file);
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("Document content does not exist");
        }
        return new Document(content);
    }
}
