package com.shivam.rag_document_qa.dto;

import java.util.UUID;

public record CitationResponse(UUID documentId, String documentName, String chunkId, int pageNumber,
                               int chunkIndex, String excerpt, double similarity) {
}
