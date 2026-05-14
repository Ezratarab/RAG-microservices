package com.example.rag_service.Service;

import com.example.kafka.Configuration.KafkaTopics;
import com.example.rag_service.Entity.HybridSearchResult;
import com.example.rag_service.Events.QueryEvent;
import com.example.rag_service.NLP.NlpProcessor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import redis.clients.jedis.search.SearchResult;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {
    @PostConstruct
    public void debug() {
        System.out.println(vectorStore.getClass());
    }

    private final VectorStore vectorStore;
    private final KeywordSearchService keywordSearchService;

    private final NlpProcessor nlpProcessor;
    private final SinkRegistry sinkRegistry;
    private final KafkaTemplate<String, QueryEvent> kafkaTemplate;

    public Flux<String> search(String query) {
        System.out.println("START SEARCH");
        String queryId = UUID.randomUUID().toString();
        Sinks.Many<String> sink = sinkRegistry.create(queryId);
        QueryEvent event = QueryEvent.builder()
                .queryId(queryId)
                .query(query)
                .build();
        System.out.println("SEND TO VECTOR SEARCH");
        kafkaTemplate.send(
                KafkaTopics.SEARCH_QUERY,
                event
        );
        return sink.asFlux()
                .doFinally(signal -> sinkRegistry.remove(queryId));
    }

    public List<HybridSearchResult> vectorSearch(String query) {
        System.out.println("START VECTOR");

        List<HybridSearchResult> results = new ArrayList<>();
        List<Document> vectorResults =
                vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(50)
                                .build()
                );
        assert vectorResults != null;
        int i = 1;
        for (Document doc : vectorResults) {
            HybridSearchResult result =
                    new HybridSearchResult(doc.getText(), i++);
            result.setId(
                    doc.getId()
            );
            results.add(result);
            log.info("VECTOR RESULT | doc={} | rank={}", doc.getText(), i);
        }
        return results;
    }

    public List<HybridSearchResult> keywordSearch(String query) {
        System.out.println("START KEYWORD");

        List<HybridSearchResult> results = new ArrayList<>();

        String cleaned = nlpProcessor.cleanText(query);
        SearchResult keywordResults =
                keywordSearchService.search(cleaned);
        int i = 1;
        for (redis.clients.jedis.search.Document doc :
                keywordResults.getDocuments()) {

            String content = keywordSearchService.extractContent(doc);

            HybridSearchResult hybridSearchResult = new HybridSearchResult(content, 0);
            hybridSearchResult.setId(doc.getId());
            hybridSearchResult.setKeywordRank(i);
            results.add(hybridSearchResult);
            i++;
        }
        return results;
    }

}