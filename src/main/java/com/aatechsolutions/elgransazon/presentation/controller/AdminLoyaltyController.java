package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.dto.LoyaltyRewardForm;
import com.aatechsolutions.elgransazon.application.service.*;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Admin/manager configuration of the loyalty add-on: card rules, catalog of
 * rewards and the per-customer stamp accounts.
 */
@Controller
@RequestMapping("/admin/loyalty")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class AdminLoyaltyController {

    private final LoyaltyProgramService programService;
    private final LoyaltyRewardService rewardService;
    private final LoyaltyAccountService accountService;
    private final LoyaltyRedemptionService redemptionService;
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

    private String accessDenied(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage",
                "El módulo de fidelización no está activo para este restaurante.");
        return "redirect:/admin/dashboard";
    }

    @GetMapping
    public String index(@RequestParam(required = false) Long editReward,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (!hasAccess()) {
            return accessDenied(redirectAttributes);
        }

        Company company = company();
        LoyaltyProgram program = programService.getOrCreate(company);

        LoyaltyRewardForm rewardForm = new LoyaltyRewardForm();
        if (editReward != null) {
            LoyaltyReward reward = rewardService.require(company, editReward);
            rewardForm.setId(reward.getId());
            rewardForm.setItemMenuId(reward.getItemMenu() != null ? reward.getItemMenu().getIdItemMenu() : null);
            rewardForm.setName(reward.getName());
            rewardForm.setDescription(reward.getDescription());
            rewardForm.setStampsCost(reward.getStampsCost());
            rewardForm.setFreeQuantity(reward.safeFreeQuantity());
            rewardForm.setSortOrder(reward.getSortOrder());
            rewardForm.setActive(reward.isActive());
        }

        model.addAttribute("program", program);
        model.addAttribute("stampCycleOpen", programService.isCycleOpen(company));
        model.addAttribute("rewardForm", rewardForm);
        model.addAttribute("rewards", rewardService.findAll(company));
        model.addAttribute("items", rewardService.eligibleItems(company));
        model.addAttribute("accountsCount", accountService.count(company));
        model.addAttribute("outstandingStamps", accountService.outstandingStamps(company));
        model.addAttribute("redemptionsCount", redemptionService.countApplied(company));
        model.addAttribute("stampsRedeemed", redemptionService.stampsRedeemed(company));
        model.addAttribute("recentRedemptions", redemptionService.recent(company));
        model.addAttribute("activeMenu", "loyalty");
        return "admin/loyalty/index";
    }

    @PostMapping("/program")
    public String saveProgram(@ModelAttribute("program") LoyaltyProgram form,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        if (!hasAccess()) {
            return accessDenied(redirectAttributes);
        }
        try {
            programService.update(company(), form, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Configuración de la tarjeta guardada.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/loyalty";
    }

    /**
     * Colours of the client card (front and back). Kept apart from the rules form so
     * saving colours never touches the texts or the stamp rules.
     */
    @PostMapping("/program/colors")
    public String saveCardColors(@RequestParam(required = false) String cardFrontColor,
                                 @RequestParam(required = false) String cardBackColor,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        if (!hasAccess()) {
            return accessDenied(redirectAttributes);
        }
        try {
            programService.updateCardColors(company(), cardFrontColor, cardBackColor, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Colores de la tarjeta guardados.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/loyalty";
    }

    @PostMapping("/rewards")
    public String saveReward(@Valid @ModelAttribute("rewardForm") LoyaltyRewardForm form,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        if (!hasAccess()) {
            return accessDenied(redirectAttributes);
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return form.getId() != null
                    ? "redirect:/admin/loyalty?editReward=" + form.getId()
                    : "redirect:/admin/loyalty";
        }
        try {
            rewardService.save(company(), form);
            redirectAttributes.addFlashAttribute("successMessage",
                    form.getId() != null ? "Recompensa actualizada." : "Recompensa creada.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return form.getId() != null
                    ? "redirect:/admin/loyalty?editReward=" + form.getId()
                    : "redirect:/admin/loyalty";
        }
        return "redirect:/admin/loyalty";
    }

    @PostMapping("/rewards/{id}/toggle")
    public String toggleReward(@PathVariable Long id,
                               @RequestParam boolean active,
                               RedirectAttributes redirectAttributes) {
        if (!hasAccess()) {
            return accessDenied(redirectAttributes);
        }
        try {
            rewardService.setActive(company(), id, active);
            redirectAttributes.addFlashAttribute("successMessage",
                    active ? "Recompensa activada." : "Recompensa desactivada.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/loyalty";
    }

    @GetMapping("/accounts")
    public String accounts(Model model, RedirectAttributes redirectAttributes) {
        if (!hasAccess()) {
            return accessDenied(redirectAttributes);
        }
        Company company = company();
        LoyaltyProgram program = programService.getOrCreate(company);
        List<LoyaltyAccount> accounts = accountService.findAll(company);
        model.addAttribute("accounts", accounts);
        model.addAttribute("program", program);
        model.addAttribute("activeMenu", "loyalty");
        return "admin/loyalty/accounts";
    }

    @PostMapping("/accounts/{id}/adjust")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public String adjustAccount(@PathVariable Long id,
                                @RequestParam int delta,
                                @RequestParam(required = false) String reason,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        if (!hasAccess()) {
            return accessDenied(redirectAttributes);
        }
        try {
            accountService.adjust(company(), id, delta, reason, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Saldo ajustado correctamente.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/loyalty/accounts";
    }
}
