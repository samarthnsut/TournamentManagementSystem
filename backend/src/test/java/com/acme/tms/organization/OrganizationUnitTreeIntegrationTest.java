package com.acme.tms.organization;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.support.ApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationUnitTreeIntegrationTest extends AbstractIntegrationTest {

    @Test
    void buildsTheSaiHaryanaSonipatTree() {
        ApiClient.Session session = api.registerTenant("sai-admin@example.com", "Sports Authority of India");
        UUID rootId = rootUnitId(session);

        UUID haryanaId = api.post("/api/v1/organization-units", Map.of(
            "parentOrganizationUnitId", rootId,
            "name", "Haryana State Association",
            "type", "STATE_ASSOCIATION"
        ), session.accessToken()).id();

        UUID sonipatId = api.post("/api/v1/organization-units", Map.of(
            "parentOrganizationUnitId", haryanaId,
            "name", "Sonipat District Association",
            "type", "DISTRICT_ASSOCIATION"
        ), session.accessToken()).id();

        JsonNode tree = api.get("/api/v1/organization-units/" + rootId + "/tree", session.accessToken()).json();

        assertThat(tree.path("name").asText()).isEqualTo("Sports Authority of India");
        JsonNode haryana = tree.path("children").get(0);
        assertThat(UUID.fromString(haryana.path("id").asText())).isEqualTo(haryanaId);
        assertThat(haryana.path("type").asText()).isEqualTo("STATE_ASSOCIATION");

        JsonNode sonipat = haryana.path("children").get(0);
        assertThat(UUID.fromString(sonipat.path("id").asText())).isEqualTo(sonipatId);
        assertThat(sonipat.path("children")).isEmpty();
    }

    @Test
    void generatesSlugFromNameAndRejectsCollisions() {
        ApiClient.Session session = api.registerTenant("slug-admin@example.com", "Slug Federation");
        UUID rootId = rootUnitId(session);

        ApiClient.Response created = api.post("/api/v1/organization-units", Map.of(
            "parentOrganizationUnitId", rootId,
            "name", "Punjab State Association",
            "type", "STATE_ASSOCIATION"
        ), session.accessToken());
        assertThat(created.json().path("slug").asText()).isEqualTo("punjab-state-association");

        ApiClient.Response collision = api.post("/api/v1/organization-units", Map.of(
            "parentOrganizationUnitId", rootId,
            "name", "Another Unit",
            "slug", "punjab-state-association",
            "type", "STATE_ASSOCIATION"
        ), session.accessToken());

        assertThat(collision.status()).isEqualTo(409);
        assertThat(collision.errorCode()).isEqualTo("SLUG_TAKEN");
    }

    @Test
    void rejectsChildUnderArchivedParent() {
        ApiClient.Session session = api.registerTenant("archive-admin@example.com", "Archive Federation");
        UUID rootId = rootUnitId(session);

        UUID stateId = api.post("/api/v1/organization-units", Map.of(
            "parentOrganizationUnitId", rootId,
            "name", "Doomed State Association",
            "type", "STATE_ASSOCIATION"
        ), session.accessToken()).id();

        assertThat(api.delete("/api/v1/organization-units/" + stateId, session.accessToken()).status()).isEqualTo(204);

        ApiClient.Response orphan = api.post("/api/v1/organization-units", Map.of(
            "parentOrganizationUnitId", stateId,
            "name", "Orphan District",
            "type", "DISTRICT_ASSOCIATION"
        ), session.accessToken());

        // An archived parent leaves the caller's subtree, so the scope check rejects it before create runs.
        assertThat(orphan.status()).isEqualTo(403);
    }

    @Test
    void archivedUnitDisappearsFromReads() {
        ApiClient.Session session = api.registerTenant("read-admin@example.com", "Read Federation");
        UUID rootId = rootUnitId(session);

        UUID stateId = api.post("/api/v1/organization-units", Map.of(
            "parentOrganizationUnitId", rootId,
            "name", "Temporary State Association",
            "type", "STATE_ASSOCIATION"
        ), session.accessToken()).id();

        api.delete("/api/v1/organization-units/" + stateId, session.accessToken());

        assertThat(api.get("/api/v1/organization-units/" + stateId, session.accessToken()).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/organization-units/" + rootId + "/tree", session.accessToken()).json().path("children")).isEmpty();
    }

    @Test
    void unauthenticatedCallsAreRejected() {
        assertThat(api.get("/api/v1/organization-units", null).status()).isEqualTo(401);
    }

    private UUID rootUnitId(ApiClient.Session session) {
        JsonNode units = api.get("/api/v1/organization-units", session.accessToken()).json();
        return UUID.fromString(units.get(0).path("id").asText());
    }
}
