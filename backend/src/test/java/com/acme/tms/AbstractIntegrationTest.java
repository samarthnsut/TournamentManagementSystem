package com.acme.tms;

import com.acme.tms.support.ApiClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@AutoConfigureMockMvc
@SpringBootTest
public abstract class AbstractIntegrationTest {

    /**
     * One container for the whole JVM. A per-class {@code @Container} would be stopped after its
     * class finished while Spring kept handing the cached context's DataSource to the next one.
     */
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    protected ApiClient api;

    /** Seeded roles, permissions and sports survive; everything a test creates does not. */
    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("""
            truncate competition, tournament, sport_configuration, venue,
                     user_role_assignment, refresh_token, app_user, organization_unit cascade
            """);
        api = new ApiClient(mockMvc, objectMapper);
    }
}
