package com.shivam.rag_document_qa.repository;

import com.shivam.rag_document_qa.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
}
