package com.shivam.rag_document_qa.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Validated
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    @Min(100)
    private int chunkSize;
    @Min(0)
    private int chunkOverlap;
    @Min(1)
    private int defaultTopK;
    @Min(1)
    @Max(100)
    private int maxTopK;
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double similarityThreshold;
    @Min(0)
    private int maxHistoryMessages;
    @Min(1)
    private long maxUploadBytes;
    @Min(1)
    private int maxUploadsPerRequest;

    @AssertTrue(message = "chunk overlap must be smaller than chunk size")
    public boolean isChunkingConfigurationValid() {
        return chunkOverlap < chunkSize;
    }

    @AssertTrue(message = "default topK must not exceed max topK")
    public boolean isTopKConfigurationValid() {
        return defaultTopK <= maxTopK;
    }

}
