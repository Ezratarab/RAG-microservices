package com.example.rag_service.NLP;

import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class NlpProcessor {

    private final TokenizerME tokenizer;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "am",
            "and", "or", "but", "how", "what", "why", "who",
            "does", "do", "did", "it", "this", "that",
            "to", "of", "in", "on", "for", "with", "as", "at", "by"
    );

    public NlpProcessor() {
        try (InputStream modelStream =
                     getClass().getClassLoader()
                             .getResourceAsStream("models/en-token.bin")) {

            TokenizerModel model = new TokenizerModel(modelStream);
            this.tokenizer = new TokenizerME(model);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load OpenNLP model", e);
        }
    }

    public List<String> extractMeaningfulWords(String text) {
        String[] tokens = tokenizer.tokenize(text);

        return Arrays.stream(tokens)
                .map(String::toLowerCase)
                .filter(token -> token.matches("[a-zA-Z]{2,}"))// remove punctuation
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toList());
    }

    public String cleanText(String text) {
        return String.join(" ", extractMeaningfulWords(text));
    }

    public List<String> tokenize(String text) {
        int size = 300;
        String[] words = text.split("\\s+");

        List<String> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();

        for (String word : words) {
            chunk.append(word);
            if (chunk.length() >= size) {
                chunks.add(chunk.toString().strip());
                chunk = new StringBuilder();
            }
        }
        return chunks;
    }
}