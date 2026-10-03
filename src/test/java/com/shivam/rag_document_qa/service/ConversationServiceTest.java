package com.shivam.rag_document_qa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivam.rag_document_qa.ai.PromptBuilder;
import com.shivam.rag_document_qa.config.RagProperties;
import com.shivam.rag_document_qa.dto.AskRequest;
import com.shivam.rag_document_qa.dto.CitationResponse;
import com.shivam.rag_document_qa.entity.ChatMessage;
import com.shivam.rag_document_qa.entity.Conversation;
import com.shivam.rag_document_qa.exception.ApiException;
import com.shivam.rag_document_qa.repository.ChatMessageRepository;
import com.shivam.rag_document_qa.repository.ConversationRepository;
import com.shivam.rag_document_qa.retrieval.VectorRetrievalService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class ConversationServiceTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ChatMessageRepository messageRepository = mock(ChatMessageRepository.class);
    private final VectorRetrievalService retrievalService = mock(VectorRetrievalService.class);
    private final PromptBuilder promptBuilder = mock(PromptBuilder.class);
    private final ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
    private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RagProperties properties = new RagProperties();

    private ConversationService service;

    @BeforeEach
    void initializeServiceWithTestConfiguration() {
        properties.setMaxHistoryMessages(4);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = new ConversationService(conversationRepository, messageRepository, retrievalService,
                promptBuilder, chatClientBuilder, objectMapper, properties);
    }

    @Test
    void createsConversationWithTrimmedOrDefaultTitle() {
        var titled = service.createConversation("  Project questions  ");
        var untitled = service.createConversation("  ");

        assertThat(titled.title()).isEqualTo("Project questions");
        assertThat(untitled.title()).isEqualTo("New conversation");
        verify(conversationRepository, org.mockito.Mockito.times(2)).save(any(Conversation.class));
    }

    @Test
    void deletesMessagesBeforeDeletingConversation() {
        UUID id = UUID.randomUUID();
        Conversation conversation = new Conversation("Questions");
        when(conversationRepository.findById(id)).thenReturn(Optional.of(conversation));

        service.deleteConversation(id);

        InOrder order = inOrder(messageRepository, conversationRepository);
        order.verify(messageRepository).deleteByConversationId(conversation.getId());
        order.verify(conversationRepository).delete(conversation);
    }

    @Test
    void returnsNotFoundForUnknownConversation() {
        UUID id = UUID.randomUUID();
        when(conversationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConversation(id))
                .isInstanceOf(ApiException.class)
                .hasMessage("Conversation not found.");
    }

    @Test
    void returnsMessagesWithParsedStoredCitations() throws Exception {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation("Questions");
        CitationResponse citation = new CitationResponse(UUID.randomUUID(), "guide.pdf",
                UUID.randomUUID().toString(), 2, 0, "Relevant excerpt", 0.9);
        ChatMessage message = new ChatMessage(conversation, ChatMessage.Role.ASSISTANT,
                "Here is the answer.", objectMapper.writeValueAsString(List.of(citation)));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(List.of(message));

        var responses = service.getConversationMessages(conversationId);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.role()).isEqualTo(ChatMessage.Role.ASSISTANT);
            assertThat(response.content()).isEqualTo("Here is the answer.");
            assertThat(response.citations()).containsExactly(citation);
        });
    }

    @Test
    void storesQuestionAndNoResultsAnswerWithoutCallingChatModel() {
        Conversation conversation = new Conversation("Existing conversation");
        UUID conversationId = conversation.getId();
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(
                any(UUID.class), any(PageRequest.class))).thenReturn(List.of());
        when(retrievalService.findRelevantSources(any(AskRequest.class))).thenReturn(List.of());
        AskRequest request = new AskRequest("What does the guide say?", conversationId,
                null, null, null, null);

        var response = service.answerQuestion(request);

        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.answer()).contains("couldn't find relevant information");
        assertThat(response.sources()).isEmpty();
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues()).extracting(ChatMessage::getRole)
                .containsExactly(ChatMessage.Role.USER, ChatMessage.Role.ASSISTANT);
        assertThat(messageCaptor.getAllValues().get(1).getCitationsJson()).isEqualTo("[]");
        verify(chatClient, never()).prompt();
        verify(messageRepository).findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, properties.getMaxHistoryMessages()));
    }

    @Test
    void generatesAnswerWithRetrievedSourcesAndRecentHistory() {
        Conversation conversation = new Conversation("Existing conversation");
        UUID conversationId = conversation.getId();
        CitationResponse citation = new CitationResponse(UUID.randomUUID(), "guide.pdf",
                UUID.randomUUID().toString(), 2, 1, "The guide says to start here.", 0.91);
        List<ChatMessage> history = List.of(
                new ChatMessage(conversation, ChatMessage.Role.USER, "Earlier question", null));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(
                any(UUID.class), any(PageRequest.class))).thenReturn(history);
        when(retrievalService.findRelevantSources(any(AskRequest.class))).thenReturn(List.of(citation));
        when(chatClient.prompt().messages(anyList()).call().content()).thenReturn("Start with step one. [S1]");
        AskRequest request = new AskRequest("What should I do?", conversationId, null, null, null, null);

        var response = service.answerQuestion(request);

        assertThat(response.answer()).isEqualTo("Start with step one. [S1]");
        assertThat(response.sources()).containsExactly(citation);
        verify(promptBuilder).buildGroundedAnswerMessages(request.question(), history, List.of(citation));
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues().get(1).getCitationsJson()).contains("guide.pdf", "pageNumber");
    }

    @Test
    void mapsChatModelErrorsToServiceUnavailable() {
        Conversation conversation = new Conversation("Existing conversation");
        UUID conversationId = conversation.getId();
        CitationResponse citation = new CitationResponse(UUID.randomUUID(), "guide.pdf",
                UUID.randomUUID().toString(), 1, 0, "Source text.", 0.8);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(
                any(UUID.class), any(PageRequest.class))).thenReturn(List.of());
        when(retrievalService.findRelevantSources(any(AskRequest.class))).thenReturn(List.of(citation));
        when(chatClient.prompt().messages(anyList()).call().content())
                .thenThrow(new IllegalStateException("model unavailable"));

        assertThatThrownBy(() -> service.answerQuestion(new AskRequest(
                "Question?", conversationId, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("chat model is unavailable");
    }

    @Test
    void listsConversationsByMostRecentlyUpdated() {
        when(conversationRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")))
                .thenReturn(List.of(new Conversation("Recent"), new Conversation("Older")));

        var results = service.listConversations();

        assertThat(results).extracting(response -> response.title())
                .containsExactly("Recent", "Older");
    }
}
