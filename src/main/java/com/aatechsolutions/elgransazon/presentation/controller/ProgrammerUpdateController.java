package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.SystemUpdateService;
import com.aatechsolutions.elgransazon.domain.entity.SystemUpdate;
import com.aatechsolutions.elgransazon.domain.entity.SystemUpdateRole;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Programmer module for publishing system update announcements.
 *
 * <p>Each update has a version, a changelog and the roles that should receive
 * the automatic modal. Only PROGRAMMER can manage them.</p>
 */
@Controller
@RequestMapping("/programmer/updates")
@PreAuthorize("hasRole('PROGRAMMER')")
@RequiredArgsConstructor
@Slf4j
public class ProgrammerUpdateController {

    private final SystemUpdateService systemUpdateService;

    @GetMapping
    public String listUpdates(Model model) {
        model.addAttribute("updates", systemUpdateService.findAll());
        model.addAttribute("latest", systemUpdateService.findLatestPublished().orElse(null));
        model.addAttribute("totalCount", systemUpdateService.countAll());
        model.addAttribute("publishedCount", systemUpdateService.countPublished());
        return "programmer/updates/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        SystemUpdate update = new SystemUpdate();
        update.setPublished(true);
        model.addAttribute("update", update);
        prepareFormModel(model, false);
        return "programmer/updates/form";
    }

    @PostMapping
    public String createUpdate(@Valid @ModelAttribute("update") SystemUpdate update,
                               BindingResult result,
                               @RequestParam(name = "targetRoles", required = false)
                               List<SystemUpdateRole> targetRoles,
                               @RequestParam(value = "published", required = false, defaultValue = "false")
                               boolean published,
                               Authentication authentication,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        applyAudience(update, targetRoles, result);
        update.setPublished(published);

        if (result.hasErrors()) {
            prepareFormModel(model, false);
            return "programmer/updates/form";
        }

        try {
            SystemUpdate saved = systemUpdateService.create(update, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Actualización " + saved.getVersion() + " publicada correctamente");
            return "redirect:/programmer/updates";
        } catch (IllegalArgumentException e) {
            log.warn("Cannot create system update: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, false);
            return "programmer/updates/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model,
                               RedirectAttributes redirectAttributes) {
        SystemUpdate update = systemUpdateService.findById(id).orElse(null);
        if (update == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Actualización no encontrada");
            return "redirect:/programmer/updates";
        }
        model.addAttribute("update", update);
        prepareFormModel(model, true);
        return "programmer/updates/form";
    }

    @PostMapping("/{id}")
    public String updateUpdate(@PathVariable Long id,
                               @Valid @ModelAttribute("update") SystemUpdate update,
                               BindingResult result,
                               @RequestParam(name = "targetRoles", required = false)
                               List<SystemUpdateRole> targetRoles,
                               @RequestParam(value = "published", required = false, defaultValue = "false")
                               boolean published,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        update.setId(id);
        applyAudience(update, targetRoles, result);
        update.setPublished(published);

        if (result.hasErrors()) {
            prepareFormModel(model, true);
            return "programmer/updates/form";
        }

        try {
            SystemUpdate saved = systemUpdateService.update(id, update);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Actualización " + saved.getVersion() + " guardada correctamente");
            return "redirect:/programmer/updates";
        } catch (IllegalArgumentException e) {
            log.warn("Cannot update system update {}: {}", id, e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, true);
            return "programmer/updates/form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String togglePublished(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            SystemUpdate update = systemUpdateService.togglePublished(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    Boolean.TRUE.equals(update.getPublished())
                            ? "Actualización " + update.getVersion() + " publicada"
                            : "Actualización " + update.getVersion() + " oculta");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/programmer/updates";
    }

    @PostMapping("/{id}/delete")
    public String deleteUpdate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            SystemUpdate update = systemUpdateService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Actualización no encontrada"));
            systemUpdateService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Actualización " + update.getVersion() + " eliminada");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/programmer/updates";
    }

    private void applyAudience(SystemUpdate update, List<SystemUpdateRole> targetRoles,
                               BindingResult result) {
        Set<SystemUpdateRole> roles = targetRoles == null
                ? new HashSet<>()
                : new HashSet<>(targetRoles);
        update.setTargetRoles(roles);
        if (roles.isEmpty()) {
            result.rejectValue("targetRoles", "update.roles.required",
                    "Selecciona al menos un rol para el modal");
        }
    }

    private void prepareFormModel(Model model, boolean isEdit) {
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("roles", SystemUpdateRole.values());
    }
}
