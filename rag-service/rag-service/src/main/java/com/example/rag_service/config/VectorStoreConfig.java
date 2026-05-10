package com.example.rag_service.config;

import jakarta.annotation.PostConstruct;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Primary;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPooled;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;
    @Value("${spring.data.redis.port}")
    private int redisPort;
    @Value("${spring.data.redis.jedis.pool.max-active}")
    private int maxConnections;
    @Value("${spring.data.redis.jedis.pool.timeout:2000}")
    private int socketTimeout;

    @PostConstruct
    public void debug() {
        System.out.println("REDIS HOST = " + redisHost);
        System.out.println("REDIS PORT = " + redisPort);
    }

    @Bean
    public JedisPooled jedisPooled() {
        GenericObjectPoolConfig<Connection> pooledConfig = new GenericObjectPoolConfig<>();
        pooledConfig.setMaxTotal(maxConnections);

        return new JedisPooled(redisHost, redisPort);
    }


    @Bean(name = "vectorStore")
    @Primary
    public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("rag-index")
                .prefix("doc:")
                .initializeSchema(true)
                .build();
    }
}