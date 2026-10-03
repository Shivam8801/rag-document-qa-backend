package com.shivam.rag_document_qa.document;

import com.shivam.rag_document_qa.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageAwareChunkerTest {

	@Test
	void chunksWithinPagesWithOverlapAndStablePageMetadata() {
		RagProperties properties = new RagProperties();
		properties.setChunkSize(12);
		properties.setChunkOverlap(3);
		PageAwareChunker chunker = new PageAwareChunker(properties);

		List<DocumentChunk> chunks = chunker.splitPagesIntoChunks(List.of("alpha beta gamma", "delta epsilon"));

		assertThat(chunks).isNotEmpty();
		assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text().length()).isLessThanOrEqualTo(12));
		assertThat(chunks).filteredOn(chunk -> chunk.pageNumber() == 1).isNotEmpty();
		assertThat(chunks).filteredOn(chunk -> chunk.pageNumber() == 2).isNotEmpty();
		assertThat(chunks).extracting(DocumentChunk::chunkIndex)
				.containsExactlyElementsOf(java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());
		assertThat(chunks.stream().filter(chunk -> chunk.pageNumber() == 1)
				.map(DocumentChunk::text).reduce("", String::concat)).contains("alpha", "gamma");
		assertThat(chunks.stream().filter(chunk -> chunk.pageNumber() == 2)
				.map(DocumentChunk::text).reduce("", String::concat)).contains("delta", "epsilon");
	}

	@Test
	void skipsBlankPagesAndReturnsNoChunksForAnAllBlankDocument() {
		RagProperties properties = new RagProperties();
		properties.setChunkSize(1000);
		properties.setChunkOverlap(150);
		PageAwareChunker chunker = new PageAwareChunker(properties);
		assertThat(chunker.splitPagesIntoChunks(List.of("", " \n\t "))).isEmpty();
		assertThat(chunker.splitPagesIntoChunks(List.of("first page", "", "third page")))
				.extracting(DocumentChunk::pageNumber).containsOnly(1, 3);
	}
}
