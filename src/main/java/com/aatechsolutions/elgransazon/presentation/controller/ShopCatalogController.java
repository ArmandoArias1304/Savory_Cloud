package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.GlobalSystemConfigService;
import com.aatechsolutions.elgransazon.application.service.ShopProductService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.util.ShopWhatsAppLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of the programmer's shop for restaurant admins and managers.
 *
 * <p>Each product card links to WhatsApp with a prefilled message containing
 * the restaurant name and the product, so the programmer knows who is asking.
 * The card also opens a detail page with the full photo, description and
 * promotion, which is where the WhatsApp request is finally sent.</p>
 */
@Controller
@RequestMapping("/admin/shop")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class ShopCatalogController {

    private final ShopProductService shopProductService;
    private final GlobalSystemConfigService globalSystemConfigService;

    @GetMapping
    public String catalog(@RequestParam(required = false) String search,
                          @RequestParam(required = false) String category,
                          Model model) {
        List<ShopProduct> products = shopProductService.findActive(search, category);

        Company company = CompanyContext.getCurrentCompany();
        String restaurantName = company != null ? company.getName() : null;
        String whatsappNumber = globalSystemConfigService.getConfiguration().getShopWhatsappNumber();

        Map<Long, String> whatsappLinks = new LinkedHashMap<>();
        for (ShopProduct product : products) {
            whatsappLinks.put(product.getIdProduct(), ShopWhatsAppLink.build(
                    whatsappNumber,
                    restaurantName,
                    product.getName(),
                    ShopWhatsAppLink.formatPrice(product.getFinalPrice(), product.getCurrency())));
        }

        model.addAttribute("products", products);
        model.addAttribute("whatsappLinks", whatsappLinks);
        model.addAttribute("whatsappConfigured", ShopWhatsAppLink.isValidPhone(whatsappNumber));
        model.addAttribute("restaurantName", restaurantName);
        model.addAttribute("categories", shopProductService.findCategories(true));
        model.addAttribute("search", search);
        model.addAttribute("category", category);

        return "admin/shop/list";
    }

    /**
     * Detail page of a published product: large photo, full description and
     * the promotional price when there is one.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        ShopProduct product = shopProductService.findById(id)
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .orElse(null);
        if (product == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ese producto ya no está disponible en la tienda");
            return "redirect:/admin/shop";
        }

        Company company = CompanyContext.getCurrentCompany();
        String restaurantName = company != null ? company.getName() : null;
        String whatsappNumber = globalSystemConfigService.getConfiguration().getShopWhatsappNumber();

        model.addAttribute("product", product);
        model.addAttribute("whatsappLink", ShopWhatsAppLink.build(
                whatsappNumber,
                restaurantName,
                product.getName(),
                ShopWhatsAppLink.formatPrice(product.getFinalPrice(), product.getCurrency())));
        model.addAttribute("whatsappConfigured", ShopWhatsAppLink.isValidPhone(whatsappNumber));
        model.addAttribute("restaurantName", restaurantName);

        return "admin/shop/detail";
    }
}
