package com.geointelli.ai.property.service.service.impl;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.RealtorClientsLeadsCardDTO;
import com.geointelli.ai.property.service.dto.RealtorClientsLeadsCardDTO.ClientItem;
import com.geointelli.ai.property.service.dto.RealtorClientsLeadsCardDTO.ClientsSection;
import com.geointelli.ai.property.service.dto.RealtorClientsLeadsCardDTO.LeadItem;
import com.geointelli.ai.property.service.dto.RealtorClientsLeadsCardDTO.LeadsSection;
import com.geointelli.ai.property.service.entity.CrmClient;
import com.geointelli.ai.property.service.entity.CrmLead;
import com.geointelli.ai.property.service.entity.LeadStage;
import com.geointelli.ai.property.service.repository.CrmClientRepository;
import com.geointelli.ai.property.service.repository.CrmLeadRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.RealtorDashboardClientsLeadsService;

import lombok.RequiredArgsConstructor;

/**
 * Builds the realtor clients-and-leads card from the current user's CRM rows (SCRUM-78). Every
 * query is scoped by the owner id, so no other user's contacts or leads can be read. Counts come
 * straight from the stored rows and stages; nothing is recomputed or cached.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RealtorDashboardClientsLeadsServiceImpl implements RealtorDashboardClientsLeadsService {
    private final CrmClientRepository clientRepository;
    private final CrmLeadRepository leadRepository;
    private final CurrentUserService currentUserService;

    @Override
    public RealtorClientsLeadsCardDTO getClientsLeadsCard(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ", got " + limit);
        }
        Long userId = currentUserService.getCurrentUserEntity().getId();
        PageRequest firstPage = PageRequest.of(0, limit);

        long clientTotal = clientRepository.countByUser_Id(userId);
        List<ClientItem> recentClients = clientRepository.findByUser_IdOrderByCreatedAtDesc(userId, firstPage)
            .stream().map(this::toClientItem).toList();

        // Start every stage at zero, then overwrite the stages the database actually returned.
        Map<LeadStage, Long> byStage = new EnumMap<>(LeadStage.class);
        for (LeadStage stage : LeadStage.values()) {
            byStage.put(stage, 0L);
        }
        for (CrmLeadRepository.LeadStageCount row : leadRepository.countByStage(userId)) {
            byStage.put(row.getStage(), row.getCount());
        }
        long leadTotal = byStage.values().stream().mapToLong(Long::longValue).sum();
        long open = byStage.get(LeadStage.NEW) + byStage.get(LeadStage.CONTACTED) + byStage.get(LeadStage.QUALIFIED);
        List<LeadItem> recentLeads = leadRepository.findByUser_IdOrderByCreatedAtDesc(userId, firstPage)
            .stream().map(this::toLeadItem).toList();

        RealtorClientsLeadsCardDTO card = new RealtorClientsLeadsCardDTO();
        card.setScope(RealtorClientsLeadsCardDTO.SCOPE_CURRENT_USER);
        card.setGeneratedAt(LocalDateTime.now());
        card.setStatus(RealtorClientsLeadsCardDTO.STATUS_OK);
        card.setLimit(limit);
        card.setClients(new ClientsSection(clientTotal, recentClients));
        card.setLeads(new LeadsSection(leadTotal, open, byStage, recentLeads));
        return card;
    }

    private ClientItem toClientItem(CrmClient c) {
        return new ClientItem(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getCreatedAt());
    }

    private LeadItem toLeadItem(CrmLead l) {
        return new LeadItem(l.getId(), l.getClient().getId(), l.getClient().getName(), l.getPropertyId(),
            l.getTitle(), l.getSource(), l.getStage(), l.getCreatedAt());
    }
}
