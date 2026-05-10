package com.example.rag_service.Consumers;

import com.example.kafka.Configuration.KafkaGroups;
import com.example.kafka.Configuration.KafkaTopics;
import com.example.rag_service.Entity.HybridSearchResult;
import com.example.rag_service.Events.QueryEvent;
import com.example.rag_service.Service.HybridSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KeywordSearchConsumer {

    private final HybridSearchService hybridSearchService;
    private final KafkaTemplate<String, QueryEvent> kafkaTemplate;

    @KafkaListener(topics = KafkaTopics.SEARCH_QUERY, groupId = KafkaGroups.KEYWORD_SEARCH)
    public void handle(QueryEvent event) {
        System.out.println("GOT FROM KEYWORD SEARCH");

        List<HybridSearchResult> results =
                hybridSearchService.keywordSearch(event.getQuery());
        System.out.println("SEND TO AGGREGATE KEYWORD SEARCH");

        kafkaTemplate.send(
                KafkaTopics.KEYWORD_RESULTS,
                new QueryEvent(
                        event.getQueryId(),
                        event.getQuery(),
                        "keyword",
                        results
                )
        );
    }
}
