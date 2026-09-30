package com.shivam.rag_document_qa.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.shivam.rag_document_qa.dto.CitationResponse;
import com.shivam.rag_document_qa.entity.ChatMessage;
import com.shivam.rag_document_qa.entity.Conversation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void separatesInstructionsContextHistoryAndCurrentQuestion() {
        UUID documentId = UUID.randomUUID();
        Conversation conversation = new Conversation("Policy questions");
        List<ChatMessage> history = List.of(
                new ChatMessage(conversation, ChatMessage.Role.USER, "How much leave?", null),
                new ChatMessage(conversation, ChatMessage.Role.ASSISTANT, "The policy grants 20 days.", null));
        List<CitationResponse> citations = List.of(new CitationResponse(
                documentId, "leave-policy.pdf", UUID.randomUUID().toString(), 4, 2,
                "Employees receive 20 days of annual leave.", 0.9));

        var messages = promptBuilder.build("Can unused leave carry over?", history, citations);

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) messages.get(0)).getText())
                .contains("Answer only from the provided source excerpts")
                .contains("Use conversation history only to resolve references or")
                .contains("[S1] leave-policy.pdf, page 4")
                .contains("Employees receive 20 days of annual leave.");
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(1)).getText()).isEqualTo("How much leave?");
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(((AssistantMessage) messages.get(2)).getText()).isEqualTo("The policy grants 20 days.");
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(3)).getText()).isEqualTo("Can unused leave carry over?");
    }
}
