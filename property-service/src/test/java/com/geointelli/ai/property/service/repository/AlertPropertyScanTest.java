package com.geointelli.ai.property.service.repository;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.geointelli.ai.property.service.dto.PropertySearchRequest;
import com.geointelli.ai.property.service.service.PropertySearchValidationService;
import com.geointelli.ai.property.service.service.impl.PropertySearchServiceImpl;

/** Executes the real Criteria ID query across more than one user-facing result page. */
@DataJpaTest(showSql=false,properties={"spring.datasource.url=jdbc:h2:mem:alert-property-scan;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none","spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PropertySearchServiceImpl.class)
class AlertPropertyScanTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PropertySearchServiceImpl search;
    @MockBean PropertySearchValidationService validation;
    @BeforeEach void setup() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS states(id BIGINT PRIMARY KEY, code VARCHAR, name VARCHAR)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS counties(id BIGINT PRIMARY KEY, name VARCHAR, state_id BIGINT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS properties(id BIGINT PRIMARY KEY, county_id BIGINT, bedroom_count NUMERIC)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS addresses(id BIGINT PRIMARY KEY, property_id BIGINT, city VARCHAR)");
        jdbc.update("INSERT INTO states VALUES(1,'FL','Florida')");
        jdbc.update("INSERT INTO counties VALUES(31,'Miami-Dade',1)");
        for(long id=1;id<=205;id++) {
            jdbc.update("INSERT INTO properties VALUES(?,31,?)",id,id%2==0 ? 3 : 1);
            jdbc.update("INSERT INTO addresses VALUES(?,?,'Miami')",id,id);
        }
    }
    @Test void keysetScanIgnoresUserPaginationAndSortButPreservesFilters() {
        PropertySearchRequest request=new PropertySearchRequest();
        request.setCountyId(31L); request.setState("fl"); request.setCity("miami");
        request.setMinBedrooms(new BigDecimal("2")); request.setSize(1); request.setPage(20);
        request.setSortBy("bedrooms"); request.setDirection("desc");
        List<Long> first=search.findMatchingIds(request,0,100);
        assertEquals(100,first.size()); assertEquals(2L,first.get(0)); assertEquals(200L,first.get(99));
        assertEquals(List.of(202L,204L),search.findMatchingIds(request,200,100));
        assertEquals(List.of(),search.findMatchingIds(request,204,100));
    }
    @Test void invalidCursorAndUnboundedBatchAreRejected() {
        assertThrows(IllegalArgumentException.class,()->search.findMatchingIds(new PropertySearchRequest(),-1,100));
        assertThrows(IllegalArgumentException.class,()->search.findMatchingIds(new PropertySearchRequest(),0,1001));
    }
}
