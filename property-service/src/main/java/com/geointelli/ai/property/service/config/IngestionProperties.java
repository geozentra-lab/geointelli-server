package com.geointelli.ai.property.service.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Input storage and worker settings of the background ingestion jobs (SCRUM-188), from application.yml/.env. */
@Component
@ConfigurationProperties(prefix = "ingestion")
@Validated
@Getter
@Setter
public class IngestionProperties {
    @Valid
    private Input input = new Input();
    @Valid
    private Jobs jobs = new Jobs();

    @Getter
    @Setter
    public static class Input {
        /** {@code s3} keeps inputs shared across instances; {@code local} pins each job to the instance that received it. */
        @Pattern(regexp = "s3|local")
        private String storage = "s3";
        private String s3Bucket;
        @NotBlank
        private String s3Prefix = "ingestion/inputs";
        @NotBlank
        private String localDirectory = "ingestion-inputs";
        /** Directory whose files may be named instead of uploaded; empty disables staged inputs. */
        private String stagingDirectory = "";
    }

    @Getter
    @Setter
    public static class Jobs {
        /** Whether this instance polls for and runs queued jobs. */
        private boolean enabled = true;
        /** Stable name of this instance; defaults to the host name. Jobs with local inputs are pinned to it. */
        private String instanceId = "";
        @Min(1000) @Max(3600000)
        private long pollDelayMillis = 5000;
        @Min(60) @Max(86400)
        private int leaseSeconds = 600;
        @Min(5) @Max(3600)
        private int heartbeatSeconds = 30;
        /** How long a job with local inputs may wait in the queue before it is marked INTERRUPTED. */
        @Min(60) @Max(86400)
        private int queueDeadlineSeconds = 600;
        @Min(1) @Max(10000)
        private int csvBatchSize = 500;
        @Min(0) @Max(100000)
        private int maxItemsPerJob = 1000;
        @Min(1) @Max(32)
        private int apiWorkers = 10;
        @Min(1) @Max(1000)
        private int apiBatchSize = 50;
        @Min(0) @Max(600000)
        private long apiBatchPauseMillis = 2000;
        @Min(1) @Max(10)
        private int apiAttempts = 3;

        /** The configured instance ID, or the host name when none is configured. */
        public String resolvedInstanceId() {
            if (instanceId != null && !instanceId.isBlank()) return instanceId.trim();
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException unknown) {
                return "instance";
            }
        }
    }
}
