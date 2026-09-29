package com.shivam.rag_document_qa.dto;

import com.shivam.rag_document_qa.entity.Document;
import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(UUID documentId, String fileName, String originalFileName,
                               String contentType, long fileSize, int pageCount, int chunkCount,
                               String status, Instant createdAt, Instant updatedAt) {
    public static DocumentResponse fromDocument(Document document) {
        // status field is added for dto as metadata
        return new DocumentResponse(document.getId(), document.getFileName(), document.getOriginalFileName(),
                document.getContentType(), document.getFileSize(), document.getPageCount(),
                document.getChunkCount(), "PROCESSED", document.getCreatedAt(), document.getUpdatedAt());
    }
}
