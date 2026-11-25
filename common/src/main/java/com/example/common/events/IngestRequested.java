package com.example.common.events;

/** Asks the ingestion worker to process a stored document. */
public record IngestRequested(String documentId) {
}
