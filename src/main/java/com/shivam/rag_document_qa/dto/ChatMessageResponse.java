package com.shivam.rag_document_qa.dto;

import com.shivam.rag_document_qa.entity.ChatMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(UUID id, ChatMessage.Role role, String content, Instant createdAt,
                                  List<CitationResponse> citations) {
}
