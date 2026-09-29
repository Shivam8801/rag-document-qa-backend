package com.shivam.rag_document_qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDocumentRequest(@NotBlank @Size(max = 512) String name) {
}
