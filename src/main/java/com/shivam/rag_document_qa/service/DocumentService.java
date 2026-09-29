package com.shivam.rag_document_qa.service;

import com.shivam.rag_document_qa.config.RagProperties;
import com.shivam.rag_document_qa.document.DocumentChunk;
import com.shivam.rag_document_qa.document.PageAwareChunker;
import com.shivam.rag_document_qa.document.PdfTextExtractor;
import com.shivam.rag_document_qa.entity.Document;
import com.shivam.rag_document_qa.exception.ApiException;
import com.shivam.rag_document_qa.repository.DocumentRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final int VECTOR_WRITE_BATCH_SIZE = 100;
    private static final int VECTOR_DELETE_BATCH_SIZE = 500;

    private final DocumentRepository documentRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final PageAwareChunker chunker;
    private final VectorStore vectorStore;
    private final RagProperties properties;

    public DocumentService(DocumentRepository documentRepository, PdfTextExtractor pdfTextExtractor,
                           PageAwareChunker chunker, VectorStore vectorStore, RagProperties properties) {
        this.documentRepository = documentRepository;
        this.pdfTextExtractor = pdfTextExtractor;
        this.chunker = chunker;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public List<Document> uploadAll(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_FILES", "At least one PDF file is required.");
        }
        if (files.size() > properties.getMaxUploadsPerRequest()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_FILES",
                    "At most " + properties.getMaxUploadsPerRequest() + " files may be uploaded at once.");
        }
        List<Document> uploaded = new ArrayList<>();
        List<StoredVectorSet> stored = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                StoredVectorSet item = uploadOne(file);
                stored.add(item);
                uploaded.add(item.document());
            }
            return List.copyOf(uploaded);
        } catch (RuntimeException exception) {
            rollbackUploads(stored);
            throw exception;
        }
    }

    private StoredVectorSet uploadOne(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Uploaded files must not be empty.");
        }
        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "An uploaded file exceeds the configured size limit.");
        }
        String name = safeFileName(file.getOriginalFilename());
        log.info("Starting document processing for {}", name);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_READ_ERROR", "The uploaded file could not be read.");
        }
        PdfTextExtractor.ExtractedPdf pdf = pdfTextExtractor.extract(bytes);
        log.info("Extracted {} pages from {}", pdf.pageCount(), name);
        List<DocumentChunk> chunks = chunker.chunkText(pdf.pages());
        if (chunks.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_PDF",
                    "The PDF contains no extractable text.");
        }

        Document document = documentRepository.save(
                new Document(name, name, "application/pdf", bytes.length, pdf.pageCount(), chunks.size()));
        log.info("Generating embeddings for document {} with {} chunks", document.getId(), chunks.size());
        List<String> vectorIds = new ArrayList<>(chunks.size());
        List<org.springframework.ai.document.Document> vectors = new ArrayList<>(chunks.size());
        for (DocumentChunk chunk : chunks) {
            String vectorId = vectorId(document.getId(), chunk.chunkIndex());
            vectorIds.add(vectorId);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentId", document.getId().toString());
            metadata.put("documentName", document.getFileName());
            metadata.put("pageNumber", chunk.pageNumber());
            metadata.put("chunkIndex", chunk.chunkIndex());
            metadata.put("createdAt", Instant.now().toString());
            vectors.add(org.springframework.ai.document.Document.builder()
                    .id(vectorId)
                    .text(chunk.text())
                    .metadata(metadata)
                    .build());
        }
        try {
            for (int start = 0; start < vectors.size(); start += VECTOR_WRITE_BATCH_SIZE) {
                int end = Math.min(start + VECTOR_WRITE_BATCH_SIZE, vectors.size());
                vectorStore.add(vectors.subList(start, end));
            }
        } catch (RuntimeException exception) {
            try {
                deleteVectorIds(vectorIds);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            documentRepository.delete(document);
            log.warn("Vector indexing failed for document {}; metadata record was removed", document.getId());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INDEXING_FAILED",
                    "The document could not be indexed. Check the vector database and embedding service.");
        }
        log.info("Indexed document {} with {} pages and {} chunks", document.getId(), pdf.pageCount(), chunks.size());
        return new StoredVectorSet(document, vectorIds);
    }

    public List<Document> list() {
        return documentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Document get(UUID id) {
        return documentRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found."));
    }

    public Document rename(UUID id, String name) {
        Document document = get(id);
        document.rename(name.trim());
        return documentRepository.save(document);
    }

    public void delete(UUID id) {
        Document document = get(id);
        List<String> ids = new ArrayList<>(document.getChunkCount());
        for (int index = 0; index < document.getChunkCount(); index++) {
            ids.add(vectorId(id, index));
        }
        try {
            deleteVectorIds(ids);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DELETE_FAILED",
                    "Document vectors could not be deleted. The document was not removed.");
        }
        documentRepository.delete(document);
        log.info("Deleted document {} and {} vector chunks", id, ids.size());
    }

    private void rollbackUploads(List<StoredVectorSet> stored) {
        for (int index = stored.size() - 1; index >= 0; index--) {
            StoredVectorSet item = stored.get(index);
            try {
                deleteVectorIds(item.vectorIds());
            } catch (RuntimeException exception) {
                log.error("Could not roll back vector chunks for document {} ({})",
                        item.document().getId(), exception.getClass().getSimpleName());
            }
            documentRepository.deleteById(item.document().getId());
        }
    }

    private void deleteVectorIds(List<String> ids) {
        for (int start = 0; start < ids.size(); start += VECTOR_DELETE_BATCH_SIZE) {
            int end = Math.min(start + VECTOR_DELETE_BATCH_SIZE, ids.size());
            vectorStore.delete(ids.subList(start, end));
        }
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "document.pdf";
        }
        String normalized = originalName.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (name.isBlank()) {
            return "document.pdf";
        }
        return name.length() > 512 ? name.substring(name.length() - 512) : name;
    }

    public static String vectorId(UUID documentId, int chunkIndex) {
        return UUID.nameUUIDFromBytes((documentId + ":" + chunkIndex).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private record StoredVectorSet(Document document, List<String> vectorIds) {
    }
}
