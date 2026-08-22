package com.personalfinance.personfinancerest.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {
}
