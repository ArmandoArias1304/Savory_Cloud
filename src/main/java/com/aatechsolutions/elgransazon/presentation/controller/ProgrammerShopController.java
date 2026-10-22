package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.GlobalSystemConfigService;
import com.aatechsolutions.elgransazon.application.service.ImageStorageService;
import com.aatechsolutions.elgransazon.application.service.ShopProductService;
import com.aatechsolutions.elgransazon.domain.entity.ShopPricingMode;
import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;
import com.aatechsolutions.elgransazon.util.ShopWhatsAppLink;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/**
 * Shop catalogue management for the programmer.
 *
 * <p>Products are global: they are published once and every restaurant sees
 * them. Photos are stored through the existing image storage service in the
 * global {@code shop-products} folder.</p>
 */
@Controller
@RequestMapping("/programmer/shop")
@PreAuthorize("hasRole('PROGRAMMER')")
@RequiredArgsConstructor
@Slf4j
public class ProgrammerShopController {

    private final ShopProductService shopProductService;
    private final ImageStorageService imageStorageService;
    private final GlobalSystemConfigService globalSystemConfigService;

    @GetMapping
    public String listProducts(@RequestParam(required = false) String search,
                               @RequestParam(required = false) String category,
                               @RequestParam(required = false) Boolean active,
                               Model model) {
        model.addAttribute("products", shopProductService.findForProgrammer(search, category, active));
        model.addAttribute("categories", shopProductService.findCategories(false));
        model.addAttribute("search", search);
        model.addAttribute("category", category);
        model.addAttribute("active", active);
        model.addAttribute("totalCount", shopProductService.countAll());
        model.addAttribute("activeCount", shopProductService.countActive());
        model.addAttribute("whatsappNumber",
                globalSystemConfigService.getConfiguration().getShopWhatsappNumber());
        return "programmer/shop/list";
    }

    /**
     * Save the WhatsApp number that receives the restaurant requests.
     */
    @PostMapping("/whatsapp")
    public String updateWhatsappNumber(@RequestParam(required = false) String shopWhatsappNumber,
                                       RedirectAttributes redirectAttributes) {
        if (shopWhatsappNumber != null && !shopWhatsappNumber.isBlank()
                && !ShopWhatsAppLink.isValidPhone(shopWhatsappNumber)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Escribe un WhatsApp válido con lada del país (10 a 15 dígitos).");
            return "redirect:/programmer/shop";
        }

        try {
            globalSystemConfigService.updateShopWhatsappNumber(shopWhatsappNumber);
            redirectAttributes.addFlashAttribute("successMessage", "WhatsApp de ventas actualizado");
        } catch (Exception e) {
            log.error("Error updating shop WhatsApp number: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No se pudo guardar el WhatsApp: " + e.getMessage());
        }
        return "redirect:/programmer/shop";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        ShopProduct product = new ShopProduct();
        product.setActive(true);
        product.setPricingMode(ShopPricingMode.FIXED);
        product.setPrice(BigDecimal.ZERO);
        product.setSortOrder(0);
        model.addAttribute("product", product);
        prepareFormModel(model, false);
        return "programmer/shop/form";
    }

