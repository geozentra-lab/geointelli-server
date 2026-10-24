package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.PortfolioHoldingDTO;
import com.geointelli.ai.property.service.dto.PortfolioHoldingRequest;
import com.geointelli.ai.property.service.dto.PortfolioPageDTO;

/** Manages holdings after verifying the current user's ownership of the parent portfolio. */
public interface PortfolioHoldingService {
    /** Adds an internal property once per portfolio; the same property may appear in other portfolios. */
    PortfolioHoldingDTO create(Long portfolioId, PortfolioHoldingRequest request);
    /** Lists a bounded page of holdings, newest ID first. */
    PortfolioPageDTO<PortfolioHoldingDTO> list(Long portfolioId, int page, int size);
    /** Returns a holding only when it belongs to the requested owned portfolio. */
    PortfolioHoldingDTO get(Long portfolioId, Long holdingId);
    /** Patches supplied facts; nested financing/occupancy sections are replaced, or cleared by null. */
    PortfolioHoldingDTO update(Long portfolioId, Long holdingId, PortfolioHoldingRequest request);
    /** Removes the holding and its actual financial records; property, parcel, sale and image records remain. */
    void delete(Long portfolioId, Long holdingId);
}
