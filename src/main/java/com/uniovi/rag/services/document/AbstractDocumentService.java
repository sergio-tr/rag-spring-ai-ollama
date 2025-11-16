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
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public abstract class AbstractDocumentService<T> implements DocumentService {

    protected final PgVectorStore vectorStore;
    protected final ChatClient chatClient;

    public AbstractDocumentService(PgVectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    @Override
    public void add(List<Document> documents) {
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
    }

    protected String extractContent(MultipartFile file) {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();

        if (contentType == null) {
            throw new IllegalArgumentException("Tipo de archivo no especificado");
        }

        if (fileName == null) {
            throw new IllegalArgumentException("Nombre de archivo no especificado");
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
            throw new RuntimeException("Error procesando el archivo", e);
        }


        throw new IllegalArgumentException("Tipo de archivo no soportado");
    }

    protected String extractFromPdf(MultipartFile file) throws Exception {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(document);
            
            // Validar que el texto no esté vacío
            if (rawText == null || rawText.trim().isEmpty()) {
                throw new IllegalArgumentException("El PDF no contiene texto extraíble. Puede estar protegido o ser una imagen.");
            }
            
            // Normalizar el texto extraído para mejorar la extracción posterior
            String normalized = normalizeExtractedText(rawText);
            
            // Validar longitud mínima
            if (normalized.length() < 100) {
                // Log warning pero no fallar - algunos documentos pueden ser muy cortos
            }
            
            return normalized;
        } catch (Exception e) {
            throw new RuntimeException("Error procesando el PDF: " + e.getMessage(), e);
        }
    }
    
    /**
     * Normaliza el texto extraído de PDFs para mejorar la extracción posterior.
     * Limpia espacios múltiples, normaliza saltos de línea y caracteres especiales.
     */
    protected String normalizeExtractedText(String text) {
        if (text == null) return "";
        
        return text
            // Normalizar espacios múltiples a un solo espacio
            .replaceAll("\\s+", " ")
            // Normalizar saltos de línea múltiples a uno solo
            .replaceAll("\\n\\s*\\n+", "\n")
            // Normalizar espacios alrededor de dos puntos
            .replaceAll("\\s*:\\s*", ": ")
            // Normalizar espacios alrededor de paréntesis
            .replaceAll("\\s*\\(\\s*", " (")
            .replaceAll("\\s*\\)", ")")
            // Normalizar viñetas (diferentes tipos a •)
            .replaceAll("[•·▪▫◦‣⁃]", "•")
            // Limpiar caracteres de control excepto saltos de línea
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
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
