package com.shivam.rag_document_qa.controller;

import com.shivam.rag_document_qa.dto.AskRequest;
import com.shivam.rag_document_qa.dto.AskResponse;
import com.shivam.rag_document_qa.service.ConversationService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qa")
@Tag(name = "Question answering", description = "Ask questions grounded in indexed PDF content.")
public class QaController {

    private final ConversationService conversationService;

    public QaController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a question about documents",
            description = "Retrieves relevant chunks from pgvector, optionally filters by document IDs, "
                    + "uses recent conversation history when supplied, and returns an answer with source citations. "
                    + "Omit conversationId to start a new conversation.")
    public AskResponse answerQuestion(@Valid @RequestBody AskRequest request) {
        return conversationService.answerQuestion(request);
    }
}
