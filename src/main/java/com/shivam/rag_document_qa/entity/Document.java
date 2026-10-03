package com.shivam.rag_document_qa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(nullable = false, length = 512)
    private String fileName;

    @Column(nullable = false, length = 512, updatable = false)
    private String originalFileName;

    @Column(nullable = false, length = 128)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false)
    private int pageCount;

    @Column(nullable = false)
    private int chunkCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Document() {
    }

    public Document(String fileName, String originalFileName, String contentType,
                    long fileSize, int pageCount, int chunkCount) {
        this.id = UUID.randomUUID();
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.pageCount = pageCount;
        this.chunkCount = chunkCount;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    // runs before a new entity is inserted into the database
    @PrePersist
    private void initializeTimestampsBeforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    // runs before an existing entity is updated
    @PreUpdate
    private void updateTimestampBeforeDatabaseUpdate() {
        updatedAt = Instant.now();
    }

    public void renameTo(String fileName) {
        this.fileName = fileName;
    }
}
