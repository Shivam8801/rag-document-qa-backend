package com.shivam.rag_document_qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivam.rag_document_qa.ai.PromptBuilder;
import com.shivam.rag_document_qa.config.RagProperties;
import com.shivam.rag_document_qa.dto.AskRequest;
import com.shivam.rag_document_qa.dto.AskResponse;
import com.shivam.rag_document_qa.dto.ChatMessageResponse;
import com.shivam.rag_document_qa.dto.CitationResponse;
import com.shivam.rag_document_qa.dto.ConversationResponse;
import com.shivam.rag_document_qa.entity.ChatMessage;
import com.shivam.rag_document_qa.entity.Conversation;
import com.shivam.rag_document_qa.exception.ApiException;
import com.shivam.rag_document_qa.repository.ChatMessageRepository;
import com.shivam.rag_document_qa.repository.ConversationRepository;
import com.shivam.rag_document_qa.retrieval.VectorRetrievalService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final VectorRetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;

    public ConversationService(ConversationRepository conversationRepository,
                               ChatMessageRepository messageRepository, VectorRetrievalService retrievalService,
                               PromptBuilder promptBuilder, ChatClient.Builder chatClientBuilder,
                               ObjectMapper objectMapper, RagProperties properties) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ConversationResponse createConversation(String requestedTitle) {
        String title = requestedTitle == null || requestedTitle.isBlank() ? "New conversation" : requestedTitle.trim();
        return ConversationResponse.fromConversation(conversationRepository.save(new Conversation(title)));
    }

    public List<ConversationResponse> listConversations() {
        return conversationRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .map(ConversationResponse::fromConversation).toList();
    }

    public ConversationResponse getConversation(UUID id) {
        return ConversationResponse.fromConversation(requireConversation(id));
    }

    @Transactional
    public void deleteConversation(UUID id) {
        Conversation conversation = requireConversation(id);
        messageRepository.deleteByConversationId(conversation.getId());
        conversationRepository.delete(conversation);
    }

    public List<ChatMessageResponse> getConversationMessages(UUID id) {
        requireConversation(id);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(id).stream()
                .map(this::toChatMessageResponse).toList();
    }

    public AskResponse answerQuestion(AskRequest request) {
        Conversation conversation;
        if (request.conversationId() == null) {
            String title = request.question().trim();
            if (title.length() > 80) {
                title = title.substring(0, 77) + "...";
            }
            conversation = conversationRepository.save(new Conversation(title));
        } else {
            conversation = requireConversation(request.conversationId());
        }
        log.info("Processing question for conversation {}", conversation.getId());

        List<ChatMessage> history = loadRecentConversationHistory(conversation.getId());
        List<CitationResponse> citations = retrievalService.findRelevantSources(request);
        messageRepository.save(new ChatMessage(conversation, ChatMessage.Role.USER, request.question(), null));

        String answer;
        if (citations.isEmpty()) {
            answer = "I couldn't find relevant information in the indexed documents to answer that question.";
        } else {
            try {
                answer = chatClient.prompt()
                        .messages(promptBuilder.buildGroundedAnswerMessages(request.question(), history, citations))
                        .call()
                        .content();
                if (answer == null || answer.isBlank()) {
                    answer = "I couldn't generate an answer from the retrieved document excerpts.";
                }
            } catch (RuntimeException exception) {
                log.warn("Chat model request failed for conversation {}", conversation.getId());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_UNAVAILABLE",
                        "The chat model is unavailable. Check the Ollama service and model configuration.");
            }
        }
        messageRepository.save(new ChatMessage(conversation, ChatMessage.Role.ASSISTANT, answer,
                serializeSourceCitations(citations)));
        conversation.updateLastModifiedTime();
        conversationRepository.save(conversation);
        return new AskResponse(conversation.getId(), answer, List.copyOf(citations));
    }

    private List<ChatMessage> loadRecentConversationHistory(UUID conversationId) {
        int limit = properties.getMaxHistoryMessages();
        if (limit <= 0) {
            return List.of();
        }
        List<ChatMessage> recent = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, limit));
        List<ChatMessage> ordered = new ArrayList<>(recent);
        Collections.reverse(ordered);
        return List.copyOf(ordered);
    }

    private Conversation requireConversation(UUID id) {
        return conversationRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Conversation not found."));
    }

    private String serializeSourceCitations(List<CitationResponse> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize source citations.", exception);
        }
    }

    private ChatMessageResponse toChatMessageResponse(ChatMessage message) {
        List<CitationResponse> citations = List.of();
        if (message.getCitationsJson() != null && !message.getCitationsJson().isBlank()) {
            try {
                citations = objectMapper.readValue(message.getCitationsJson(), new TypeReference<>() { });
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Stored citations could not be parsed for message "
                        + message.getId(), exception);
            }
        }
        return new ChatMessageResponse(message.getId(), message.getRole(), message.getContent(),
                message.getCreatedAt(), citations);
    }
}
