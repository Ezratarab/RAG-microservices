package com.example.rag_service.Entity;

import lombok.Data;

@Data
public class HybridSearchResult {
    private String content;
    //scores
//    private double vectoreScore;
//    private double keywordScore;
    //ranks for rrf
    private int vectoreRank;
    private int keywordRank;
    //final score
    private double finalScore;

    public HybridSearchResult(String content, int vectoreRank) {
        this.content = content;
//        this.vectoreScore = vectoreScore;
//        this.keywordScore = 0;//because I treat vectore first
        this.finalScore = 0.0;
        this.vectoreRank = vectoreRank;//because I treat vectore first
        this.keywordRank = 0;
    }
}
