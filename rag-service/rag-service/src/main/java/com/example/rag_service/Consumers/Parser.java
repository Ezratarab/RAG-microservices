package com.example.rag_service.Consumers;

import com.example.kafka.Configuration.KafkaGroups;
import com.example.kafka.Configuration.KafkaTopics;
import com.example.rag_service.DTO.Message;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class Parser {

    @KafkaListener(topics = KafkaTopics.UPLOADED, groupId = KafkaGroups.PARSER)
    public void listen(Message message) {
        System.out.println("Received message: " + message.getTitle() + " - " + message.getContent());
    }
}
