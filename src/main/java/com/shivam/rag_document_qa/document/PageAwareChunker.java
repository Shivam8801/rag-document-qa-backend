package com.shivam.rag_document_qa.document;

import com.shivam.rag_document_qa.config.RagProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PageAwareChunker {

    private final RagProperties properties;

    public PageAwareChunker(RagProperties properties) {
        this.properties = properties;
    }

    public List<DocumentChunk> chunkText(List<String> pageTexts) {
        int size = properties.getChunkSize();
        int overlap = properties.getChunkOverlap();
        if (overlap < 0 || overlap >= size) {
            throw new IllegalStateException("RAG chunk overlap must be smaller than chunk size.");
        }
        List<DocumentChunk> chunks = new ArrayList<>();

        // Chunk each page independently so a chunk never combines text from different pages
        for (int pageIndex = 0; pageIndex < pageTexts.size(); pageIndex++) {
            String text = normalize(pageTexts.get(pageIndex));
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + size, text.length());
                if (end < text.length()) {
                    int boundary = text.lastIndexOf(' ', end);
                    if (boundary > start) {
                        end = boundary;
                    }
                }
                String value = text.substring(start, end).trim();
                if (!value.isEmpty()) {
                    chunks.add(new DocumentChunk(pageIndex + 1, chunks.size(), value));
                }
                if (end >= text.length()) {
                    break;
                }
                int nextStart = Math.max(start + 1, end - overlap);
                int priorWordBoundary = text.lastIndexOf(' ', nextStart);
                if (priorWordBoundary >= start) {
                    nextStart = priorWordBoundary + 1;
                } else if (nextStart < end) {
                    nextStart = end;
                }
                while (nextStart < text.length() && Character.isWhitespace(text.charAt(nextStart))) {
                    nextStart++;
                }
                start = nextStart;
            }
        }
        return List.copyOf(chunks);
    }

    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }
}
