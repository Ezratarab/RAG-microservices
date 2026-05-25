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
public class VectorSearchConsumer {

    private final HybridSearchService hybridSearchService;
    private final KafkaTemplate<String, QueryEvent> kafkaTemplate;

    @KafkaListener(topics = KafkaTopics.SEARCH_QUERY, groupId = KafkaGroups.VECTOR_SEARCH)
    public void handle(QueryEvent event) {
        System.out.println("GOT FROM VECTOR SEARCH");

        List<HybridSearchResult> results =
                hybridSearchService.vectorSearch(event.getQuery());
        System.out.println("SEND TO AGGREGATE VECTOR SEARCH");
        kafkaTemplate.send(
                KafkaTopics.VECTOR_RESULTS,
                new QueryEvent(
                        event.getQueryId(),
                        event.getQuery(),
                        "vector",
                        results
                )
        );
    }
}
