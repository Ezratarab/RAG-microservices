package com.example.rag_service.NLP;

import com.example.rag_service.config.RagProperties;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns an uploaded file into text, then into overlapping, sentence-aligned chunks.
 * Pure parsing: storing/embedding happens in the ingestion worker.
 */
@Component
@RequiredArgsConstructor
public class DocumentParser {

    private static final Pattern PATH_LIKE = Pattern.compile("^[a-zA-Z0-9_./]+$");

    private final RagProperties props;

    public static boolean isSupported(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || name.endsWith(".docx");
    }

    public String extractText(String fileName, InputStream in) throws IOException {
        String name = fileName.toLowerCase(Locale.ROOT);
        String text;
        if (name.endsWith(".pdf")) {
            text = parsePdf(in);
        } else if (name.endsWith(".docx")) {
            text = parseDocx(in);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileName);
        }
        return text.replaceAll("\\b/\\w+\\b", "").replaceAll("\\s+", " ").trim();
    }

    /** Every chunk gets {@code baseMetadata} (ownerId, documentId, fileName) plus its index and length. */
    public List<Document> chunk(String text, Map<String, Object> baseMetadata) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : splitIntoSentences(text)) {
            sentence = sentence.trim();
            if (isNoise(sentence)) continue;
            if (current.length() + sentence.length() > props.chunkSize()) {
                addPiece(pieces, current.toString());
                current = new StringBuilder(tail(current.toString(), props.chunkOverlap()));
            }
            current.append(sentence).append(' ');
        }
        addPiece(pieces, current.toString());

        List<Document> chunks = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put("chunkIndex", i);
            metadata.put("length", pieces.get(i).length());
            chunks.add(new Document(pieces.get(i), metadata));
        }
        return chunks;
    }

    private static void addPiece(List<String> pieces, String text) {
        String cleaned = text.trim();
        if (cleaned.length() >= 50) {
            pieces.add(cleaned);
        }
    }

    private static boolean isNoise(String sentence) {
        return sentence.length() < 20 || PATH_LIKE.matcher(sentence).matches();
    }

    private static String tail(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(text.length() - maxChars);
    }

    private static List<String> splitIntoSentences(String text) {
        return Arrays.asList(text.split("(?<=[.!?])\\s+"));
    }

    private static String parsePdf(InputStream in) throws IOException {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(in))) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Encrypted PDFs are not supported.");
            }
            return new PDFTextStripper().getText(document);
        }
    }

    private static String parseDocx(InputStream in) throws IOException {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(in)) {
            document.getParagraphs().forEach(p -> {
                String t = p.getText();
                if (t != null && !t.isBlank()) {
                    text.append(t).append(' ');
                }
            });
        }
        return text.toString();
    }
}
