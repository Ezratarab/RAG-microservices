package com.example.rag_service.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One chunk found by vector search, keyword search, or both. Ranks start at 1; 0 = not found by that search. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HybridSearchResult {
    private String id;
    private String content;
    private int vectorRank;
    private int keywordRank;
    private double finalScore;

    public static HybridSearchResult vector(String id, String content, int rank) {
        return new HybridSearchResult(id, content, rank, 0, 0.0);
    }

    public static HybridSearchResult keyword(String id, String content, int rank) {
        return new HybridSearchResult(id, content, 0, rank, 0.0);
    }
}
