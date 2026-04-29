package com.example.rag_service.NLP;

import lombok.AllArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class PdfParser {
    private final VectorStore vectorStore;

    //publisher
    public void uploadFile(String fileName, InputStream inputStream) throws IOException {
        List<String> units;
        String lower = fileName
                .trim()
                .toLowerCase()
                .replaceAll("\\s+", "");
        try (inputStream) {
            if (lower.endsWith(".pdf")) {
                units = parsePDF(inputStream);
            } else if (lower.endsWith(".docx")) {
                units = parseDOCX(inputStream);
            } else {
                throw new IllegalArgumentException("Unsupported file type: " + fileName);
            }
        }

        List<Document> allChunks = new ArrayList<>();
        int globalIndex = 0;

        for (int i = 0; i < units.size(); i++) {
            String unitText = units.get(i);
            if (unitText.isBlank()) continue;
            List<Document> chunks = chunkText(fileName, unitText, 5000, 500);
            for (Document doc : chunks) {
                log.info("Processed file={}", fileName);
                doc.getMetadata().putAll(Map.of(
                        "fileName", fileName,
                        "unitIndex", i + 1,
                        "chunkIndex", globalIndex++,
                        "length", doc.getText().length()
                ));
            }
            allChunks.addAll(chunks);
        }
        vectorStore.add(allChunks);
    }

    //chunk consumer
    public List<Document> chunkText(String fileName, String text, int maxChars, int overlapChars) {
        List<String> sentences = splitIntoSentences(text);

        List<Document> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        int index = 0;

        for (String sentence : sentences) {

            if (currentChunk.length() + sentence.length() > maxChars) {

                chunks.add(new Document(currentChunk.toString().trim(), Map.of(
                        "fileName", fileName,
                        "chunkIndex", index++
                )));

                int overlapStart = Math.max(0, currentChunk.length() - overlapChars);
                currentChunk = new StringBuilder(currentChunk.substring(overlapStart));
            }

            currentChunk.append(sentence).append(" ");
        }

        if (!currentChunk.isEmpty()) {
            String chunkText = currentChunk.toString().trim();
            if (!chunkText.isBlank()) {
                Document chunk = new Document(chunkText, Map.of(
                        "fileName", fileName,
                        "chunkIndex", index
                ));
                chunks.add(chunk);
            }
        }

        return chunks;
    }

    private List<String> splitIntoSentences(String text) {
        return List.of(text.split("(?<=[.!?])\\s+"));
    }

    // parse pdf consumer
    public List<String> parsePDF(InputStream inputStream) throws IOException {
        List<String> pagesText = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(inputStream))) {

            if (document.isEncrypted()) {
                throw new IllegalStateException("Document is encrypted.");
            }

            PDFTextStripper stripper = new PDFTextStripper();

            int totalPages = document.getNumberOfPages();

            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);

                String pageText = stripper.getText(document);
                pagesText.add(pageText);
            }
        }

        return pagesText;
    }

    //parse docx consumer
    public List<String> parseDOCX(InputStream inputStream) throws IOException {
        List<String> paragraphs = new ArrayList<>();

        try (XWPFDocument document = new XWPFDocument(inputStream)) {

            document.getParagraphs().forEach(p -> {
                if (!p.getText().isBlank()) {
                    paragraphs.add(p.getText());
                }
            });
        }

        return paragraphs;
    }
}
