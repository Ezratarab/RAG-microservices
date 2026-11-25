package com.example.rag_service.Search;

import com.example.rag_service.Entity.HybridSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfMergerTest {

    private final RrfMerger merger = new RrfMerger();

    @Test
    void chunkFoundByBothSearchesRanksAboveChunksFoundByOne() {
        List<HybridSearchResult> vector = List.of(
                HybridSearchResult.vector("a", "only vector", 1),
                HybridSearchResult.vector("both", "in both", 2));
        List<HybridSearchResult> keyword = List.of(
                HybridSearchResult.keyword("doc:k", "only keyword", 1),
                HybridSearchResult.keyword("doc:both", "in both", 2));

        List<HybridSearchResult> merged = merger.merge(vector, keyword);

        assertThat(merged).extracting(HybridSearchResult::getId).first().isEqualTo("both");
        assertThat(merged).hasSize(3);
    }

    @Test
    void keywordPrefixIsNormalizedSoTheSameChunkIsMerged() {
        List<HybridSearchResult> merged = merger.merge(
                List.of(HybridSearchResult.vector("x", "c", 1)),
                List.of(HybridSearchResult.keyword("doc:x", "c", 1)));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getVectorRank()).isEqualTo(1);
        assertThat(merged.get(0).getKeywordRank()).isEqualTo(1);
    }

    @Test
    void keywordMatchesWeighMoreThanVectorMatchesAtTheSameRank() {
        List<HybridSearchResult> merged = merger.merge(
                List.of(HybridSearchResult.vector("v", "c", 1)),
                List.of(HybridSearchResult.keyword("doc:k", "c", 1)));

        assertThat(merged.get(0).getId()).isEqualTo("k");
    }

    @Test
    void emptyInputsGiveEmptyResult() {
        assertThat(merger.merge(List.of(), List.of())).isEmpty();
    }
}
