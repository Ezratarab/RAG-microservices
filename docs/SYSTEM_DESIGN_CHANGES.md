# System design: your code now and what I'll change

Each section has three parts:
- **Now:** the real code from this repo, shortened with `...`.
- **After:** what I'd change it to.
- **Why:** the reason behind the change.

> **Status: implemented** on branch `refactor/system-design`. "Now" shows the code as it was before that change.
> Where the final code differs from a sketch here (for example, document records are stored in Redis instead of a JPA table, and uploads go to a Docker volume instead of S3), the code is the reference.

---

## D1 + D2 + D3: the RAG query path (Kafka request/response with state held in memory)

### Now
```java
// HybridSearchService.search
String queryId = UUID.randomUUID().toString();
Sinks.Many<String> sink = sinkRegistry.create(queryId);          // state in THIS instance's memory
kafkaTemplate.send(KafkaTopics.SEARCH_QUERY, event);             // no key
return sink.asFlux().doFinally(signal -> sinkRegistry.remove(queryId));
```
```java
// VectorSearchConsumer / KeywordSearchConsumer
@KafkaListener(topics = KafkaTopics.SEARCH_QUERY, groupId = KafkaGroups.VECTOR_SEARCH)
public void handle(QueryEvent event) {
    List<HybridSearchResult> results = hybridSearchService.vectorSearch(event.getQuery());
    kafkaTemplate.send(KafkaTopics.VECTOR_RESULTS, new QueryEvent(...));   // no key
}
```
```java
// AggregatorConsumer
private final Map<String, List<HybridSearchResult>> keywordResults = new ConcurrentHashMap<>();
private final Map<String, List<HybridSearchResult>> vectorResults  = new ConcurrentHashMap<>();
...
if (vector == null || keyword == null) { return; }        // waits forever if one never arrives
...
Sinks.Many<String> sink = sinkRegistry.get(id);            // null on another instance or after a restart
chatService.ask(context, event.getQuery())
        .doOnNext(sink::tryEmitNext)                        // NullPointerException if sink is null
        .doOnComplete(sink::tryEmitComplete)                // no doOnError, so errors are lost
        .subscribe();
```
```properties
spring.kafka.consumer.auto-offset-reset=earliest            # old queries are replayed on restart
```

### After
```java
// HybridSearchService: one method, no Kafka, no shared state
public Flux<String> search(String query, String ownerId) {
    Mono<List<HybridSearchResult>> vector = Mono.fromCallable(() -> vectorSearch(query, ownerId))
            .subscribeOn(Schedulers.boundedElastic());
    Mono<List<HybridSearchResult>> keyword = Mono.fromCallable(() -> keywordSearch(query, ownerId))
            .subscribeOn(Schedulers.boundedElastic());

    return Mono.zip(vector, keyword)                     // both searches run in parallel
            .timeout(Duration.ofSeconds(5))              // D2: never wait forever
            .map(t -> rrfMerger.merge(t.getT1(), t.getT2()))
            .flatMapMany(merged -> chatService.ask(buildContext(merged), query))
            .timeout(Duration.ofSeconds(90))
            .onErrorResume(e -> {
                log.error("RAG query failed", e);
                return Flux.just("[error] Sorry, I couldn't answer right now.");
            });
}
```
```java
// RrfMerger: the mergeAndRank() logic moved out of AggregatorConsumer, unchanged, so it's unit-testable
@Component
public class RrfMerger {
    private static final int K = 75;
    private static final double VECTOR_WEIGHT = 1.0, KEYWORD_WEIGHT = 2.5;
    public List<HybridSearchResult> merge(List<HybridSearchResult> vector, List<HybridSearchResult> keyword) { ... }
}
```
**Delete:** `SinkRegistry`, `AggregatorConsumer`, `VectorSearchConsumer`, `KeywordSearchConsumer`, `QueryEvent.results`/`source`, the `rag.search`/`rag.*.results` topics and `PartialResultEvent`.

### Why
- **Stateless services:** any instance can now serve any request, because nothing about the request lives outside the call itself.
- **Kafka is for asynchronous work.** It isn't a good fit when a user is waiting for an answer.
- **Timeouts and error handling:** every wait has a time limit, and errors reach the client instead of disappearing.
- **No replays:** with no topic on the query path, there's nothing to replay or deduplicate.

