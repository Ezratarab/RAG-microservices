package com.example.rag_service.Service;

import com.example.rag_service.Entity.HybridSearchResult;
import com.example.rag_service.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.SearchResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordSearchService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JedisPooled jedis;
    private final RagProperties props;

    /**
     * Full-text search limited to the caller's own chunks.
     *
     * @param cleanedQuery words already reduced to [a-z]{2,} by NlpProcessor, so they carry no query syntax
     */
    public List<HybridSearchResult> search(String cleanedQuery, String ownerId) {
        String words = Arrays.stream(cleanedQuery.split(" "))
                .filter(w -> !w.isBlank())
                .collect(Collectors.joining("|"));
        if (words.isEmpty()) {
            return List.of();
        }
        String query = "@ownerId:{" + escapeTag(ownerId) + "} @content:(" + words + ")";

        SearchResult result = jedis.ftSearch(props.indexName(), query,
                FTSearchParams.searchParams().limit(0, props.topK()));

        List<HybridSearchResult> hits = new ArrayList<>();
        int rank = 1;
        for (Document doc : result.getDocuments()) {
            String content = extractContent(doc);
            if (content != null) {
                hits.add(HybridSearchResult.keyword(doc.getId(), content, rank++));
            }
        }
        return hits;
    }

    /** RediSearch TAG values must escape punctuation (UUIDs contain '-'). */
    public static String escapeTag(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

    private String extractContent(Document doc) {
        Object raw = doc.get("$");
        if (raw == null) return null;
        try {
            JsonNode node = MAPPER.readTree(raw.toString());
            JsonNode content = node.get("content");
            return content == null ? null : content.asText();
        } catch (Exception e) {
            log.warn("Could not read chunk {}", doc.getId(), e);
            return null;
        }
    }
}
