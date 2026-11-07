package com.geointelli.ai.property.service.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps a job's input files after the HTTP request that uploaded them has finished, so the job can run in
 * the background and be resumed later. Files are addressed by the job's location plus a file name.
 */
public interface IngestionInputStore {

    /** Size and a change marker of a stored file; the marker differs when the content was replaced. */
    record StoredInput(long size, String etag) { }

    /** {@code S3} (shared across instances) or {@code LOCAL} (readable only on this instance). */
    String storageType();

    /** Instance scope recorded on jobs with LOCAL inputs; null for shared storage. */
    String scope();

    /** Location under which the files of this job are stored. */
    String locationFor(UUID jobId);

    /**
     * Copies the content to {@code location/name}.
     * @param size content length in bytes
     * @return size and change marker of the stored copy
     */
    StoredInput store(String location, String name, InputStream content, long size) throws IOException;

    /** Describes a stored file, or empty when it no longer exists. */
    Optional<StoredInput> describe(String location, String name);

    /** Returns a local, readable path for the file, downloading it into {@code workDir} when needed. */
    Path materialize(String location, String name, Path workDir) throws IOException;

    /** Removes every file stored under the location; missing files are ignored. */
    void deleteAll(String location);
}