---

## D4 + D5: signup can fail halfway, and user data lives in two places

### Now
```java
// UserService.createUser (after the security fix)
User user = User.builder().phone(...).username(...).email(...)
        .password(passwordEncoder.encode(userRequest.getPassword())).build();
String userUUID = keycloakService.createUserInKeycloak(user, userRequest.getPassword()).block(); // step 1: Keycloak
user.setUUID(userUUID);
userRepository.save(user);                                                                        // step 2: Oracle
keycloakService.assignClientRole(userUUID, keycloakService.client_uuid).block();                  // step 3: Keycloak
```
```java
// User entity: duplicates what Keycloak already owns
private String username;  private String phone;  private String email;  private String password;
```

### After
```java
@Transactional
public void createUser(UserDTO req) {
    String keycloakId = keycloakService.createUserInKeycloak(req).block();   // Keycloak owns credentials and profile
    try {
        keycloakService.assignClientRole(keycloakId, clientUuid).block();
        userRepository.save(User.builder().keycloakId(keycloakId).build());  // only our own app data
    } catch (Exception e) {
        keycloakService.deleteUser(keycloakId).block();                      // compensation: undo step 1
        throw e;
    }
}
```
```java
@Entity @Table(name = "AUTH_USERS")
public class User {
    @Id @GeneratedValue private Long id;
    @Column(unique = true, nullable = false, length = 36) private String keycloakId;
    // no password, email or phone: read them from Keycloak or the JWT claims
}
```

### Why
- **Dual-write problem:** two systems can't share one transaction, so you either undo earlier steps when a later one fails (the **saga** pattern) or keep the data in just one system.
- **One owner per piece of data:** if email lives in two places, the copies eventually disagree.

---

## D6: client token stored in the database and growing forever

### Now
```java
// TokenRefresher: inserts a NEW row every 4 minutes and never deletes the old ones
@Scheduled(fixedRate = 4 * 60 * 1000)
public void refresh() {
    String token = keycloakService.getClientTokenFromKeycloak().block();
    tokenService.saveToken(clientId, token);
}
// TokenService: crashes once 2+ rows exist (expects a single result)
return repo.findSecretTokenByUsername(clientId).getToken();
```
Related files: `SecretToken` entity, `SecretTokenRepository`, `Initializer`.

### After
```properties
spring.security.oauth2.client.registration.keycloak-admin.provider=keycloak
spring.security.oauth2.client.registration.keycloak-admin.client-id=saas-auth
spring.security.oauth2.client.registration.keycloak-admin.client-secret=${KEYCLOAK_CLIENT_SECRET}
spring.security.oauth2.client.registration.keycloak-admin.authorization-grant-type=client_credentials
spring.security.oauth2.client.provider.keycloak.token-uri=${keycloak.base-url}/realms/saas/protocol/openid-connect/token
```
```java
@Bean
WebClient keycloakAdminClient(OAuth2AuthorizedClientManager manager) {
    var oauth = new ServletOAuth2AuthorizedClientExchangeFilterFunction(manager);
    oauth.setDefaultClientRegistrationId("keycloak-admin");   // token fetched, cached in memory, refreshed automatically
    return WebClient.builder().apply(oauth.oauth2Configuration()).build();
}
```
**Delete:** `SecretToken`, `SecretTokenRepository`, `TokenService`, `TokenRefresher` and `Initializer`.

### Why
- **Pick storage by how long the data lives:** a 5-minute token belongs in memory, not in a durable table.
- **Use what the framework already provides** instead of writing it by hand.

---

## D7: hardcoded `localhost`

### Now
```java
// KeycloakService (in 3 places)
"http://localhost:" + port + "/admin/realms/" + realm + "/users"
```
Inside Docker, `localhost` is the auth-service container itself, so these calls fail.

### After
```properties
keycloak.base-url=${KEYCLOAK_BASE_URL:http://keycloak:8080}
```
```java
@Value("${keycloak.base-url}") private String keycloakBaseUrl;
...
keycloakBaseUrl + "/admin/realms/" + realm + "/users"
```

