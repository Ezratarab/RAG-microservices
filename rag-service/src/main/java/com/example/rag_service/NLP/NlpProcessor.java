package com.example.rag_service.NLP;

import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class NlpProcessor {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "am",
            "and", "or", "but", "how", "what", "why", "who",
            "does", "do", "did", "it", "this", "that",
            "to", "of", "in", "on", "for", "with", "as", "at", "by"
    );

    private final TokenizerME tokenizer;

    public NlpProcessor() {
        try (InputStream modelStream = getClass().getClassLoader().getResourceAsStream("models/en-token.bin")) {
            if (modelStream == null) {
                throw new IllegalStateException("models/en-token.bin not found on classpath");
            }
            this.tokenizer = new TokenizerME(new TokenizerModel(modelStream));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load OpenNLP model", e);
        }
    }

    /**
     * Lower-cased words of 2+ letters, without stop words. Because only [a-z] survives,
     * the result is also safe to put into a RediSearch query (no query-syntax characters).
     */
    public List<String> extractMeaningfulWords(String text) {
        return Arrays.stream(tokenizer.tokenize(text))
                .map(String::toLowerCase)
                .filter(token -> token.matches("[a-z]{2,}"))
                .filter(token -> !STOP_WORDS.contains(token))
                .toList();
    }

    public String cleanText(String text) {
        return String.join(" ", extractMeaningfulWords(text));
    }
}
