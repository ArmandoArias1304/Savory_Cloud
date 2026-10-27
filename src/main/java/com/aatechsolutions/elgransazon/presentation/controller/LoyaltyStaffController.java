package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.dto.LoyaltyRedeemLine;
import com.aatechsolutions.elgransazon.application.dto.LoyaltyRedemptionResult;
import com.aatechsolutions.elgransazon.application.service.*;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Staff-facing loyalty counter shared by every role that serves customers:
 * waiters at a table, cashiers at the counter, and admins/managers helping
 * anywhere.
 *
 * <p>The screen searches a registered customer by name or phone, shows how many
 * stamps they have and which rewards they can already take, and lets staff build
 * a basket with several rewards. Cashier/admin/manager redeem it as a $0.00
 * order; waiters can attach the free lines to the table's open bill so the
 * kitchen knows where to send them.</p>
 */
@Controller
@RequestMapping("/loyalty")
@PreAuthorize("hasAnyRole('ROLE_WAITER', 'ROLE_CASHIER', 'ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class LoyaltyStaffController {

    private static final String LAYOUT_ADMIN = "admin";
    private static final String LAYOUT_CASHIER = "cashier";
    private static final String LAYOUT_WAITER = "waiter";

    private final LoyaltyProgramService programService;
    private final LoyaltyRewardService rewardService;
    private final LoyaltyAccountService accountService;
    private final LoyaltyRedemptionService redemptionService;
    private final CustomerService customerService;
    private final FeatureAccessService featureAccessService;
    private final LicenseService licenseService;

    private Company company() {
        return CompanyContext.requireCurrentCompany();
    }

    private boolean hasAccess() {
        Company company = company();
        return licenseService.hasCustomerModuleAccess()
                && featureAccessService.isEnabled(company, FeatureKey.LOYALTY);
    }

    private String layout(Authentication authentication) {
        if (hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_MANAGER")) {
            return LAYOUT_ADMIN;
        }
        if (hasRole(authentication, "ROLE_CASHIER")) {
            return LAYOUT_CASHIER;
        }
        return LAYOUT_WAITER;
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (role.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private String accessDenied(Authentication authentication, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage",
                "El módulo de fidelización no está activo para este restaurante.");
        return switch (layout(authentication)) {
            case LAYOUT_ADMIN -> "redirect:/admin/dashboard";
            case LAYOUT_CASHIER -> "redirect:/cashier/dashboard";
            default -> "redirect:/waiter/dashboard";
        };
    }

    @GetMapping
    public String index(@RequestParam(required = false) String term,
                        @RequestParam(required = false) Long customerId,
                        @RequestParam(required = false) Long orderId,
                        Authentication authentication,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (!hasAccess()) {
            return accessDenied(authentication, redirectAttributes);
        }

        Company company = company();
        String layout = layout(authentication);
        LoyaltyProgram program = programService.getOrCreate(company);
        List<LoyaltyReward> rewards = rewardService.findActive(company);

        model.addAttribute("layout", layout);
        // Company id lets the page subscribe to this restaurant's WebSocket topics.
        model.addAttribute("companyId", company.getIdCompany());
        model.addAttribute("showSettlement", !LAYOUT_WAITER.equals(layout));
        model.addAttribute("program", program);
        model.addAttribute("stampCycleOpen", programService.isCycleOpen(company));
        model.addAttribute("rewards", rewards);
        model.addAttribute("term", term != null ? term : "");
        model.addAttribute("selectedOrderId", orderId);
        model.addAttribute("activeMenu", "redeem");

        // Any role can attach the free lines to a table that is already open;
        // counter-only redemptions leave the selector on "new order".
        model.addAttribute("openOrders", redemptionService.openOrdersForStaff(company));

        if (term != null && !term.trim().isEmpty()) {
            model.addAttribute("searchResults", accountService.search(company, term));
        }

        if (customerId != null) {
            customerService.findByIdAndCompany(customerId, company).ifPresent(customer -> {
                model.addAttribute("selectedCustomer", customer);
                accountService.find(company, customer).ifPresentOrElse(account -> {
                    model.addAttribute("selectedAccount", account);
                    model.addAttribute("recentEvents", accountService.recentEvents(account));
                }, () -> model.addAttribute("selectedAccount", null));
            });
        }

        LoyaltyAccount selectedAccount = (LoyaltyAccount) model.getAttribute("selectedAccount");
        long affordable = rewards.stream()
                .filter(reward -> selectedAccount != null
                        && selectedAccount.safeBalance() >= reward.safeStampsCost())
                .count();
        model.addAttribute("affordableRewards", affordable);

        if (!LAYOUT_WAITER.equals(layout)) {
            model.addAttribute("pendingRewardOrders", redemptionService.pendingRewardOrders(company));
        }

        return "loyalty/redeem";
    }

    @PostMapping("/redeem")
    public String redeem(@RequestParam Long customerId,
                         @RequestParam(required = false) String items,
                         @RequestParam(required = false) Long orderId,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        if (!hasAccess()) {
            return accessDenied(authentication, redirectAttributes);
        }
        try {
            List<LoyaltyRedeemLine> lines = LoyaltyRedeemLine.parse(items);

            LoyaltyRedemptionResult result = redemptionService.redeemBasket(
                    company(), customerId, lines, orderId, authentication.getName());

            redirectAttributes.addFlashAttribute("successMessage",
                    "Canje aplicado: " + result.rewardName()
                            + ". Pedido " + result.orderNumber()
                            + " · " + result.stampsSpent() + " sello(s) descontados."
                            + " Saldo: " + result.remainingBalance() + " sello(s)."
                            + (orderId != null ? " Se agregó a la cuenta abierta." : ""));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Loyalty redemption rejected for customer {}: {}", customerId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error redeeming loyalty reward for customer {}", customerId, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No se pudo canjear la recompensa: " + e.getMessage());
        }
        return "redirect:/loyalty?customerId=" + customerId
                + (orderId != null ? "&orderId=" + orderId : "");
    }

    /**
     * Closes a standalone reward order without collecting money. Reward orders
     * total $0.00 and cannot pass the payment screen, so staff finishes them here
     * once the free item has been handed over.
     */
    @PostMapping("/orders/{orderId}/settle")
    @PreAuthorize("hasAnyRole('ROLE_CASHIER', 'ROLE_ADMIN', 'ROLE_MANAGER')")
    public String settleRewardOrder(@PathVariable Long orderId,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        if (!hasAccess()) {
            return accessDenied(authentication, redirectAttributes);
        }
        try {
            redemptionService.settleRewardOrder(company(), orderId, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Pedido de recompensa cerrado sin cobro ($0.00).");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error settling reward order {}", orderId, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No se pudo cerrar el pedido: " + e.getMessage());
        }
        return "redirect:/loyalty";
    }
}