### Why
**Configuration comes from the environment, not the code** (the 12-factor app principle). The same jar then runs locally, in Docker or in the cloud.

---

## D8: configuration that disagrees with itself

### Now
```properties
# rag-service application.properties
server.port=8083                                   # compose maps 8082:8082
spring.ai.ollama.chat.options.model=llama3.2
spring.ai.ollama.chat.model=llama3.1:latest        # a second, different model; the puller only downloads llama3.2
spring.ai.ollama.embedding.options.temperature=1.0 # has no effect on embeddings
spring.kafka.bootstrap-servers=kafka:29092         # the Docker hostname mixed with the port meant for the host
spring.data.redis.host=localhost
```

### After
```properties
server.port=8082
spring.ai.ollama.base-url=${OLLAMA_URL:http://localhost:11434}
spring.ai.ollama.chat.options.model=${CHAT_MODEL:llama3.2}
spring.ai.ollama.embedding.options.model=nomic-embed-text
spring.kafka.bootstrap-servers=${KAFKA_SERVERS:localhost:29092}
spring.data.redis.host=${REDIS_HOST:localhost}
```
```java
@ConfigurationProperties("rag") @Validated
public record RagProperties(@Min(1) int topK, @NotBlank String indexName) {}   // bad config stops startup immediately
```

### Why
- **One source of truth per setting:** defaults for running locally, environment variables for Docker.
- **Fail fast:** a wrong value should stop the service at startup, not on the first user request.

---

## D9: schema managed by `ddl-auto=update`

### Now
```properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```
Because of this, you had to drop the card columns by hand.

### After
```xml
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-oracle</artifactId></dependency>
```
```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
```
```sql
-- billing-service/src/main/resources/db/migration/V1__init.sql
CREATE TABLE BILLING_PAYMENT (
  ID NUMBER PRIMARY KEY, CUSTOMER_ID VARCHAR2(36) NOT NULL,
  BILL_PRICE NUMBER(12,2) NOT NULL, PAYMENT_TOKEN VARCHAR2(255) NOT NULL);
CREATE SEQUENCE PAYMENT_SEQ;
```

### Why
**Versioned migrations:** every schema change is a numbered, reviewed file applied the same way everywhere. `validate` makes Hibernate check the schema instead of changing it.

---

## D10: no gateway, and security code copied three times

### Now
- `JWTConverter.java` is copied into auth-service, billing-service and rag-service.
- `SecurityConfig.java` is copied the same way.
- Every service publishes its own port (8081, 8082, 8083).

### After
```
common-security/            <- new Maven module: JWTConverter + base SecurityConfig
gateway-service/            <- Spring Cloud Gateway, the ONLY service with published ports
```
```yaml
# gateway application.yml
spring.cloud.gateway.routes:
  - { id: auth,    uri: http://auth-service:8081,    predicates: [ Path=/auth/** ] }
  - { id: billing, uri: http://billing-service:8082, predicates: [ Path=/billing/** ] }
  - { id: rag,     uri: http://rag-service:8083,     predicates: [ Path=/rag/** ] }
```

### Why
- **One edge layer** handles routing, TLS, rate limiting and CORS once, in one place.
- **No copy-paste:** a security fix in the shared module reaches all three services at once.

---

## D11: billing never receives any usage

### Now
```java
// BillingService: private, and nothing calls it
private UserUsage addUsage(String customerId, String transactionType) { ... }
```
```java
// BillingController: the endpoint is commented out
//    @PostMapping("/addUsage")
```

### After
```java
// rag-service, after an answer finishes streaming
kafkaTemplate.send("billing.usage", ownerId,                         // key = user, so one user's events stay in order
        new UsageEvent(UUID.randomUUID().toString(), ownerId, "RAG_QUERY", Instant.now()));
```
```java
// billing-service
@KafkaListener(topics = "billing.usage", groupId = "billing")
@Transactional
public void onUsage(UsageEvent e) {
    if (processedEvents.existsById(e.eventId())) return;             // idempotent: duplicates are ignored
    processedEvents.save(new ProcessedEvent(e.eventId()));
    addUsage(e.userId(), e.type());
}
```

### Why
This is where Kafka fits well: **asynchronous metering**. Answers aren't blocked when billing is down, and no usage is lost.

