package com.shivam.rag_document_qa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    public enum Role { USER, ASSISTANT }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String citationsJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(Conversation conversation, Role role, String content, String citationsJson) {
        this.id = UUID.randomUUID();
        this.conversation = conversation;
        this.role = role;
        this.content = content;
        this.citationsJson = citationsJson;
        this.createdAt = Instant.now();
    }

}
