package com.uniovi.rag.service.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractDocumentService implements DocumentService {

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
            throw new IllegalStateException("Failed to clear database", e);
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
    
    @Override
    public int deleteDocumentByDocumentId(String documentId) {
        if (documentId == null || documentId.trim().isEmpty()) {
            log().warn("Attempted to delete document with null or empty document_id");
            return 0;
        }
        
        try {
            // Delete all chunks with this document_id from vector_store
            int deletedChunks = jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'document_id' = ?",
                documentId
            );
            
            // Also try to delete by id field in metadata (fallback for older documents)
            if (deletedChunks == 0) {
                deletedChunks = jdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata->>'id' = ?",
                    documentId
                );
            }
            
            // Delete from documents table if document_name matches
            int deletedDocs = jdbcTemplate.update(
                "DELETE FROM documents WHERE document_name = ? OR (metadata->>'document_id')::text = ?",
                documentId, documentId
            );
            
            log().info("Deleted {} chunks and {} document entries (document_id length: {})",
                      deletedChunks, deletedDocs, documentId.length());
            
            return deletedChunks;
        } catch (Exception e) {
            log().error("Error deleting document by document_id (id length: {})", documentId.length(), e);
            throw new IllegalStateException("Failed to delete document", e);
        }
    }

    @Override
    public boolean hasDocumentWithId(String documentId) {
        if (documentId == null || documentId.trim().isEmpty()) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'document_id' = ?",
                Integer.class,
                documentId
            );
            if (count != null && count > 0) {
                return true;
            }
            count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'id' = ?",
                Integer.class,
                documentId
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log().warn("Error checking document_id existence (id length: {})", documentId.length(), e);
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
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Error processing the file", e);
        } catch (Exception e) {
            throw new IllegalStateException("Error processing the file", e);
        }


        throw new IllegalArgumentException("Not supported file type");
    }

    protected String extractFromPdf(MultipartFile file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File is null");
        }
        
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(document);
            
            // Validate that the text is not empty
            if (rawText == null || rawText.trim().isEmpty()) {
                log().error("PDF extraction returned empty text (filename length: {})", filename != null ? filename.length() : 0);
                throw new IllegalArgumentException("The PDF does not contain extractable text. It may be protected or an image.");
            }
            
            log().info("PDF extracted {} characters (filename length: {})", rawText.length(), filename.length());
            
            // Normalize the extracted text to improve subsequent extraction
            String normalized = normalizeExtractedText(rawText);
            
            log().info("After normalization: {} characters (filename length: {})", normalized.length(), filename != null ? filename.length() : 0);
            
            // Validate minimum length
            if (normalized.length() < 20) {
                log().warn("Normalized text is very short ({} chars; filename length: {})", normalized.length(), filename != null ? filename.length() : 0);
            }
            
            return normalized;
        } catch (IllegalArgumentException e) {
            // Re-lanzar IllegalArgumentException tal cual
            throw e;
        } catch (Exception e) {
            log().error("Error processing PDF file (filename length: {})", filename != null ? filename.length() : 0, e);
            throw new IllegalStateException("Error processing the PDF " + filename + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Normalize the extracted text from PDFs to improve subsequent extraction.
     * Clean multiple spaces, normalize line breaks and special characters.
     */
    protected String normalizeExtractedText(String text) {
        if (text == null) return "";
        
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace('\u00A0', ' ')  // NBSP (Non-breaking space)
            .replace('\u2007', ' ')  // Figure space
            .replace('\u202F', ' ')  // Narrow NBSP
            .replace('\u2009', ' ')  // Thin space
            // Collapse horizontal whitespace only
            .replaceAll("[ \t]+", " ")
            // Normalize multiple line breaks to one
            .replaceAll("\\n\\s*\\n+", "\n")
            // Normalize spaces around colons
            .replaceAll("\\s*:\\s*", ": ")
            // Normalize spaces around parentheses
            .replaceAll("\\s*\\(\\s*", " (")
            .replaceAll("\\s*\\)", ")")
            // Normalize bullets (convert different bullet types to •)
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

    protected String extractFromCsv(MultipartFile file) throws IOException {
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

    /**
     * Splits content into chunks that fit within embedding model context limits.
     * Breaks at word boundaries to avoid splitting words.
     *
     * @param content The full content to split
     * @param maxCharsPerChunk Maximum characters per chunk
     * @return List of content chunks
     */
    protected List<String> splitContentIntoChunks(String content, int maxCharsPerChunk) {
        if (content == null || content.trim().isEmpty()) {
            return List.of("");
        }
        String trimmed = content.trim();
        if (trimmed.length() <= maxCharsPerChunk) {
            return List.of(trimmed);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < trimmed.length()) {
            int end = computeChunkEndIndex(trimmed, start, maxCharsPerChunk);
            String chunk = trimmed.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start = end;
        }
        log().info("Split content into {} chunks (max {} chars per chunk, total {} chars)",
                chunks.size(), maxCharsPerChunk, trimmed.length());
        return chunks;
    }

    private static int computeChunkEndIndex(String trimmed, int start, int maxCharsPerChunk) {
        int end = Math.min(start + maxCharsPerChunk, trimmed.length());
        if (end >= trimmed.length()) {
            return end;
        }
        int lastBreak = end;
        for (int i = end - 1; i > start + (maxCharsPerChunk * 2 / 3); i--) {
            char c = trimmed.charAt(i);
            if (c == '\n' || c == '.' || c == '!' || c == '?' || c == ' ') {
                lastBreak = i + 1;
                break;
            }
        }
        return lastBreak;
    }
}
