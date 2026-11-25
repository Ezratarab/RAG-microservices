package com.example.common.events;

/** Kafka topic names shared by producers and consumers. */
public final class Topics {
    private Topics() {
    }

    /** rag-service -> rag-service: a stored upload waiting to be parsed and embedded. Key = documentId. */
    public static final String RAG_INGEST = "rag.ingest";

    /** rag-service -> billing-service: one billable action. Key = userId, so a user's events stay ordered. */
    public static final String BILLING_USAGE = "billing.usage";
}
