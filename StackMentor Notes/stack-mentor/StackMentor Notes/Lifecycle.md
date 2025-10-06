## Complete Development Lifecycle

### **Phase 1: Plan & Design** (1-2 weeks)

**Goal**: Define clear contracts and architecture before writing code. Enables parallel frontend/backend development and prevents rework.

#### Step 1.1: Requirements Gathering

- **Feature list**: User stories with acceptance criteria (e.g., "As a user, I can send direct messages to other users, seeing delivery/read status")
- **SLOs (Service Level Objectives)**: Define targets—99.9% uptime, P95 latency <200ms, message delivery <1s
- **Non-functional requirements**: Expected user load (10k DAU), data retention policies, GDPR compliance needs
- **Why**: Measurable targets drive technical decisions. Knowing 10k vs 1M users changes infrastructure choices.

#### Step 1.2: API Contract Design (OpenAPI Specification)

- **Create OpenAPI 3.0 spec** for every endpoint before implementation
    - Request/response schemas with data types
    - Authentication requirements per endpoint
    - Error response formats (4xx, 5xx codes with error codes like `INVALID_CONVERSATION`)
    - Example requests/responses
- **Generate client stubs**: Use OpenAPI Generator to create TypeScript client for React Native
- **Contract tests**: Consumer (frontend) expectations match provider (backend) implementation
- **Why**: Frontend/backend teams work in parallel. Frontend mocks API responses from spec. Contract breaks caught in CI before integration. Auto-generated clients prevent manual HTTP code.

**Example OpenAPI snippet**:
```yaml
/api/messages:
	  post:
	    summary: Send message
	    requestBody:
	      content:
	        application/json:
	          schema:
	            type: object
	            required: [conversationId, text]
	            properties:
	              conversationId: {type: string, format: uuid}
	              text: {type: string, maxLength: 10000}
	    responses:
	      201:
	        description: Message created
	        content:
	          application/json:
	            schema:
	              $ref: '#/components/schemas/Message'
	      400: {$ref: '#/components/responses/BadRequest'}
```
#### Step 1.3: Database Schema Design

- **ERD (Entity Relationship Diagram)**: Draw relationships between User, Conversation, Message, Group entities
- **Identify foreign keys, indexes**: Conversations need index on participant IDs for fast lookup
- **Plan for scale**: Partition strategy for messages table if expecting >100M rows (partition by conversation_id or timestamp)
- **Data types**: UUID for IDs (128-bit, globally unique), TIMESTAMP WITH TIME ZONE for dates (avoid timezone bugs), JSONB for flexible metadata
- **Why**: Visual ERD catches missing relationships early. Index planning prevents slow queries in production. Partition strategy expensive to add later.

#### Step 1.4: Authentication Flow Diagrams

- **Signup flow**: User enters email/password → Cognito creates account → sends verification email → user clicks link → backend verifies token → marks account active
- **Login flow**: Credentials sent to Cognito → JWT tokens returned (access token, refresh token) → app stores tokens securely → includes access token in Authorization header on API calls
- **Token refresh**: Access token expires after 1 hour → app detects 401 response → uses refresh token to get new access token → retries failed request
- **Password reset**: User requests reset → Cognito sends code → user enters code + new password → Cognito updates password
- **Why**: Visualizing flows catches edge cases—what if user doesn't verify email? How long is reset code valid? Prevents security bugs.

#### Step 1.5: Wireframes/UI Mockups

- **Sketch main screens**: Chat list, conversation view, group creation, settings
- **User flows**: How do users navigate from chat list → new conversation → select participants → send first message
- **Loading/error states**: What shows when messages loading? Network error UI?
- **Why**: Frontend team knows what to build. Identifies missing API endpoints (e.g., "need endpoint to search users by name"). Prevents "forgot password reset screen" situations.

---

### **Phase 2: Infrastructure as Code (IaC) Setup** (1 week)

**Goal**: Define all AWS resources in version-controlled Terraform/CDK. Creates reproducible staging and production environments.
#### Step 2.1: Choose IaC Tool

- **Terraform** (HCL language) or **AWS CDK** (TypeScript/Java code)
- **Recommendation**: Terraform for declarative simplicity, CDK if you prefer programming language constructs
- **Why**: CDK type-safety catches errors pre-deploy, better for complex logic. Terraform has larger community, more providers.

#### Step 2.2: VPC and Networking

**Create Terraform modules**:

- **VPC**: CIDR block 10.0.0.0/16 (65k private IPs)
- **Public subnets** (2 availability zones): 10.0.1.0/24, 10.0.2.0/24 for ALB and NAT Gateway
- **Private subnets** (2 AZs): 10.0.10.0/24, 10.0.11.0/24 for ECS tasks, RDS, Redis
- **Internet Gateway**: Attached to VPC, routes public subnet traffic to internet
- **NAT Gateway** (one per AZ): Allows private subnet resources to initiate outbound connections (pull Docker images, call external APIs) while blocking inbound
- **Route tables**: Public subnets route 0.0.0.0/0 to IGW, private subnets route 0.0.0.0/0 to NAT Gateway
- **Why**: Multi-AZ for high availability (if AZ fails, other takes over). Private subnets protect database from internet exposure. NAT allows ECS to download packages without public IP.

**Security Groups**:

- **ALB SG**: Allow inbound 80 (HTTP) and 443 (HTTPS) from 0.0.0.0/0, outbound to ECS SG
- **ECS SG**: Allow inbound 8080 from ALB SG only, outbound to RDS/Redis SGs and 0.0.0.0/0 (for API calls)
- **RDS SG**: Allow inbound 5432 from ECS SG only, no outbound needed
- **Redis SG**: Allow inbound 6379 from ECS SG only
- **Why**: Principle of least privilege. RDS only accepts connections from ECS, not entire internet. Reduces attack surface.

#### Step 2.3: ECR (Elastic Container Registry)

- **Create repository**: `stackmentor-backend`
- **Lifecycle policy**: Keep last 10 images, delete older (prevents unbounded storage costs)
- **Scan on push**: Enable vulnerability scanning for security
- **Why**: Private Docker registry integrated with ECS. Scans catch vulnerable dependencies before deployment.

#### Step 2.4: RDS PostgreSQL

- **Engine**: PostgreSQL 15.x (latest stable)
- **Instance class**: Start with db.t4g.micro (2 vCPU, 1GB RAM, burstable) for staging, db.r6g.large+ for production
- **Multi-AZ**: Enable for production (automatic failover to standby in different AZ if primary fails)
- **Storage**: GP3 SSD, 20GB min, enable autoscaling to 100GB
- **Subnet group**: Use private subnets only
- **Parameter group**: Custom settings—`shared_buffers=256MB`, `max_connections=200`, `log_statement=all` for debugging
- **Automated backups**: Retain 7 days, backup window 3-4am UTC (low traffic)
- **Secrets Manager integration**: Store master password in Secrets Manager, reference in Terraform, enable automatic rotation
- **Why**: Managed service handles patching, backups. Multi-AZ prevents downtime during AZ failures. GP3 cheaper than io1 with same performance. Secrets Manager prevents password in plaintext config.

#### Step 2.5: ElastiCache Redis

- **Engine**: Redis 7.x
- **Node type**: cache.t4g.micro for staging, cache.r6g.large+ for production
- **Cluster mode**: Disabled for simplicity (single shard), enable for >5GB data
- **Replication**: 1-2 read replicas for production HA
- **Subnet group**: Private subnets
- **Security group**: Only allow ECS
- **Why**: Managed Redis for Pub/Sub and caching. Replication provides failover. Cluster mode needed for horizontal scaling beyond single node memory.

#### Step 2.6: S3 Buckets

- **Bucket name**: `stackmentor-media-prod` (globally unique)
- **Versioning**: Enable (recover deleted/overwritten files)
- **Lifecycle rules**: Delete objects in `temp/` prefix after 1 day, transition to Glacier after 90 days for old backups
- **CORS configuration**: Allow origins `https://app.stackmentor.com`, methods GET/PUT, headers Authorization/Content-Type
- **Server-side encryption**: AES-256 by default
- **Block public access**: Enabled (use presigned URLs for controlled access)
- **Why**: Versioning prevents accidental deletion. Lifecycle rules reduce storage costs (Glacier 80% cheaper). CORS allows frontend to upload directly. Encryption at rest for compliance.

#### Step 2.7: Cognito User Pool

- **Password policy**: Min 8 chars, require uppercase, lowercase, number
- **MFA**: Optional (SMS or TOTP app), enforce for admin users
- **Email verification**: Required before login (prevents fake signups)
- **Token expiration**: Access token 1 hour, refresh token 30 days
- **Attributes**: email (required, mutable), name, profile_picture_url (custom)
- **App client**: Create for backend (server-side auth) and frontend (user-facing flows)
- **Lambda triggers**: Pre-signup hook to check email domain blocklist, post-confirmation to create User record in database
- **Why**: Managed auth reduces security liability. Short access token limits compromise window. Lambda triggers ensure database sync with Cognito.

#### Step 2.8: IAM Roles

- **ECS Task Execution Role**: Allows ECS to pull images from ECR, write logs to CloudWatch, read secrets from Secrets Manager
    - Policies: `AmazonECSTaskExecutionRolePolicy`, custom policy for Secrets Manager
- **ECS Task Role**: Runtime permissions for application—access S3 buckets, query RDS (if using IAM auth), publish CloudWatch metrics
    - Policies: Custom policy with minimum S3/CloudWatch permissions
- **CI/CD Role**: GitHub Actions assumes this role to push images to ECR, update ECS services
    - Trust policy: Allow GitHub OIDC provider to assume
- **Why**: Task Execution Role is ECS plumbing, Task Role is your app's permissions. Separation prevents over-privileging. GitHub OIDC avoids storing AWS credentials in GitHub secrets.

#### Step 2.9: Route 53 Hosted Zone

- **Register domain** (if needed): `stackmentor.com` via Route 53 or external registrar
- **Create hosted zone**: Generates nameservers, update at domain registrar
- **A record**: `api.stackmentor.com` → ALB DNS name (alias record, no direct IP needed)
- **Why**: Route 53 handles DNS queries globally with low latency. Alias records automatically update if ALB IP changes.

#### Step 2.10: ACM (AWS Certificate Manager) SSL Certificate

- **Request certificate** for `*.stackmentor.com` (wildcard covers api, app, admin subdomains)
- **DNS validation**: Add CNAME record to Route 53 (automated if Route 53 manages domain)
- **Auto-renewal**: ACM renews 60 days before expiry
- **Attach to ALB**: HTTPS listener uses this certificate
- **Why**: Free SSL certificates, automatic renewal. Wildcard simplifies multi-subdomain setup.

#### Step 2.11: CloudWatch Log Groups

- **Create groups**: `/ecs/stackmentor-backend`, `/ecs/liquibase-migration`
- **Retention**: 7 days staging, 30 days production (balance cost vs debugging needs)
- **Why**: Centralized logging. Retention policy prevents unbounded storage costs.

#### Step 2.12: Deploy Staging Environment

- **Terraform workspaces** or **separate state files** for staging/production
- **Variable overrides**: Staging uses smaller instance types (db.t4g.micro vs db.r6g.large), single NAT Gateway
- **Apply infrastructure**: `terraform apply` creates all resources in ~15 minutes
- **Output values**: Export RDS endpoint, Redis endpoint, S3 bucket name for application config
- **Why**: Staging validates IaC correctness before production. Smaller instances reduce staging costs (80% cheaper). Separate workspaces prevent accidental production changes.

---

### **Phase 3: Local Development Environment** (Week 1-2 of development)

**Goal**: Mirror production environment locally using Docker. Fast iteration without AWS costs.

#### Step 3.1: Docker Compose Setup

**Create `docker-compose.yml`**:
```yaml
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: stackmentor
      POSTGRES_USER: dev
      POSTGRES_PASSWORD: devpass
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
  
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
  
  localstack:  # Optional: S3/Cognito emulation
    image: localstack/localstack
    environment:
      SERVICES: s3,cognito-idp
    ports:
      - "4566:4566"
  
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/stackmentor
      SPRING_REDIS_HOST: redis
      AWS_ENDPOINT: http://localstack:4566  # Override for local S3
    depends_on:
      - postgres
      - redis
```

