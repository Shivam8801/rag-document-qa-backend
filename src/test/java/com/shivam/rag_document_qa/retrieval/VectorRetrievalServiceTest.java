package com.shivam.rag_document_qa.retrieval;

import com.shivam.rag_document_qa.config.RagProperties;
import com.shivam.rag_document_qa.dto.AskRequest;
import com.shivam.rag_document_qa.entity.Document;
import com.shivam.rag_document_qa.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class VectorRetrievalServiceTest {

	@Test
	void mapsRetrievedMetadataToCitationFromPersistedDocument() {
		UUID documentId = UUID.randomUUID();
		String chunkId = UUID.randomUUID().toString();
		RagProperties properties = new RagProperties();
		properties.setDefaultTopK(5);
		properties.setMaxTopK(20);
		properties.setSimilarityThreshold(0.65);
		VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
		DocumentRepository documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
		when(documentRepository.existsById(documentId)).thenReturn(true);
		when(documentRepository.findById(documentId)).thenReturn(Optional.of(
				new Document("renamed-policy.pdf", "original-policy.pdf", "application/pdf",
						1200, 4, 3)));
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(
				java.util.List.of(org.springframework.ai.document.Document.builder()
						.id(chunkId)
						.text("The leave allowance is 20 days.")
						.metadata(Map.of("documentId", documentId.toString(), "pageNumber", 2, "chunkIndex", 1))
						.build()));

		VectorRetrievalService retrievalService = new VectorRetrievalService(
				vectorStore, properties, documentRepository);

		var citations = retrievalService.findRelevantSources(new AskRequest(
				"How many leave days?", null, documentId, null, null, null));

		assertThat(citations).hasSize(1);
		assertThat(citations.getFirst().documentId()).isEqualTo(documentId);
		assertThat(citations.getFirst().documentName()).isEqualTo("renamed-policy.pdf");
		assertThat(citations.getFirst().chunkId()).isEqualTo(chunkId);
		assertThat(citations.getFirst().pageNumber()).isEqualTo(2);
		assertThat(citations.getFirst().chunkIndex()).isEqualTo(1);
		assertThat(citations.getFirst().excerpt()).contains("20 days");
	}
}
