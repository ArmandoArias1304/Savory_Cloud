package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.application.dto.LoyaltyCustomerView;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyAccountRepository;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyProgramRepository;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyStampEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Creates and reads loyalty accounts. The account balance is a cache; the
 * authoritative history lives in the ledger.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyAccountService {

    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyProgramRepository programRepository;
    private final CustomerRepository customerRepository;
    private final LoyaltyStampEventRepository eventRepository;
    private final LoyaltyLedgerService ledgerService;
    private final LoyaltyCycleService cycleService;
    private final DateTimeService dateTimeService;

    @Transactional
    public LoyaltyAccount getOrCreate(Company company, Customer customer) {
        return accountRepository.findByCompanyAndCustomer(company, customer)
                .orElseGet(() -> accountRepository.save(LoyaltyAccount.builder()
                        .company(company)
                        .customer(customer)
                        .balance(0)
                        .lifetimeEarned(0)
                        .lifetimeRedeemed(0)
                        .active(true)
                        .build()));
    }

    /** Locks one account row; the caller must be inside a write transaction. */
    @Transactional
    public LoyaltyAccount lock(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta de fidelización no encontrada"));
    }

    @Transactional
    public LoyaltyAccount save(LoyaltyAccount account) {
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Optional<LoyaltyAccount> find(Company company, Customer customer) {
        return accountRepository.findByCompanyAndCustomer(company, customer);
    }

    @Transactional(readOnly = true)
    public Optional<LoyaltyAccount> find(Company company, Long customerId) {
        return accountRepository.findByCompanyAndCustomerId(company, customerId);
    }

    @Transactional(readOnly = true)
    public List<LoyaltyAccount> findAll(Company company) {
        return accountRepository.findByCompanyOrderByBalance(company);
    }

    @Transactional(readOnly = true)
    public long count(Company company) {
        return accountRepository.countByCompany(company);
    }

    @Transactional(readOnly = true)
    public long outstandingStamps(Company company) {
        return accountRepository.sumBalanceByCompany(company);
    }

    @Transactional(readOnly = true)
    public List<LoyaltyStampEvent> recentEvents(LoyaltyAccount account) {
        return eventRepository.findTop50ByAccountOrderByCreatedAtDesc(account);
    }

    /**
     * Staff picker. Requires at least two characters and never leaves the
     * current company; limits the result to 20 customers.
     */
    @Transactional(readOnly = true)
    public List<LoyaltyCustomerView> search(Company company, String rawTerm) {
        if (rawTerm == null || rawTerm.trim().length() < 2) {
            return List.of();
        }

        String term = rawTerm.trim();
        int stampsRequired = programRepository.findByCompany(company)
                .map(LoyaltyProgram::getStampsRequired)
                .orElse(10);

        List<Customer> customers = customerRepository.searchActiveByCompany(
                company, term, PageRequest.of(0, 20));

        List<LoyaltyCustomerView> result = new ArrayList<>(customers.size());
        for (Customer customer : customers) {
            LoyaltyAccount account = accountRepository.findByCompanyAndCustomer(company, customer).orElse(null);
            result.add(new LoyaltyCustomerView(
                    customer.getIdCustomer(),
                    customer.getFullName(),
                    customer.getPhone(),
                    account != null ? account.safeBalance() : 0,
                    stampsRequired,
                    account != null ? account.safeLifetimeEarned() : 0,
                    account != null && account.getLastEarnedAt() != null
                            ? dateTimeService.formatToCompanyTime(account.getLastEarnedAt(), "dd/MM/yyyy")
                            : null
            ));
        }
        return result;
    }

    /**
     * Audited manual adjustment. Only ADMIN should reach this method from the
     * controller. A negative adjustment cannot take the balance below zero.
     */
    @Transactional
    public LoyaltyAccount adjust(Company company,
                                 Long accountId,
                                 int delta,
                                 String reason,
                                 String username) {
        if (!cycleService.processAndCheck(company)) {
            throw new IllegalStateException(
                    "El periodo de sellos venció. Configura una nueva fecha o quita el límite "
                            + "antes de ajustar sellos.");
        }
        if (delta == 0) {
            throw new IllegalArgumentException("El ajuste debe ser diferente de cero");
        }

        LoyaltyAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta de fidelización no encontrada"));

        if (!account.getCompany().getIdCompany().equals(company.getIdCompany())) {
            throw new IllegalArgumentException("La cuenta no pertenece a este restaurante");
        }
        if (!account.isActive()) {
            throw new IllegalStateException("La cuenta está desactivada");
        }

        if (delta > 0) {
            account.earn(delta);
        } else {
            account.redeem(-delta);
        }

        LoyaltyAccount saved = accountRepository.save(account);
        ledgerService.record(company, saved, null, null,
                LoyaltyStampEventType.ADJUST, delta, saved.safeBalance(),
                reason != null && !reason.isBlank() ? reason.trim() : "Ajuste manual",
                username);

        log.info("Loyalty account {} adjusted by {} stamps by {}: {}",
                accountId, delta, username, reason);
        return saved;
    }
}
