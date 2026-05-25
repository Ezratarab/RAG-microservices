package com.example.rag_service.Consumers;

import com.example.rag_service.Entity.HybridSearchResult;
import com.example.rag_service.Events.QueryEvent;
import com.example.rag_service.Service.ChatService;
import com.example.rag_service.Service.SinkRegistry;
import lombok.RequiredArgsConstructor;
import main.java.com.example.rag_service.Constants.KafkaGroups;
import main.java.com.example.rag_service.Constants.KafkaTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

//IMPLEMENTING WITH RRF (BASED ON RANK)
@Service
@RequiredArgsConstructor
public class AggregatorConsumer {

    private final ChatService chatService;
    private final SinkRegistry sinkRegistry;
    private final Map<String, List<HybridSearchResult>> keywordResults = new ConcurrentHashMap<>();
    private final Map<String, List<HybridSearchResult>> vectorResults = new ConcurrentHashMap<>();

    @KafkaListener(topics = {
            KafkaTopics.VECTOR_RESULTS,
            KafkaTopics.KEYWORD_RESULTS
    }, groupId = KafkaGroups.EMBEDDING)
    public void aggregate(QueryEvent event) {
        String id = event.getQueryId();
        System.out.println("GOT AGGREGATE QUERY");
        System.out.println("RESULTS: ");
        System.out.println(event.getResults() + "\n\n");
        if ("vector".equals(event.getSource())) {
            vectorResults.put(id, event.getResults());
        } else {
            keywordResults.put(id, event.getResults());
        }
        List<HybridSearchResult> keyword = keywordResults.get(id);
        List<HybridSearchResult> vector = vectorResults.get(id);

        if (vector == null || keyword == null) {
            return;
        }
        vectorResults.remove(id);
        keywordResults.remove(id);

        List<HybridSearchResult> merged =
                mergeAndRank(vector, keyword);
        System.out.println("Merged results: ");
        System.out.println(merged + "\n\n");
        Sinks.Many<String> sink = sinkRegistry.get(id);

        String context = merged.stream()
                .limit(5)
                .map(HybridSearchResult::getContent)
                .collect(Collectors.joining("\n\n"));
        System.out.println("CONTEXT: " + context);
        System.out.println(context + "\n\n");
        chatService.ask(context, event.getQuery())
                .doOnNext(System.out::print)
                .doOnNext(sink::tryEmitNext)
                .doOnComplete(sink::tryEmitComplete)
                .subscribe();
    }


    private List<HybridSearchResult> mergeAndRank(
            List<HybridSearchResult> vector,
            List<HybridSearchResult> keyword
    ) {

        Map<String, HybridSearchResult> map = new HashMap<>();

        // vector results
        for (HybridSearchResult r : vector) {
            r.setId(normalizeId(r.getId()));
            map.put(r.getId(), r);
        }

        // keyword results
        for (HybridSearchResult r : keyword) {
            r.setId(normalizeId(r.getId()));
            map.merge(r.getId(), r, (a, b) -> {
                a.setKeywordRank(b.getKeywordRank());
                return a;
            });
        }

        int k = 75;
        double vw = 1.0;
        double kw = 2.5;

        for (HybridSearchResult r : map.values()) {

            double kvScore = r.getVectoreRank() <= 0
                    ? 0
                    : vw * (1.0 / (k + r.getVectoreRank()));

            double kwScore = r.getKeywordRank() <= 0
                    ? 0
                    : kw * (1.0 / (k + r.getKeywordRank()));

            r.setFinalScore(kvScore + kwScore);
        }

        return map.values().stream()
                .sorted(Comparator.comparingDouble(HybridSearchResult::getFinalScore).reversed())
                .toList();
    }

    private String normalizeId(String id) {
        return id.replace("doc:", "");
    }

}