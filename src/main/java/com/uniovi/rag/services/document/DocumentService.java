package com.uniovi.rag.services.document;

import com.uniovi.rag.model.Loggable;
import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService extends Loggable {

    void processDocument(MultipartFile file);

    void add(List<Document> documents);
}
