package com.example.rag_service.NLP;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@AllArgsConstructor
public class PdfParser {

    private final VectorStore vectorStore;


    public void uploadFile(String fileName, InputStream inputStream) throws IOException {
        String text;
        try (inputStream) {
            if (fileName.toLowerCase().endsWith(".pdf")) {
                text = parsePDF(inputStream);
            } else if (fileName.toLowerCase().endsWith(".docx")) {
                text = parseDOCX(inputStream);
            } else {
                throw new IllegalArgumentException("Unsupported file type: " + fileName);
            }
        }
        text = cleanText(text);
        List<Document> chunks = chunkText(fileName, text, 1200, 150);

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().putAll(Map.of(
                    "fileName", fileName,
                    "chunkIndex", i,
                    "length", chunks.get(i).getText().length()
            ));
        }
        vectorStore.add(chunks);
        log.info("Uploaded file {} with {} chunks", fileName, chunks.size());
    }

    public List<Document> chunkText(String fileName, String text, int maxChars, int overlapChars) {
        List<String> sentences = splitIntoSentences(text);
        List<Document> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int index = 0;
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (isNoise(sentence)) continue;
            if (current.length() + sentence.length() > maxChars) {
                addChunk(chunks, current.toString(), fileName, index++);
                current = new StringBuilder(getOverlap(current.toString(), overlapChars));
            }
            current.append(sentence).append(" ");
        }
        if (!current.isEmpty()) {
            addChunk(chunks, current.toString(), fileName, index);
        }
        return chunks;
    }

    private void addChunk(List<Document> chunks, String text, String fileName, int index) {
        String cleaned = text.trim();
        if (cleaned.length() < 50) return;
        chunks.add(new Document(cleaned, Map.of(
                "content", cleaned,
                "fileName", fileName,
                "chunkIndex", index,
                "length", cleaned.length()
        )));
    }

    private String cleanText(String text) {
        return text
                .replaceAll("\\b/\\w+\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isNoise(String sentence) {
        if (sentence == null) return true;
        if (sentence.length() < 20) return true;
        if (Pattern.matches("^[a-zA-Z0-9_./]+$", sentence)) return true;

        return false;
    }

    private String getOverlap(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return text.substring(text.length() - maxChars);
    }

    private List<String> splitIntoSentences(String text) {
        return Arrays.asList(
                text.split("(?<=[.!?])\\s+")
        );
    }

    public String parsePDF(InputStream inputStream) throws IOException {
        StringBuilder text = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(inputStream))) {
            if (document.isEncrypted()) {
                throw new IllegalStateException("Document is encrypted.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            text.append(stripper.getText(document));
        }
        return text.toString();
    }

    public String parseDOCX(InputStream inputStream) throws IOException {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            document.getParagraphs().forEach(p -> {
                String t = p.getText();
                if (t != null && !t.isBlank()) {
                    text.append(t).append(" ");
                }
            });
        }
        return text.toString();
    }
}