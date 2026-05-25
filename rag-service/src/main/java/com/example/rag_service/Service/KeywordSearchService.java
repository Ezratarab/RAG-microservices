package com.example.rag_service.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.SearchResult;

import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KeywordSearchService {

    private final JedisPooled jedis;

    public SearchResult search(String query) {
        String redisQuery =
                "@content:(" +
                        Arrays.stream(query.split(" "))
                                .map(String::trim)
                                .filter(word -> !word.isEmpty())
                                .collect(Collectors.joining("|")) +
                        ")";
        return jedis.ftSearch("rag-index", redisQuery, FTSearchParams.searchParams().withScores()
                .limit(0, 50));
    }

    public String extractContent(Document doc) {
        Object raw = doc.get("$");
        if (raw == null) return null;

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(raw.toString());
            System.out.println(node.get("content").asText() + "\n***********\n");

            return node.get("content").asText();
        } catch (Exception e) {
            return null;
        }
    }
}
