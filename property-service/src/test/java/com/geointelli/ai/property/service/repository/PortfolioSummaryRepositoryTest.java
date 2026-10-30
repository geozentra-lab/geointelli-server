package com.geointelli.ai.property.service.repository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.PortfolioSummaryService;
import com.geointelli.ai.property.service.service.impl.PortfolioSummaryServiceImpl;
import com.geointelli.ai.property.service.service.impl.PortfolioValueSummaryServiceImpl;
import com.geointelli.ai.property.service.service.impl.PortfolioValuationServiceImpl;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import com.geointelli.ai.property.service.entity.HoldingFinancingType;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
import com.geointelli.ai.property.service.service.impl.PortfolioFinancialSummaryServiceImpl;
import com.geointelli.ai.property.service.service.impl.PortfolioExposureSummaryServiceImpl;

/** Executes actual JPQL constructors, joins and aggregation against isolated migration-created tables. */
@DataJpaTest(showSql=false,properties={"spring.datasource.url=jdbc:h2:mem:portfolio-summary;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none","spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PortfolioSummaryRepository.class,PortfolioSummaryServiceImpl.class,PortfolioValueSummaryServiceImpl.class,
    PortfolioValuationServiceImpl.class,PortfolioFinancialSummaryServiceImpl.class,PortfolioExposureSummaryServiceImpl.class,
    PortfolioSummaryRepositoryTest.ReportClock.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortfolioSummaryRepositoryTest {
    @Autowired JdbcTemplate jdbc;
    @SpyBean PortfolioSummaryRepository summaries;
    @Autowired PortfolioSummaryService reports;
    @MockBean CurrentUserService currentUser;
    @TestConfiguration
    static class ReportClock {
        @Bean("portfolioSummaryClock") Clock clock() { return Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"),ZoneOffset.UTC); }
    }
    final LocalDate from=LocalDate.of(2026,10,1), to=LocalDate.of(2026,10,26);
    @BeforeAll void createSchema() throws Exception {
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE states(id BIGINT PRIMARY KEY, code VARCHAR(2), name VARCHAR(100))");
        jdbc.execute("CREATE TABLE counties(id BIGINT PRIMARY KEY, name VARCHAR(100), state_id BIGINT)");
        jdbc.execute("CREATE TABLE properties(id BIGINT PRIMARY KEY, county_id BIGINT)");
        try(Connection connection=jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V16__add_portfolios_and_holdings.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V17__add_holding_financial_records.sql"));
        }
    }
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM holding_financial_records");
        jdbc.update("DELETE FROM portfolio_holdings");
        jdbc.update("DELETE FROM portfolios");
        jdbc.update("DELETE FROM properties");
        jdbc.update("DELETE FROM counties");
        jdbc.update("DELETE FROM states");
        jdbc.update("DELETE FROM users");
        User user=new User(); user.setId(8L); when(currentUser.getCurrentUserEntity()).thenReturn(user);
        jdbc.update("INSERT INTO users VALUES(8),(9)");
        jdbc.update("INSERT INTO states VALUES(1,'FL','Florida')");
        jdbc.update("INSERT INTO counties VALUES(31,'Miami-Dade',1)");
        jdbc.update("INSERT INTO properties VALUES(100,31),(101,NULL),(102,31)");
        jdbc.update("INSERT INTO portfolios(id,user_id,name) VALUES(1,8,'Mine'),(2,9,'Foreign'),(3,8,'Empty')");
        jdbc.update("INSERT INTO portfolio_holdings(id,portfolio_id,property_id,ownership_share_percent,financing_type,occupancy_status) "
            + "VALUES(10,1,100,50,'CASH','RENTED'),(11,1,101,100,'UNKNOWN','UNKNOWN'),(20,2,102,100,'CASH','VACANT')");
        record(10,"RENT","1000","2026-10-01","2026-09-01","2026-12-31");
        record(10,"RENT","2000","2026-10-26",null,null);
        record(10,"REPAIRS","400","2026-10-15",null,null);
        record(11,"INSURANCE","100","2026-10-02",null,null);
        record(10,"RENT","99999","2026-09-30",null,null);
        record(10,"RENT","99999","2026-10-27",null,null);
        record(20,"RENT","99999","2026-10-15",null,null);
    }
    void record(long holding,String category,String amount,String date,String start,String end) {
        jdbc.update("INSERT INTO holding_financial_records(holding_id,category,amount,transaction_date,period_start,period_end) "
            + "VALUES(?,?,?,?,?,?)",holding,category,new BigDecimal(amount),LocalDate.parse(date),
            start==null ? null : LocalDate.parse(start),end==null ? null : LocalDate.parse(end));
    }
    @Test void financialGroupingUsesInclusiveCashDatesAndExcludesOtherOwners() {
        var groups=summaries.financials(1L,8L,from,to);
        assertEquals(3,groups.size());
        var rent=groups.stream().filter(g->g.category()==FinancialRecordCategory.RENT).findFirst().orElseThrow();
        assertEquals(0,new BigDecimal("3000").compareTo(rent.amount())); assertEquals(2,rent.recordCount());
        assertTrue(summaries.financials(1L,9L,from,to).isEmpty()); assertEquals(0,summaries.holdingCount(1L,9L));
        var result=new PortfolioFinancialSummaryServiceImpl(summaries).summarize(1L,8L,from,to);
        assertEquals(new BigDecimal("1500.00"),result.income()); assertEquals(new BigDecimal("300.00"),result.expenses());
        assertEquals(new BigDecimal("1200.00"),result.cashFlow()); assertEquals(4,result.recordCount());
    }
    @Test void holdingProjectionPreservesMissingGeographyAndFinancing() {
        var holdings=summaries.holdings(1L,8L); assertEquals(2,holdings.size());
        assertEquals(HoldingFinancingType.CASH,holdings.get(0).financingType()); assertEquals(31L,holdings.get(0).countyId());
        assertNull(holdings.get(1).countyId()); assertNull(holdings.get(1).outstandingBalance());
        assertTrue(summaries.holdings(1L,9L).isEmpty());
        var result=new PortfolioExposureSummaryServiceImpl(summaries).summarize(1L,8L,to);
        assertEquals(2,result.totalHoldings()); assertEquals(new BigDecimal("100.00"),result.rentalOccupancyPercent());
        assertEquals(2,result.counties().size());
    }
    @Test void emptyPortfolioHasNoFabricatedCashOrGeography() {
        var result=new PortfolioFinancialSummaryServiceImpl(summaries).summarize(3L,8L,from,to);
        assertEquals("EMPTY",result.status()); assertEquals(new BigDecimal("0.00"),result.cashFlow());
        assertTrue(summaries.holdings(3L,8L).isEmpty());
    }
    @Test void composedReportUsesRealComponentTransactionsAndExplicitMissingValuations() {
        var result=reports.summarize(1L,from,to);
        assertEquals("AVAILABLE",result.status()); assertEquals(new BigDecimal("1200.00"),result.financials().cashFlow());
        assertEquals("UNAVAILABLE",result.valuationAndDebt().value().status());
        assertEquals("PARTIAL",result.valuationAndDebt().debt().status());
        assertEquals(new BigDecimal("0.00"),result.valuationAndDebt().debt().knownTotal());
        assertEquals(2,result.occupancyAndExposure().totalHoldings()); assertTrue(result.errors().isEmpty());
    }
    @Test void aRolledBackComponentDoesNotPoisonOtherTransactions() {
        doThrow(new DataAccessResourceFailureException("test database failure")).doCallRealMethod().when(summaries).holdings(1L,8L);
        var result=reports.summarize(1L,from,to);
        assertEquals("PARTIAL",result.status()); assertNull(result.valuationAndDebt());
        assertEquals(new BigDecimal("1200.00"),result.financials().cashFlow()); assertNotNull(result.occupancyAndExposure());
        assertEquals("valuationAndDebt",result.errors().get(0).section());
    }
    @Test void compositionEnforcesOwnershipBeforeQueryingHoldingFacts() {
        assertEquals(404,assertThrows(ResponseStatusException.class,()->reports.summarize(2L,from,to)).getStatusCode().value());
        verify(summaries,never()).holdings(2L,8L);
    }
    @Test void oneDayRangeIncludesThatDaysCash() {
        var groups=summaries.financials(1L,8L,from,from);
        assertEquals(1,groups.size()); assertEquals(0,new BigDecimal("1000").compareTo(groups.get(0).amount()));
    }
    @Test void moreThanOneResultPageIsIncluded() {
        for (int i=0;i<125;i++) record(10,"OTHER_INCOME","1","2026-10-10",null,null);
        var result=new PortfolioFinancialSummaryServiceImpl(summaries).summarize(1L,8L,from,to);
        assertEquals(129,result.recordCount()); assertEquals(new BigDecimal("1562.50"),result.income());
    }
}
