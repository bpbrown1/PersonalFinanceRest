package com.personalfinance.personfinancerest.account;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AccountConfiguration {

    @Bean
    @ConditionalOnMissingBean(FinancialAccountActivity.class)
    FinancialAccountActivity financialAccountActivity() {
        // Transactions do not exist yet. Their module will provide this policy implementation.
        return accountId -> false;
    }
}
