package com.example.rag_service.Events;

import com.example.rag_service.Entity.HybridSearchResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PartialResultEvent {
    private List<HybridSearchResult> vector;
    private List<HybridSearchResult> keyword;
}
