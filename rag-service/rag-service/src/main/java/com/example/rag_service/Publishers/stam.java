package com.example.rag_service.Publishers;

import com.example.kafka.Configuration.KafkaTopics;
import com.example.rag_service.DTO.Message;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class stam {
    private final KafkaTemplate<String, Message> kafkaTemplate;

    public void sendMessage(Message message) {
        kafkaTemplate.send(KafkaTopics.UPLOADED, message);
    }
}
