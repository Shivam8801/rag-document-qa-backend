package com.shivam.rag_document_qa.document;

public record DocumentChunk(int pageNumber, int chunkIndex, String text) {
}
