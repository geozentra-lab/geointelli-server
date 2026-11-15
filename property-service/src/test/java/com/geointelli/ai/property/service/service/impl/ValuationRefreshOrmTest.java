package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.Map;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;
import com.geointelli.ai.property.service.entity.Sale;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Real application, Hibernate hooks, PostgreSQL migration and HTTP history in an isolated schema. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "valuation.refresh.enabled=true",
    "valuation.refresh.scope=PROPERTY", "alerts.enabled=false", "alerts.publisher.enabled=false",
    "contact-requests.publisher-enabled=false", "ingestion.jobs.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="VALUATION_TEST_DB_URL", matches=".+")
class ValuationRefreshOrmTest {
    private static final String SCHEMA = "valuation_orm_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mvc;
    @MockBean private JwtDecoder jwtDecoder;
    @MockBean private S3Client s3Client;
    @MockBean private S3Presigner s3Presigner;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("VALUATION_TEST_DB_URL");
        if (!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/geozentra_scrum59_java_test"))
            throw new IllegalStateException("Only the dedicated local verification database is allowed");
        try (var connection = DriverManager.getConnection(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
                var statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS postgis");
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        registry.add("spring.datasource.url", () -> url + "?currentSchema=" + SCHEMA + ",public");
        registry.add("spring.datasource.username", () -> System.getenv("DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @BeforeAll
    void migration() throws Exception {
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V33__add_valuation_history_and_refresh_requests.sql")));
    }

    @AfterAll
    void cleanup() {
        // Generated schema name inside a strictly restricted test database; no shared source data is touched.
        jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @Test
    void committedAndRolledBackOrmWritesAndFrozenHistoryHttp() throws Exception {
        var transaction = new TransactionTemplate(transactionManager);
        Long id = transaction.execute(status -> {
            var property = new Property();
            property.setBedroomCount(new BigDecimal("3"));
            entityManager.persist(property);
            entityManager.flush();
            var sale = new Sale();
            sale.setProperty(property);
            sale.setSalePrice(new BigDecimal("695000"));
            sale.setQualifiedFlag("Q");
            entityManager.persist(sale);
            return property.getId();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM property_valuation_refresh_requests",Integer.class)).isEqualTo(2);
        transaction.executeWithoutResult(status -> entityManager.find(Property.class,id).setMessage("metadata only"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM property_valuation_refresh_requests",Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            entityManager.find(Property.class,id).setBedroomCount(new BigDecimal("8"));
            entityManager.flush();
            throw new IllegalStateException("Source verification failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM property_valuation_refresh_requests",Integer.class)).isEqualTo(2);
        transaction.executeWithoutResult(status -> entityManager.find(Property.class,id).setBedroomCount(new BigDecimal("4")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM property_valuation_refresh_requests",Integer.class)).isEqualTo(3);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            var prediction = new PropertyValuePrediction(); prediction.setPropertyId(id);
            entityManager.persist(prediction);
            entityManager.flush();
        })).hasMessageContaining("published by Python");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM property_value_predictions",Integer.class)).isZero();
        jdbc.update("""
            INSERT INTO property_value_prediction_history
              (property_id,provenance,valuation_status,predicted_price,forecast_1y,confidence,
               prediction_date,loaded_at,row_sha256,evidence)
            VALUES (?,'SEEDED_CURRENT','supported',671182.72,700000,'high','2026-11-08',
                    '2026-11-08T20:24:20Z',repeat('a',64),
                    '{"last_sale":{"price":695000,"date":"2026-05-06"}}')
            """,id);
        mvc.perform(get("/api/properties/id/{id}/valuations",id))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].provenanceLabel").value("recorded before history"))
            .andExpect(jsonPath("$.content[0].valuation.estimate.value").value(671182.72))
            .andExpect(jsonPath("$.content[0].modelEvidence.lastSale.price").value(695000));
        mvc.perform(get("/api/properties/id/9223372036854775807/valuations")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/valuation-refresh-requests")).andExpect(status().isUnauthorized());
        when(jwtDecoder.decode("normal-token")).thenReturn(Jwt.withTokenValue("normal-token").header("alg","none")
            .subject("normal").claim("realm_access",Map.of("roles",List.of("USER"))).build());
        when(jwtDecoder.decode("admin-token")).thenReturn(Jwt.withTokenValue("admin-token").header("alg","none")
            .subject("operator").claim("realm_access",Map.of("roles",List.of("admin"))).build());
        mvc.perform(get("/api/admin/valuation-refresh-requests").header("Authorization","Bearer normal-token"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/valuation-refresh-requests").header("Authorization","Bearer admin-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
    }
}
