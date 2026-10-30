package com.geointelli.ai.notification.service.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.geointelli.ai.notification.service.config.NotificationProperties;
import com.geointelli.ai.notification.service.dto.AlertEventEnvelopeDTO;
import com.geointelli.ai.notification.service.dto.DecodedAlertEventDTO;
import com.geointelli.ai.notification.service.dto.NewMatchPayloadDTO;
import com.geointelli.ai.notification.service.exception.InvalidAlertEventException;
import com.geointelli.ai.notification.service.service.AlertEventDecoderService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Supports the producer's NEW_MATCH v1 events; future types/versions are
 * retained in the DLQ for recovery.
 */
@Service
public class AlertEventDecoderServiceImpl implements AlertEventDecoderService {
    private final JsonMapper json;
    private final Validator validator;
    private final NotificationProperties settings;

    public AlertEventDecoderServiceImpl(@Qualifier("notificationEventMapper") JsonMapper json,
            Validator validator, NotificationProperties settings) {
        this.json = json;
        this.validator = validator;
        this.settings = settings;
    }

    @Override
    public DecodedAlertEventDTO decode(byte[] body) {
        if (body == null || body.length == 0 || body.length > settings.getMaxBodyBytes())
            throw invalid("INVALID_MESSAGE_SIZE");
        AlertEventEnvelopeDTO event;
        try {
            event = json.readValue(body, AlertEventEnvelopeDTO.class);
        } catch (RuntimeException badJson) {
            throw invalid("INVALID_EVENT_JSON");
        }
        if (event == null || !validator.validate(event).isEmpty())
            throw invalid("INVALID_ENVELOPE");
        if (!"property-service".equals(event.source()) || event.eventVersion() != 1)
            throw invalid("UNSUPPORTED_SOURCE_OR_VERSION");
        if (!"NEW_MATCH".equals(event.eventType()))
            throw invalid("UNSUPPORTED_EVENT_TYPE");
        if (event.savedSearchId() == null || event.propertyId() != null || !event.payload().isObject())
            throw invalid("INVALID_NEW_MATCH_TARGET");
        NewMatchPayloadDTO payload;
        try {
            payload = json.treeToValue(event.payload(), NewMatchPayloadDTO.class);
        } catch (RuntimeException badPayload) {
            throw invalid("INVALID_NEW_MATCH_PAYLOAD");
        }
        if (!validator.validate(payload).isEmpty())
            throw invalid("INVALID_NEW_MATCH_PAYLOAD");
        int preview = payload.propertyIds().size();
        if (payload.newMatchCount() > payload.matchedCount() || payload.newMatchCount() < preview
                || payload.previewTruncated() != (payload.newMatchCount() > preview)
                || new HashSet<>(payload.propertyIds()).size() != preview
                || payload.observationStartedAt().isAfter(payload.observationFinishedAt())
                || payload.observationFinishedAt().isAfter(event.occurredAt()))
            throw invalid("INCONSISTENT_NEW_MATCH_PAYLOAD");
        try {
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(sorted(json.valueToTree(event))).getBytes(StandardCharsets.UTF_8)));
            return new DecodedAlertEventDTO(event, payload, fingerprint);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    /**
     * Object-key order and whitespace do not change the fingerprint; array order
     * remains significant.
     */
    private JsonNode sorted(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = json.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            for (Map.Entry<String, JsonNode> field : value.properties())
                fields.put(field.getKey(), field.getValue());
            for (Map.Entry<String, JsonNode> field : fields.entrySet())
                result.set(field.getKey(), sorted(field.getValue()));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = json.createArrayNode();
            for (JsonNode child : value)
                result.add(sorted(child));
            return result;
        }
        return value;
    }

    private InvalidAlertEventException invalid(String code) {
        return new InvalidAlertEventException(code);
    }
}
