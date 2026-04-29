package com.example.rag_service.Service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public Flux<String> ask(String context, String query) {
        String prompt = """
                You are a helpful AI assistant.
                
                Use the provided context to answer the user's question.
                
                If the context contains relevant information, summarize the top 3 most relevant facts.
                
                If the answer is not clearly found, generate the best possible summary using the available context.
                
                Do not invent facts outside the context.
                
                Context:
                %s
                
                Question:
                %s
                """.formatted(context, query);

        return chatClient.prompt()
                .system("You are a precise factual assistant.")
                .user(prompt)
                .stream()
                .content();
    }
}