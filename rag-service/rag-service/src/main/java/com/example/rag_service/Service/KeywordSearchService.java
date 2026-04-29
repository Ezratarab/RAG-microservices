package com.example.rag_service.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.SearchResult;

import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class KeywordSearchService {

    private final JedisPooled jedis = new JedisPooled("localhost", 6379);

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
        try {
            Object jsonObj = doc.get("$");

            if (jsonObj != null) {
                String jsonString = jsonObj.toString();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(jsonString);

                return node.get("content").asText();
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return "doesnt exist";
    }
}
