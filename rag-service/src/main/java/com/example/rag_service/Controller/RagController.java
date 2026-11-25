package com.example.rag_service.Controller;

import com.example.rag_service.Documents.DocumentService;
import com.example.rag_service.Documents.StoredDocument;
import com.example.rag_service.Service.HybridSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Instant;

/**
 * Every operation is scoped to the caller (JWT "sub"): users only search and manage
 * their own documents.
 */
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagController {

    private final HybridSearchService hybridSearchService;
    private final DocumentService documentService;

    public record AskRequest(@NotBlank @Size(max = 2000) String query) {
    }

    public record DocumentResponse(String id, String fileName, StoredDocument.Status status,
                                   int chunkCount, String error, Instant createdAt) {
        static DocumentResponse from(StoredDocument d) {
            return new DocumentResponse(d.id(), d.fileName(), d.status(), d.chunkCount(), d.error(), d.createdAt());
        }
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> ask(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AskRequest request) {
        return hybridSearchService.search(request.query(), jwt.getSubject());
    }

    /** Returns at once with status PENDING; poll GET /rag/documents/{id} until READY or FAILED. */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse upload(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam("file") MultipartFile file) throws IOException {
        return DocumentResponse.from(documentService.upload(jwt.getSubject(), file));
    }

    @GetMapping("/documents/{id}")
    public DocumentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return DocumentResponse.from(documentService.get(jwt.getSubject(), id));
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) throws IOException {
        documentService.delete(jwt.getSubject(), id);
    }
}
