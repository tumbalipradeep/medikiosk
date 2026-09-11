package in.devmedi.kiosk.module.audit;

import in.devmedi.kiosk.module.audit.entity.AuditEvent;
import in.devmedi.kiosk.module.audit.entity.AuditEventType;
import in.devmedi.kiosk.module.audit.entity.AuditOutcome;
import in.devmedi.kiosk.module.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the V13 Flyway migration applies cleanly and the audit_events
 * table has the correct structural shape. Also confirms the JPA entity
 * round-trips through the repository. This test runs as part of the normal
 * {@code @SpringBootTest} context so it exercises Flyway against the test
 * H2 database.
 */
@SpringBootTest
class AuditSchemaIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    void flywayV13MigrationApplied() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '13' AND \"success\" = TRUE",
                Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void auditEventsTableHasExpectedColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='AUDIT_EVENTS' ORDER BY ORDINAL_POSITION",
                String.class);
        assertThat(columns).containsExactly(
                "ID", "EVENT_TYPE", "OCCURRED_AT", "ACTOR_USERNAME", "ACTOR_ROLE",
                "CASE_ID", "OPERATION", "RESOURCE_TYPE", "OUTCOME", "FAILURE_REASON", "REQUEST_PATH");
    }

    @Test
    void auditEventsIndexesExist() {
        List<String> indexNames = jdbcTemplate.queryForList(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME='AUDIT_EVENTS'",
                String.class);
        assertThat(indexNames)
                .contains("IDX_AUDIT_EVENTS_OCCURRED_AT")
                .contains("IDX_AUDIT_EVENTS_ACTOR")
                .contains("IDX_AUDIT_EVENTS_CASE")
                .contains("IDX_AUDIT_EVENTS_TYPE");
    }

    @Test
    void repositoryRoundTripPersistsAndReadsBack() {
        AuditEvent event = new AuditEvent(
                AuditEventType.FHIR_EXPORT, Instant.now(),
                "physician", "PHYSICIAN", "case-schema-test",
                "EXPORT", "Bundle", AuditOutcome.SUCCESS, null,
                "/physician/cases/case-schema-test/fhir");
        AuditEvent saved = auditEventRepository.save(event);
        auditEventRepository.flush();

        AuditEvent loaded = auditEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getCaseId()).isEqualTo("case-schema-test");
        assertThat(loaded.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    }
}