package com.example.rag_service.Service;

import com.example.rag_service.Entity.HybridSearchResult;
import com.example.rag_service.NLP.NlpProcessor;
import com.example.rag_service.NLP.PdfParser;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import redis.clients.jedis.search.SearchResult;

import java.util.*;
import java.util.stream.Collectors;

//BEFORE KAFKA

//ALL THE COMMENTS ARE FOR CALCULATING RAG WITH SCORE FUSION
//NOW IM IMPLEMENTING WITH RRF (BASED ON RANK)
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final VectorStore vectorStore;
    private final KeywordSearchService keywordSearchService;
    private final ChatService chatService;


    private final NlpProcessor nlpProcessor = new NlpProcessor();

    public Flux<String> hybridSearch(String query) throws Exception {
        return Flux.defer(() -> {
            System.out.println("QUERY: " + query);
            long threadId = Thread.currentThread().getId();
            System.out.println("THREAD ID: " + threadId);
            Map<String, HybridSearchResult> merged = new HashMap<>();
//        System.out.println("INITIAL MEGED: " + merged);
            // ===== Vector Search =====
            List<Document> vectorResults =
                    vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query(query)
                                    .topK(50)
                                    .build()
                    );
//        System.out.println("VECTORE RESULTS: " + vectorResults);
            assert vectorResults != null;
            int i = 1;
            for (Document doc : vectorResults) {
                String key = doc.getText();
                merged.put(key, new HybridSearchResult(
                        doc.getText(),
                        //doc.getScore(),
                        i
                ));
                i++;
            }
//        System.out.println("MERGED AFTER VECTOR: " + merged);

            // ===== Keyword Search =====
            String cleaned = nlpProcessor.cleanText(query);
//        System.out.println("CLEANED QUERY: " + cleaned);
            SearchResult keywordResults =
                    keywordSearchService.search(cleaned);
            i = 1;
            for (redis.clients.jedis.search.Document doc :
                    keywordResults.getDocuments()) {

                String content = keywordSearchService.extractContent(doc);
                if (!merged.containsKey(content)) {
                    merged.put(content, new HybridSearchResult(content, 0));
                }
                HybridSearchResult hybridSearchResult = merged.get(content);
                //hybridSearchResult.setKeywordScore(doc.getScore());
                hybridSearchResult.setKeywordRank(i);
                merged.put(content, hybridSearchResult);
                i++;
            }
            List<HybridSearchResult> results =
                    new ArrayList<>(merged.values());
//        System.out.println("KEYWORD results: " + keywordResults);
//        for (HybridSearchResult hybridSearchResult : results) {
//            System.out.println(hybridSearchResult.getKeywordScore());
//        }
//        System.out.println("MERGED AFTER KEYWORD: " + results);
//        double maxKeyword = results.stream()
//                .mapToDouble(HybridSearchResult::getKeywordScore)
//                .max()
//                .orElse(1.0);
//        System.out.println("MAX KEYWORD: " + maxKeyword);
            for (HybridSearchResult r : results) {
                int k = 68;
                double vectorWeight = 1.7;
                double keywordWeight = 1.0;
//            double vector = Optional.ofNullable(r.getVectoreScore()).orElse(0.0);
//            double keyword = Optional.ofNullable(r.getKeywordScore()).orElse(0.0);
//            double normalizedKeyword = maxKeyword == 0
//                    ? 0
//                    : keyword / maxKeyword;
                double finalVector = r.getVectoreRank() == 0 ? 0 : vectorWeight * (1.0 / (k + r.getVectoreRank()));
                double finalKeyword = r.getKeywordRank() == 0 ? 0 : keywordWeight * (1.0 / (k + r.getKeywordRank()));
//            double finalScore =
//                    (0.7 * vector)
//                            + (0.3 * normalizedKeyword * normalizedKeyword);

                double finalScore = finalVector + finalKeyword;
                r.setFinalScore(finalScore);
            }
            results = results.stream()
                    .sorted(Comparator.comparingDouble(HybridSearchResult::getFinalScore).reversed())
                    .toList();
//        System.out.println("END results: " + results);
            String context = results.stream()
                    .limit(3)
                    .map(HybridSearchResult::getContent)
                    .collect(Collectors.joining("\n\n"));
            System.out.println("START\n");
            ClassPathResource resource =
                    new ClassPathResource("docs/resume.pdf");

            //System.out.println(pdfParser.parse(resource.getFile().getAbsolutePath()));
            chatService.ask(context, query).doOnNext(System.out::print).blockLast();
            return chatService.ask(context, query);
        }).subscribeOn(Schedulers.boundedElastic());
    }


}