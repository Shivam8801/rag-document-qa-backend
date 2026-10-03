package com.shivam.rag_document_qa.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AskRequest(
        @NotBlank @Size(max = 4000) String question,
        UUID conversationId,
        UUID documentId,
        @Size(max = 50) List<UUID> documentIds,
        @Min(1) @Max(100) Integer topK,
        @DecimalMin("0.0") @DecimalMax("1.0") Double similarityThreshold) {
}
