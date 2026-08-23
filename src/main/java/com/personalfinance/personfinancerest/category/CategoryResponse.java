package com.personalfinance.personfinancerest.category;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID ownerId,
        String name,
        CategoryApplicability applicability,
        UUID parentId,
        CategoryStatus status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
    static CategoryResponse from(TransactionCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getOwnerId(),
                category.getName(),
                category.getApplicability(),
                category.getParentId(),
                category.getStatus(),
                category.getArchivedAt(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
