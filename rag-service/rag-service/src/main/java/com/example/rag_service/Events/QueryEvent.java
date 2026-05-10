package com.example.rag_service.Events;

import com.example.rag_service.Entity.HybridSearchResult;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QueryEvent {
    private String queryId;
    private String query;
    private String source; // vector or keyword
    private List<HybridSearchResult> results;
}
