package com.geointelli.ai.property.service.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.dto.ListingSourceDTO;
import com.geointelli.ai.property.service.dto.ListingSourceFieldDTO;
import com.geointelli.ai.property.service.entity.ListingFieldAvailability;
import com.geointelli.ai.property.service.entity.ListingFieldGroup;
import com.geointelli.ai.property.service.entity.ListingSource;
import com.geointelli.ai.property.service.exception.ListingSourceNotFoundException;
import com.geointelli.ai.property.service.repository.ListingSourceFieldRepository;
import com.geointelli.ai.property.service.repository.ListingSourceRepository;
import com.geointelli.ai.property.service.service.ListingSourceService;
import lombok.RequiredArgsConstructor;

/**
 * Reads the seeded listing providers and their field mapping. Codes are matched in any letter case; filter
 * words are matched in any letter case too and rejected with the list of allowed values when unknown.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingSourceServiceImpl implements ListingSourceService {

    private final ListingSourceRepository sources;
    private final ListingSourceFieldRepository fields;

    @Override
    public List<ListingSourceDTO> list() {
        return sources.findAllByOrderByCreatedAtDesc().stream()
            .map(s -> ListingSourceDTO.from(s, fields.countBySourceId(s.getId()), null))
            .toList();
    }

    @Override
    public ListingSourceDTO get(String code) {
        ListingSource source = find(code);
        List<ListingSourceFieldDTO> mapping = fields.findFields(source.getId(), null, null).stream()
            .map(ListingSourceFieldDTO::from)
            .toList();
        return ListingSourceDTO.from(source, mapping.size(), mapping);
    }

    @Override
    public List<ListingSourceFieldDTO> fields(String code, String group, String availability) {
        ListingFieldGroup fieldGroup = parse(group, "group", ListingFieldGroup.class);
        ListingFieldAvailability fieldAvailability = parse(availability, "availability", ListingFieldAvailability.class);
        ListingSource source = find(code);
        return fields.findFields(source.getId(), fieldGroup, fieldAvailability).stream()
            .map(ListingSourceFieldDTO::from)
            .toList();
    }

    /** Looks the provider up by code, in any letter case; 404 when absent. */
    private ListingSource find(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        return sources.findByCodeIgnoreCase(code.trim())
            .orElseThrow(() -> new ListingSourceNotFoundException(code.trim().toUpperCase(Locale.ROOT)));
    }

    /**
     * Turns an optional filter word into its enum constant. A blank value means "no filter"; an unknown value
     * is a 400 whose message lists the allowed names.
     */
    private static <E extends Enum<E>> E parse(String value, String parameter, Class<E> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String wanted = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(type.getEnumConstants())
            .filter(constant -> constant.name().equals(wanted))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(parameter + " must be one of "
                + Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "))));
    }
}
