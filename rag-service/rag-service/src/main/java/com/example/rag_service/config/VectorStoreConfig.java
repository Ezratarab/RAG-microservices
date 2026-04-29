package com.example.rag_service.config;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPooled;

import java.lang.reflect.Field;

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

    @Bean
    public JedisPooled jedisPooled() {
        GenericObjectPoolConfig<Connection> pooledConfig = new GenericObjectPoolConfig<>();
        pooledConfig.setMaxTotal(maxConnections);

        return new JedisPooled(pooledConfig, redisHost, redisPort, socketTimeout);
    }
    

    @Bean
    public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("rag-index")
                .prefix("doc:")
                .initializeSchema(true)
                .build();
    }
}