- **Why postgres:15**: Match production version exactly (prevents SQL dialect issues)
- **Why redis:7-alpine**: Lightweight image, same version as ElastiCache
- **Why localstack**: Test S3 uploads without AWS costs, but optional (can test in staging)
- **Volume mount**: Persists database between restarts (`docker-compose down` doesn't lose data)

#### Step 3.2: Spring Boot Configuration Profiles

**`application.yml`**:
```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
  
---
spring:
  config:
    activate:
      on-profile: local
  datasource:
    url: jdbc:postgresql://localhost:5432/stackmentor
    username: dev
    password: devpass
  redis:
    host: localhost
    port: 6379
  
---
spring:
  config:
    activate:
      on-profile: staging
  datasource:
    url: ${RDS_ENDPOINT}  # From Terraform output
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}  # From Secrets Manager
  redis:
    host: ${REDIS_ENDPOINT}
```

- **Profile activation**: Set `SPRING_PROFILES_ACTIVE=staging` in ECS task definition
- **Secrets injection**: ECS pulls `DB_PASSWORD` from Secrets Manager at runtime, injects as environment variable
- **Why**: Single codebase runs everywhere. No hardcoded credentials. Profile switching automatic based on environment.

#### Step 3.3: Application Structure Implementation

**Already completed (from your progress)**:

- JPA entities with relationships, composite keys for join tables (DirectConversationParticipantId)
- Repositories extending JpaRepository with custom query methods
- Initial services (UserService, EmailService)
- Specifications for dynamic filtering (UserSpecifications)

**Next: Complete remaining services**:

- **ConversationService**: Create conversations (direct/group), list user's conversations, add/remove participants
- **MessageService**: Send message, list messages for conversation (paginated), mark as read, update read status
- **GroupService**: Create group, add/remove members, update group metadata (name, avatar)

**Service layer best practices**:

- **@Transactional**: Annotate methods that modify data (ensures atomicity—either all changes commit or all rollback)
- **DTO conversion**: Services accept/return DTOs, never expose entities (prevents accidental lazy-loading exceptions, decouples API from DB schema)
- **Business validation**: Check permissions (user in conversation before sending message), validate input (message not empty, conversation exists)
**Example**:
```java
@Service
@Transactional
public class MessageService {
    public MessageDto sendMessage(String senderId, SendMessageRequest request) {
        // Validate sender is participant
        if (!conversationRepo.isUserParticipant(request.getConversationId(), senderId)) {
            throw new ForbiddenException("Not a conversation participant");
        }
        
        Message message = new Message();
        message.setConversationId(request.getConversationId());
        message.setSenderId(senderId);
        message.setText(request.getText());
        message.setTimestamp(Instant.now());
        
        Message saved = messageRepo.save(message);
        
        // Publish to Redis for real-time delivery (Phase 5)
        redisPublisher.publish("conversation:" + request.getConversationId(), saved);
        
        return MessageDto.from(saved);
    }
}
```

- **Why DTOs**: Changing entity structure doesn't break API. Can hide sensitive fields (User.passwordHash). Prevents N+1 query issues from lazy collections.

#### Step 3.4: Unit Testing (Concurrent with Step 3.3)

**Write tests as you implement each service method**
**Dependencies**:
```kotlin
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.mockito:mockito-core")
testImplementation("org.mockito:mockito-junit-jupiter")
```

**Example unit test**:
```java
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    @Mock
    private MessageRepository messageRepo;
    
    @Mock
    private ConversationRepository conversationRepo;
    
    @InjectMocks
    private MessageService messageService;
    
    @Test
    void sendMessage_success() {
        // Given
        String senderId = "user123";
        SendMessageRequest request = new SendMessageRequest("conv456", "Hello");
        when(conversationRepo.isUserParticipant("conv456", "user123")).thenReturn(true);
        when(messageRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        MessageDto result = messageService.sendMessage(senderId, request);
        
        // Then
        assertNotNull(result);
        assertEquals("Hello", result.getText());
        verify(messageRepo).save(any(Message.class));
    }
    
    @Test
    void sendMessage_notParticipant_throwsForbidden() {
        // Given
        when(conversationRepo.isUserParticipant(any(), any())).thenReturn(false);
        
        // When/Then
        assertThrows(ForbiddenException.class, 
            () -> messageService.sendMessage("user123", new SendMessageRequest("conv456", "Hi")));
    }
}
```

**Coverage target**: 80%+ for service layer (critical business logic) **Run tests**: `./gradlew test` (integrates with CI later) **Why now**: Refactoring services later breaks tests (early warning). Cheaper to fix bugs in tests than production. Forces you to think about edge cases.

---

### **Phase 4: REST API Controllers** (1 week)

**Goal**: Expose services via HTTP endpoints. Map requests/responses to OpenAPI contract.

#### Step 4.1: Controller Implementation

**Create controllers matching OpenAPI spec**:
```java
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto sendMessage(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody SendMessageRequest request
    ) {
        return messageService.sendMessage(user.getId(), request);
    }
    
    @GetMapping
    public Page<MessageDto> getMessages(
        @RequestParam String conversationId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return messageService.getMessages(conversationId, PageRequest.of(page, size));
    }
    
    @PutMapping("/{messageId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable String messageId
    ) {
        messageService.markAsRead(messageId, user.getId());
    }
}
```

**Key elements**:
- **@AuthenticationPrincipal**: Extracts authenticated user from Spring Security context (populated from Cognito JWT in Phase 6)
- **@Valid**: Triggers validation on request body (checks @NotNull, @Size, custom validators)
- **@ResponseStatus**: Returns 201 Created for POST, 204 No Content for PUT (matches OpenAPI spec)
- **Pagination**: Use Spring's Page/Pageable to avoid loading all messages (1M+ messages in active conversation)

#### Step 4.2: DTO Definitions with Validation
**Example**:
```java
public record SendMessageRequest(
    @NotNull(message = "Conversation ID required")
    @Pattern(regexp = UUID_REGEX)
    String conversationId,
    
    @NotBlank(message = "Message text required")
    @Size(max = 10000, message = "Message too long")
    String text,
    
    @Size(max = 5)
    List<String> attachmentIds
) {}

public record MessageDto(
    String id,
    String conversationId,
    String senderId,
    String senderName,
    String text,
    List<String> attachmentUrls,
    Instant timestamp,
    boolean isRead
) {
    public static MessageDto from(Message entity, String currentUserId) {
        return new MessageDto(
            entity.getId(),
            entity.getConversationId(),
            entity.getSenderId(),
            entity.getSender().getName(),
            entity.getText(),
            entity.getAttachments().stream()
                .map(a -> s3Service.generatePresignedUrl(a.getS3Key()))
                .toList(),
            entity.getTimestamp(),
            entity.getReadStatuses().stream()
                .anyMatch(rs -> rs.getUserId().equals(currentUserId) && rs.isRead())
        );
    }
}
```

**Why records**: Immutable, auto-generates equals/hashCode/toString. Java 17+ feature. **Why validation**: Fails fast with clear error (400 Bad Request + message), prevents invalid data reaching service layer. **Why presigned URLs**: S3 attachments not publicly accessible. Backend generates temporary URL (valid 5 minutes) with embedded signature. Frontend uses URL directly to download without exposing S3 credentials.

#### Step 4.3: Global Exception Handling
**Example**:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                FieldError::getDefaultMessage
            ));
        return new ErrorResponse("VALIDATION_ERROR", "Invalid input", errors);
    }
    
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleForbidden(ForbiddenException ex) {
        return new ErrorResponse("FORBIDDEN", ex.getMessage(), null);
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return new ErrorResponse("INTERNAL_ERROR", "Something went wrong", null);
    }
}

public record ErrorResponse(String errorCode, String message, Map<String, String> details) {}
```

**Why**: Consistent error format across all endpoints. Frontend can parse errorCode programmatically ("VALIDATION_ERROR" → show field errors, "FORBIDDEN" → show permission denied message). Prevents leaking stack traces to users.

#### Step 4.4: Integration Testing

**Use `@SpringBootTest` with Testcontainers for full stack testing**:
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class MessageControllerIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private MessageRepository messageRepo;
    
    @Test
    void sendMessage_createsInDatabase() {
        // Given: authenticated user (mock JWT token)
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateTestToken("user123"));
        
        SendMessageRequest request = new SendMessageRequest("conv456", "Hello");
        HttpEntity<SendMessageRequest> entity = new HttpEntity<>(request, headers);
        
        // When
        ResponseEntity<MessageDto> response = restTemplate.postForEntity(
            "/api/messages", entity, MessageDto.class
        );
        
        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().id());
        
        // Verify database
        Message saved = messageRepo.findById(response.getBody().id()).orElseThrow();
        assertEquals("Hello", saved.getText());
    }
}
```

**Why Testcontainers**: Spins up real PostgreSQL + Redis in Docker for tests. Ensures queries work (catches PostgreSQL-specific SQL). Cleans up automatically after tests. More realistic than H2 in-memory database.

**When to run**: On PR merge to main branch in CI. Too slow for every commit (~30 seconds to start containers).

---

### **Phase 5: CI/CD Pipeline Setup** (2-3 days)

**Goal**: Automate testing, building, and deployment. Catch issues before human review.

#### Step 5.1: GitHub Actions Workflow

**Create `.github/workflows/ci.yml`**:
```yaml
name: CI Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Ktlint check
        run: ./gradlew ktlintCheck
      
      - name: Checkstyle
        run: ./gradlew checkstyleMain checkstyleTest

  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
      
      - name: Run unit tests
        run: ./gradlew test
      
      - name: Publish test results
        uses: EnricoMi/publish-unit-test-result-action@v2
        if: always()
        with:
          files: build/test-results/**/*.xml
      
      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          files: build/reports/jacoco/test/jacocoTestReport.xml

  static-analysis:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: SonarQube Scan
        uses: sonarsource/sonarcloud-github-action@master
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}

  build:
    needs: [lint, unit-tests, static-analysis]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
      
      - name: Build JAR
        run: ./gradlew bootJar
      
      - name: Build Docker image
        run: docker build -t stackmentor-backend:${{ github.sha }} .
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::${{ secrets.AWS_ACCOUNT_ID }}:role/GitHubActionsRole
          aws-region: us-east-1
      
      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2
      
      - name: Push to ECR
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        run: |
          docker tag stackmentor-backend:${{ github.sha }} \
            $ECR_REGISTRY/stackmentor-backend:${{ github.sha }}
          docker tag stackmentor-backend:${{ github.sha }} \
            $ECR_REGISTRY/stackmentor-backend:latest
          docker push $ECR_REGISTRY/stackmentor-backend:${{ github.sha }}
          docker push $ECR_REGISTRY/stackmentor-backend:latest

  integration-tests:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run integration tests
        run: ./gradlew integrationTest
        env:
          TESTCONTAINERS_RYUK_DISABLED: false

  deploy-staging:
    needs: integration-tests
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::${{ secrets.AWS_ACCOUNT_ID }}:role/GitHubActionsRole
          aws-region: us-east-1
      
      - name: Update ECS service
        run: |
          aws ecs update-service \
            --cluster stackmentor-staging \
            --service backend \
            --force-new-deployment

      - name: Wait for deployment
        run: |
          aws ecs wait services-stable \
            --cluster stackmentor-staging \
            --services backend
      
      - name: Run smoke tests
        run: |
          curl -f https://staging-api.stackmentor.com/actuator/health || exit 1
```

**Pipeline stages explained**:
1. **Lint**: Catches code style issues (unused imports, inconsistent formatting). Ktlint auto-fixes with `./gradlew ktlintFormat`.
2. **Unit tests**: Fast feedback (~2 minutes). Fails PR if tests break.
3. **Static analysis**: SonarQube detects code smells (complex methods, duplicated code), security hotspots (SQL injection risk, hardcoded credentials).
4. **Build**: Compiles code, creates Docker image, pushes to ECR with git commit SHA tag (traceability—"which code is running in production?"). Uses GitHub OIDC (OpenID Connect) to assume AWS role without storing credentials. 5. **Integration tests**: Spins up Testcontainers, runs full HTTP request→database tests. Catches SQL errors, transaction issues, constraint violations. 6. **Deploy staging**: On main branch merge, automatically deploys to staging ECS cluster. Force-new-deployment pulls latest image from ECR. 7. **Smoke tests**: Basic health check confirms staging didn't crash. More comprehensive E2E tests run separately (Phase 8).

**Why this order**: Fast tests first (lint in 30s), slow tests later (integration 5 minutes). Fails fast—why wait for integration tests if unit tests already failed? Staging deploy only on main branch (feature branches don't deploy).

#### Step 5.2: Liquibase Validation in CI

**Add to workflow**:
```yaml
  liquibase-validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Validate changelogs
        run: ./gradlew liquibaseValidate
      
      - name: Check for pending changes
        run: |
          ./gradlew liquibaseStatus | tee status.txt
          if grep -q "is up to date" status.txt; then
            echo "No pending migrations"
          else
            echo "WARNING: Pending migrations detected"
          fi
```

**Why**: Catches invalid changelog XML (typo in column name, missing foreign key). Prevents deploying broken migrations. Shows warnings if developer forgot to commit changelog.

#### Step 5.3: Branch Protection Rules

**Configure in GitHub repository settings**:

- Require pull request reviews (1 approval minimum)
- Require status checks to pass: lint, unit-tests, static-analysis, liquibase-validate
- Require branches to be up to date (prevents merge conflicts)
- Require signed commits (prevents impersonation)

**Why**: Prevents broken code merging to main. Forces code review (catches logic bugs linters miss). Up-to-date requirement ensures tests run against latest main branch.

#### Step 5.4: Secrets Management in CI

**Store in GitHub Secrets** (Settings → Secrets and variables → Actions):

- `AWS_ACCOUNT_ID`: Your AWS account number
- `SONAR_TOKEN`: SonarQube API token for static analysis
- `CODECOV_TOKEN`: Code coverage reporting

**Never store in secrets**:

- AWS access keys (use OIDC role assumption instead)
- Database passwords (ECS pulls from Secrets Manager at runtime)

**Why**: GitHub encrypts secrets, audit logs access. OIDC tokens expire automatically (no long-lived credentials to rotate). Secrets Manager rotates database passwords without code changes.

---

### **Phase 6: Database Migrations Best Practices** (Ongoing from Phase 3)

**Goal**: Change database schema safely without downtime or data loss.

#### Step 6.1: Migration Principles

**Backward-compatible changes** (safe for zero-downtime):

- Add nullable columns: `ALTER TABLE users ADD COLUMN phone_number VARCHAR(20)`
- Add tables: `CREATE TABLE notifications (...)`
- Add indexes: `CREATE INDEX idx_messages_conversation ON messages(conversation_id)`
- Expand column size: `ALTER TABLE users ALTER COLUMN name TYPE VARCHAR(200)`

**Backward-incompatible changes** (require two-phase deployment):

- Drop columns: Old code still references column, crashes if dropped
- Rename columns: Old code queries old name
- Add NOT NULL constraints: Old code doesn't populate, INSERT fails
- Change data types (VARCHAR to INT): Old code sends wrong type

#### Step 6.2: Two-Phase Migration Strategy

**Example: Renaming `users.username` to `users.email`**

**Phase 1 - Add new column**:
```xml
<changeSet id="2025-10-01-add-email" author="dev">
  <addColumn tableName="users">
    <column name="email" type="VARCHAR(255)"/>
  </addColumn>
  
  <sql>UPDATE users SET email = username</sql>
  
  <addNotNullConstraint tableName="users" columnName="email"/>
</changeSet>
```

- Deploy migration + app code that writes to both `username` and `email`
- Run for 24 hours (ensures all instances updated)
- Backfill data if needed

**Phase 2 - Drop old column**:
```xml
<changeSet id="2025-10-02-drop-username" author="dev">
  <dropColumn tableName="users" columnName="username"/>
</changeSet>
```

- Deploy app code that only references `email`
- Wait for rollout, then deploy migration to drop `username`

**Why two phases**: Old app instances (still running during deployment) don't crash. Rollback possible—if Phase 2 breaks, redeploy Phase 1 app code (still writes to both columns).

#### Step 6.3: Liquibase Changelog Organization

**Structure**:
```
resources/db/changelog/
  db.changelog-master.xml
  changes/
    001-initial-schema.xml
    002-add-groups.xml
    003-add-message-attachments.xml
    004-index-messages-timestamp.xml
```

**Master file includes all**:
```xml
<databaseChangeLog>
  <include file="changes/001-initial-schema.xml"/>
  <include file="changes/002-add-groups.xml"/>
  <include file="changes/003-add-message-attachments.xml"/>
  <include file="changes/004-index-messages-timestamp.xml"/>
</databaseChangeLog>
```

**Each change file has unique ID**:
```xml
<changeSet id="004-index-messages-timestamp" author="jane">
  <createIndex tableName="messages" indexName="idx_messages_timestamp">
    <column name="timestamp"/>
  </createIndex>
  
  <rollback>
    <dropIndex tableName="messages" indexName="idx_messages_timestamp"/>
  </rollback>
</changeSet>
```

**Why separate files**: Easy code review (see exactly what changed). Git history shows who added which migration when. Rollback tag enables automatic revert if migration causes issues.

#### Step 6.4: Testing Migrations Locally

**Before committing**:
```bash
# Apply migration
./gradlew liquibaseUpdate

# Check status
./gradlew liquibaseStatus

# Test rollback (if defined)
./gradlew liquibaseRollbackCount -PliquibaseCommandValue=1

# Reapply
./gradlew liquibaseUpdate
```

**Verify**:
- App starts without errors
- Integration tests pass (catch foreign key violations, missing columns)
- No data loss (check row counts before/after)

**Why local testing**: Catches errors before CI, faster feedback. Rollback test ensures you can undo if production breaks.

#### Step 6.5: Production Migration Execution

**Option A: Separate migration job** (recommended):
```bash
# ECS Task Definition for one-time migration
{
  "family": "liquibase-migration",
  "taskRoleArn": "...",
  "containerDefinitions": [{
    "name": "migration",
    "image": "stackmentor-backend:v1.2.3",
    "command": ["./gradlew", "liquibaseUpdate"],
    "environment": [
      {"name": "SPRING_PROFILES_ACTIVE", "value": "production"}
    ]
  }]
}

# Run before deploying new app version
aws ecs run-task \
  --cluster stackmentor-prod \
  --task-definition liquibase-migration \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[...],securityGroups=[...]}"
```

**Wait for completion**, then deploy app.

**Option B: Run on app startup** (simpler but risky):

- Liquibase runs automatically on Spring Boot startup
- **Risk**: Multiple ECS tasks start simultaneously, race to apply migrations (locks database table, others wait or timeout)
- **Mitigation**: Use Liquibase's DATABASECHANGELOGLOCK table (built-in locking)

**Recommendation**: Separate job for critical migrations, startup for minor changes (adding indexes). Separate job allows monitoring migration progress, faster rollback if needed.

---

### **Phase 7: Authentication Integration (Cognito + Spring Security)** (1 week)

**Goal**: Secure API endpoints with JWT tokens issued by Cognito. Only authenticated users access protected resources.

#### Step 7.1: Cognito User Pool Configuration

**Already created in Phase 2 IaC, now configure**:

**App Client settings**:

- Auth flows: USER_PASSWORD_AUTH (username/password), REFRESH_TOKEN_AUTH
- Token expiration: Access 1 hour, ID token 1 hour, Refresh 30 days
- Read/write attributes: email, name, phone_number, custom:profilePictureUrl

**Lambda triggers** (optional but recommended):
```javascript
  // Lambda function
  exports.handler = async (event) => {
    const { userAttributes } = event.request;
    
    // Call your API to create user
    await fetch('https://api-internal.stackmentor.com/internal/users', {
      method: 'POST',
      headers: { 'X-Internal-Secret': process.env.INTERNAL_SECRET },
      body: JSON.stringify({
        cognitoId: event.userName,
        email: userAttributes.email,
        name: userAttributes.name
      })
    });
    
    return event;
  };
```

- **Why**: Keeps your database in sync with Cognito automatically. No manual user creation after signup.

#### Step 7.2: Spring Security Configuration

```kotlin
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.security:spring-security-oauth2-jose")
implementation("org.springframework.security:spring-security-oauth2-resource-server")
```

**Security configuration**:
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // Not needed for stateless JWT
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );
        
        return http.build();
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        // Validates JWT signature using Cognito's public keys
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }
    
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Extract roles from Cognito groups
            List<String> groups = jwt.getClaimAsStringList("cognito:groups");
            if (groups == null) return Collections.emptyList();
            
            return groups.stream()
                .map(group -> new SimpleGrantedAuthority("ROLE_" + group))
                .collect(Collectors.toList());
        });
        return converter;
    }
}
```

**`application.yml` addition**:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://cognito-idp.us-east-1.amazonaws.com/${COGNITO_USER_POOL_ID}
          # Spring fetches public keys from /.well-known/jwks.json automatically
```

