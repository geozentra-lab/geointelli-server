package com.geointelli.ai.property.service.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import com.geointelli.ai.property.service.enums.IngestionDataset;

/**
 * Loads one environmental reference file (SCRUM-201) with PostgreSQL COPY: the rows are normalised in Java,
 * streamed into a temporary staging table and moved into the target table with INSERT ... SELECT, replacing the
 * rows of the same scope (the county for flood zones, the state for the statewide files). Rows are never mapped to
 * JPA entities; a flood polygon can be 32 MB of WKT.
 */
public interface ReferenceDataImportService {

    /**
     * One file to load.
     *
     * @param dataset      CENSUS_TRACTS, NRI_TRACTS, FLOOD_ZONES or STORM_EVENTS
     * @param file         readable local copy of the input
     * @param sourceFile   file name recorded with every row and in the load history, for example 12086_miami-dade.csv
     * @param scopeFips    5-digit county FIPS for FLOOD_ZONES, 2-digit state FIPS for the statewide datasets; every
     *                     row must belong to it and its existing rows are replaced
     * @param downloadedOn date the source was downloaded (data vintage)
     * @param jobId        ingestion job that loads the file, recorded in the load history; may be null
     */
    record LoadRequest(IngestionDataset dataset, Path file, String sourceFile, String scopeFips,
            LocalDate downloadedOn, UUID jobId) { }

    /**
     * Outcome of a load.
     *
     * @param rowsRead            data rows in the file
     * @param rowsLoaded          source rows stored (every row is kept; equals rowsRead on success)
     * @param rowsWritten         table rows written: flood polygons are cut into parts, so this can exceed rowsLoaded
     * @param rowsWithoutGeometry flood rows whose polygon has no area left after repair (stored with geom NULL),
     *                            storm events without coordinates; 0 for the other datasets
     */
    record LoadResult(int rowsRead, int rowsLoaded, int rowsWritten, int rowsWithoutGeometry) { }

    /** A row the importer refuses; the whole file is rolled back. */
    class InvalidReferenceRowException extends IllegalArgumentException {
        private final long row;

        public InvalidReferenceRowException(long row, String message) {
            super("row " + row + ": " + message);
            this.row = row;
        }

        /** 1-based data row number (the header is row 0). */
        public long row() { return row; }
    }

    /**
     * Loads the file. Must run inside a transaction: the staging table, the replacement and the load history
     * commit together with the caller's job checkpoint, or not at all.
     *
     * @throws InvalidReferenceRowException if a row is malformed or outside the scope
     * @throws IOException                   if the file cannot be read
     */
    LoadResult load(LoadRequest request) throws IOException;

    /** Data rows in the file (parsed as CSV, so quoted line breaks do not count). */
    long countRows(Path file) throws IOException;
}
