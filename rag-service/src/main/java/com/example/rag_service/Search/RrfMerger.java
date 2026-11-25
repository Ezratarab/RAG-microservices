package com.example.rag_service.Search;

import com.example.rag_service.Entity.HybridSearchResult;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion: score = sum over searches of weight / (K + rank).
 * Uses ranks, not raw scores, so vector and keyword results can be combined
 * even though their scores are on different scales.
 */
@Component
public class RrfMerger {

    static final int K = 75;
    static final double VECTOR_WEIGHT = 1.0;
    static final double KEYWORD_WEIGHT = 2.5;

    public List<HybridSearchResult> merge(List<HybridSearchResult> vector, List<HybridSearchResult> keyword) {
        Map<String, HybridSearchResult> byId = new LinkedHashMap<>();

        for (HybridSearchResult r : vector) {
            byId.put(normalizeId(r.getId()), copyOf(r));
        }
        for (HybridSearchResult r : keyword) {
            byId.merge(normalizeId(r.getId()), copyOf(r), (existing, kw) -> {
                existing.setKeywordRank(kw.getKeywordRank());
                return existing;
            });
        }

        for (HybridSearchResult r : byId.values()) {
            double score = 0;
            if (r.getVectorRank() > 0) score += VECTOR_WEIGHT / (K + r.getVectorRank());
            if (r.getKeywordRank() > 0) score += KEYWORD_WEIGHT / (K + r.getKeywordRank());
            r.setFinalScore(score);
        }

        return byId.values().stream()
                .sorted(Comparator.comparingDouble(HybridSearchResult::getFinalScore).reversed())
                .toList();
    }

    /** Vector store ids come without the key prefix, keyword (RediSearch) ids with it. */
    static String normalizeId(String id) {
        return id.startsWith("doc:") ? id.substring(4) : id;
    }

    private static HybridSearchResult copyOf(HybridSearchResult r) {
        return new HybridSearchResult(normalizeId(r.getId()), r.getContent(),
                r.getVectorRank(), r.getKeywordRank(), 0.0);
    }
}
