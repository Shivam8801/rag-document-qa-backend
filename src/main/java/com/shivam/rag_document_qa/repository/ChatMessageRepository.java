package com.shivam.rag_document_qa.repository;

import com.shivam.rag_document_qa.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
	List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
	List<ChatMessage> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);
	@Transactional
	void deleteByConversationId(UUID conversationId);
}
