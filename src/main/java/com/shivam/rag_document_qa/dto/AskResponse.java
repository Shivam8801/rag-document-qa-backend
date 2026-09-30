package com.shivam.rag_document_qa.dto;

import java.util.List;
import java.util.UUID;

public record AskResponse(UUID conversationId, String answer, List<CitationResponse> sources) {
}
