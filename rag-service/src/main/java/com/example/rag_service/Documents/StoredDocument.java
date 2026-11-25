package com.example.rag_service.Documents;

import java.time.Instant;

/** An uploaded file and its ingestion state. Chunks in the vector store point back via metadata documentId. */
public record StoredDocument(
        String id,
        String ownerId,
        String fileName,
        String sha256,
        Status status,
        int chunkCount,
        String error,
        Instant createdAt
) {
    public enum Status { PENDING, READY, FAILED }

    public StoredDocument ready(int chunks) {
        return new StoredDocument(id, ownerId, fileName, sha256, Status.READY, chunks, null, createdAt);
    }

    public StoredDocument failed(String reason) {
        return new StoredDocument(id, ownerId, fileName, sha256, Status.FAILED, 0, reason, createdAt);
    }
}
