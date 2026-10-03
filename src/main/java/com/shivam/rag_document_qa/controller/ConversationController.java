package com.shivam.rag_document_qa.controller;

import com.shivam.rag_document_qa.dto.ChatMessageResponse;
import com.shivam.rag_document_qa.dto.ConversationResponse;
import com.shivam.rag_document_qa.dto.CreateConversationRequest;
import com.shivam.rag_document_qa.service.ConversationService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@Tag(name = "Conversations", description = "Create and manage question-answer conversation history.")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @Operation(summary = "Create a conversation",
            description = "Creates an empty conversation, optionally with a title. Use its ID in subsequent question requests.")
    public ResponseEntity<ConversationResponse> createConversation(
            @Valid @RequestBody(required = false) CreateConversationRequest request) {
        ConversationResponse response =
                conversationService.createConversation(request == null ? null : request.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List conversations",
            description = "Returns conversation metadata ordered by most recently updated.")
    public List<ConversationResponse> listConversations() {
        return conversationService.listConversations();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a conversation",
            description = "Returns metadata for the specified conversation.")
    public ConversationResponse getConversation(@PathVariable UUID id) {
        return conversationService.getConversation(id);
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "List conversation messages",
            description = "Returns the conversation's user and assistant messages in chronological order, including stored source citations.")
    public List<ChatMessageResponse> getConversationMessages(@PathVariable UUID id) {
        return conversationService.getConversationMessages(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a conversation",
            description = "Deletes a conversation and its stored messages.")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        conversationService.deleteConversation(id);
        return ResponseEntity.noContent().build();
    }
}
