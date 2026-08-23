package com.personalfinance.personfinancerest.account;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class AccountConfiguration {

    @Bean
    @ConditionalOnMissingBean(FinancialAccountActivity.class)
    FinancialAccountActivity financialAccountActivity(List<FinancialAccountActivitySource> activitySources) {
        return accountId -> activitySources.stream().anyMatch(source -> source.existsFor(accountId));
    }
}
