package com.shivam.rag_document_qa.retrieval;

import com.shivam.rag_document_qa.config.RagProperties;
import com.shivam.rag_document_qa.dto.AskRequest;
import com.shivam.rag_document_qa.dto.CitationResponse;
import com.shivam.rag_document_qa.exception.ApiException;
import com.shivam.rag_document_qa.repository.DocumentRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class VectorRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(VectorRetrievalService.class);

    private final VectorStore vectorStore;
    private final RagProperties properties;
    private final DocumentRepository documentRepository;

    public VectorRetrievalService(VectorStore vectorStore, RagProperties properties,
                                  DocumentRepository documentRepository) {
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.documentRepository = documentRepository;
    }

    public List<CitationResponse> search(AskRequest request) {
        int topK = request.topK() == null ? properties.getDefaultTopK() : request.topK();
        if (topK < 1 || topK > properties.getMaxTopK()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOP_K",
                    "topK must be between 1 and " + properties.getMaxTopK() + ".");
        }
        double threshold = request.similarityThreshold() == null
                ? properties.getSimilarityThreshold() : request.similarityThreshold();
        Set<UUID> documentIds = new LinkedHashSet<>();
        if (request.documentId() != null) {
            documentIds.add(request.documentId());
        }
        if (request.documentIds() != null) {
            documentIds.addAll(request.documentIds());
        }
        if (documentIds.size() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_DOCUMENT_FILTERS",
                    "A maximum of 50 document filters is supported.");
        }
        for (UUID documentId : documentIds) {
            if (!documentRepository.existsById(documentId)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "A requested document was not found.");
            }
        }

        Map<String, CitationResponse> unique = new LinkedHashMap<>();
        Map<UUID, Optional<com.shivam.rag_document_qa.entity.Document>> sourceDocuments = new LinkedHashMap<>();
        if (documentIds.isEmpty()) {
            collect(search(request.question(), topK, threshold, null), unique, sourceDocuments);
        } else {
            for (UUID documentId : documentIds) {
                FilterExpressionBuilder filter = new FilterExpressionBuilder();
                collect(search(request.question(), topK, threshold,
                        filter.eq("documentId", documentId.toString()).build()), unique, sourceDocuments);
            }
        }
        List<CitationResponse> results = unique.values().stream()
                .sorted(Comparator.comparingDouble(CitationResponse::similarity).reversed())
                .limit(topK)
                .toList();
        log.info("Retrieved {} relevant chunks for question across {} document filters",
                results.size(), documentIds.size());
        return results;
    }

    private List<Document> search(String question, int topK, double threshold,
                                  org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(threshold);
        if (filterExpression != null) {
            builder.filterExpression(filterExpression);
        }
        try {
            return vectorStore.similaritySearch(builder.build());
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RETRIEVAL_UNAVAILABLE",
                    "Document search is unavailable. Check the vector database and embedding service.");
        }
    }

    private void collect(List<Document> documents, Map<String, CitationResponse> unique,
                         Map<UUID, Optional<com.shivam.rag_document_qa.entity.Document>> sourceDocuments) {
        if (documents == null) {
            return;
        }
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            try {
                UUID documentId = UUID.fromString(String.valueOf(metadata.get("documentId")));
                Optional<com.shivam.rag_document_qa.entity.Document> sourceDocument =
                        sourceDocuments.computeIfAbsent(documentId, documentRepository::findById);
                if (sourceDocument.isEmpty()) {
                    continue;
                }
                int pageNumber = integer(metadata.get("pageNumber"));
                int chunkIndex = integer(metadata.get("chunkIndex"));
                double score = document.getScore() == null ? 0.0 : document.getScore();
                CitationResponse citation = new CitationResponse(documentId, sourceDocument.get().getFileName(),
                        document.getId(), pageNumber, chunkIndex, document.getText(), score);
                unique.putIfAbsent(documentId + ":" + chunkIndex, citation);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                log.warn("Ignoring vector record {} with invalid source metadata", document.getId());
            }
        }
    }

    private int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
