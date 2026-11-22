package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.InvestorOpportunitiesCardDTO;
import com.geointelli.ai.property.service.dto.InvestorOpportunitiesCardDTO.Item;
import com.geointelli.ai.property.service.dto.OpportunityBrowseRequest;
import com.geointelli.ai.property.service.dto.OpportunityPageDTO;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.repository.PortfolioHoldingRepository;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.InvestorOpportunitiesService;
import com.geointelli.ai.property.service.service.OpportunityBrowseService;
import com.geointelli.ai.property.service.service.OpportunityBrowseService.SortOrder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the first page of the shared opportunity browse (SCRUM-76), best score first, and wraps it as a dashboard
 * card. The explanations (factors) and the evidence coverage of every item are copied unchanged; the only addition
 * is whether the investor already holds the property. An empty candidate set stays empty: the card reports the
 * browse status (NOT_SCORED, NO_ELIGIBLE_OPPORTUNITIES or NO_MATCHES) and lists no items.
 */
@Service
@Transactional(readOnly = true)
public class InvestorOpportunitiesServiceImpl implements InvestorOpportunitiesService {

    static final int MAX_PORTFOLIOS = 100;
    static final String NOTE = "First page of the shared opportunity browse (GET /api/opportunities, SCRUM-76) in "
            + "score order, bounded by limit. Factors and coveragePercent are the shared service's own; nothing is "
            + "rescored. Scores come from stored valuations, not from listings: a listed property is not known to "
            + "be for sale. alreadyHeld marks properties already in the included portfolios.";

    private final CurrentUserService currentUserService;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final OpportunityBrowseService opportunityBrowseService;
    private final Clock clock;

    public InvestorOpportunitiesServiceImpl(CurrentUserService currentUserService,
                                            PortfolioRepository portfolioRepository,
                                            PortfolioHoldingRepository portfolioHoldingRepository,
                                            OpportunityBrowseService opportunityBrowseService,
                                            @Qualifier("portfolioSummaryClock") Clock clock) {
        this.currentUserService = currentUserService;
        this.portfolioRepository = portfolioRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.opportunityBrowseService = opportunityBrowseService;
        this.clock = clock;
    }

    @Override
    public InvestorOpportunitiesCardDTO getCard(Long portfolioId, Long countyId, Long stateId, Integer limit) {
        positive("portfolioId", portfolioId);
        positive("countyId", countyId);
        positive("stateId", stateId);
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        Long userId = currentUserService.getCurrentUserEntity().getId();

        // Only the current user's portfolios: a foreign portfolio is the same 404 as a missing one
        List<Long> portfolioIds;
        if (portfolioId != null) {
            portfolioRepository.findByIdAndUser_Id(portfolioId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
            portfolioIds = List.of(portfolioId);
        } else {
            portfolioIds = portfolioRepository
                    .findByUser_Id(userId, PageRequest.of(0, MAX_PORTFOLIOS, Sort.by(Sort.Direction.ASC, "id")))
                    .map(Portfolio::getId)
                    .getContent();
        }

        // The shared service does the ranking and explains it; the card only takes the first page
        OpportunityPageDTO page = opportunityBrowseService.browse(
                new OpportunityBrowseRequest(0, size, countyId, stateId, null, null, null, SortOrder.SCORE_DESC.name()));
        List<OpportunityPageDTO.Item> candidates = page.results().content();

        Set<Long> held = new HashSet<>();
        if (!candidates.isEmpty() && !portfolioIds.isEmpty()) {
            held.addAll(portfolioHoldingRepository.findHeldPropertyIds(portfolioIds,
                    candidates.stream().map(OpportunityPageDTO.Item::propertyId).toList()));
        }

        InvestorOpportunitiesCardDTO card = new InvestorOpportunitiesCardDTO();
        card.setScope(portfolioId == null
                ? InvestorOpportunitiesCardDTO.SCOPE_ALL_PORTFOLIOS : InvestorOpportunitiesCardDTO.SCOPE_PORTFOLIO);
        card.setPortfolioId(portfolioId);
        card.setPortfolioCount(portfolioIds.size());
        card.setStatus(candidates.isEmpty()
                ? InvestorOpportunitiesCardDTO.STATUS_EMPTY : InvestorOpportunitiesCardDTO.STATUS_OK);
        card.setGeneratedAt(clock.instant());
        card.setScoreVersion(page.scoreVersion());
        card.setCandidateStatus(page.status());
        card.setCountyId(countyId);
        card.setStateId(stateId);
        card.setLimit(size);
        card.setTotalCandidates(page.results().totalElements());
        card.setItems(candidates.stream().map(candidate -> toItem(candidate, held)).toList());
        card.setNotes(page.notes());
        card.setNote(NOTE);
        return card;
    }

    private static Item toItem(OpportunityPageDTO.Item candidate, Set<Long> held) {
        return new Item(candidate.propertyId(), candidate.folio(), candidate.address(), candidate.city(),
                candidate.zip(), candidate.countyName(), candidate.stateCode(), candidate.score(), candidate.maxScore(),
                candidate.coveragePercent(), candidate.estimatedValue(), candidate.forecastGrowth1yPct(),
                candidate.factors(), candidate.valuationDataThrough(), candidate.scoredAt(),
                held.contains(candidate.propertyId()));
    }

    private static void positive(String name, Long value) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be a positive number");
        }
    }
}