    @PostMapping
    public String createProduct(@Valid @ModelAttribute("product") ShopProduct product,
                                BindingResult result,
                                @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                @RequestParam(value = "active", required = false, defaultValue = "false") boolean active,
                                @RequestParam(value = "featured", required = false, defaultValue = "false") boolean featured,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        product.setActive(active);
        product.setFeatured(featured);
        // The photo must come from an upload, never from a client-supplied URL.
        product.setImageUrl(null);
        validatePricing(product, result);
        if (!result.hasErrors()) {
            applyImage(product, imageFile, result, true);
        }

        if (result.hasErrors()) {
            prepareFormModel(model, false);
            return "programmer/shop/form";
        }

        try {
            ShopProduct saved = shopProductService.create(product);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Producto \"" + saved.getName() + "\" creado exitosamente");
            return "redirect:/programmer/shop";
        } catch (Exception e) {
            log.error("Error creating shop product: {}", e.getMessage());
            // The product was not saved: remove the uploaded photo.
            if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
                imageStorageService.deleteImage(product.getImageUrl());
            }
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, false);
            return "programmer/shop/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        ShopProduct product = shopProductService.findById(id).orElse(null);
        if (product == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Producto no encontrado");
            return "redirect:/programmer/shop";
        }
        model.addAttribute("product", product);
        prepareFormModel(model, true);
        return "programmer/shop/form";
    }

    @PostMapping("/{id}")
    public String updateProduct(@PathVariable Long id,
                                @Valid @ModelAttribute("product") ShopProduct product,
                                BindingResult result,
                                @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                @RequestParam(value = "active", required = false, defaultValue = "false") boolean active,
                                @RequestParam(value = "featured", required = false, defaultValue = "false") boolean featured,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        ShopProduct existing = shopProductService.findById(id).orElse(null);
        if (existing == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Producto no encontrado");
            return "redirect:/programmer/shop";
        }

        product.setIdProduct(id);
        product.setActive(active);
        product.setFeatured(featured);
        // Keep the stored photo; only a new upload can replace it.
        product.setImageUrl(existing.getImageUrl());
        validatePricing(product, result);

        String previousImage = existing.getImageUrl();
        if (!result.hasErrors()) {
            applyImage(product, imageFile, result,
                    existing.getImageUrl() == null || existing.getImageUrl().isBlank());
        }

        if (result.hasErrors()) {
            prepareFormModel(model, true);
            return "programmer/shop/form";
        }

        boolean replacedImage = imageFile != null && !imageFile.isEmpty()
                && previousImage != null && !previousImage.equals(product.getImageUrl());
        try {
            shopProductService.update(id, product);
            if (replacedImage) {
                imageStorageService.deleteImage(previousImage);
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Producto \"" + product.getName() + "\" actualizado exitosamente");
            return "redirect:/programmer/shop";
        } catch (Exception e) {
            log.error("Error updating shop product {}: {}", id, e.getMessage());
            // Do not leave an orphan photo when the update fails.
            if (replacedImage) {
                imageStorageService.deleteImage(product.getImageUrl());
            }
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, true);
            return "programmer/shop/form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggleProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            ShopProduct product = shopProductService.toggleActive(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    Boolean.TRUE.equals(product.getActive())
                            ? "Producto publicado en la tienda"
                            : "Producto oculto de la tienda");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/programmer/shop";
    }

    @PostMapping("/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            ShopProduct product = shopProductService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
            shopProductService.delete(id);
            imageStorageService.deleteImage(product.getImageUrl());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Producto \"" + product.getName() + "\" eliminado");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/programmer/shop";
    }

    /**
     * A fixed price is mandatory, a range needs both bounds in order, and a
     * quote publishes no amount. Only a fixed price may carry a promotion.
     */
    private void validatePricing(ShopProduct product, BindingResult result) {
        ShopPricingMode mode = product.getPricingModeOrDefault();
        product.setPricingMode(mode);

        if (mode == ShopPricingMode.FIXED) {
            if (product.getPrice() == null) {
                result.rejectValue("price", "price.required", "El precio es requerido");
                return;
            }
            validateDiscount(product, result);
            return;
        }

        // Ranges and quotes are never announced as a promotion.
        product.setDiscountPrice(null);

        if (mode == ShopPricingMode.RANGE) {
            if (product.getPrice() == null) {
                result.rejectValue("price", "price.required",
                        "Indica el precio mínimo del rango");
            }
            if (product.getMaxPrice() == null) {
                result.rejectValue("maxPrice", "maxPrice.required",
                        "Indica el precio máximo del rango");
            } else if (product.getPrice() != null
                    && product.getMaxPrice().compareTo(product.getPrice()) <= 0) {
                result.rejectValue("maxPrice", "maxPrice.order",
                        "El precio máximo debe ser mayor al precio mínimo");
            }
            return;
        }

        // Quote: nothing is published, and the note explains what it depends on.
        product.setPrice(BigDecimal.ZERO);
        product.setMaxPrice(null);
    }

    /**
     * A promotion is only valid when it is positive and below the regular
     * price; the programmer gets the reason right on the field.
     */
    private void validateDiscount(ShopProduct product, BindingResult result) {
        BigDecimal discount = product.getDiscountPrice();
        if (discount == null) {
            return;
        }
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            result.rejectValue("discountPrice", "discount.positive",
                    "El precio con descuento debe ser mayor a 0");
            return;
        }
        if (product.getPrice() == null || discount.compareTo(product.getPrice()) >= 0) {
            result.rejectValue("discountPrice", "discount.abovePrice",
                    "El precio con descuento debe ser menor al precio normal");
        }
    }

    /**
     * Upload the photo when a file arrives and enforce its presence when needed.
     */
    private void applyImage(ShopProduct product, MultipartFile imageFile,
                            BindingResult result, boolean required) {
        boolean hasFile = imageFile != null && !imageFile.isEmpty();
        boolean hasUrl = product.getImageUrl() != null && !product.getImageUrl().isBlank();

        if (!hasFile && !hasUrl && required) {
            result.rejectValue("imageUrl", "image.required", "La foto del producto es obligatoria");
            return;
        }
        if (!hasFile) {
            return;
        }
        if (!imageStorageService.isValidImage(imageFile)) {
            result.rejectValue("imageUrl", "image.invalid",
                    "La imagen debe ser JPG, PNG, GIF o WEBP y pesar máximo 3 MB");
            return;
        }
        try {
            product.setImageUrl(imageStorageService.saveImage(
                    imageFile, "shop-products", product.getName()));
        } catch (Exception e) {
            log.error("Error uploading shop product image: {}", e.getMessage());
            result.rejectValue("imageUrl", "image.error",
                    "No se pudo subir la imagen: " + e.getMessage());
        }
    }

    private void prepareFormModel(Model model, boolean isEdit) {
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("categories", shopProductService.findCategories(false));
    }
}
