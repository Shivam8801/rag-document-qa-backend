package com.shivam.rag_document_qa.document;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.shivam.rag_document_qa.exception.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void rejectsFilesThatDoNotHavePdfSignature() {
        assertThatThrownBy(() -> extractor.extract("not a PDF".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not a valid PDF");
    }

    @Test
    void rejectsMalformedPdfWithPdfSignature() {
        assertThatThrownBy(() -> extractor.extract("%PDF-1.7\nbroken".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("readable PDF");
    }

    @Test
    void extractsTextSeparatelyForEveryPage() throws IOException {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addTextPage(document, "first page");
            addTextPage(document, "second page");
            document.save(output);
            pdf = output.toByteArray();
        }

        PdfTextExtractor.ExtractedPdf extracted = extractor.extract(pdf);

        assertThat(extracted.pageCount()).isEqualTo(2);
        assertThat(extracted.pages()).hasSize(2);
        assertThat(extracted.pages().get(0)).contains("first page");
        assertThat(extracted.pages().get(1)).contains("second page");
    }

    private void addTextPage(PDDocument document, String text) throws IOException {
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
}
