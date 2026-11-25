package com.example.rag_service.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.time.Duration;

@Configuration
public class VectorStoreConfig {

    /** Key prefix of every chunk stored by the vector store. */
    public static final String CHUNK_PREFIX = "doc:";

    @Bean
    public JedisPooled jedisPooled(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.timeout:2s}") Duration timeout,
            RagProperties props) {

        GenericObjectPoolConfig<Connection> pool = new GenericObjectPoolConfig<>();
        pool.setMaxTotal(props.redisMaxConnections());   // previously configured but never passed to Jedis

        DefaultJedisClientConfig.Builder client = DefaultJedisClientConfig.builder()
                .socketTimeoutMillis((int) timeout.toMillis());
        if (StringUtils.hasText(password)) {
            client.password(password);
        }
        return new JedisPooled(new HostAndPort(host, port), client.build(), pool);
    }

    @Bean
    @Primary
    public VectorStore vectorStore(JedisPooled jedis, EmbeddingModel embeddingModel, RagProperties props) {
        return RedisVectorStore.builder(jedis, embeddingModel)
                .indexName(props.indexName())
                .prefix(CHUNK_PREFIX)
                .initializeSchema(true)
                .contentFieldName("content")
                .embeddingFieldName("embedding")
                // Indexed metadata so searches and deletes can filter by owner/document.
                .metadataFields(
                        MetadataField.tag("ownerId"),
                        MetadataField.tag("documentId"),
                        MetadataField.text("fileName"))
                .build();
    }
}
