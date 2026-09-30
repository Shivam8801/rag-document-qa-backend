package com.shivam.rag_document_qa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shivam.rag_document_qa.config.RagProperties;
import com.shivam.rag_document_qa.document.PageAwareChunker;
import com.shivam.rag_document_qa.document.PdfTextExtractor;
import com.shivam.rag_document_qa.entity.Document;
import com.shivam.rag_document_qa.exception.ApiException;
import com.shivam.rag_document_qa.repository.DocumentRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class DocumentServiceTest {

    private final DocumentRepository documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
    private final VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    private final RagProperties properties = new RagProperties();

    private DocumentService service;

    @BeforeEach
    void setUp() {
        properties.setChunkSize(1000);
        properties.setChunkOverlap(100);
        properties.setMaxUploadBytes(1_000_000);
        properties.setMaxUploadsPerRequest(5);
        service = new DocumentService(documentRepository, new PdfTextExtractor(),
                new PageAwareChunker(properties), vectorStore, properties);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void indexesEachPageChunkWithStableIdAndRequiredMetadata() throws IOException {
        List<Document> uploaded = service.uploadAll(List.of(pdfUpload("report.pdf", "first page", "second page")));

        assertThat(uploaded).hasSize(1);
        Document saved = uploaded.get(0);
        ArgumentCaptor<List<org.springframework.ai.document.Document>> vectorsCaptor =
                ArgumentCaptor.captor();
        verify(vectorStore).add(vectorsCaptor.capture());

        List<org.springframework.ai.document.Document> vectors = vectorsCaptor.getValue();
        assertThat(vectors).hasSize(2);
        assertThat(vectors).allSatisfy(vector -> {
            int chunkIndex = (int) vector.getMetadata().get("chunkIndex");
            assertThat(vector.getId()).isEqualTo(DocumentService.vectorId(saved.getId(), chunkIndex));
            assertThat(vector.getMetadata())
                    .containsEntry("documentId", saved.getId().toString())
                    .containsEntry("documentName", "report.pdf");
        });
        assertThat(vectors).extracting(vector -> vector.getMetadata().get("pageNumber"))
                .containsExactly(1, 2);
        assertThat(vectors).extracting(org.springframework.ai.document.Document::getText)
                .containsExactly("first page", "second page");
    }

    @Test
    void deletesStableVectorIdsBeforeDeletingDocumentMetadata() {
        UUID id = UUID.randomUUID();
        Document document = new Document("report.pdf", "report.pdf", "application/pdf", 100, 2, 3);
        when(documentRepository.findById(id)).thenReturn(Optional.of(document));

        service.delete(id);

        ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.captor();
        InOrder order = inOrder(vectorStore, documentRepository);
        order.verify(vectorStore).delete(idsCaptor.capture());
        order.verify(documentRepository).delete(document);
        assertThat(idsCaptor.getValue()).containsExactly(
                DocumentService.vectorId(id, 0),
                DocumentService.vectorId(id, 1),
                DocumentService.vectorId(id, 2));
    }

    @Test
    void doesNotDeleteMetadataWhenVectorDeletionFails() {
        UUID id = UUID.randomUUID();
        Document document = new Document("report.pdf", "report.pdf", "application/pdf", 100, 1, 2);
        when(documentRepository.findById(id)).thenReturn(Optional.of(document));
        doThrow(new IllegalStateException("vector store unavailable")).when(vectorStore).delete(anyList());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("vectors could not be deleted");

        verify(documentRepository, never()).delete(document);
    }

    @Test
    void removesVectorsAndMetadataWhenIndexingFails() throws IOException {
        doThrow(new IllegalStateException("embedding service unavailable")).when(vectorStore).add(anyList());

        assertThatThrownBy(() -> service.uploadAll(List.of(pdfUpload("report.pdf", "page one", "page two"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("could not be indexed");

        ArgumentCaptor<Document> savedDocumentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(savedDocumentCaptor.capture());
        Document savedDocument = savedDocumentCaptor.getValue();
        ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.captor();
        verify(vectorStore).delete(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(
                DocumentService.vectorId(savedDocument.getId(), 0),
                DocumentService.vectorId(savedDocument.getId(), 1));
        verify(documentRepository).delete(savedDocument);
    }

    private MultipartFile pdfUpload(String filename, String... pageTexts) throws IOException {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(PDType1Font.HELVETICA, 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(output);
            pdf = output.toByteArray();
        }
        return new MockMultipartFile("files", filename, "application/pdf", pdf);
    }
}
