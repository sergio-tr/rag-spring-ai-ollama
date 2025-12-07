package com.uniovi.rag.services.evaluation;

import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

/**
 * Simple implementation of MultipartFile for converting File to MultipartFile.
 * This is used when loading documents directly without HTTP requests.
 */
public class SimpleMultipartFile implements MultipartFile {
    
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;
    
    public SimpleMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.name = name != null ? name : "file";
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.content = content != null ? content : new byte[0];
    }
    
    @Override
    @NonNull
    public String getName() {
        return name;
    }
    
    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }
    
    @Override
    public String getContentType() {
        return contentType;
    }
    
    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }
    
    @Override
    public long getSize() {
        return content.length;
    }
    
    @Override
    @NonNull
    public byte[] getBytes() throws IOException {
        return content;
    }
    
    @Override
    @NonNull
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(content);
    }
    
    @Override
    public void transferTo(@NonNull File dest) throws IOException, IllegalStateException {
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(content);
        }
    }
}

