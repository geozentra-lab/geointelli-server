package com.geointelli.ai.property.service.service.impl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.postgresql.PGConnection;
import org.postgresql.copy.PGCopyOutputStream;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.ReferenceDataImportService;
import com.geointelli.ai.property.service.service.impl.ReferenceDataRows.Column;
import com.geointelli.ai.property.service.service.impl.ReferenceDataRows.RowMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * COPY loader of the reference files: environmental (SCRUM-201) and school (SCRUM-202). Within the caller's
 * transaction: lock the scope, create the staging table, stream the normalised rows into it with PgJDBC COPY, check
 * them, delete the scope's current rows, INSERT ... SELECT into the target table, check what was written and record
 * the load. School files replace the counties or years they contain (schools are upserted, so they keep their id),
 * then re-link zones to schools or rank the grades. Any failure rolls the whole file back, so a scope is either fully
 * replaced or left as it was.
 */
@Service
@Slf4j
public class ReferenceDataImportServiceImpl implements ReferenceDataImportService {
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
        .setIgnoreEmptyLines(true).get();
    private static final int COPY_BUFFER = 1 << 20;

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ReferenceDataImportServiceImpl(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    @Override
    public LoadResult load(LoadRequest request) throws IOException {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A reference data load must run inside a transaction");
        }
        IngestionDataset dataset = request.dataset();
        String scope = request.scopeFips();
        long started = System.nanoTime();
        jdbc.query(ReferenceDataSql.lock(), resultSet -> null, "reference:" + dataset + ":" + scope);
        int rowsRead;
        try (Reader reader = Files.newBufferedReader(request.file(), StandardCharsets.UTF_8);
                CSVParser parser = FORMAT.parse(reader)) {
            RowMapper mapper = ReferenceDataRows.forDataset(dataset, scope, parser.getHeaderNames(), objectMapper);
            jdbc.execute(ReferenceDataSql.createStaging(dataset, mapper.columns()));
            rowsRead = copy(dataset, mapper, parser);
        }
        long copied = System.nanoTime();

        String duplicateKeySql = ReferenceDataSql.duplicateKey(dataset);
        if (duplicateKeySql != null) {
            List<String> duplicate = jdbc.queryForList(duplicateKeySql, String.class);
            if (!duplicate.isEmpty()) {
                throw new InvalidReferenceRowException(0, "the key " + duplicate.get(0) + " appears more than once");
            }
        }
        if (dataset == IngestionDataset.SCHOOLS) {
            List<String> duplicate = jdbc.queryForList(ReferenceDataSql.duplicateStateSchoolId(), String.class);
            if (!duplicate.isEmpty()) {
                throw new InvalidReferenceRowException(0, "the state_school_id " + duplicate.get(0) + " is given to several schools");
            }
        }
        // School datasets replace the counties or years their file contains; the others replace their scope.
        int deleted = dataset.isSchoolData()
            ? jdbc.update(ReferenceDataSql.deleteSchoolScope(dataset))
            : jdbc.update(ReferenceDataSql.deleteScope(dataset), scope);
        if (ReferenceDataSql.insertTakesSourceFile(dataset)) {
            jdbc.update(ReferenceDataSql.insert(dataset), request.sourceFile());
        } else {
            jdbc.update(ReferenceDataSql.insert(dataset));
        }
        if (dataset.isSchoolData()) {
            String after = ReferenceDataSql.afterSchoolInsert(dataset);
            if (after != null) jdbc.update(after);
        }
        Map<String, Object> counts = jdbc.queryForMap(ReferenceDataSql.loaded(dataset),
            ReferenceDataSql.scopeParameters(dataset, scope));
        int rowsLoaded = ((Number) counts.get("loaded")).intValue();
        int rowsWritten = ((Number) counts.get("written")).intValue();
        int withoutGeometry = ((Number) counts.get("without_geometry")).intValue();
        if (rowsLoaded != rowsRead) {
            throw new IllegalStateException(request.sourceFile() + ": " + rowsRead + " rows read but " + rowsLoaded + " stored");
        }
        if (dataset == IngestionDataset.CENSUS_TRACTS && withoutGeometry > 0) {
            throw new InvalidReferenceRowException(0, withoutGeometry + " tracts have no area after their polygon was repaired");
        }
        if (dataset == IngestionDataset.SCHOOL_ZONES && withoutGeometry > 0) {
            throw new InvalidReferenceRowException(0, withoutGeometry + " zones have no area after their polygon was repaired");
        }
        requireValidGeometry(dataset, scope);
        if (dataset == IngestionDataset.SCHOOL_ZONES) {
            Long unlinked = jdbc.queryForObject(ReferenceDataSql.unlinkedZones(), Long.class);
            if (unlinked != null && unlinked > 0) {
                log.info("Reference data {} {}: {} zones name a school_key that no loaded school has; load schools to link them",
                    dataset, request.sourceFile(), unlinked);
            }
        }
        jdbc.update(ReferenceDataSql.recordLoad(), dataset.name(), ReferenceDataSql.source(dataset),
            Date.valueOf(request.downloadedOn()), request.sourceFile(),
            dataset == IngestionDataset.FLOOD_ZONES ? scope : null, rowsRead, rowsLoaded, request.jobId());
        log.info("Reference data {} {}: {} rows read, {} replaced, {} written, {} without geometry; copy {} ms, load {} ms",
            dataset, request.sourceFile(), rowsRead, deleted, rowsWritten, withoutGeometry,
            (copied - started) / 1_000_000, (System.nanoTime() - copied) / 1_000_000);
        return new LoadResult(rowsRead, rowsLoaded, rowsWritten, withoutGeometry);
    }

