package com.acme.tms.document;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.support.ApiClient;
import com.acme.tms.support.CompetitionFixture;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The presigned flow against a real MinIO, per the Sprint 7 DoD. A mocked S3 would prove that the
 * code calls the SDK; it would not prove that the URL we hand a client actually accepts a PUT,
 * which is the only part a user experiences.
 */
class DocumentUploadIntegrationTest extends AbstractIntegrationTest {

    private static final String BUCKET = "tms-docs-test";

    private static final MinIOContainer MINIO =
        new MinIOContainer(DockerImageName.parse("minio/minio:RELEASE.2024-06-13T22-53-53Z"));

    static {
        MINIO.start();
    }

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.endpoint", MINIO::getS3URL);
        registry.add("app.storage.access-key", MINIO::getUserName);
        registry.add("app.storage.secret-key", MINIO::getPassword);
        registry.add("app.storage.bucket", () -> BUCKET);
    }

    @Autowired
    private S3Client s3Client;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void createBucket() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (BucketAlreadyOwnedByYouException expected) {
            // Fine — the container outlives individual tests.
        }
    }

    private record Tenant(ApiClient.Session session, UUID organizationUnitId, UUID tournamentId) {

        String token() {
            return session.accessToken();
        }
    }

    private Tenant tenant(String email) {
        ApiClient.Session session = api.registerTenant(email, "Docs Federation");
        UUID organizationUnitId = UUID.fromString(
            api.get("/api/v1/organization-units", session.accessToken()).json().get(0).path("id").asText());
        UUID tournamentId = api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", organizationUnitId, "name", "Documented Games"), session.accessToken()).id();
        return new Tenant(session, organizationUnitId, tournamentId);
    }

    private ApiClient.Response initUpload(Tenant tenant, String fileName, String mimeType, long size) {
        return api.post("/api/v1/documents/upload-init", Map.of(
            "fileName", fileName,
            "mimeType", mimeType,
            "sizeBytes", size,
            "entityType", "TOURNAMENT",
            "entityId", tenant.tournamentId()
        ), tenant.token());
    }

    private int putToPresignedUrl(String url, byte[] body, String contentType) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    @Test
    void aFileGoesUpThroughThePresignedUrlAndBecomesAnAttachedDocument() throws Exception {
        Tenant tenant = tenant("upload@example.com");
        byte[] content = "%PDF-1.4 age proof".getBytes();

        ApiClient.Response init = initUpload(tenant, "age-proof.pdf", "application/pdf", content.length);
        assertThat(init.status()).isEqualTo(200);
        String uploadId = init.json().path("uploadId").asText();
        String presignedUrl = init.json().path("presignedUrl").asText();
        assertThat(presignedUrl).contains("X-Amz-Signature");

        // The bytes go straight to storage; they never pass through the application.
        assertThat(putToPresignedUrl(presignedUrl, content, "application/pdf")).isEqualTo(200);

        ApiClient.Response attached = api.post("/api/v1/documents/" + uploadId + "/attach", Map.of(), tenant.token());

        assertThat(attached.status()).isEqualTo(201);
        JsonNode document = attached.json();
        assertThat(document.path("fileName").asText()).isEqualTo("age-proof.pdf");
        assertThat(document.path("mimeType").asText()).isEqualTo("application/pdf");
        // The size recorded is the one that actually landed, not the one that was declared.
        assertThat(document.path("sizeBytes").asLong()).isEqualTo(content.length);
        assertThat(document.path("organizationUnitId").asText())
            .isEqualTo(tenant.organizationUnitId().toString());
        assertThat(document.path("uploadedBy").asText()).isEqualTo(tenant.session().userId().toString());
    }

    @Test
    void theDownloadUrlActuallyServesTheBytesBack() throws Exception {
        Tenant tenant = tenant("download@example.com");
        byte[] content = "%PDF-1.4 rulebook".getBytes();

        ApiClient.Response init = initUpload(tenant, "rulebook.pdf", "application/pdf", content.length);
        putToPresignedUrl(init.json().path("presignedUrl").asText(), content, "application/pdf");
        api.post("/api/v1/documents/" + init.json().path("uploadId").asText() + "/attach",
            Map.of(), tenant.token());

        JsonNode listed = api.get(
            "/api/v1/documents?entityType=TOURNAMENT&entityId=" + tenant.tournamentId(), tenant.token()).json();

        assertThat(listed).hasSize(1);
        String downloadUrl = listed.get(0).path("downloadUrl").asText();
        assertThat(downloadUrl).contains("X-Amz-Signature");

        HttpResponse<byte[]> fetched = http.send(
            HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());

        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).isEqualTo(content);
        // Served as a download rather than rendered inline under our own origin.
        assertThat(fetched.headers().firstValue("content-disposition").orElse(""))
            .contains("attachment");
    }

    @Test
    void anExecutableIsRejectedBeforeAnyUrlIsIssued() {
        Tenant tenant = tenant("mime@example.com");

        ApiClient.Response response = initUpload(tenant, "totally-safe.exe", "application/x-msdownload", 1024);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("MIME_TYPE_NOT_ALLOWED");
    }

    @Test
    void aFileOverTheLimitIsRejectedBeforeAnyUrlIsIssued() {
        Tenant tenant = tenant("toobig@example.com");

        ApiClient.Response response = initUpload(tenant, "huge.pdf", "application/pdf", 10_485_761L);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void anObjectLargerThanDeclaredIsCaughtAtAttachAndNotRecorded() throws Exception {
        // A presigned PUT constrains the content type but not the length. Declaring 10 bytes and
        // pushing far more is the obvious abuse, and attach is the only place it can be caught.
        Tenant tenant = tenant("liar@example.com");

        ApiClient.Response init = initUpload(tenant, "small.pdf", "application/pdf", 10);
        byte[] actuallyHuge = new byte[10_485_761];
        assertThat(putToPresignedUrl(init.json().path("presignedUrl").asText(), actuallyHuge, "application/pdf"))
            .isEqualTo(200);

        ApiClient.Response attached = api.post(
            "/api/v1/documents/" + init.json().path("uploadId").asText() + "/attach", Map.of(), tenant.token());

        assertThat(attached.status()).isEqualTo(400);
        assertThat(attached.errorCode()).isEqualTo("FILE_TOO_LARGE");
        assertThat(api.get("/api/v1/documents?entityType=TOURNAMENT&entityId=" + tenant.tournamentId(),
            tenant.token()).json()).isEmpty();
    }

    @Test
    void attachingBeforeTheClientUploadedIsAConflict() {
        Tenant tenant = tenant("neveruploaded@example.com");
        ApiClient.Response init = initUpload(tenant, "promised.pdf", "application/pdf", 100);

        ApiClient.Response attached = api.post(
            "/api/v1/documents/" + init.json().path("uploadId").asText() + "/attach", Map.of(), tenant.token());

        assertThat(attached.status()).isEqualTo(409);
        assertThat(attached.errorCode()).isEqualTo("UPLOAD_NOT_COMPLETED");
    }

    @Test
    void anUploadCannotBeAttachedTwice() throws Exception {
        Tenant tenant = tenant("twice@example.com");
        byte[] content = "%PDF-1.4".getBytes();

        ApiClient.Response init = initUpload(tenant, "once.pdf", "application/pdf", content.length);
        putToPresignedUrl(init.json().path("presignedUrl").asText(), content, "application/pdf");
        String uploadId = init.json().path("uploadId").asText();

        assertThat(api.post("/api/v1/documents/" + uploadId + "/attach", Map.of(), tenant.token()).status())
            .isEqualTo(201);
        ApiClient.Response second = api.post("/api/v1/documents/" + uploadId + "/attach", Map.of(), tenant.token());

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.errorCode()).isEqualTo("ALREADY_ATTACHED");
    }

    @Test
    void aFileCannotBeAttachedToAnotherTenantsTournament() {
        // The permission is checked against the *owning* entity's unit, not against anything the
        // caller sent, so knowing an id is not enough.
        Tenant mine = tenant("owner@example.com");
        Tenant theirs = tenant("outsider@example.com");

        ApiClient.Response response = api.post("/api/v1/documents/upload-init", Map.of(
            "fileName", "intrusion.pdf",
            "mimeType", "application/pdf",
            "sizeBytes", 100,
            "entityType", "TOURNAMENT",
            "entityId", mine.tournamentId()
        ), theirs.token());

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void anotherTenantCannotListMyDocuments() throws Exception {
        Tenant mine = tenant("listowner@example.com");
        byte[] content = "%PDF-1.4 private".getBytes();
        ApiClient.Response init = initUpload(mine, "private.pdf", "application/pdf", content.length);
        putToPresignedUrl(init.json().path("presignedUrl").asText(), content, "application/pdf");
        api.post("/api/v1/documents/" + init.json().path("uploadId").asText() + "/attach", Map.of(), mine.token());

        Tenant theirs = tenant("listoutsider@example.com");
        ApiClient.Response response = api.get(
            "/api/v1/documents?entityType=TOURNAMENT&entityId=" + mine.tournamentId(), theirs.token());

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void anEntityTypeNobodyDeployedAResolverForIsRefused() {
        // The resolver set doubles as the allow-list of attachable things: "attach this to an
        // audit_log row" is not a request that can be expressed.
        Tenant tenant = tenant("badtype@example.com");

        ApiClient.Response response = api.post("/api/v1/documents/upload-init", Map.of(
            "fileName", "wherever.pdf",
            "mimeType", "application/pdf",
            "sizeBytes", 100,
            "entityType", "AUDIT_LOG",
            "entityId", UUID.randomUUID()
        ), tenant.token());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("ENTITY_TYPE_NOT_ATTACHABLE");
    }

    @Test
    void anUnknownEntityIsANotFound() {
        Tenant tenant = tenant("missing@example.com");

        ApiClient.Response response = api.post("/api/v1/documents/upload-init", Map.of(
            "fileName", "orphan.pdf",
            "mimeType", "application/pdf",
            "sizeBytes", 100,
            "entityType", "TOURNAMENT",
            "entityId", UUID.randomUUID()
        ), tenant.token());

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.errorCode()).isEqualTo("ENTITY_NOT_FOUND");
    }

    @Test
    void documentsAttachToARegistrationToo() throws Exception {
        // Age proofs are the actual use case, and they hang off an entry rather than the tournament.
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedIndividualCompetition(api, "regdocs@example.com", 2);
        UUID registrationId = UUID.fromString(api.get(
            "/api/v1/competitions/" + entered.competitionId() + "/registrations", entered.token())
            .json().get(0).path("id").asText());

        byte[] content = "%PDF-1.4 birth certificate".getBytes();
        ApiClient.Response init = api.post("/api/v1/documents/upload-init", Map.of(
            "fileName", "birth-certificate.pdf",
            "mimeType", "application/pdf",
            "sizeBytes", content.length,
            "entityType", "REGISTRATION",
            "entityId", registrationId
        ), entered.token());
        assertThat(init.status()).isEqualTo(200);

        putToPresignedUrl(init.json().path("presignedUrl").asText(), content, "application/pdf");
        ApiClient.Response attached = api.post(
            "/api/v1/documents/" + init.json().path("uploadId").asText() + "/attach", Map.of(), entered.token());

        assertThat(attached.status()).isEqualTo(201);
        assertThat(attached.json().path("entityType").asText()).isEqualTo("REGISTRATION");

        JsonNode listed = api.get(
            "/api/v1/documents?entityType=REGISTRATION&entityId=" + registrationId, entered.token()).json();
        assertThat(listed).hasSize(1);
    }

    @Test
    void theObjectKeyIsChosenByTheServerAndNamespacedByTenant() {
        // A client-supplied key would be a path traversal, and would let one tenant overwrite
        // another's object.
        Tenant tenant = tenant("keys@example.com");

        ApiClient.Response init = initUpload(tenant, "../../etc/passwd", "application/pdf", 100);
        String url = init.json().path("presignedUrl").asText();

        assertThat(url).contains(tenant.organizationUnitId().toString());
        assertThat(url).doesNotContain("..");
        assertThat(url).doesNotContain("etc/passwd");
    }

    @Test
    void uploadsAndAttachmentsBothLandInTheAuditTrail() throws Exception {
        Tenant tenant = tenant("docaudit@example.com");
        byte[] content = "%PDF-1.4".getBytes();

        ApiClient.Response init = initUpload(tenant, "audited.pdf", "application/pdf", content.length);
        putToPresignedUrl(init.json().path("presignedUrl").asText(), content, "application/pdf");
        api.post("/api/v1/documents/" + init.json().path("uploadId").asText() + "/attach", Map.of(), tenant.token());

        List<String> actions = new java.util.ArrayList<>();
        api.get("/api/v1/audit-logs?limit=200", tenant.token()).json()
            .forEach(row -> actions.add(row.path("action").asText()));

        assertThat(actions).contains("document:upload-init", "document:attach");
    }
}
