package com.personalfinance.personfinancerest.category;

import java.util.Set;
import java.util.UUID;

/**
 * Expands category groups for consumers such as transaction reporting.
 */
public interface CategoryHierarchy {

    Set<UUID> categoryAndDescendantIds(UUID ownerId, UUID rootCategoryId);
}
