package com.shivam.rag_document_qa.dto;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(@Size(max = 200) String title) {
}