    @Override
    public long countRows(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8); CSVParser parser = FORMAT.parse(reader)) {
            long rows = 0;
            for (CSVRecord ignored : parser) rows++;
            return rows;
        }
    }

    /** Every stored polygon must be valid; a part that is not would make spatial lookups unreliable. */
    private void requireValidGeometry(IngestionDataset dataset, String scope) {
        String sql = ReferenceDataSql.invalidGeometry(dataset);
        if (sql == null) return;
        Long invalid = jdbc.queryForObject(sql, Long.class, ReferenceDataSql.scopeParameters(dataset, scope));
        if (invalid != null && invalid > 0) {
            throw new IllegalStateException(invalid + " stored geometries are not valid after repair");
        }
    }

    /** Streams the header and the normalised rows into the staging table through COPY; returns the data rows. */
    private int copy(IngestionDataset dataset, RowMapper mapper, CSVParser parser) throws IOException {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        PGCopyOutputStream out = null;
        boolean finished = false;
        try {
            PGConnection pg = connection.unwrap(PGConnection.class);
            out = new PGCopyOutputStream(pg, ReferenceDataSql.copy(dataset, mapper.columns()), COPY_BUFFER);
            Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), 1 << 16);
            writeLine(writer, mapper.columns().stream().map(Column::name).toList());
            int rows = 0;
            for (CSVRecord record : parser) {
                List<String> values;
                try {
                    values = mapper.map(record);
                } catch (IllegalArgumentException invalid) {
                    throw new InvalidReferenceRowException(record.getRecordNumber(), invalid.getMessage());
                }
                writeLine(writer, values);
                rows++;
            }
            writer.flush();
            out.endCopy();
            finished = true;
            return rows;
        } catch (SQLException error) {
            throw new DataAccessResourceFailureException("COPY into the staging table failed: " + error.getMessage(), error);
        } finally {
            if (!finished && out != null && out.isActive()) {
                try {
                    out.cancelCopy();
                } catch (SQLException ignored) {
                    // The transaction is rolled back by the caller either way.
                }
            }
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /** One CSV line for COPY: null is an empty unquoted field (SQL NULL); other values are quoted when needed. */
    static void writeLine(Writer writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(',');
            String value = values.get(i);
            if (value != null) writeField(writer, value);
        }
        writer.write('\n');
    }

    static void writeField(Writer writer, String value) throws IOException {
        boolean quote = value.isEmpty();
        for (int i = 0; i < value.length() && !quote; i++) {
            char c = value.charAt(i);
            quote = c == ',' || c == '"' || c == '\n' || c == '\r';
        }
        if (!quote) {
            writer.write(value);
            return;
        }
        writer.write('"');
        writer.write(value.indexOf('"') < 0 ? value : value.replace("\"", "\"\""));
        writer.write('"');
    }
}
