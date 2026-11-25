package com.example.rag_service.Documents;

import com.example.rag_service.config.RagProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Keeps the original upload until ingestion is done (and for re-processing).
 * Backed by a directory (a Docker volume in compose). With several rag-service
 * instances, point it at shared storage or swap this class for S3/MinIO.
 */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(RagProperties props) {
        this.root = Path.of(props.storageDir());
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage dir " + root, e);
        }
    }

    public void save(String documentId, byte[] content) throws IOException {
        Files.write(pathFor(documentId), content);
    }

    public InputStream open(String documentId) throws IOException {
        return Files.newInputStream(pathFor(documentId));
    }

    public void delete(String documentId) throws IOException {
        Files.deleteIfExists(pathFor(documentId));
    }

    /** Only server-generated UUIDs are accepted, so a path can never escape the storage dir. */
    private Path pathFor(String documentId) {
        return root.resolve(UUID.fromString(documentId).toString());
    }
}