---

## D12: money stored as `int` and `double`

### Now
```java
private int billPrice;                                   // Payment
private double amount;                                   // UserUsage
Map<String, Double> PRICES = Map.of("CHAT_GPT", 1.0, ...);  // PriceList
```

### After
```java
@Column(precision = 12, scale = 2) private BigDecimal billPrice;
@Column(precision = 12, scale = 2) private BigDecimal amount;
Map<String, BigDecimal> PRICES = Map.of("RAG_QUERY", new BigDecimal("1.00"), ...);
```

### Why
Binary floating point can't represent 0.10 exactly, so totals drift by fractions over many additions. `BigDecimal` is exact.

---

## D13 + D14: ingestion inside the HTTP request, with no document lifecycle

### Now
```java
// EmbeddingController
@PostMapping(value = "/upload")
public void upload(@RequestParam("files") List<MultipartFile> files) throws Exception {
    for (MultipartFile file : files) {
        pdfParser.uploadFile(file.getOriginalFilename(), file.getInputStream());  // parse + embed everything synchronously
    }
}
```
Other gaps:
- No owner on the chunks.
- No duplicate check, so re-uploading the same file duplicates its chunks.
- No way to delete or update a document.

### After
```java
@PostMapping("/documents")
@ResponseStatus(HttpStatus.ACCEPTED)
public DocumentDTO upload(@AuthenticationPrincipal Jwt jwt, @RequestParam MultipartFile file) {
    String sha = DigestUtils.sha256Hex(file.getInputStream());
    return documentRepo.findByOwnerIdAndSha256(jwt.getSubject(), sha)            // idempotent
        .map(DocumentDTO::from)
        .orElseGet(() -> {
            String key = storage.put(file);                                      // S3 or MinIO
            Document doc = documentRepo.save(new Document(jwt.getSubject(), file.getOriginalFilename(), sha, key, Status.PENDING));
            kafkaTemplate.send("rag.ingest", doc.getId(), new IngestRequested(doc.getId()));
            return DocumentDTO.from(doc);                                        // client polls GET /documents/{id}
        });
}

@KafkaListener(topics = "rag.ingest", groupId = "ingest")
public void ingest(IngestRequested e) {
    // load the file, parse it, chunk it (adding ownerId + documentId metadata), embed it, then set status READY or FAILED
}

@DeleteMapping("/documents/{id}")   // deletes the chunks whose metadata has documentId == id
```

### Why
- **Slow work runs in the background:** the HTTP request returns at once with a job ID, and Kafka absorbs bursts of uploads.
- **A document is an entity** with an ID, owner and status, so it can be tracked, re-run and deleted.

---

## D15: startup order and health checks

### Now
```yaml
rag-service:
  depends_on:
    ollama: { condition: service_healthy }      # but the models may still be downloading
ollama:
  healthcheck:
    test: [ "CMD", "curl", "-f", "http://localhost:11434/api/tags" ]   # curl may not exist in this image
keycloak:
  volumes:
    - ./oracle-drivers:/opt/keycloak/providers  # this folder doesn't exist at the repo root
```
Nothing creates the database users `keycloakService`, `authService` and `billingService`.

### After
```yaml
rag-service:
  depends_on:
    ollama-puller: { condition: service_completed_successfully }
ollama:
  healthcheck:
    test: [ "CMD", "ollama", "list" ]
keycloak:
  volumes:
    - ./infra/keycloak/providers:/opt/keycloak/providers     # the jar comes from Maven, not git
oracle-xe:
  volumes:
    - ./infra/oracle/init:/container-entrypoint-initdb.d     # 01-users.sql creates the 3 users
auth-service:
  healthcheck:
    test: [ "CMD", "wget", "-qO-", "http://localhost:8081/actuator/health/readiness" ]
```

### Why
**Readiness vs liveness:** "the container is running" is not the same as "ready to serve". Every dependency needs a health check that tells the truth, and setup steps (database users, model downloads) should be explicit init jobs.

---

## D16: observability

### Now
```java
System.out.println("START SEARCH");
System.out.println("GOT AGGREGATE QUERY");
System.out.println("CONTEXT: " + context);       // also prints document contents to the logs
@PostConstruct public void debug() { System.out.println(vectorStore.getClass()); }
```

