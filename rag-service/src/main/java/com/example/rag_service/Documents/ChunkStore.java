package com.example.rag_service.Documents;

import com.example.rag_service.config.RagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.SearchResult;

import static com.example.rag_service.Service.KeywordSearchService.escapeTag;

/** Chunk-level operations the VectorStore API doesn't cover. */
@Component
@RequiredArgsConstructor
public class ChunkStore {

    private static final int BATCH = 1000;

    private final JedisPooled jedis;
    private final RagProperties props;

    /** Removes every chunk of a document (used on delete and before re-ingesting). */
    public void deleteByDocumentId(String documentId) {
        String query = "@documentId:{" + escapeTag(documentId) + "}";
        while (true) {
            SearchResult result = jedis.ftSearch(props.indexName(), query,
                    FTSearchParams.searchParams().noContent().limit(0, BATCH));
            if (result.getDocuments().isEmpty()) {
                return;
            }
            String[] keys = result.getDocuments().stream()
                    .map(redis.clients.jedis.search.Document::getId)
                    .toArray(String[]::new);
            if (jedis.del(keys) == 0) {
                return;   // nothing removed: stop instead of looping on a stale index
            }
        }
    }
}
