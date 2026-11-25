package com.example.rag_service.Documents;

import com.example.common.events.IngestRequested;
import com.example.common.events.Topics;
import com.example.rag_service.NLP.DocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Background ingestion: parse, chunk, embed, index. Safe to run twice for the same
 * document (Kafka is at-least-once): READY documents are skipped and old chunks are
 * removed before new ones are added.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestWorker {

    private final DocumentRepository repository;
    private final FileStorage storage;
    private final DocumentParser parser;
    private final ChunkStore chunkStore;
    private final VectorStore vectorStore;

    @KafkaListener(topics = Topics.RAG_INGEST, groupId = "rag-ingest")
    public void ingest(IngestRequested request) {
        StoredDocument doc = repository.findById(request.documentId()).orElse(null);
        if (doc == null) {
            log.info("Document {} no longer exists, skipping", request.documentId());
            return;
        }
        if (doc.status() == StoredDocument.Status.READY) {
            log.info("Document {} already ingested, skipping duplicate job", doc.id());
            return;
        }

        try (InputStream in = storage.open(doc.id())) {
            String text = parser.extractText(doc.fileName(), in);
            List<Document> chunks = parser.chunk(text, Map.of(
                    "ownerId", doc.ownerId(),
                    "documentId", doc.id(),
                    "fileName", doc.fileName()));

            chunkStore.deleteByDocumentId(doc.id());
            if (!chunks.isEmpty()) {
                vectorStore.add(chunks);
            }
            repository.save(doc.ready(chunks.size()));
            log.info("Ingested document {} into {} chunks", doc.id(), chunks.size());
        } catch (Exception e) {
            log.warn("Ingestion of document {} failed", doc.id(), e);
            repository.save(doc.failed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }
}
