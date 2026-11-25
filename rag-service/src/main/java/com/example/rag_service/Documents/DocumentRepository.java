package com.example.rag_service.Documents;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import redis.clients.jedis.JedisPooled;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Document records in Redis. Keys use the "ragdoc:" prefix, which is outside the
 * vector index prefix ("doc:"), so records never show up in searches.
 */
@Repository
@RequiredArgsConstructor
public class DocumentRepository {

    private static final String KEY = "ragdoc:";
    private static final String SHA_KEY = "ragdoc:sha:";

    private final JedisPooled jedis;

    public void save(StoredDocument d) {
        Map<String, String> fields = new HashMap<>();
        fields.put("id", d.id());
        fields.put("ownerId", d.ownerId());
        fields.put("fileName", d.fileName());
        fields.put("sha256", d.sha256());
        fields.put("status", d.status().name());
        fields.put("chunkCount", Integer.toString(d.chunkCount()));
        fields.put("error", d.error() == null ? "" : d.error());
        fields.put("createdAt", d.createdAt().toString());
        jedis.hset(KEY + d.id(), fields);
        jedis.set(shaKey(d.ownerId(), d.sha256()), d.id());
    }

    public Optional<StoredDocument> findById(String id) {
        Map<String, String> f = jedis.hgetAll(KEY + id);
        if (f == null || f.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new StoredDocument(
                f.get("id"),
                f.get("ownerId"),
                f.get("fileName"),
                f.get("sha256"),
                StoredDocument.Status.valueOf(f.get("status")),
                Integer.parseInt(f.getOrDefault("chunkCount", "0")),
                f.getOrDefault("error", "").isEmpty() ? null : f.get("error"),
                Instant.parse(f.get("createdAt"))));
    }

    /** Same owner + same file content = same document (idempotent upload). */
    public Optional<StoredDocument> findByOwnerAndSha(String ownerId, String sha256) {
        String id = jedis.get(shaKey(ownerId, sha256));
        return id == null ? Optional.empty() : findById(id);
    }

    public void delete(StoredDocument d) {
        jedis.del(KEY + d.id(), shaKey(d.ownerId(), d.sha256()));
    }

    private static String shaKey(String ownerId, String sha256) {
        return SHA_KEY + ownerId + ":" + sha256;
    }
}
