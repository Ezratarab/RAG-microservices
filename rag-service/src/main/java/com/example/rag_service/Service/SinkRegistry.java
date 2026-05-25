package com.example.rag_service.Service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SinkRegistry {

    private final Map<String, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();

    public Sinks.Many<String> create(String queryId) {
        Sinks.Many<String> sink =
                Sinks.many().multicast().onBackpressureBuffer();

        sinks.put(queryId, sink);
        return sink;
    }

    public Sinks.Many<String> get(String queryId) {
        return sinks.get(queryId);
    }

    public void remove(String queryId) {
        sinks.remove(queryId);
    }
}