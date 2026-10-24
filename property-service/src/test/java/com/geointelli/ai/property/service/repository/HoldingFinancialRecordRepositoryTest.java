package com.geointelli.ai.property.service.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
import com.geointelli.ai.property.service.entity.HoldingFinancialRecord;

/** Executes real JPA filtering, counting and ordering against an explicitly isolated H2 database. */
@DataJpaTest(showSql = false, properties = {
    "spring.datasource.url=jdbc:h2:mem:financial-repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HoldingFinancialRecordRepositoryTest {
    @Autowired
    private HoldingFinancialRecordRepository records;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    void createSchema() throws Exception {
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE properties (id BIGINT PRIMARY KEY)");
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V16__add_portfolios_and_holdings.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V17__add_holding_financial_records.sql"));
        }
    }

    @BeforeEach
    void insertRows() {
        jdbc.update("INSERT INTO users VALUES (7)");
        jdbc.update("INSERT INTO properties VALUES (100)");
        jdbc.update("INSERT INTO portfolios (id,user_id,name) VALUES (10,7,'Rentals'),(11,7,'Other')");
        jdbc.update("INSERT INTO portfolio_holdings (id,portfolio_id,property_id) VALUES (20,10,100),(21,11,100)");
        jdbc.update("INSERT INTO holding_financial_records (id,holding_id,category,amount,transaction_date) VALUES "
            + "(30,20,'RENT',2500,'2025-01-10'),(31,20,'REPAIRS',100,'2025-01-10'),"
            + "(32,20,'RENT',2500,'2025-02-01'),(33,21,'RENT',999,'2025-01-10')");
    }

    private PageRequest page(int number, int size) {
        return PageRequest.of(number, size, Sort.by(Sort.Direction.DESC, "transactionDate", "id"));
    }

    @Test
    void nullFiltersReturnOnlyTheRequestedHoldingsRecordsInStableOrder() {
        Page<HoldingFinancialRecord> result = records.findRecords(20L, null, null, null, page(0, 20));
        assertEquals(List.of(32L, 31L, 30L), result.getContent().stream().map(HoldingFinancialRecord::getId).toList());
        assertEquals(3, result.getTotalElements());
    }

    @Test
    void dateBoundsAreInclusiveAndCategoryFilterIsApplied() {
        LocalDate day = LocalDate.of(2025, 1, 10);
        Page<HoldingFinancialRecord> result = records.findRecords(20L, day, day, FinancialRecordCategory.RENT, page(0, 20));
        assertEquals(List.of(30L), result.getContent().stream().map(HoldingFinancialRecord::getId).toList());
    }

    @Test
    void singleSidedDateFiltersAreSupported() {
        Page<HoldingFinancialRecord> after = records.findRecords(20L, LocalDate.of(2025, 2, 1), null, null, page(0, 20));
        assertEquals(List.of(32L), after.getContent().stream().map(HoldingFinancialRecord::getId).toList());
        Page<HoldingFinancialRecord> before = records.findRecords(20L, null, LocalDate.of(2025, 1, 10), null, page(0, 20));
        assertEquals(List.of(31L, 30L), before.getContent().stream().map(HoldingFinancialRecord::getId).toList());
    }

    @Test
    void paginationCountsAllMatchesAndUsesTheSameDateTieBreak() {
        Page<HoldingFinancialRecord> result = records.findRecords(20L, null, null, null, page(1, 1));
        assertEquals(3, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals(31L, result.getContent().get(0).getId());
    }

    @Test
    void missingAndCrossHoldingRecordsStayUnavailable() {
        assertTrue(records.findByIdAndHolding_Id(30L, 21L).isEmpty());
        assertTrue(records.findByIdAndHolding_Id(999L, 20L).isEmpty());
        assertTrue(records.findRecords(999L, null, null, null, page(0, 20)).isEmpty());
    }
}