**How JWT validation works**:

1. Frontend sends request with `Authorization: Bearer <jwt_token>` header
2. Spring Security intercepts request, extracts token
3. JwtDecoder fetches Cognito's public keys (cached after first request)
4. Verifies token signature (proves Cognito issued it, not tampered)
5. Checks expiration (exp claim < current time = reject)
6. Checks audience (aud claim matches app client ID)
7. Extracts user ID from `sub` claim (Cognito user UUID)
8. Populates SecurityContext with authenticated principal

**Why JWT**: Stateless (no server-side session storage), scales horizontally (any ECS instance validates), secure (cryptographic signature prevents forgery).

#### Step 7.3: User Principal Extraction

**Create UserPrincipal**:
```java
public record UserPrincipal(String id, String email, List<String> roles) {
    
    public static UserPrincipal fromJwt(Jwt jwt) {
        return new UserPrincipal(
            jwt.getSubject(),  // Cognito user UUID
            jwt.getClaimAsString("email"),
            jwt.getClaimAsStringList("cognito:groups")
        );
    }
    
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
```

**Argument resolver** (inject into controllers):
```java
@Component
public class UserPrincipalArgumentResolver implements HandlerMethodArgumentResolver {
    
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(UserPrincipal.class);
    }
    
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException("No authentication");
        }
        return UserPrincipal.fromJwt(jwt);
    }
}
```

**Register resolver**:
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new UserPrincipalArgumentResolver());
    }
}
```

**Usage in controller**:
```java
@PostMapping("/messages")
public MessageDto sendMessage(UserPrincipal user, @Valid @RequestBody SendMessageRequest req) {
    return messageService.sendMessage(user.id(), req);
}
```

**Why custom principal**: Cleaner than extracting JWT claims in every controller. Type-safe access to user properties. Easy to mock in tests.

#### Step 7.4: Role-Based Access Control

**Add roles in Cognito Groups**:

- Group "admins" with role
- Group "moderators" with role
- Regular users have no group

**Protect endpoints**:
```java
@DeleteMapping("/groups/{groupId}")
@PreAuthorize("hasRole('ROLE_admins')")
public void deleteGroup(@PathVariable String groupId) {
    groupService.deleteGroup(groupId);
}
```

**Or check in service**:
```java
public void deleteMessage(String messageId, UserPrincipal user) {
    Message message = messageRepo.findById(messageId).orElseThrow();
    
    // Only sender or admin can delete
    if (!message.getSenderId().equals(user.id()) && !user.hasRole("admins")) {
        throw new ForbiddenException("Cannot delete message");
    }
    
    messageRepo.delete(message);
}
```

**Why groups/roles**: Flexible permissions without code changes. Add user to "moderators" group in Cognito console, immediately gains powers. Scales better than hardcoding user IDs.

#### Step 7.5: Testing Authentication Locally

**Option A: Create test user in Cognito**:
```bash
aws cognito-idp sign-up \
  --client-id <APP_CLIENT_ID> \
  --username testuser@example.com \
  --password Test1234! \
  --user-attributes Name=email,Value=testuser@example.com

# Confirm user (admin command, skips email verification)
aws cognito-idp admin-confirm-sign-up \
  --user-pool-id <USER_POOL_ID> \
  --username testuser@example.com

# Get JWT token
aws cognito-idp initiate-auth \
  --client-id <APP_CLIENT_ID> \
  --auth-flow USER_PASSWORD_AUTH \
  --auth-parameters USERNAME=testuser@example.com,PASSWORD=Test1234!
