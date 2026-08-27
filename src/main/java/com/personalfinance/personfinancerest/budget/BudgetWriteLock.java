package com.personalfinance.personfinancerest.budget;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Coordinates budget creation and period changes across application instances. */
@Repository
class BudgetWriteLock {

    private final JdbcTemplate jdbcTemplate;

    BudgetWriteLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void acquire(UUID ownerId) {
        // Lock an existing owner row: locking a missing target budget cannot prevent duplicate copies.
        jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE id = ? FOR UPDATE", UUID.class, ownerId);
    }
}
