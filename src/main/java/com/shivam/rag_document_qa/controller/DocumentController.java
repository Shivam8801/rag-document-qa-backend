package com.shivam.rag_document_qa.controller;

import com.shivam.rag_document_qa.dto.DocumentResponse;
import com.shivam.rag_document_qa.dto.UpdateDocumentRequest;
import com.shivam.rag_document_qa.service.DocumentService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Upload, browse, rename, and delete PDF documents and their indexed chunks.")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping({"", "/upload"})
    @Operation(summary = "Upload PDF documents",
            description = "Accepts one `file` part or multiple repeated `files` parts. Extracts each PDF page, "
                    + "splits the text into page-aware chunks, generates embeddings, and indexes them in pgvector.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Documents uploaded and indexed."),
            @ApiResponse(responseCode = "400", description = "Invalid, empty, or missing PDF upload."),
            @ApiResponse(responseCode = "413", description = "Upload exceeds configured size limits."),
            @ApiResponse(responseCode = "422", description = "PDF contains no extractable text."),
            @ApiResponse(responseCode = "503", description = "Vector indexing is unavailable.")
    })
    public ResponseEntity<List<DocumentResponse>> uploadDocuments(
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        List<MultipartFile> uploads = new ArrayList<>();
        if (files != null) {
            uploads.addAll(files);
        }
        if (file != null) {
            uploads.add(file);
        }
        List<DocumentResponse> responses = documentService.uploadDocuments(uploads).stream()
                .map(DocumentResponse::fromDocument).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @GetMapping
    @Operation(summary = "List indexed documents",
            description = "Returns metadata for all successfully processed documents without exposing extracted text.")
    public List<DocumentResponse> listDocuments() {
        return documentService.listDocuments().stream().map(DocumentResponse::fromDocument).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document metadata",
            description = "Returns metadata, page count, and indexed chunk count for the requested document.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document metadata."),
            @ApiResponse(responseCode = "404", description = "Document does not exist.")
    })
    public DocumentResponse getDocument(@PathVariable UUID id) {
        return DocumentResponse.fromDocument(documentService.getDocument(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename a document",
            description = "Changes the display name used for the document and its source citations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document renamed."),
            @ApiResponse(responseCode = "400", description = "Name is blank or too long."),
            @ApiResponse(responseCode = "404", description = "Document does not exist.")
    })
    public DocumentResponse renameDocument(
            @PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest request) {
        return DocumentResponse.fromDocument(documentService.renameDocument(id, request.name()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document",
            description = "Removes document metadata and all associated vectors from pgvector.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Document and vectors deleted."),
            @ApiResponse(responseCode = "404", description = "Document does not exist."),
            @ApiResponse(responseCode = "503", description = "Vector deletion failed; metadata was retained.")
    })
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