### After
```java
log.debug("search start queryId={}", queryId);
MDC.put("queryId", queryId);                     // every log line for this request carries the id
```
```xml
<!-- in all services -->
<artifactId>spring-boot-starter-actuator</artifactId>
<artifactId>micrometer-tracing-bridge-otel</artifactId>
<artifactId>micrometer-registry-prometheus</artifactId>
```
```java
Timer.builder("rag.llm.latency").register(registry).record(...);
```

### Why
**Logs, metrics and traces** are the three pillars of observability. A correlation ID (`queryId`) lets you follow one request through every service.

---

## D17: build broken on master, and no tests

### Now
- On GitHub, `KafkaTopics` and `KafkaGroups` are declared as `package main.java.com...`, and three files import `com.example.kafka.Configuration.*`, so rag-service doesn't compile. Your uncommitted local changes fix this.
- `UserService` injects `SecurityConfig`, which exists only in the `PRODUCTION` profile, so the dev profile crashes on startup.
- There are no tests and no CI.

### After
```java
// UserService: remove this line, it isn't used
private final SecurityConfig securityConfig;
```
```yaml
# .github/workflows/ci.yml
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    strategy: { matrix: { service: [auth-service, billing-service, rag-service] } }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17, cache: maven }
      - run: cd ${{ matrix.service }} && ./mvnw -B verify
```
```java
class RrfMergerTest {
    @Test void resultFoundByBothSearchesRanksFirst() { ... }
}
// plus one Testcontainers test per service (Oracle, Redis)
```

### Why
**CI catches a broken master within minutes.** Tests on the core logic (RRF ranking) protect the most important part of the product.

---

## D18: repo hygiene

### Now
- Committed: `.idea/`, `out/`, `ojdbc11.jar` (twice), `en-token.bin` (twice) and `resume.pdf` (twice).
- There's no `.gitignore` at the root.

### After
```gitignore
# .gitignore
.idea/
out/
target/
*.jar
!**/.mvn/wrapper/*.jar
*.iml
.env
```
```bash
git rm -r --cached .idea out
git rm --cached auth-service/src/main/java/com/example/auth_service/Security/KeyCloak/oracle-drivers/ojdbc11.jar
```
- Get `ojdbc11` from Maven Central.
- Download `en-token.bin` during the build, or keep it in `resources` only once.
- Replace the résumé with a fake sample PDF.

### Why
**Commit source code, not build output or personal files.** It keeps the repo small, reviewable and safe.

---

## D19: smaller correctness issues

| Where | Now | After |
|---|---|---|
| `VectorStoreConfig` | `pooledConfig.setMaxTotal(...)` is created, then `new JedisPooled(redisHost, redisPort)` ignores it | `new JedisPooled(pooledConfig, redisHost, redisPort, socketTimeout)` |
| `HybridSearchService.vectorSearch` | `assert vectorResults != null;` (asserts are off at runtime) | `if (vectorResults == null) return List.of();` |
| `NlpProcessor.tokenize` | drops the last chunk and joins words without spaces | delete it (it's unused; `PdfParser` does the chunking) |
| `User.email` | `@Column(length = 20)` | `@Column(length = 254)` (the email standard's maximum) |
| `PaymentRepository` | `JpaRepository<Payment, Integer>` and `Payment findByCustomerId` | `JpaRepository<Payment, Long>` and `List<Payment> findByCustomerId` |
| `EmbeddingController` | `@RestController("/rag")` only names the bean | `@RestController @RequestMapping("/rag")` |
| `UserController.createUser` | `@RequestBody UserDTO` (validation never runs) | `@Valid @RequestBody UserDTO` |

---

## Suggested order

1. **D17 + D19:** get the build green, since everything else depends on it.
2. **D1–D3:** simplify the query path.
3. **D7, D8, D15:** make `docker compose up` work end to end.
4. **D6, D4/D5:** tokens and signup consistency.
5. **D9, D18:** Flyway and repo cleanup.
6. **D13/D14, D11:** async ingestion and usage events (the right uses of Kafka).
7. **D10, D16:** gateway, shared security module and observability.
