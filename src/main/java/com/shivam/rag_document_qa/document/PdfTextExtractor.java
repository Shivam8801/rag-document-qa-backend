package com.shivam.rag_document_qa.document;

import com.shivam.rag_document_qa.exception.ApiException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor {

    public ExtractedPdf extractPageText(byte[] content) {

        // Reject missing/empty uploads and files without a valid PDF header
        if (!containsPdfHeader(content)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PDF", "The uploaded file is not a valid PDF.");
        }

        try (PDDocument document = PDDocument.load(content)) {
            // Handle unreadable or encrypted PDFs
            if (document.isEncrypted()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ENCRYPTED_PDF", "Encrypted PDFs are not supported.");
            }

            // Looping through pages
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> pages = new ArrayList<>(document.getNumberOfPages());
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(document));
            }
            if (pages.isEmpty() || pages.stream().allMatch(text -> text == null || text.isBlank())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_PDF",
                        "The PDF contains no extractable text.");
            }
            return new ExtractedPdf(document.getNumberOfPages(), List.copyOf(pages));
        } catch (InvalidPasswordException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ENCRYPTED_PDF", "Encrypted PDFs are not supported.");
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PDF", "The uploaded file is not a readable PDF.");
        }
    }

    private boolean containsPdfHeader(byte[] content) {
        byte[] header = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int searchLimit = Math.min(content.length - header.length, 1024);
        for (int start = 0; start <= searchLimit; start++) {
            boolean matches = true;
            for (int index = 0; index < header.length; index++) {
                if (content[start + index] != header[index]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    public record ExtractedPdf(int pageCount, List<String> pages) {
    }
}
