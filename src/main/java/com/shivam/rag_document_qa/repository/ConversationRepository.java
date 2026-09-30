package com.shivam.rag_document_qa.repository;

import com.shivam.rag_document_qa.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
}