```

**Option B: Mock JWT in tests**:
```java
@Test
void sendMessage_withValidToken_succeeds() {
    String token = "mock-jwt-token";
    
    mockMvc.perform(post("/api/messages")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "conversationId": "conv123",
              "text": "Hello"
            }
            """))
        .andExpect(status().isCreated());
}
```

**Use Spring Security test utilities**:
```java
@Test
@WithMockUser(username = "user123", roles = {"USER"})
void getMessage_authenticated_succeeds() {
    mockMvc.perform(get("/api/messages?conversationId=conv123"))
        .andExpect(status().isOk());
}
```

**Why**: Testing with real Cognito slow (network calls). Mocking faster, tests auth logic without external dependency.

---

### **Phase 8: WebSocket Real-Time Messaging** (1-2 weeks)

**Goal**: Push messages to connected clients instantly. Enable chat experience without polling.

#### Step 8.1: WebSocket Configuration

**Add dependency**:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-websocket")
```

**Configure STOMP over WebSocket**:
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory broker for /topic and /queue destinations
        config.enableSimpleBroker("/topic", "/queue");
        
        // Application destination prefix for @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
        
        // User-specific destination prefix
        config.setUserDestinationPrefix("/user");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")  // Configure CORS
            .withSockJS();  // Fallback for browsers without WebSocket support
    }
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Add interceptor for authentication
        registration.interceptors(new AuthChannelInterceptor());
    }
}
```

**WebSocket endpoints**:
- `/ws`: WebSocket handshake endpoint
- `/app/message.send`: Client sends message (handled by @MessageMapping)
- `/topic/conversation.{conversationId}`: Server broadcasts to all conversation participants
- `/user/queue/notification`: Server sends to specific user

#### Step 8.2: Authentication Interceptor

**Authenticate WebSocket connections using JWT**:
```java
public class AuthChannelInterceptor implements ChannelInterceptor {
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Extract token from header
            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                String jwt = token.substring(7);
                
                // Validate JWT (same as REST API)
                try {
                    Jwt decodedJwt = jwtDecoder.decode(jwt);
                    UserPrincipal user = UserPrincipal.fromJwt(decodedJwt);
                    
                    // Store in session attributes
                    accessor.setUser(new UserPrincipalPrincipal(user));
                } catch (JwtException e) {
                    throw new UnauthorizedException("Invalid token");
                }
            } else {
                throw new UnauthorizedException("Missing token");
            }
        }
        
        return message;
    }
}
```

**Why**: Prevents unauthorized WebSocket connections. User can only subscribe to their own conversations. Token validation reuses same logic as REST API.

#### Step 8.3: Message Handling

**Controller for incoming WebSocket messages**:
```java
@Controller
public class WebSocketMessageController {
    
    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    
    @MessageMapping("/message.send")
    public void sendMessage(
        @Payload SendMessageRequest request,
        @AuthenticationPrincipal UserPrincipal user
    ) {
        // Save to database
        MessageDto message = messageService.sendMessage(user.id(), request);
        
        // Broadcast to conversation participants
        messagingTemplate.convertAndSend(
            "/topic/conversation." + request.conversationId(),
            message
        );
        
        // Send typing stopped notification
        messagingTemplate.convertAndSend(
            "/topic/conversation." + request.conversationId() + ".typing",
            new TypingEvent(user.id(), false)
        );
    }
    
    @MessageMapping("/typing.start")
    public void startTyping(
        @Payload TypingRequest request,
        @AuthenticationPrincipal UserPrincipal user
    ) {
        // Don't save to database, just broadcast
        messagingTemplate.convertAndSend(
            "/topic/conversation." + request.conversationId() + ".typing",
            new TypingEvent(user.id(), true)
        );
    }
}
```

**Why @MessageMapping**: Similar to @RequestMapping for REST. Maps WebSocket destination to handler method. SimpMessagingTemplate sends messages to subscribed clients.

#### Step 8.4: Redis Pub/Sub for Horizontal Scaling

**Problem**: User A connects to ECS task 1, User B connects to ECS task 2. When A sends message, task 1 broadcasts via SimpMessagingTemplate, but task 2 doesn't know (B doesn't receive message).

**Solution**: Publish messages to Redis channel, all ECS tasks subscribe and relay to their WebSocket clients.

**Add dependency**:
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
**Redis configuration**:
```java
@Configuration
public class RedisConfig {
    
    @Bean
    public RedisMessageListenerContainer redisContainer(
        RedisConnectionFactory connectionFactory,
        MessageListener messageListener
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageListener, new PatternTopic("conversation.*"));
        return container;
    }
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setDefaultSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        return template;
    }
}
```

**Publish to Redis when message sent**:
```java
@Service 
public class MessageService { 

	private final RedisTemplate<String, Object> redisTemplate; 
	
	public MessageDto sendMessage(String senderId, SendMessageRequest request) { 
		// Save to database 
		Message message = messageRepo.save(/* ... */); 
		MessageDto dto = MessageDto.from(message); 
		// Publish to Redis (all ECS tasks receive) 
		redisTemplate.convertAndSend("conversation." + request.conversationId(), dto);
		return dto; 
	}
}
```
**Subscribe and relay to WebSocket clients**:
```java
@Component
public class RedisMessageListener implements MessageListener {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            MessageDto dto = objectMapper.readValue(message.getBody(), MessageDto.class);
            
            // Extract conversation ID from channel name
            String conversationId = channel.replace("conversation.", "");
            
            // Broadcast to local WebSocket clients
            messagingTemplate.convertAndSend("/topic/conversation." + conversationId, dto);
            
        } catch (Exception e) {
            log.error("Failed to process Redis message", e);
        }
    }
}
```

**Flow**:
1. User A (on task 1) sends message via WebSocket
2. Task 1 saves to database, publishes to Redis channel `conversation.123`
3. Redis broadcasts to all subscribed tasks (1, 2, 3, ...)
4. Each task receives from Redis, broadcasts via SimpMessagingTemplate to its connected WebSocket clients
5. User B (on task 2) receives message in real-time

**Why Redis Pub/Sub**: Simple broadcast pattern. Low latency (< 10ms). Alternative is sticky sessions (route user to same ECS task always) but breaks load balancing.

#### Step 8.5: Load Balancer Configuration for WebSockets

**ALB requirements**:

- Target group with WebSocket support (enabled by default for HTTP/HTTPS)
- Idle timeout > WebSocket keep-alive interval
- Sticky sessions optional (not required with Redis Pub/Sub)

**Terraform config**:
```hcl
resource "aws_lb_target_group" "backend" {
  name     = "stackmentor-backend-tg"
  port     = 8080
  protocol = "HTTP"
  vpc_id   = aws_vpc.main.id
  
  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
  }
  
  # Important for WebSockets
  deregistration_delay = 30
  
  # Idle timeout must exceed client keep-alive
  # Default is 60s, increase if using longer keep-alive
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = "443"
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS-1-2-2017-01"
  certificate_arn   = aws_acm_certificate.cert.arn
  
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}
```

**Why idle timeout matters**: WebSocket connection stays open for hours. If ALB timeout (60s) < client keep-alive (120s), ALB closes connection thinking it's idle. Client must send ping/pong frames every 30-45s.

#### Step 8.6: Testing WebSockets

**Local testing with wscat**:
```bash
# Install
npm install -g wscat

# Connect
wscat -c ws://localhost:8080/ws \
  -H "Authorization: Bearer <jwt_token>"

# After CONNECTED frame, subscribe to conversation
> SUBSCRIBE
> id:sub-1
> destination:/topic/conversation.123
>
> ^@

# Send message
> SEND
> destination:/app/message.send
> content-type:application/json
>
> {"conversationId":"123","text":"Hello"}
> ^@

# Receive broadcast
< MESSAGE
< destination:/topic/conversation.123
< content-type:application/json

< {"id":"msg456","conversationId":"123","text":"Hello",...}
```

**Integration test**:
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @Test
    void sendMessage_broadcastsToSubscribers() throws Exception {
        String url = "ws://localhost:" + port + "/ws";
        
        WebSocketStompClient stompClient = new WebSocketStompClient(
            new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())))
        );
        
        CompletableFuture<MessageDto> future = new CompletableFuture<>();
        
        StompSession session = stompClient.connect(url, new StompSessionHandlerAdapter() {}).get();
        
        session.subscribe("/topic/conversation.123", new StompFrameHandler() {
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                future.complete((MessageDto) payload);
            }
        });
        
        session.send("/app/message.send", new SendMessageRequest("123", "Test"));
        
        MessageDto received = future.get(5, TimeUnit.SECONDS);
        assertEquals("Test", received.text());
    }
}
```

**Why test WebSocket**: Easy to break with configuration changes (CORS, authentication, Redis). Integration test catches before production.

---

### **Phase 9: Frontend Development (React Native)** (3-4 weeks)

**Goal**: Build mobile app that consumes backend API and WebSocket endpoints.

#### Step 9.1: Project Setup

**Initialize React Native**:
```bash
npx react-native@latest init StackMentorApp --template react-native-template-typescript
cd StackMentorApp
```

**Install dependencies**:
```bash
npm install @react-navigation/native @react-navigation/native-stack
npm install react-native-screens react-native-safe-area-context
npm install axios @stomp/stompjs react-native-url-polyfill
npm install @react-native-async-storage/async-storage  # Secure token storage
npm install react-native-dotenv  # Environment variables
```

**Configure environment**:
```bash
# .env.development
API_BASE_URL=http://localhost:8080/api
WS_URL=ws://localhost:8080/ws

# .env.staging
API_BASE_URL=https://staging-api.stackmentor.com/api
WS_URL=wss://staging-api.stackmentor.com/ws

# .env.production
API_BASE_URL=https://api.stackmentor.com/api
WS_URL=wss://api.stackmentor.com/ws
```

**TypeScript configuration** (`tsconfig.json`):
```json
{
  "compilerOptions": {
    "target": "esnext",
    "module": "commonjs",
    "lib": ["es2017"],
    "allowJs": true,
    "jsx": "react-native",
    "strict": true,
    "moduleResolution": "node",
    "baseUrl": "./src",
    "paths": {
      "@/*": ["./*"]
    },
    "esModuleInterop": true,
    "skipLibCheck": true
  }
}
```

#### Step 9.2: API Client (Generate from OpenAPI)

**Install OpenAPI Generator**:
```bash
npm install --save-dev @openapitools/openapi-generator-cli
```

**Generate TypeScript client**:
```bash
openapi-generator-cli generate \
  -i ../backend/openapi.yaml \
  -g typescript-axios \
  -o src/api/generated
```

**Wrapper for authentication**:
```typescript
// src/api/apiClient.ts
import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { API_BASE_URL } from '@env';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
});

// Add JWT token to all requests
apiClient.interceptors.request.use(async (config) => {
  const token = await AsyncStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;

  }
  return config;
});

// Handle token refresh on 401
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      
      try {
        const refreshToken = await AsyncStorage.getItem('refreshToken');
        if (!refreshToken) {
          throw new Error('No refresh token');
        }
        
        // Call Cognito to refresh token
        const response = await axios.post(
          `https://cognito-idp.us-east-1.amazonaws.com/`,
          {
            AuthFlow: 'REFRESH_TOKEN_AUTH',
            ClientId: COGNITO_CLIENT_ID,
            AuthParameters: {
              REFRESH_TOKEN: refreshToken,
            },
          }
        );
        
        const newAccessToken = response.data.AuthenticationResult.AccessToken;
        await AsyncStorage.setItem('accessToken', newAccessToken);
        
        // Retry original request with new token
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return apiClient(originalRequest);
        
      } catch (refreshError) {
        // Refresh failed, logout user
        await AsyncStorage.multiRemove(['accessToken', 'refreshToken']);
        // Navigate to login screen (use navigation ref)
        navigationRef.navigate('Login');
        return Promise.reject(refreshError);
      }
    }
    
    return Promise.reject(error);
  }
);

export default apiClient;
```

**Why generated client**: Type-safe API calls, autocomplete in IDE, automatically updates when OpenAPI spec changes. No manual typing of request/response interfaces.
**Why interceptors**: Centralized token management. Every API call automatically includes auth header. Transparent token refresh—components don't handle 401 errors manually.

#### Step 9.3: Authentication Flow

**Auth context** (manages login state):
```typescript
// src/contexts/AuthContext.tsx
import React, { createContext, useState, useEffect, useContext } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { CognitoIdentityProviderClient, InitiateAuthCommand } from '@aws-sdk/client-cognito-identity-provider';

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  signup: (email: string, password: string, name: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  
  const cognitoClient = new CognitoIdentityProviderClient({ region: 'us-east-1' });
  
  useEffect(() => {
    // Check if user already logged in
    loadUser();
  }, []);
  
  const loadUser = async () => {
    try {
      const token = await AsyncStorage.getItem('accessToken');
      if (token) {
        // Decode JWT to get user info (or fetch from API)
        const decoded = decodeJwt(token);
        setUser({
          id: decoded.sub,
          email: decoded.email,
        });
      }
    } catch (error) {
      console.error('Failed to load user', error);
    } finally {
      setIsLoading(false);
    }
  };
  
  const login = async (email: string, password: string) => {
    const command = new InitiateAuthCommand({
      AuthFlow: 'USER_PASSWORD_AUTH',
      ClientId: COGNITO_CLIENT_ID,
      AuthParameters: {
        USERNAME: email,
        PASSWORD: password,
      },
    });
    
    const response = await cognitoClient.send(command);
    
    const { AccessToken, RefreshToken, IdToken } = response.AuthenticationResult!;
    
    await AsyncStorage.multiSet([
      ['accessToken', AccessToken!],
      ['refreshToken', RefreshToken!],
      ['idToken', IdToken!],
    ]);
    
    const decoded = decodeJwt(AccessToken!);
    setUser({
      id: decoded.sub,
      email: decoded.email,
    });
  };
  
  const logout = async () => {
    await AsyncStorage.multiRemove(['accessToken', 'refreshToken', 'idToken']);
    setUser(null);
  };
  
  const signup = async (email: string, password: string, name: string) => {
    const command = new SignUpCommand({
      ClientId: COGNITO_CLIENT_ID,
      Username: email,
      Password: password,
      UserAttributes: [
        { Name: 'email', Value: email },
        { Name: 'name', Value: name },
      ],
    });
    
    await cognitoClient.send(command);
    // User must confirm email before logging in
  };
  
  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout, signup }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};
```

**Login screen**:
```typescript
// src/screens/LoginScreen.tsx
import React, { useState } from 'react';
import { View, TextInput, Button, Text } from 'react-native';
import { useAuth } from '@/contexts/AuthContext';

export const LoginScreen: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const { login } = useAuth();
  
  const handleLogin = async () => {
    try {
      setError('');
      await login(email, password);
      // Navigation handled by auth state change
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    }
  };
  
  return (
    <View style={styles.container}>
      <TextInput
        placeholder="Email"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        keyboardType="email-address"
        style={styles.input}
      />
      <TextInput
        placeholder="Password"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        style={styles.input}
      />
      {error && <Text style={styles.error}>{error}</Text>}
      <Button title="Login" onPress={handleLogin} />
    </View>
  );
};
```

**Why context**: Shares auth state across entire app. Any component can access user info, login/logout functions. Re-renders components when auth state changes.

**Why AsyncStorage**: Persists tokens across app restarts. User stays logged in. Secure on iOS (Keychain), Android (EncryptedSharedPreferences).

#### Step 9.4: Navigation Setup

**App navigation structure**:
```typescript
// src/navigation/AppNavigator.tsx
import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useAuth } from '@/contexts/AuthContext';

import { LoginScreen } from '@/screens/LoginScreen';
import { SignupScreen } from '@/screens/SignupScreen';
import { ConversationListScreen } from '@/screens/ConversationListScreen';
import { ConversationScreen } from '@/screens/ConversationScreen';
import { NewConversationScreen } from '@/screens/NewConversationScreen';

const Stack = createNativeStackNavigator();

export const AppNavigator: React.FC = () => {
  const { user, isLoading } = useAuth();
  
  if (isLoading) {
    return <LoadingScreen />;
  }
  
  return (
    <NavigationContainer>
      <Stack.Navigator>
        {user ? (
          // Authenticated screens
          <>
            <Stack.Screen 
              name="ConversationList" 
              component={ConversationListScreen}
              options={{ title: 'Messages' }}
            />
            <Stack.Screen 
              name="Conversation" 
              component={ConversationScreen}
              options={({ route }) => ({ title: route.params.conversationName })}
            />
            <Stack.Screen 
              name="NewConversation" 
              component={NewConversationScreen}
              options={{ title: 'New Message' }}
            />
          </>
        ) : (
          // Unauthenticated screens
          <>
            <Stack.Screen name="Login" component={LoginScreen} />
            <Stack.Screen name="Signup" component={SignupScreen} />
          </>
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
};
```

**Why conditional rendering**: Prevents showing protected screens to logged-out users. No need for auth guards on every screen. Navigation state resets on login/logout.

#### Step 9.5: WebSocket Connection

**WebSocket service**:
```typescript
// src/services/websocketService.ts
import { Client, StompConfig } from '@stomp/stompjs';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { WS_URL } from '@env';

class WebSocketService {
  private client: Client | null = null;
  private subscriptions: Map<string, any> = new Map();
  
  async connect(): Promise<void> {
    const token = await AsyncStorage.getItem('accessToken');
    if (!token) throw new Error('No access token');
    
    const config: StompConfig = {
      brokerURL: WS_URL,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: (str) => console.log('STOMP:', str),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    };
    
    this.client = new Client(config);
    
    return new Promise((resolve, reject) => {
      this.client!.onConnect = () => {
        console.log('WebSocket connected');
        resolve();
      };
      
      this.client!.onStompError = (frame) => {
        console.error('STOMP error', frame);
        reject(new Error(frame.headers.message));
      };
      
      this.client!.activate();
    });
  }
  
  disconnect(): void {
    this.subscriptions.forEach((sub) => sub.unsubscribe());
    this.subscriptions.clear();
    this.client?.deactivate();
    this.client = null;
  }
  
  subscribeToConversation(
    conversationId: string, 
    onMessage: (message: MessageDto) => void
  ): () => void {
    if (!this.client?.connected) {
      throw new Error('WebSocket not connected');
    }
    
    const subscription = this.client.subscribe(
      `/topic/conversation.${conversationId}`,
      (message) => {
        const dto = JSON.parse(message.body) as MessageDto;
        onMessage(dto);
      }
    );
    
    const key = `conversation-${conversationId}`;
    this.subscriptions.set(key, subscription);
    
    // Return unsubscribe function
    return () => {
      subscription.unsubscribe();
      this.subscriptions.delete(key);
    };
  }
  
  sendMessage(conversationId: string, text: string): void {
    if (!this.client?.connected) {
      throw new Error('WebSocket not connected');
    }
    
    this.client.publish({
      destination: '/app/message.send',
      body: JSON.stringify({ conversationId, text }),
    });
  }
  
  sendTypingIndicator(conversationId: string, isTyping: boolean): void {
    if (!this.client?.connected) return;
    
    this.client.publish({
      destination: isTyping ? '/app/typing.start' : '/app/typing.stop',
      body: JSON.stringify({ conversationId }),
    });
  }
}

export const websocketService = new WebSocketService();
```

**Usage in conversation screen**:
```typescript
// src/screens/ConversationScreen.tsx
import React, { useState, useEffect, useRef } from 'react';
import { FlatList, TextInput, Button } from 'react-native';
import { websocketService } from '@/services/websocketService';
import { useAuth } from '@/contexts/AuthContext';

export const ConversationScreen: React.FC<{ route }> = ({ route }) => {
  const { conversationId } = route.params;
  const [messages, setMessages] = useState<MessageDto[]>([]);
  const [inputText, setInputText] = useState('');
  const { user } = useAuth();
  
  useEffect(() => {
    // Load initial messages from API
    loadMessages();
    
    // Connect WebSocket and subscribe
    websocketService.connect().then(() => {
      const unsubscribe = websocketService.subscribeToConversation(
        conversationId,
        (newMessage) => {
          setMessages((prev) => [...prev, newMessage]);
          // Mark as read if not from current user
          if (newMessage.senderId !== user?.id) {
            markAsRead(newMessage.id);
          }
        }
      );
      
      // Cleanup on unmount
      return () => {
        unsubscribe();
      };
    });
  }, [conversationId]);
  
  const loadMessages = async () => {
    const response = await apiClient.get(`/messages`, {
      params: { conversationId, page: 0, size: 50 },
    });
    setMessages(response.data.content);
  };
  
  const sendMessage = () => {
    if (!inputText.trim()) return;
    
    websocketService.sendMessage(conversationId, inputText);
    setInputText('');
  };
  
  const handleTyping = (text: string) => {
    setInputText(text);
    
    // Debounce typing indicator
    if (typingTimeoutRef.current) {
      clearTimeout(typingTimeoutRef.current);
    }
    
    websocketService.sendTypingIndicator(conversationId, true);
    
    typingTimeoutRef.current = setTimeout(() => {
      websocketService.sendTypingIndicator(conversationId, false);
    }, 2000);
  };
  
  return (
    <View style={styles.container}>
      <FlatList
        data={messages}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => <MessageBubble message={item} isOwn={item.senderId === user?.id} />}
        inverted  // Latest message at bottom
      />
      <View style={styles.inputContainer}>
        <TextInput
          value={inputText}
          onChangeText={handleTyping}
          placeholder="Type a message..."
          style={styles.input}
        />
        <Button title="Send" onPress={sendMessage} />
      </View>
    </View>
  );
};
```

**Why WebSocket in app**: Real-time experience. No polling (battery drain, network waste). Push notifications complement for background messages (Phase 11).

**Why reconnect logic**: Mobile networks unstable (WiFi → cellular transition). Automatic reconnection maintains session without user intervention.

#### Step 9.6: State Management (Optional: Redux/Zustand)

**For complex state, use Zustand** (simpler than Redux)
```typescript
// src/stores/conversationStore.ts
import create from 'zustand';
import { apiClient } from '@/api/apiClient';

interface ConversationStore {
  conversations: Conversation[];
  isLoading: boolean;
  error: string | null;
  fetchConversations: () => Promise<void>;
  markConversationRead: (conversationId: string) => void;
}

export const useConversationStore = create<ConversationStore>((set, get) => ({
  conversations: [],
  isLoading: false,
  error: null,
  
  fetchConversations: async () => {
    set({ isLoading: true, error: null });
    try {
      const response = await apiClient.get('/conversations');
      set({ conversations: response.data, isLoading: false });
    } catch (error) {
      set({ error: error.message, isLoading: false });
    }
  },
  
  markConversationRead: (conversationId: string) => {
    set((state) => ({
      conversations: state.conversations.map((conv) =>
        conv.id === conversationId ? { ...conv, unreadCount: 0 } : conv
      ),
    }));
  },
}));
```

**Usage**:
```typescript
const ConversationListScreen: React.FC = () => {
  const { conversations, isLoading, fetchConversations } = useConversationStore();
  
  useEffect(() => {
    fetchConversations();
  }, []);
  
  if (isLoading) return <LoadingSpinner />;
  
  return (
    <FlatList
      data={conversations}
      renderItem={({ item }) => <ConversationItem conversation={item} />}
    />
  );
};
```

**Why Zustand**: Lightweight (1kb), no boilerplate, TypeScript support, works with React hooks naturally. Overkill for small apps—use React Context + useState for simpler cases.

#### Step 9.7: Component Testing

**Install testing library**:
```bash
npm install --save-dev @testing-library/react-native @testing-library/jest-native
```

**Test example**:
```typescript
// src/screens/__tests__/LoginScreen.test.tsx
import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { LoginScreen } from '../LoginScreen';
import { AuthProvider } from '@/contexts/AuthContext';

jest.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({
    login: jest.fn().mockResolvedValue(undefined),
  }),
}));

describe('LoginScreen', () => {
  it('renders login form', () => {
    const { getByPlaceholderText, getByText } = render(
      <AuthProvider>
        <LoginScreen />
      </AuthProvider>
    );
    
    expect(getByPlaceholderText('Email')).toBeTruthy();
    expect(getByPlaceholderText('Password')).toBeTruthy();
    expect(getByText('Login')).toBeTruthy();
  });
  
  it('calls login with email and password', async () => {
    const mockLogin = jest.fn();
    jest.spyOn(require('@/contexts/AuthContext'), 'useAuth').mockReturnValue({
      login: mockLogin,
    });
    
    const { getByPlaceholderText, getByText } = render(<LoginScreen />);
    
    fireEvent.changeText(getByPlaceholderText('Email'), 'test@example.com');
    fireEvent.changeText(getByPlaceholderText('Password'), 'password123');
    fireEvent.press(getByText('Login'));
    
    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith('test@example.com', 'password123');
    });
  });
});
```

**Run tests**:
```bash
npm test
```

**Why component tests**: Catch UI regressions (button disappeared, form validation broken). Faster than manual testing. Document expected behavior.

---

### **Phase 10: Containerization & ECS Deployment** (1 week)

**Goal**: Package backend as Docker image, deploy to ECS Fargate for production hosting.

#### Step 10.1: Production Dockerfile

**Create optimized multi-stage build**:
```dockerfile
# Build stage
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Copy only build files first (cache layer)
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Download dependencies (cached if build files unchanged)
RUN gradle dependencies --no-daemon

# Copy source code
COPY src ./src

# Build JAR
RUN gradle bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

**Key optimizations**:

- **Multi-stage**: Build in full JDK image (larger), run in JRE-only (200MB smaller)
- **Layer caching**: Dependencies downloaded only when build.gradle changes
- **Non-root user**: Security best practice (prevents container escape attacks)
- **Alpine base**: Minimal image size (40MB vs 200MB for Ubuntu-based)
- **JVM flags**: `-XX:MaxRAMPercentage=75.0` limits heap to 75% of container memory (prevents OOMKilled)
- **Health check**: Docker/ECS monitors app health, restarts if unhealthy

**Why multi-stage**: Final image 150MB vs 800MB without. Faster pulls, cheaper storage, smaller attack surface.

#### Step 10.2: Build and Push to ECR

**Tag and push**:
bash

```bash
# Authenticate Docker to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Build image
docker build -t stackmentor-backend:v1.0.0 .

# Tag for ECR
docker tag stackmentor-backend:v1.0.0 <account-id>.dkr.ecr.us-east-1.amazonaws.com/stackmentor-backend:v1.0.0
docker tag stackmentor-backend:v1.0.0 <account-id>.dkr.ecr.us-east-1.amazonaws.com/stackmentor-backend:latest

# Push
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/stackmentor-backend:v1.0.0
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/stackmentor-backend:latest
```

**Automated in CI** (already in Phase 5 workflow).

#### Step 10.3: ECS Task Definition

**Create in Terraform**:
```hcl
resource "aws_ecs_task_definition" "backend" {
  family                   = "stackmentor-backend"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"   # 0.5 vCPU
  memory                   = "1024"  # 1 GB
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn
  
  container_definitions = jsonencode([
    {
      name      = "backend"
      image     = "${aws_ecr_repository.backend.repository_url}:latest"
      essential = true
      
      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]
      
      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "production"
        },
        {
          name  = "SPRING_REDIS_HOST"
          value = aws_elasticache_cluster.redis.cache_nodes[0].address
        }
      ]
      
      secrets = [
        {
          name      = "SPRING_DATASOURCE_URL"
          valueFrom = "${aws_secretsmanager_secret.db_url.arn}"
        },
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = "${aws_secretsmanager_secret.db_password.arn}"
        }
      ]
      
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = "/ecs/stackmentor-backend"
          "awslogs-region"        = "us-east-1"
          "awslogs-stream-prefix" = "ecs"
        }
      }
      
      healthCheck = {
        command     = ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])
}
```

**Key elements**:

- **Fargate**: Serverless compute (no EC2 management)
- **awsvpc network mode**: Each task gets own ENI (network interface) with security group
- **CPU/memory**: Start small (512 CPU / 1GB RAM ~ $35/month). Scale based on load.
- **Secrets from Secrets Manager**: Never plaintext passwords in task definition
- **Health check**: ECS stops routing traffic to unhealthy tasks, starts replacement

**Why Fargate over EC2**: No patching, scaling, or capacity planning. Pay only for task runtime. Easier autoscaling.

#### Step 10.4: ECS Service Configuration
```hcl
resource "aws_ecs_service" "backend" {
  name            = "backend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = 2  # Start with 2 for HA
  launch_type     = "FARGATE"
  
  network_configuration {
    subnets         = aws_subnet.private[*].id
    security_groups = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false  # Private subnet uses NAT
  }
  
  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "backend"
    container_port   = 8080
  }
  
  deployment_configuration {
    maximum_percent         = 200  # Allow 2x tasks during deploy
    minimum_healthy_percent = 100  # Always keep 100% running
    
    deployment_circuit_breaker {
      enable   = true
      rollback = true  # Auto-rollback if deployment fails
    }
  }
  
  enable_ecs_managed_tags = true
  propagate_tags          = "SERVICE"
  
  depends_on = [aws_lb_listener.https]
}
```

**Deployment behavior**:

- Desired count 2 → ECS runs 2 tasks always
- On deployment: Start 2 new tasks (total 4 running at 200%), wait for healthy, stop 2 old tasks
- Circuit breaker: If new tasks fail health check 3 times, automatic rollback to previous version

**Why 2 minimum**: High availability. If one task crashes or AZ fails, other handles traffic. For production, 3+ tasks across 3 AZs.

#### Step 10.5: Auto Scaling
```hcl
resource "aws_appautoscaling_target" "ecs_target" {
  max_capacity       = 10
  min_capacity       = 2
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.backend.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "ecs_cpu" {
  name               = "cpu-autoscaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs_target.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs_target.service_namespace
  
  target_tracking_scaling_policy_configuration {
    target_value       = 70.0  # Target 70% CPU
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    scale_in_cooldown  = 300  # Wait 5 min before scaling down
    scale_out_cooldown = 60   # Wait 1 min before scaling up again
  }
}

resource "aws_appautoscaling_policy" "ecs_memory" {
  name               = "memory-autoscaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs_target.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs_target.service_namespace
  
  target_tracking_scaling_policy_configuration {
    target_value       = 80.0  # Target 80% memory
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
  }
}
```

**Scaling behavior**:

- CPU > 70% for 3 minutes → add 1 task
- Memory > 80% → add 1 task
- Metrics below target for 15 minutes → remove 1 task (gradual scale-down prevents flapping)

**Why target tracking**: Simple configuration, AWS handles scaling math. Alternative: step scaling for more control (add 2 tasks if CPU > 90%).

---

### **Phase 11: Monitoring, Logging & Alerting** (Ongoing from Phase 3)

**Goal**: Observable system. Know when things break before users complain.

#### Step 11.1: Structured Logging

**Logback configuration** (`src/main/resources/logback-spring.xml`):
```xml
<configuration>
  <springProfile name="production">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>requestId</includeMdcKeyName>
        <includeMdcKeyName>userId</includeMdcKeyName>
        <fieldNames>
          <timestamp>timestamp</timestamp>
          <message>message</message>
          <logger>logger</logger>
          <level>level</level>
        </fieldNames>
      </encoder>
    </appender>
    
    <root level="INFO">
      <appender-ref ref="CONSOLE" />
    </root>
  </springProfile>
</configuration>
```

**Add request ID filter**:
```java
@Component
public class RequestIdFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        ```java
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }
}
```

**Add user ID to MDC in authentication**:
```java
@Component
public class UserContextFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            MDC.put("userId", jwt.getSubject());
        }
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }
}
```

**Log example output**:
```json
{
  "timestamp": "2025-10-02T14:23:45.123Z",
  "level": "INFO",
  "logger": "io.stackmentor.service.MessageService",
  "message": "Message sent successfully",
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "userId": "user-123",
  "conversationId": "conv-456",
  "messageId": "msg-789"
}
```

**Why structured JSON**: CloudWatch Insights can query fields. Find all errors for specific user: `fields @timestamp, message | filter userId = "user-123" and level = "ERROR"`. Impossible with plain text logs.

**Why correlation IDs**: Trace single request through distributed system. Frontend generates requestId, passes to backend, backend passes to downstream services. All logs for that request have same ID.

#### Step 11.2: CloudWatch Insights Queries

**Saved queries** (create in CloudWatch console):

**Error rate by endpoint**:
```
fields @timestamp, message, endpoint
| filter level = "ERROR"
| stats count() by endpoint
| sort count desc
```

**Slow queries** (> 1 second):
```
fields @timestamp, userId, query, duration
| filter logger = "org.hibernate.SQL" and duration > 1000
| sort duration desc
| limit 20
```

**Failed login attempts**:
```
fields @timestamp, email, ipAddress
| filter logger = "io.stackmentor.service.AuthService" and message = "Login failed"
| stats count() by email
| sort count desc
```

**Active users (last 5 minutes)**:
```
fields userId
| filter @timestamp > ago(5m)
| stats count_distinct(userId)
```

**Why saved queries**: One-click debugging. During incident, run query to identify affected users, error patterns. Historical data for capacity planning.

#### Step 11.3: Custom Metrics

**Add Micrometer** (already included in spring-boot-starter-actuator):
```java
@Service
public class MessageService {
    
    private final MeterRegistry meterRegistry;
    private final Counter messagesCounter;
    private final Timer messageSendTimer;
    
    public MessageService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.messagesCounter = Counter.builder("messages.sent")
            .description("Total messages sent")
            .tag("type", "direct")
            .register(meterRegistry);
        this.messageSendTimer = Timer.builder("messages.send.duration")
            .description("Time to send message")
            .register(meterRegistry);
    }
    
    public MessageDto sendMessage(String senderId, SendMessageRequest request) {
        return messageSendTimer.record(() -> {
            // Business logic
            MessageDto result = /* ... */;
            messagesCounter.increment();
            return result;
        });
    }
}
```

**Expose to CloudWatch**:
```yaml
# application.yml
management:
  metrics:
    export:
      cloudwatch:
        namespace: StackMentor
        batch-size: 20
        step: 1m  # Send metrics every minute
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

**Dashboard metrics**:

- Request rate (requests/second)
- Error rate (errors/total requests %)
- P50, P95, P99 latency (milliseconds)
- Database connection pool usage
- JVM heap memory (used/max)
- Messages sent (count/minute)
- Active WebSocket connections

**Why custom metrics**: Business KPIs beyond infrastructure. Track feature usage (messages sent, groups created), conversion funnels (signup → verification → first message).

#### Step 11.4: Distributed Tracing (Optional but Recommended)

**Add AWS X-Ray** for cross-service tracing:
```kotlin
// build.gradle.kts
implementation("com.amazonaws:aws-xray-recorder-sdk-spring:2.14.0")
implementation("com.amazonaws:aws-xray-recorder-sdk-aws-sdk-v2:2.14.0")
```

**Configure**:
```java
@Configuration
public class XRayConfig {
    
    @Bean
    public Filter TracingFilter() {
        return new AWSXRayServletFilter("StackMentor");
    }
    
    @Bean
    public AWSXRayRecorderBuilder xrayRecorderBuilder() {
        return AWSXRayRecorderBuilder.standard()
            .withSegmentListener(new SLF4JSegmentListener())
            .withPlugin(new ECSPlugin())
            .withPlugin(new EC2Plugin());
    }
}
```

**Instrument database queries**:
```java
@Aspect
@Component
public class RepositoryTracingAspect {
    
    private static final AWSXRay recorder = AWSXRayRecorderBuilder.defaultRecorder();
    
    @Around("execution(* io.stackmentor.repository.*.*(..))")
    public Object traceRepository(ProceedingJoinPoint joinPoint) throws Throwable {
        Subsegment subsegment = recorder.beginSubsegment(joinPoint.getSignature().toShortString());
        subsegment.putMetadata("repository", joinPoint.getTarget().getClass().getSimpleName());
        
        try {
            return joinPoint.proceed();
        } catch (Exception e) {
            subsegment.addException(e);
            throw e;
        } finally {
            recorder.endSubsegment();
        }
    }
}
```

**X-Ray trace view**:
```
Request: POST /api/messages (250ms total)
├─ MessageController.sendMessage (5ms)
├─ MessageService.sendMessage (240ms)
│  ├─ ConversationRepository.findById (15ms) - PostgreSQL query
│  ├─ MessageRepository.save (180ms) - PostgreSQL insert
│  └─ RedisPublisher.publish (40ms) - Redis publish
└─ Response serialization (5ms)
```

**Why tracing**: Identifies bottlenecks. "Why is API slow?" → X-Ray shows 90% time in database query → optimize query or add index. Correlates errors across services.

#### Step 11.5: Alerting Strategy

**CloudWatch Alarms** (create in Terraform):

**High error rate**:
```hcl
resource "aws_cloudwatch_metric_alarm" "high_error_rate" {
  alarm_name          = "backend-high-error-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "5XXError"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 10  # More than 10 errors per minute
  alarm_description   = "High error rate detected"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  
  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
  }
}
```

**High CPU usage**:
```hcl
resource "aws_cloudwatch_metric_alarm" "high_cpu" {
  alarm_name          = "ecs-high-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 85  # 85% CPU for 3 minutes
  alarm_description   = "ECS tasks CPU usage too high"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  
  dimensions = {
    ServiceName = aws_ecs_service.backend.name
    ClusterName = aws_ecs_cluster.main.name
  }
}
```

**Database connection exhaustion**:
```hcl
resource "aws_cloudwatch_metric_alarm" "db_connections" {
  alarm_name          = "rds-high-connections"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = 180  # Out of 200 max
  alarm_description   = "Database connection pool nearly exhausted"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  
  dimensions = {
    DBInstanceIdentifier = aws_db_instance.postgres.id
  }
}
```

**Custom metric alarm** (message send failures):
```hcl
resource "aws_cloudwatch_metric_alarm" "message_send_failures" {
  alarm_name          = "message-send-failures"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "messages.send.failed"
  namespace           = "StackMentor"
  period              = 300  # 5 minutes
  statistic           = "Sum"
  threshold           = 5
  alarm_description   = "Message sending failing"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}
```

**SNS topic for notifications**:
```hcl
resource "aws_sns_topic" "alerts" {
  name = "stackmentor-alerts"
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = "ops@stackmentor.com"
}

resource "aws_sns_topic_subscription" "slack" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.slack_notifier.arn
}
```

**Alert levels**:

- **Critical** (P1): Service down, data loss risk → Page on-call engineer via PagerDuty
- **High** (P2): Degraded performance, elevated errors → Email + Slack
- **Medium** (P3): Resource warnings (disk 80% full) → Slack only
- **Low** (P4): Info (deployment succeeded) → Logs only

**Why layered alerting**: Not everything is urgent. Disk at 60% is info, 80% is warning, 90% is critical. Prevents alert fatigue.

#### Step 11.6: Dashboards

**Create CloudWatch Dashboard** (JSON definition):
```json
{
  "widgets": [
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/ApplicationELB", "RequestCount", { "stat": "Sum", "label": "Total Requests" }],
          [".", "TargetResponseTime", { "stat": "Average", "label": "Avg Latency" }],
          [".", "HTTPCode_Target_5XX_Count", { "stat": "Sum", "label": "5XX Errors" }]
        ],
        "period": 60,
        "stat": "Sum",
        "region": "us-east-1",
        "title": "API Health",
        "yAxis": { "left": { "min": 0 } }
      }
    },
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/ECS", "CPUUtilization", { "stat": "Average" }],
          [".", "MemoryUtilization", { "stat": "Average" }]
        ],
        "title": "ECS Resource Usage"
      }
    },
    {
      "type": "log",
      "properties": {
        "query": "SOURCE '/ecs/stackmentor-backend'\n| fields @timestamp, level, message\n| filter level = \"ERROR\"\n| sort @timestamp desc\n| limit 20",
        "region": "us-east-1",
        "title": "Recent Errors"
      }
    }
  ]
}
```

**SLO Dashboard** (business metrics):

- **Availability**: (Total requests - 5XX errors) / Total requests * 100% → Target: 99.9%
- **Latency**: P95 response time → Target: < 200ms
- **Throughput**: Messages sent per minute → Track growth

**Why dashboards**: Single pane of glass. During incident, quickly see what's abnormal. Post-incident, correlate metrics to identify root cause.

---

### **Phase 12: End-to-End Testing & Load Testing** (1 week before production)

**Goal**: Validate entire system works together. Ensure performance under load.

#### Step 12.1: E2E Testing with Detox

**Install Detox** (React Native):
```bash
npm install --save-dev detox
detox init -r jest
```

**Configure** (`.detoxrc.json`):
```json
{
  "testRunner": "jest",
  "runnerConfig": "e2e/config.json",
  "apps": {
    "ios": {
      "type": "ios.app",
      "binaryPath": "ios/build/Build/Products/Release-iphonesimulator/StackMentorApp.app",
      "build": "xcodebuild -workspace ios/StackMentorApp.xcworkspace -scheme StackMentorApp -configuration Release -sdk iphonesimulator -derivedDataPath ios/build"
    },
    "android": {
      "type": "android.apk",
      "binaryPath": "android/app/build/outputs/apk/release/app-release.apk",
      "build": "cd android && ./gradlew assembleRelease assembleAndroidTest -DtestBuildType=release"
    }
  },
  "devices": {
    "simulator": {
      "type": "ios.simulator",
      "device": { "type": "iPhone 15" }
    },
    "emulator": {
      "type": "android.emulator",
      "device": { "avdName": "Pixel_5_API_31" }
    }
  },
  "configurations": {
    "ios": {
      "device": "simulator",
      "app": "ios"
    },
    "android": {
      "device": "emulator",
      "app": "android"
    }
  }
}
```

**E2E test example**:
```typescript
// e2e/messaging.test.ts
describe('Messaging Flow', () => {
  beforeAll(async () => {
    await device.launchApp({
      newInstance: true,
      permissions: { notifications: 'YES' }
    });
  });
  
  it('should complete full messaging flow', async () => {
    // Login
    await element(by.id('email-input')).typeText('testuser@example.com');
    await element(by.id('password-input')).typeText('Test1234!');
    await element(by.id('login-button')).tap();
    
    // Wait for conversation list
    await waitFor(element(by.id('conversation-list')))
      .toBeVisible()
      .withTimeout(5000);
    
    // Start new conversation
    await element(by.id('new-conversation-button')).tap();
    await element(by.id('search-users-input')).typeText('Jane');
    await element(by.id('user-jane-doe')).tap();
    await element(by.id('start-conversation-button')).tap();
    
    // Send message
    await element(by.id('message-input')).typeText('Hello Jane!');
    await element(by.id('send-button')).tap();
    
    // Verify message appears
    await expect(element(by.text('Hello Jane!'))).toBeVisible();
    
    // Verify delivery status
    await waitFor(element(by.id('message-delivered-icon')))
      .toBeVisible()
      .withTimeout(3000);
  });
  
  it('should show typing indicator', async () => {
    // Assume already in conversation
    await element(by.id('message-input')).typeText('Test');
    
    // Backend simulates other user typing (use mock WebSocket in test env)
    await expect(element(by.text('Jane is typing...'))).toBeVisible();
  });
  
  it('should handle offline mode', async () => {
    // Disable network
    await device.disableSynchronization();
    await device.setURLBlacklist(['.*']);
    
    // Try to send message
    await element(by.id('message-input')).typeText('Offline message');
    await element(by.id('send-button')).tap();
    
    // Verify queued indicator
    await expect(element(by.id('message-pending-icon'))).toBeVisible();
    
    // Re-enable network
    await device.setURLBlacklist([]);
    await device.enableSynchronization();
    
    // Verify message sent
    await waitFor(element(by.id('message-delivered-icon')))
      .toBeVisible()
      .withTimeout(5000);
  });
});
```

**Run tests**:
```bash
# iOS
detox build --configuration ios
detox test --configuration ios

# Android
detox build --configuration android
detox test --configuration android
```

**Why E2E tests**: Catches integration issues (frontend expects field "messageId", backend sends "id"). Tests real user flows. Runs against staging environment.

#### Step 12.2: Load Testing with k6

**Install k6**:
```bash
brew install k6  # macOS
# or download from k6.io
```

**Load test script**:
```javascript
// load-test/messaging.js
import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');
const BASE_URL = __ENV.BASE_URL || 'https://staging-api.stackmentor.com';
const WS_URL = __ENV.WS_URL || 'wss://staging-api.stackmentor.com/ws';

// Test configuration
export const options = {
  stages: [
    { duration: '2m', target: 100 },   // Ramp up to 100 users
    { duration: '5m', target: 100 },   // Stay at 100 users
    { duration: '2m', target: 200 },   // Ramp to 200 users
    { duration: '5m', target: 200 },   // Stay at 200 users
    { duration: '2m', target: 0 },     // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% of requests under 500ms
    http_req_failed: ['rate<0.01'],    // Less than 1% errors
    errors: ['rate<0.05'],             // Less than 5% errors
  },
};

// Login and get JWT token
function login() {
  const payload = JSON.stringify({
    username: `testuser${__VU}@example.com`,  // __VU is virtual user ID
    password: 'Test1234!',
  });
  
  const res = http.post(`${BASE_URL}/auth/login`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  
  check(res, {
    'login successful': (r) => r.status === 200,
    'has access token': (r) => r.json('accessToken') !== undefined,
  });
  
  return res.json('accessToken');
}

// Main test scenario
export default function () {
  const token = login();
  const headers = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
  
  // Create conversation
  let res = http.post(
    `${BASE_URL}/api/conversations`,
    JSON.stringify({
      participantIds: [`user${__VU + 1}`],
      type: 'DIRECT',
    }),
    { headers }
  );
  
  check(res, {
    'conversation created': (r) => r.status === 201,
  }) || errorRate.add(1);
  
  const conversationId = res.json('id');
  
  // Send 10 messages
  for (let i = 0; i < 10; i++) {
    res = http.post(
      `${BASE_URL}/api/messages`,
      JSON.stringify({
        conversationId: conversationId,
        text: `Load test message ${i} from VU ${__VU}`,
      }),
      { headers }
    );
    
    check(res, {
      'message sent': (r) => r.status === 201,
      'message has ID': (r) => r.json('id') !== undefined,
    }) || errorRate.add(1);
    
    sleep(1);  // 1 second between messages
  }
  
  // Test WebSocket connection
  const wsUrl = `${WS_URL}?access_token=${token}`;
  const wsRes = ws.connect(wsUrl, { headers }, function (socket) {
    socket.on('open', () => {
      console.log('WebSocket connected');
      
      // Subscribe to conversation
      socket.send(JSON.stringify({
        type: 'SUBSCRIBE',
        destination: `/topic/conversation.${conversationId}`,
      }));
    });
    
    socket.on('message', (data) => {
      console.log('Received message:', data);
    });
    
    socket.on('error', (e) => {
      console.error('WebSocket error:', e);
      errorRate.add(1);
    });
    
    socket.setTimeout(() => {
      socket.close();
    }, 30000);  // Stay connected 30 seconds
  });
  
  check(wsRes, {
    'WebSocket connected': (r) => r && r.status === 101,
  });
  
  sleep(5);  // Wait 5 seconds before next iteration
}
```

**Run load test**:
```bash
# Against staging
k6 run --env BASE_URL=https://staging-api.stackmentor.com load-test/messaging.js

# Generate HTML report
k6 run --out json=results.json load-test/messaging.js
k6 report results.json --export results.html
```

**Analyze results**:
```
scenarios: (100.00%) 1 scenario, 200 max VUs, 16m30s max duration
✓ login successful.......................: 100.00% ✓ 12000 ✗ 0
✓ message sent...........................: 99.95%  ✓ 119940 ✗ 60
✓ WebSocket connected....................: 98.50%  ✓ 11820 ✗ 180

http_req_duration........................: avg=245ms  p(95)=420ms  p(99)=680ms
http_reqs................................: 132000  (220/s)
ws_sessions..............................: 12000   (20/s)
errors...................................: 240/132000 (0.18%)
```

**Identify bottlenecks**:

- P95 latency 420ms (under 500ms threshold ✓)
- Error rate 0.18% (under 1% threshold ✓)
- Database CPU at 85% during peak → Consider read replica
- ECS scaled to 8 tasks → Cost optimization: increase task size, reduce count

**Why load testing**: Prevents "launch day crash". Discovers bottlenecks in staging. Validates autoscaling configuration. Capacity planning (200 users = 8 tasks = $X/month).

#### Step 12.3: Security Scanning

**SAST (Static Analysis)**:
```bash
# Already in CI via SonarQube
./gradlew sonarqube
```

**DAST (Dynamic Application Security Testing)** with OWASP ZAP:
```bash
# Run ZAP baseline scan
docker run -t owasp/zap2docker-stable zap-baseline.py \
  -t https://staging-api.stackmentor.com \
  -r zap-report.html
```

**Dependency scanning** (already in CI via Dependabot/Snyk):
```bash
# Check for vulnerable dependencies
./gradlew dependencyCheckAnalyze
```

**Penetration testing** (hire professional or use AWS Inspector):
```hcl
resource "aws_inspector2_enabler" "inspector" {
  account_ids    = [data.aws_caller_identity.current.account_id]
  resource_types = ["ECR", "EC2", "LAMBDA"]
}
```

**Security checklist**:

- ✓ All API endpoints require authentication (except /health)
- ✓ JWT tokens have expiration (1 hour)
- ✓ Passwords hashed with bcrypt (handled by Cognito)
- ✓ SQL injection prevented (JPA parameterized queries)
- ✓ XSS prevented (React escapes by default)
- ✓ CSRF not applicable (stateless API)
- ✓ Rate limiting (via API Gateway)
- ✓ TLS 1.2+ only (ALB SSL policy)
- ✓ Security headers (add to Spring Boot)

**Add security headers**:
```java
@Configuration
public class SecurityHeadersConfig {
    
    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SecurityHeadersFilter());
        registration.addUrlPatterns("/*");
        return registration;
    }
}

public class SecurityHeadersFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
        throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
        httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        chain.doFilter(request, response);
    }
}
```

**Why security scanning**: Automates vulnerability detection. OWASP Top 10 coverage. Compliance requirements (SOC 2, PCI DSS if handling payments).

---

### **Phase 13: Production Deployment & Launch** (3-5 days)

**Goal**: Deploy to production with minimal risk. Have rollback plan ready.

#### Step 13.1: Pre-Deployment Checklist

**Infrastructure**:

- ✓ Production Terraform applied (VPC, RDS, Redis, ECS, ALB)
- ✓ DNS configured (api.stackmentor.com → ALB)
- ✓ SSL certificate valid
- ✓ Secrets Manager populated (database password, API keys)
- ✓ CloudWatch alarms configured
- ✓ Backups enabled (RDS automated, daily snapshots)

**Application**:

- ✓ All tests passing (unit, integration, E2E)
- ✓ Load testing completed (performance acceptable)
- ✓ Security scan passed (no critical vulnerabilities)
- ✓ Database migrations tested in staging
- ✓ Rollback plan documented

**Monitoring**:

- ✓ Dashboards created
- ✓ Alert recipients configured (email, Slack, PagerDuty)
- ✓ Log retention set (30 days production)
- ✓ X-Ray tracing enabled

**Documentation**:

- ✓ API documentation published (Swagger UI)
- ✓ Runbook created (how to deploy, rollback, debug)
- ✓ On-call rotation scheduled
- ✓ Incident response playbook

#### Step 13.2: Database Migration Execution

**Run migration job** (separate from app deployment):
```bash
# Create one-time ECS task
aws ecs run-task \
  --cluster stackmentor-prod \
  --task-definition liquibase-migration:5 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-abc123],securityGroups=[sg-xyz789],assignPublicIp=DISABLED}"

# Monitor logs
aws logs tail /ecs/liquibase-migration --follow
```

**Verify migration success**:
```bash
# Connect to database
psql -h prod-db.stackmentor.com -U admin -d stackmentor

# Check changelog
SELECT * FROM databasechangelog ORDER BY dateexecuted DESC LIMIT 5;

# Verify new tables/columns exist
\d messages
```

**If migration fails**:

- Don't deploy app (would crash on startup)
- Review error logs
- Fix changelog
- Test in staging again
- Retry migration

**Why separate migration**: App deployment takes 5-10 minutes (rolling update), migration takes 30 seconds. If migration during app startup, some old tasks crash (expect old schema), some new tasks crash (migration locked by first task). Separate migration ensures schema ready before app update.

#### Step 13.3: Blue/Green Deployment

**ECS blue/green via CodeDeploy**:
```hcl
resource "aws_codedeploy_app" "backend" {
  name             = "stackmentor-backend"
  compute_platform = "ECS"
}

resource "aws_codedeploy_deployment_group" "backend" {
  app_name               = aws_codedeploy_app.backend.name
  deployment_group_name  = "backend-deployment-group"
  service_role_arn       = aws_iam_role.codedeploy.arn
  deployment_config_name = "CodeDeployDefault.ECSCanary10Percent5Minutes"
  
  blue_green_deployment_config {
    terminate_blue_instances_on_deployment_success {
      action                           = "TERMINATE"
      termination_wait_time_in_minutes = 5
    }
    
    deployment_ready_option {
      action_on_timeout = "CONTINUE_DEPLOYMENT"
    }
    
    green_fleet_provisioning_option {
      action = "COPY_AUTO_SCALING_GROUP"
    }
  }
  
  ecs_service {
    cluster_name = aws_ecs_cluster.main.name
    service_name = aws_ecs_service.backend.name
  }
  
  load_balancer_info {
    target_group_pair_info {
      prod_traffic_route {
        listener_arns = [aws_lb_listener.https.arn]
      }
      
      target_group {
        name = aws_lb_target_group.blue.name
      }
      
      target_group {
        name = aws_lb_target_group.green.name
      }
    }
  }
  
  auto_rollback_configuration {
    enabled = true
    events  = ["DEPLOYMENT_FAILURE", "DEPLOYMENT_STOP_ON_ALARM"]
  }
  
  alarm_configuration {
    enabled = true
    alarms  = [aws_cloudwatch_metric_alarm.high_error_rate.alarm_name]
  }
}
```

**Deployment process**:

1. CodeDeploy creates new ECS tasks (green) with new image version
2. Waits for health checks to pass
3. Shifts 10% traffic to green tasks
4. Monitors CloudWatch alarms for 5 minutes
5. If no alarms triggered, shifts remaining 90% traffic
6. Monitors for another 5 minutes
7. If stable, terminates old (blue) tasks
8. **If alarms trigger at any point**: Automatic rollback to blue tasks

**Trigger deployment**:
```bash
# Update ECS service with new task definition
aws ecs update-service \
  --cluster stackmentor-prod \
  --service backend \
  --task-definition stackmentor-backend:42 \
  --deployment-configuration "deploymentCircuitBreaker={enable=true,rollback=true}"

# Monitor deployment
aws ecs describe-services \
  --cluster stackmentor-prod \
  --services backend \
  --query 'services[0].deployments'
```

**Watch metrics during deployment**:

- Error rate (should stay < 1%)
- P95 latency (should stay < 500ms)
- Task health checks (all passing)
- CloudWatch Logs (no new errors)

**Why blue/green**: Zero downtime. Instant rollback (switch ALB back to blue target group). Canary phase (10%) catches issues before affecting all users.

#### Step 13.4: Post-Deployment Verification

**Automated smoke tests**:
```bash
#!/bin/bash
# smoke-test.sh

BASE_URL="https://api.stackmentor.com"

echo "Testing health endpoint..."
curl -f $BASE_URL/actuator/health || exit 1

echo "Testing authentication..."
TOKEN=$(curl -s -X POST $BASE_URL/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"smoketest@stackmentor.com","password":"'$SMOKE_TEST_PASSWORD'"}' \
  | jq -r '.accessToken')

if [ -z "$TOKEN" ]; then
  echo "Login failed"
  exit 1
fi

echo "Testing API endpoints..."
curl -f -H "Authorization: Bearer $TOKEN" $BASE_URL/api/conversations || exit 1
curl -f -H "Authorization: Bearer $TOKEN" $BASE_URL/api/users/me || exit 1

echo "Testing WebSocket..."
# Use wscat or custom script
wscat -c wss://api.stackmentor.com/ws \
  -H "Authorization: Bearer $TOKEN" \
  --execute 'CONNECT' \
  --wait 5 || exit 1

echo "All smoke tests passed!"
```

**Run automatically in CI after deployment**:
```yaml
# .github/workflows/deploy-production.yml
jobs:
  deploy:
    steps:
      # ... deployment steps ...
      
      - name: Run smoke tests
        run: ./smoke-test.sh
        env:
          SMOKE_TEST_PASSWORD: ${{ secrets.SMOKE_TEST_PASSWORD }}
      
      - name: Notify deployment success
        if: success()
        uses: slackapi/slack-github-action@v1
        with:
          payload: |
            {
              "text": "✅ Production deployment successful",
              "blocks": [
                {
                  "type": "section",
                  "text": {
                    "type": "mrkdwn",
                    "text": "*Deployment to production completed*\nVersion: `${{ github.sha }}`\nDeployed by: ${{ github.actor }}"
                  }
                }
              ]
            }
```

**Manual verification checklist**:

- [ ]  Open app in browser/mobile, verify loads
- [ ]  Login with test account, verify authentication works
- [ ]  Send test message, verify appears in real-time
- [ ]  Check CloudWatch dashboard, verify metrics normal
- [ ]  Review recent error logs, verify no new errors
- [ ]  Test from different network (mobile data, different region)

**Why smoke tests**: Automated validation. Catches misconfigurations (wrong environment variable, missing secret). Fails deployment before users affected.

#### Step 13.5: Rollback Procedure

**If issues discovered post-deployment**:

**Option 1: Rollback via ECS** (fastest):
```bash
# Revert to previous task definition
aws ecs update-service \
  --cluster stackmentor-prod \
  --service backend \
  --task-definition stackmentor-backend:41  # Previous version

# Force new deployment
aws ecs update-service \
  --cluster stackmentor-prod \
  --service backend \
  --force-new-deployment
```

**Option 2: Rollback via CodeDeploy** (if using blue/green):
```bash
aws deploy stop-deployment \
  --deployment-id d-XXXXXXXXX \
  --auto-rollback-enabled
```

**Option 3: Rollback database** (LAST RESORT):
```bash
# Restore from snapshot
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier stackmentor-prod-rollback \
  --db-snapshot-identifier rds:stackmentor-prod-2025-10-02-06-00

# Update app to point to rollback instance (change RDS endpoint in Secrets Manager)
aws secretsmanager update-secret \
  --secret-id prod/db/endpoint \
  --secret-string "stackmentor-prod-rollback.xxxxx.us-east-1.rds.amazonaws.com"

# Restart ECS tasks to pick up new endpoint
aws ecs update-service \
  --cluster stackmentor-prod \
  --service backend \
  --force-new-deployment
```

**When to rollback**:

- Error rate > 5% for 5 minutes
- Critical functionality broken (can't send messages, can't login)
- Data corruption detected
- Performance degradation > 50% (P95 latency doubles)

**Rollback decision tree**:

1. Can hotfix in < 15 minutes? → Deploy hotfix
2. Database migration involved? → Assess if reversible, may need restore
3. User data at risk? → Immediate rollback
4. Non-critical bug? → Rollback, fix, redeploy tomorrow

**Why documented rollback**: Panic during incident. Checklist prevents mistakes. Time is critical (every minute = lost users/revenue).

#### Step 13.6: App Store Submission (Mobile Apps)

**iOS (App Store Connect)**:

1. **Prepare assets**:
    - App icon (1024x1024)
    - Screenshots (6.7", 6.5", 5.5" displays)
    - Privacy policy URL
    - Support URL
2. **Build release IPA**:
 ```bash
   cd ios 
   xcodebuild -workspace StackMentorApp.xcworkspace \ 
	   -scheme StackMentorApp \ 
	   -configuration Release \ 
	   -archivePath StackMentorApp.xcarchive \ 
	   archive 
   xcodebuild -exportArchive \ 
	   -archivePath StackMentorApp.xcarchive \ 
	   -exportPath . \ 
	   -exportOptionsPlist ExportOptions.plist
   ```
   3. **Upload with Transporter**:
    - Open Transporter app
    - Drag IPA file
    - Click "Deliver"
4. **Configure in App Store Connect**:
    - Version 1.0.0
    - Description, keywords, category
    - Age rating
    - App Privacy details (data collection disclosure)
    - Pricing ($0 Free)
5. **Submit for review** (typical wait: 24-48 hours)

**Android (Google Play Console)**:

1. **Build release APK/AAB**:
2. **Sign with keystore**:
3. **Upload to Play Console**:
    - Create new release in Production track
    - Upload app-release.aab
    - Add release notes
    - Set rollout percentage (start at 10%, gradually increase)
4. **Content rating questionnaire** (IARC)
5. **Submit for review** (typical wait: hours to 1 day)

**Beta testing first**:

- iOS: TestFlight (invite 100 beta testers, collect feedback)
- Android: Internal testing track (20 users), then closed alpha (100 users)
- Fix critical bugs before full release

**Why staged rollout**: Android allows 10% → 50% → 100% rollout. If crash rate spikes, pause rollout, fix, resume. Protects full user base.

---

### **Phase 14: Post-Launch Operations** (Ongoing)

**Goal**: Maintain stability, iterate based on feedback, optimize costs.

#### Step 14.1: Monitoring & On-Call

**First week post-launch** (critical period):

- Monitor dashboards hourly
- Review error logs daily
- Track key metrics: DAU, MAU, message volume, error rates
- Quick response to incidents (< 15 minutes)

**On-call rotation**:

- Primary on-call: Responds to P1/P2 alerts
- Secondary on-call: Backup if primary unavailable
- Weekly rotation (prevents burnout)
- Handoff meeting: Share ongoing issues, pending deploys

**Incident response playbook**:

**Incident severity levels**:

- **P1 (Critical)**: Service down, data loss, security breach → Response: Immediate, all hands
- **P2 (High)**: Major functionality broken (can't send messages), elevated errors → Response: < 30 minutes
- **P3 (Medium)**: Minor functionality broken, performance degradation → Response: < 4 hours
- **P4 (Low)**: Cosmetic issues, feature requests → Response: Next sprint

**P1 Incident Response**:

1. **Acknowledge alert** (PagerDuty, Slack)
2. **Assess severity** (how many users affected? data at risk?)
3. **Create incident channel** (#incident-2025-10-02)
4. **Investigate** (check CloudWatch, X-Ray, recent deployments)
5. **Mitigate** (rollback deployment, scale up resources, disable feature flag)
6. **Communicate** (status page update, Twitter, in-app banner)
7. **Resolve** (verify metrics normal, close incident)
8. **Post-mortem** (within 48 hours, blameless, action items)
**Post-mortem template**:
```txt
# Incident Post-Mortem: API Outage 2025-10-02

## Summary
API returned 500 errors for 23 minutes, affecting 15% of users.

## Timeline
- 14:32 UTC: Deployment started (v1.2.3)
- 14:38 UTC: CloudWatch alarm triggered (high error rate)
- 14:40 UTC: On-call engineer paged
- 14:42 UTC: Incident channel created
- 14:45 UTC: Identified cause (database connection pool exhausted)
- 14:48 UTC: Mitigation started (rollback deployment)
- 14:52 UTC: Rollback complete, errors stopped
- 14:55 UTC: Incident resolved

## Root Cause
New feature increased database queries per request from 5 to 15. Connection pool size (20) insufficient under load.

## Impact
- 2,340 users affected
- 156 failed message sends
- $0 revenue impact (free tier users)

## Action Items
1. [ ] Increase connection pool size to 50 (Owner: @alice, Due: 2025-10-03)
2. [ ] Add load testing to CI for new features (Owner: @bob, Due: 2025-10-10)
3. [ ] Create dashboard for connection pool utilization (Owner: @charlie, Due: 2025-10-05)
4. [ ] Optimize queries in UserService.getProfile (Owner: @diana, Due: 2025-10-08)

## What Went Well
- Automated rollback prevented extended outage
- Fast response time (10 minutes to mitigation)
- Clear communication to users

## What Could Be Improved
- Load testing didn't catch this (insufficient traffic simulation)
- No alert for connection pool usage before exhaustion
```

**Why post-mortems**: Learning opportunity. Prevents same failure twice. Builds institutional knowledge. Blameless culture (focus on systems, not people).

#### Step 14.2: Performance Optimization

**After 1 month of production data**:

**Identify slow queries**:
```sql
-- Enable PostgreSQL slow query log
ALTER SYSTEM SET log_min_duration_statement = 1000; -- Log queries > 1 second
SELECT pg_reload_conf();

-- View slow queries
SELECT 
  query,
  calls,
  total_time,
  mean_time,
  max_time
FROM pg_stat_statements
ORDER BY mean_time DESC
LIMIT 10;
```

**Common optimizations**:

**Add missing indexes**:
```sql
-- Messages frequently queried by conversation_id + timestamp
CREATE INDEX idx_messages_conversation_timestamp 
ON messages(conversation_id, timestamp DESC);

-- Users searched by name
CREATE INDEX idx_users_name_trgm 
ON users USING gin(name gin_trgm_ops);
```

**Implement caching** (Redis):
```java
@Service
public class UserService {
    
    @Cacheable(value = "users", key = "#userId")
    public UserDto getUser(String userId) {
        return userRepo.findById(userId)
            .map(UserDto::from)
            .orElseThrow();
    }
    
    @CacheEvict(value = "users", key = "#userId")
    public void updateUser(String userId, UpdateUserRequest request) {
        // Update logic
    }
}
```

**Redis cache configuration**:
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 1 hour
  redis:
    host: ${REDIS_ENDPOINT}
    port: 6379
```

**Add read replicas** (for read-heavy workloads):
```hcl
resource "aws_db_instance" "postgres_replica" {
  replicate_source_db = aws_db_instance.postgres.id
  instance_class      = "db.r6g.large"
  publicly_accessible = false
  
  tags = {
    Name = "stackmentor-prod-replica"
  }
}
```

**Route read queries to replica**:
```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    @Primary
    public DataSource dataSource() {
        RoutingDataSource routingDataSource = new RoutingDataSource();
        routingDataSource.setTargetDataSources(Map.of(
            "primary", primaryDataSource(),
            "replica", replicaDataSource()
        ));
        routingDataSource.setDefaultTargetDataSource(primaryDataSource());
        return routingDataSource;
    }
    
    @Bean
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create()
            .url(primaryUrl)
            .build();
    }
    
    @Bean
    public DataSource replicaDataSource() {
        return DataSourceBuilder.create()
            .url(replicaUrl)
            .build();
    }
}

// Use @Transactional(readOnly = true) to route to replica
@Service
public class MessageService {
    
    @Transactional(readOnly = true)
    public Page<MessageDto> getMessages(String conversationId, Pageable pageable) {
        // Reads from replica
        return messageRepo.findByConversationId(conversationId, pageable)
            .map(MessageDto::from);
    }
    
    @Transactional
    public MessageDto sendMessage(String senderId, SendMessageRequest request) {
        // Writes to primary
        Message message = messageRepo.save(/* ... */);
        return MessageDto.from(message);
    }
}
```

**Why read replicas**: Offload read traffic (80% of queries). Primary handles writes only. Scale reads horizontally (add more replicas). Replica lag typically < 1 second.

#### Step 14.3: Cost Optimization

**Monthly AWS cost breakdown** (estimate for 10k DAU):

- ECS Fargate (2 tasks): $50
- RDS (db.t4g.large): $90
- ElastiCache (cache.t4g.micro): $15
- ALB: $25
- NAT Gateway: $45
- Data transfer: $30
- S3: $5
- CloudWatch: $10
- **Total**: ~$270/month

**Optimization strategies**:

**1. Use Savings Plans / Reserved Instances**:

- Commit to 1-year RDS reserved instance: Save 40% ($90 → $54)
- Commit to Fargate Compute Savings Plan: Save 50% ($50 → $25)

**2. Right-size resources**:
```bash
# Analyze actual usage
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ServiceName,Value=backend \
  --start-time 2025-09-01T00:00:00Z \
  --end-time 2025-10-01T00:00:00Z \
  --period 86400 \
  --statistics Average

# If average CPU < 30%, reduce task size
# 512 CPU / 1024 MB → 256 CPU / 512 MB (50% savings)
```

**3. Use S3 Intelligent-Tiering**:
```hcl
resource "aws_s3_bucket_lifecycle_configuration" "media" {
  bucket = aws_s3_bucket.media.id
  
  rule {
    id     = "intelligent-tiering"
    status = "Enabled"
    
    transition {
      days          = 0
      storage_class = "INTELLIGENT_TIERING"
    }
  }
  
  rule {
    id     = "expire-temp-uploads"
    status = "Enabled"
    
    filter {
      prefix = "temp/"
    }
    
    expiration {
      days = 1
    }
  }
}
```

**4. Optimize data transfer**:

- Enable CloudFront CDN for S3 media (reduces data transfer costs)
- Use VPC endpoints for S3 (free data transfer within same region)
- Compress API responses (gzip)

**5. Schedule non-production environments**:
```bash
# Stop staging environment outside business hours (save 70%)
aws ecs update-service \
  --cluster stackmentor-staging \
  --service backend \
  --desired-count 0

# Automate with Lambda + EventBridge (cron)
```

**Cost monitoring**:

- AWS Cost Explorer: Track by service, tag
- Budgets: Alert when monthly cost > $300
- Cost anomaly detection: Alert on unexpected spikes

**Why cost optimization**: Every dollar saved = extended runway. Over-provisioning common in early days. Review quarterly.

#### Step 14.4: Feature Iteration

**Collect user feedback**:

- In-app feedback button → Zendesk/Intercom
- App Store reviews monitoring
- User interviews (10 users/month)
- Analytics (Mixpanel, Amplitude): Track feature usage, funnels

**Prioritization framework** (RICE):

- **Reach**: How many users affected?
- **Impact**: How much does it improve experience? (0.25x to 3x)
- **Confidence**: How sure are we? (50% to 100%)
- **Effort**: How many person-weeks?
- **Score**: (Reach × Impact × Confidence) / Effort

**Example**:

| Feature        | Reach  | Impact | Confidence | Effort    | Score  |
| -------------- | ------ | ------ | ---------- | --------- | ------ |
| Read reciepts  | 10,000 | 2x     | 100%       | 1 week    | 20,000 |
| Voice messages | 8,000  | 3x     | 80%]       | 4 weeks   | 4,800  |
| Dark mode      | 7,000  | 1x     | 100%       | 0.5 weeks | 14,000 |

Result: Prioritize read receipts → dark mode → voice messages

**Feature flags** (for gradual rollout):
```java
@Service
public class FeatureFlagService {
    
    private final RedisTemplate<String, String> redis;
    
    public boolean isEnabled(String featureName, String userId) {
        // Check if feature enabled globally
        String globalFlag = redis.opsForValue().get("feature:" + featureName);
        if ("true".equals(globalFlag)) return true;
        
        // Check if user in beta group
        Boolean isInBeta = redis.opsForSet()
            .isMember("feature:" + featureName + ":beta", userId);
        return Boolean.TRUE.equals(isInBeta);
    }
}

// Usage
@PostMapping("/messages")
public MessageDto sendMessage(UserPrincipal user, @RequestBody SendMessageRequest req) {
    MessageDto message = messageService.sendMessage(user.id(), req);
    
    // New feature: AI message suggestions
    if (featureFlagService.isEnabled("ai-suggestions", user.id())) {
        aiService.generateSuggestions(message);
    }
    
    return message;
}
```

**Rollout strategy**:

1. Enable for internal team (5 users)
2. Enable for 5% of users (beta group)
3. Monitor metrics (usage, errors, feedback)
4. Gradual rollout: 10% → 25% → 50% → 100%
5. If issues, instant rollback (flip flag to false)

**Why feature flags**: Test in production safely. A/B testing (show feature to 50%, measure engagement). Kill switch for buggy features without deployment.

## Stack Justification

### **Frontend: React Native**

**Why**: Single codebase for iOS + Android (2x development speed). JavaScript ecosystem mature (npm packages for everything). Hot reloading speeds development. Near-native performance (90% of use cases). Large community (Stack Overflow answers, tutorials).

**Alternatives considered**:

- Flutter: Dart language less popular, smaller ecosystem
- Native (Swift + Kotlin): 2x code, 2x maintenance, slower iteration
- Ionic/Cordless: WebView performance worse, doesn't feel native

**Trade-offs**: Complex animations harder than native. Some platform-specific features require native modules. Bridge overhead for intensive compute (rare in chat app).

### **Backend: Java Spring Boot**

**Why**: Mature ecosystem (20+ years). Excellent for enterprise apps (banking, healthcare use Spring). Strong typing catches bugs at compile time. Spring Security battle-tested. WebSocket support excellent. JVM performance (GC tuning, JIT compilation). Extensive libraries (Apache Commons, Guava). Easy hiring (Java developers abundant).

**Alternatives considered**:

- Node.js: Single-threaded (harder to utilize multi-core), callback hell, weaker typing (even with TypeScript)
- Python (Django/Flask): GIL limits concurrency, slower performance
- Go: Great performance but smaller ecosystem, less mature ORM
- Kotlin: Consider for greenfield (better syntax), but Java fine if team knows it

**Trade-offs**: Verbose syntax (more boilerplate than Python/Node). JVM startup time slow (5-10 seconds, but Fargate caches). Memory usage higher (200MB baseline).

### **Build Tool: Gradle + Kotlin DSL**

**Why**: Flexible (can customize build logic). Incremental compilation (fast rebuilds). Dependency management (Maven Central). Kotlin DSL type-safe (autocomplete in IDE, catch typos). Multi-project builds (split into modules later).

**Alternatives considered**:

- Maven: XML verbose, less flexible
- Gradle Groovy: Dynamic typing (no autocomplete, runtime errors)

**Trade-offs**: Build configuration complex for beginners. First build slow (downloads dependencies).

### **Database Migrations: Liquibase**

**Why**: Database-agnostic (PostgreSQL today, could migrate to MySQL). Version controlled schema. Rollback support. Tracks applied changesets (won't re-apply). XML/YAML/SQL formats. Integrates with Spring Boot.

**Alternatives considered**:

- Flyway: Simpler but less features (no rollback), SQL-only
- Raw SQL scripts: No tracking, easy to miss migration, no rollback

**Trade-offs**: XML verbose. Learning curve for complex migrations.

### **Compute: AWS ECS Fargate**

**Why**: Managed container orchestration (no server patching). Integrates with ALB, CloudWatch, IAM. Cost-effective for moderate scale (< 50 containers). Simpler than Kubernetes. Auto-scaling built-in. Per-second billing.

**Alternatives considered**:

- Kubernetes (EKS): Over-kill for this scale, complex setup, expensive ($0.10/hour control plane)
- EC2 Auto Scaling: Manage instances yourself, patch OS, configure networking
- Lambda: Cold starts hurt WebSocket, 15-minute timeout insufficient for long connections
- App Runner: Simpler but less control, no VPC customization

**Trade-offs**: More expensive than EC2 (2-3x cost per vCPU). Less control than Kubernetes (can't customize scheduler). Fargate pricing higher in regions.

### **Database: RDS PostgreSQL**

**Why**: ACID compliance (data integrity for financial transactions). JSON support (flexible schema). Full-text search (pg_trgm). Powerful query optimizer. PostGIS for geospatial (future feature: location-based chat). Managed service (backups, patching, failover). Read replicas (scale reads). Multi-AZ (high availability).

**Alternatives considered**:

- MySQL: Less features (no JSONB, weaker full-text search)
- DynamoDB: NoSQL hard for relational data (conversations ↔ users), no complex joins
- Aurora: 3x more expensive, unnecessary for this scale

**Trade-offs**: More expensive than self-managed EC2 Postgres. Scaling writes limited (vertical only, can't shard easily). Vendor lock-in (migration effort high).

### **Authentication: AWS Cognito**

**Why**: Managed user pool (don't build auth yourself). OAuth/SAML federation (Google, Facebook login). MFA built-in. JWT token issuance. Lambda triggers (custom workflows). Password policies enforceable. Compliant (SOC 2, HIPAA). Automatic scaling. Free tier (50k MAU).

**Alternatives considered**:

- Auth0: Better UX but $23/month, vendor lock-in worse
- Custom auth: Security risk (password hashing, session management), time-intensive
- Firebase Auth: Google lock-in, less AWS integration

**Trade-offs**: Limited customization (UI, flows). Vendor lock-in (migration requires re-hashing passwords). Lambda triggers can be complex.

### **Object Storage: S3**

**Why**: Unlimited capacity. 99.999999999% durability (won't lose files). $0.023/GB standard, $0.004/GB Glacier. Versioning (recover deleted files). Lifecycle policies (auto-archive). Presigned URLs (secure uploads without credentials). CloudFront integration (CDN). Event notifications (Lambda triggers).

**Alternatives considered**:

- EFS: 3x more expensive, overkill for object storage
- EBS: Attached to single EC2 instance, not scalable
- Self-managed MinIO: Operational burden

**Trade-offs**: Eventual consistency (rare but exists). No file system interface (must use SDK). Data transfer costs ($0.09/GB egress).

### **Cache/Pub-Sub: Redis (ElastiCache)**

**Why**: In-memory speed (< 1ms latency). Pub/Sub for WebSocket scaling. Data structures (lists, sets, sorted sets). Managed service (automatic failover, patching). Cluster mode (scale beyond single node). Persistence optional (AOF, RDS).

**Alternatives considered**:

- Memcached: No Pub/Sub, no persistence, simpler data structures
- RabbitMQ/SQS: Message queues not ideal for broadcast pattern
- Kafka: Overkill complexity, meant for event streaming

**Trade-offs**: Data loss if node fails (enable persistence, adds latency). Memory expensive (cache.r6g.large = $0.201/hour). No built-in Lua support in ElastiCache (unlike open-source Redis).

### **Networking: VPC + ALB + Route 53**

**Why VPC**: Network isolation (private subnets unreachable from internet). Security groups (firewall rules). Multiple AZs (high availability). NAT Gateway (outbound internet for private subnets).

**Why ALB**: Layer 7 load balancing (HTTP/HTTPS routing). WebSocket support. SSL termination. Health checks (remove unhealthy targets). Path-based routing (/api → backend, /admin → admin service). Sticky sessions (if needed).

**Why Route 53**: AWS-native DNS. 100% uptime SLA. Health checks (failover to backup). Alias records (no charge for AWS resources). Global anycast network (low latency).

**Alternatives considered**:

- NLB (Network Load Balancer): Layer 4 only, no HTTP routing, use for raw TCP
- NGINX on EC2: Manual management, single point of failure
- CloudFlare: Good for DDoS protection but adds external dependency

**Trade-offs**: ALB costs $22.50/month + per-GB processed. NAT Gateway $32.40/month + data transfer. Route 53 hosted zone $0.50/month + queries.

### **API Gateway** (Optional Enhancement)

**Why**: Rate limiting (prevent abuse). API keys (track usage per client). Request validation (reduce backend load). Mock responses (development). SDK generation. WAF integration (block attacks). Caching (reduce backend calls). Usage plans (monetization).

**When not needed**: Adds latency (10-30ms). Costs money ($3.50/million requests). ALB sufficient for simple REST APIs.

**Recommendation**: Add when scaling beyond 10k DAU or need abuse protection.

### **Monitoring: CloudWatch**

**Why**: AWS-native (auto-collects metrics). Logs aggregation (searchable). Dashboards (visualize). Alarms (SNS notifications). ServiceLens (X-Ray integration). Log Insights (SQL-like queries). Contributor Insights (find top talkers).

**Alternatives considered**:

- Datadog: Better UX, expensive ($15/host/month)
- Prometheus + Grafana: Self-managed, complex setup, great for Kubernetes
- ELK Stack: Self-managed, resource-intensive

**Trade-offs**: CloudWatch Logs expensive at scale ($0.50/GB ingested). UI less polished than Datadog. Query language limited vs. Prometheus PromQL.
## Foundational Principles

1. **Test-driven development**: Write tests as you code (Phase 3-4), prevents regressions, enables refactoring
2. **Infrastructure as code**: Terraform/CDK (Phase 2) = reproducible environments, version control, disaster recovery
3. **Observability first**: Structured logs + metrics + tracing from day 1 (Phase 3, 11) = debug faster, prevent downtime
4. **Defense in depth**: VPC isolation + security groups + IAM least privilege + WAF + encryption = layered security
5. **Backward-compatible migrations**: Two-phase database changes (Phase 6) = zero-downtime deploys
6. **Gradual rollouts**: Canary deployments + feature flags (Phase 13, 14) = safe releases, instant rollback
7. **Automate everything**: CI/CD + auto-scaling + alarming (Phase 5, 10, 11) = reduce human error, scale efficiently
8. **Blameless post-mortems**: Learn from incidents (Phase 14) = improve systems, build knowledge
9. **Cost-conscious engineering**: Right-size resources + savings plans (Phase 14) = extend runway
10. **Iterate based on data**: Analytics + user feedback + RICE prioritization (Phase 14) = build what matters

This lifecycle works because it **balances speed and safety**: automate what machines do well (testing, deployment), let humans do what they do well (design, prioritize). Start simple (local Docker), add complexity incrementally (staging, production, monitoring), always with rollback plan. Result: production-ready app in 3-4 months with foundation to scale to millions of users.

## Technology Definitions

### **STOMP (Simple Text Oriented Messaging Protocol)**

Protocol for WebSocket messaging. Provides frame-based structure over WebSocket's raw connection. Defines message types: CONNECT, SEND, SUBSCRIBE, MESSAGE, DISCONNECT. Spring Boot uses it for pub/sub messaging patterns. Alternative to raw WebSocket handling—adds routing, message headers, and acknowledgments.

### **Linting Tools**

**ESLint:** JavaScript/TypeScript linter. Scans code for syntax errors, style violations, potential bugs. Configurable rules (semicolons, indentation, unused variables). Runs in IDE real-time or build process. Enforces team coding standards.

**Ktlint:** Kotlin linter. Enforces Kotlin style guide (Google/Android conventions). Auto-formats code. Checks naming, spacing, import ordering. Gradle plugin available—fails builds on violations.

### **UAT (User Acceptance Testing)**

Final testing phase before production launch. Real users test the app in production-like environment. Validates business requirements met, workflow makes sense, bugs discovered in real usage. Not automated—actual humans using the app. Gate before public release.

---

## AWS Technologies

### **VPC (Virtual Private Cloud)**

Isolated network within AWS. You define IP address range (CIDR block), create subnets (public/private), control routing tables. Think of it as your private data center in the cloud. Prevents random internet traffic from reaching your database. Resources communicate internally without traversing public internet.

**Subnets:**

- **Public:** Has route to Internet Gateway, resources get public IPs, accessible from internet
- **Private:** No direct internet access, uses NAT Gateway for outbound traffic only, your database lives here

**Internet Gateway:** Allows VPC resources to communicate with internet (attached to public subnet)

**NAT Gateway:** Sits in public subnet, allows private subnet resources to initiate outbound connections (download packages, call APIs) but blocks inbound

### **RDS (Relational Database Service)**

Managed PostgreSQL/MySQL/etc. AWS handles backups, patching, failover, replication. You just connect and query. Automatic backups, point-in-time recovery. Multi-AZ deployment for high availability (automatic failover to standby). Scales vertically (bigger instance) or horizontally (read replicas).

### **S3 (Simple Storage Service)**

Object storage. Upload files, get unique URL. Organized in buckets (like folders). Each object has key (filename/path), metadata, data. Use cases: images, videos, backups, static website hosting. 99.999999999% durability (won't lose your files). Pay only for storage used + data transfer.

**Presigned URLs:** Temporary URL with embedded credentials. Backend generates URL, frontend uses it to upload/download directly to S3 without exposing AWS credentials. Expires after set time (5 minutes, 1 hour, etc.).

### **ECS (Elastic Container Service)**

Container orchestration. Manages Docker containers across fleet of servers. You define task (what container to run, resources needed), ECS schedules it, monitors health, restarts failures.

**Fargate:** Serverless mode—AWS manages servers, you just define container specs **EC2 mode:** You manage EC2 instances, more control but more overhead

**Task Definition:** JSON/YAML config describing container (image, CPU, RAM, ports, environment variables) **Service:** Maintains desired number of running tasks, integrates with load balancer, handles deployments

### **ECR (Elastic Container Registry)**

Docker image storage. Like Docker Hub but private and AWS-integrated. Push images from local machine, ECS pulls from ECR to run. Scans images for security vulnerabilities. Encrypted at rest.

### **Route 53**

DNS service. Translates domain names (yourapp.com) to IP addresses. Hosted zone contains DNS records:

- **A record:** Points domain to IPv4 address (your load balancer)
- **CNAME:** Alias to another domain
- **MX:** Mail server records

Health checks route traffic away from unhealthy endpoints. Global anycast network (low latency).

### **API Gateway**

Managed API layer. Sits in front of your backend. Handles:

- **Routing:** Maps URLs to backend services
- **Rate limiting:** Block abusive clients (100 requests/second max)
- **Authentication:** Verify API keys, JWT tokens before reaching backend
- **Caching:** Store frequent responses in memory
- **Request/response transformation:** Modify data format

**VPC Link:** Connects API Gateway to private resources (your ECS in private subnet). Gateway is public, backend stays private.

### **Cognito**

User authentication/authorization service. Two components:

**User Pools:** User directory. Stores usernames, passwords, profiles. Handles signup, signin, email verification, password reset, MFA. Issues JWT tokens after successful login.

**Identity Pools:** Grants AWS credentials to users. Allows direct S3 upload from mobile app. Not needed if backend handles everything.

You integrate User Pools—mobile app sends credentials to Cognito, receives JWT token, includes token in API requests, Spring Boot validates token.

### **IAM (Identity and Access Management)**

Permission system for AWS resources.

**Users:** Individual accounts (you, your team) **Roles:** Temporary credentials for services (ECS task role can access S3) **Policies:** JSON documents defining permissions (can read S3, can't delete RDS)

Principle of least privilege—grant minimum required permissions. ECS task role only accesses RDS + S3, not billing or EC2 management.

### **Secrets Manager**

Stores sensitive values (database passwords, API keys) encrypted. Applications retrieve secrets at runtime via API. Automatic rotation for database credentials. Prevents hardcoding secrets in code or environment variables visible in logs.

### **CloudWatch**

Monitoring and logging platform.

**Logs:** Aggregates application logs from ECS containers. Searchable, filterable. Create log groups (per service), log streams (per container instance). Set retention (7 days, 30 days, forever).

**Metrics:** Time-series data. Built-in (CPU, memory, network) and custom (message send rate, login failures). Visualize in dashboards. Query with CloudWatch Insights.

**Alarms:** Triggers when metric crosses threshold. Send SNS notification (email, SMS), trigger Auto Scaling, invoke Lambda function.

### **SNS (Simple Notification Service)**

Pub/sub messaging for notifications. Create topic, subscribe endpoints (email, SMS, Lambda, HTTP). CloudWatch alarm publishes to topic, you receive alert. Use for operational alerts, not application messaging (that's Redis Pub/Sub).

### **Auto Scaling Groups**

Automatically adjusts number of ECS tasks based on metrics. Define scaling policies:

- **Target tracking:** Maintain CPU at 70%
- **Step scaling:** Add 2 tasks if CPU > 80%, add 4 if CPU > 90%
- **Scheduled:** Scale up during business hours

Min/max boundaries prevent runaway scaling. Saves cost (scale down at night), ensures availability (scale up under load).

### **Elastic Load Balancer (ELB)**

Distributes incoming traffic across multiple targets (ECS tasks). Types:

**Application Load Balancer (ALB):** HTTP/HTTPS traffic, path-based routing (/api/users → service A, /api/orders → service B), WebSocket support, your use case

**Network Load Balancer:** TCP/UDP, ultra-low latency, static IP

Health checks ping targets (/actuator/health), removes unhealthy tasks from rotation. SSL termination—handles HTTPS, communicates HTTP internally.

### **ElastiCache**

Managed Redis/Memcached. In-memory cache for speed. Your use: Redis Pub/Sub for WebSocket message distribution across ECS instances. User sends message → ECS task 1 → publishes to Redis → all ECS tasks subscribed receive message → broadcast to connected WebSocket clients.

Also used for session storage, database query caching.

### **SES (Simple Email Service)**

Sends transactional emails (verification, password reset). Cheaper than third-party (Sendgrid, Mailgun). Verify sender domain/email first. Cognito can use SES for user emails. Production requires moving out of sandbox (request from AWS).

---

## Other Technologies

### **CORS (Cross-Origin Resource Sharing)**

Browser security mechanism. Blocks JavaScript from domain A (yourapp.com) accessing API on domain B (api.yourapp.com). Backend sends CORS headers: