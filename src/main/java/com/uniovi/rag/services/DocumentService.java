package com.uniovi.rag.services;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentService {

    private final PgVectorStore vectorStore;

    public DocumentService(PgVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void processAndStoreDocument(MultipartFile file) {
        String content = extractContent(file);
        Document document = new Document(content);
        vectorStore.add(List.of(document));
    }

    private String extractContent(MultipartFile file) {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();

        try {
            if (contentType.equals("application/pdf")) {
                return extractFromPdf(file);
            } else if (contentType.equals("text/plain")) {
                return extractFromTxt(file);
            } else if (contentType.contains("spreadsheet") || fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                return extractFromExcel(file);
            } else if ("text/csv".equals(contentType) || "application/octet-stream".equals(contentType) && fileName.endsWith(".csv")) {
                return extractFromCsv(file);
            }
            throw new IllegalArgumentException("Tipo de archivo no soportado");
        } catch (Exception e) {
            throw new RuntimeException("Error procesando el archivo", e);
        }
    }

    private String extractFromPdf(MultipartFile file) throws Exception {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractFromTxt(MultipartFile file) throws Exception {
        return getString(file);
    }

    private String extractFromExcel(MultipartFile file) throws Exception {
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

    private String extractFromCsv(MultipartFile file) throws Exception {
        return getString(file);
    }

    private String getString(MultipartFile file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    public void add(List<Document> documents) {
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
    }
}