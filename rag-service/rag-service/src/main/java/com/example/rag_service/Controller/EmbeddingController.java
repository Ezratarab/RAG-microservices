package com.example.rag_service.Controller;

import com.example.rag_service.DTO.Message;
import com.example.rag_service.NLP.PdfParser;
import com.example.rag_service.Publishers.stam;
import com.example.rag_service.Service.HybridSearchService;
import lombok.AllArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@AllArgsConstructor
public class EmbeddingController {

    private final HybridSearchService hybridSearchService;
    private final PdfParser pdfParser;
    private final stam stam;


    @PostMapping(value = "/ask",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> ask(@RequestBody Map<String, String> body) throws Exception {
        return hybridSearchService.hybridSearch(body.get("query"));
    }

    @PostMapping(value = "/a")
    public void a(@RequestBody Map<String, String> message) throws Exception {
        Message message1 = new Message();
        message1.setTitle(message.get("title"));
        message1.setContent(message.get("content"));
        stam.sendMessage(message1);
    }

    @PostMapping(value = "/upload")
    @ResponseStatus(HttpStatus.OK)
    public void upload(@RequestParam("files") List<MultipartFile> files) throws Exception {
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename();
            if (name != null) {
                log.info("Uploading file {}", file.getOriginalFilename());
                pdfParser.uploadFile(file.getOriginalFilename(), file.getInputStream());
            }
        }
    }
}