package com.example.rag_service.Service;

import com.example.rag_service.Billing.UsagePublisher;
import com.example.rag_service.Entity.HybridSearchResult;
import com.example.rag_service.NLP.NlpProcessor;
import com.example.rag_service.Search.RrfMerger;
import com.example.rag_service.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Answers a question in one stateless call: vector + keyword search in parallel,
 * merge with RRF, stream the LLM answer. Any instance can serve any request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    static final String NOT_FOUND = "I couldn't find that in your documents.";
    static final String FAILED = "[error] Sorry, I couldn't answer right now. Please try again.";

    private final VectorStore vectorStore;
    private final KeywordSearchService keywordSearchService;
    private final NlpProcessor nlpProcessor;
    private final RrfMerger rrfMerger;
    private final ChatService chatService;
    private final UsagePublisher usagePublisher;
    private final RagProperties props;

    public Flux<String> search(String query, String ownerId) {
        String queryId = UUID.randomUUID().toString();
        log.info("RAG query start queryId={} owner={}", queryId, ownerId);

        Mono<List<HybridSearchResult>> vector = Mono.fromCallable(() -> vectorSearch(query, ownerId))
                .subscribeOn(Schedulers.boundedElastic());
        Mono<List<HybridSearchResult>> keyword = Mono.fromCallable(() -> keywordSearch(query, ownerId))
                .subscribeOn(Schedulers.boundedElastic());

        return Mono.zip(vector, keyword)
                .timeout(props.searchTimeout())
                .map(both -> rrfMerger.merge(both.getT1(), both.getT2()))
                .flatMapMany(merged -> {
                    log.debug("queryId={} merged {} chunks", queryId, merged.size());
                    if (merged.isEmpty()) {
                        return Flux.just(NOT_FOUND);
                    }
                    List<HybridSearchResult> top = merged.stream().limit(props.contextChunks()).toList();
                    return chatService.ask(top, query)
                            // Bill only answers that actually completed.
                            .doOnComplete(() -> usagePublisher.publish(ownerId, "RAG_QUERY"));
                })
                // Fails if the model goes silent for too long between tokens.
                .timeout(props.answerIdleTimeout())
                .doOnComplete(() -> log.info("RAG query done queryId={}", queryId))
                .onErrorResume(e -> {
                    log.error("RAG query failed queryId={}", queryId, e);
                    return Flux.just(FAILED);
                });
    }

    List<HybridSearchResult> vectorSearch(String query, String ownerId) {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(props.topK())
                        .filterExpression(new FilterExpressionBuilder().eq("ownerId", ownerId).build())
                        .build());
        if (docs == null) {
            return List.of();
        }
        List<HybridSearchResult> results = new ArrayList<>(docs.size());
        int rank = 1;
        for (Document doc : docs) {
            results.add(HybridSearchResult.vector(doc.getId(), doc.getText(), rank++));
        }
        return results;
    }

    List<HybridSearchResult> keywordSearch(String query, String ownerId) {
        return keywordSearchService.search(nlpProcessor.cleanText(query), ownerId);
    }
}
