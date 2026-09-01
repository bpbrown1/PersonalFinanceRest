package com.personalfinance.personfinancerest.account.currency;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts/currencies")
class AccountCurrencyController {

    @GetMapping
    List<String> findAll() {
        return SupportedCurrencyCatalog.codes();
    }
}
