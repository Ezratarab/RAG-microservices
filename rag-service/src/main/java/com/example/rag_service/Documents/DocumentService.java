package com.example.rag_service.Documents;

import com.example.common.events.IngestRequested;
import com.example.common.events.Topics;
import com.example.rag_service.NLP.DocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Upload is fast: store the file, record it as PENDING, queue an ingest job, return.
 * The slow part (parse + embed) runs in {@link IngestWorker}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository repository;
    private final FileStorage storage;
    private final ChunkStore chunkStore;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public StoredDocument upload(String ownerId, MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (!DocumentParser.isSupported(fileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .pdf and .docx files are supported");
        }
        byte[] bytes = file.getBytes();
        String sha256 = DigestUtils.sha256Hex(bytes);

        // Idempotent: the same file uploaded twice by the same user is the same document.
        var existing = repository.findByOwnerAndSha(ownerId, sha256);
        if (existing.isPresent()) {
            log.info("Upload of {} matches existing document {}", fileName, existing.get().id());
            return existing.get();
        }

        String id = UUID.randomUUID().toString();
        storage.save(id, bytes);
        StoredDocument doc = new StoredDocument(id, ownerId, fileName, sha256,
                StoredDocument.Status.PENDING, 0, null, Instant.now());
        repository.save(doc);
        kafkaTemplate.send(Topics.RAG_INGEST, id, new IngestRequested(id));
        log.info("Queued document {} ({}) for ingestion", id, fileName);
        return doc;
    }

    /** Returns the document only if the caller owns it; otherwise 404 (don't reveal it exists). */
    public StoredDocument get(String ownerId, String id) {
        return repository.findById(parseId(id))
                .filter(d -> d.ownerId().equals(ownerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public void delete(String ownerId, String id) throws IOException {
        StoredDocument doc = get(ownerId, id);
        chunkStore.deleteByDocumentId(doc.id());
        storage.delete(doc.id());
        repository.delete(doc);
        log.info("Deleted document {}", doc.id());
    }

    private static String parseId(String id) {
        try {
            return UUID.fromString(id).toString();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
