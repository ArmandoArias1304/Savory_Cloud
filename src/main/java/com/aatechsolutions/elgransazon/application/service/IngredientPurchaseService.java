package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientPurchase;
import com.aatechsolutions.elgransazon.domain.entity.IngredientPurchaseLine;
import com.aatechsolutions.elgransazon.domain.entity.IngredientPurchaseStatus;
import com.aatechsolutions.elgransazon.domain.repository.IngredientPurchaseRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IngredientPurchaseService {

    private final IngredientPurchaseRepository purchaseRepository;
    private final CfdiPurchaseXmlParser parser;
    private final IngredientService ingredientService;

    @Transactional
    public IngredientPurchase createDraft(byte[] xml, Employee uploadedBy) {
        CfdiPurchaseXmlParser.InvoiceData invoice = parser.parse(xml);
        Company company = CompanyContext.requireCurrentCompany();
        String uuid = invoice.uuid().toUpperCase(Locale.ROOT);

        IngredientPurchase existing = purchaseRepository.findByCompanyAndUuid(company, uuid).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == IngredientPurchaseStatus.PENDING) {
                existing.setIssuerRfc(invoice.issuerRfc());
                existing.setIssuerName(invoice.issuerName());
                existing.setIssuedAt(invoice.issuedAt());
                existing.setCurrency(invoice.currency());
                existing.setTotal(invoice.total());
                existing.getLines().clear();
                addConceptLines(existing, invoice);
                return purchaseRepository.saveAndFlush(existing);
            }
            throw new IllegalArgumentException("Esta factura ya fue registrada y no puede volver a sumar stock");
        }

        IngredientPurchase purchase = IngredientPurchase.builder()
                .company(company)
                .uuid(uuid)
                .issuerRfc(invoice.issuerRfc())
                .issuerName(invoice.issuerName())
                .issuedAt(invoice.issuedAt())
                .currency(invoice.currency())
                .total(invoice.total())
                .uploadedBy(uploadedBy)
                .build();

        addConceptLines(purchase, invoice);

        return purchaseRepository.saveAndFlush(purchase);
    }

    private void addConceptLines(IngredientPurchase purchase, CfdiPurchaseXmlParser.InvoiceData invoice) {
        int lineNumber = 1;
        for (CfdiPurchaseXmlParser.ConceptData concept : invoice.concepts()) {
            purchase.addLine(IngredientPurchaseLine.builder()
                    .lineNumber(lineNumber++)
                    .productCode(concept.productCode())
                    .description(concept.description())
                    .purchasedQuantity(concept.quantity())
                    .purchaseUnit(concept.unit())
                    .unitPrice(concept.unitPrice())
                    .amount(concept.amount())
                    .discount(concept.discount())
                    .vatAmount(concept.vatAmount())
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public IngredientPurchase findPending(Long purchaseId) {
        Company company = CompanyContext.requireCurrentCompany();
        IngredientPurchase purchase = purchaseRepository.findByIdAndCompany(purchaseId, company)
                .orElseThrow(() -> new IllegalArgumentException("La compra no existe"));
        if (purchase.getStatus() != IngredientPurchaseStatus.PENDING) {
            throw new IllegalStateException("La compra ya fue confirmada");
        }
        return purchase;
    }

    @Transactional(readOnly = true)
    public IngredientPurchase findDetails(Long purchaseId) {
        Company company = CompanyContext.requireCurrentCompany();
        return purchaseRepository.findByIdAndCompany(purchaseId, company)
                .orElseThrow(() -> new IllegalArgumentException("La compra no existe"));
    }

    @Transactional(readOnly = true)
    public List<IngredientPurchase> findAll() {
        Company company = CompanyContext.requireCurrentCompany();
        return purchaseRepository.findByCompanyOrderByIssuedAtDesc(company);
    }

    @Transactional
    public IngredientPurchase confirm(Long purchaseId, List<Long> lineIds,
            List<Long> ingredientIds, List<BigDecimal> conversionFactors,
            boolean includeVat, Employee confirmedBy) {
        Company company = CompanyContext.requireCurrentCompany();
        IngredientPurchase purchase = purchaseRepository.findByIdAndCompany(purchaseId, company)
                .orElseThrow(() -> new IllegalArgumentException("La compra no existe"));
        if (purchase.getStatus() != IngredientPurchaseStatus.PENDING) {
            throw new IllegalStateException("La compra ya fue confirmada");
        }
        if (lineIds == null || ingredientIds == null || conversionFactors == null
                || lineIds.size() != purchase.getLines().size()
                || ingredientIds.size() != lineIds.size()
                || conversionFactors.size() != lineIds.size()) {
            throw new IllegalArgumentException("Debes asignar un ingrediente y una conversión a cada concepto");
        }

        Set<Long> submittedLineIds = new HashSet<>(lineIds);
        if (submittedLineIds.size() != purchase.getLines().size()
                || purchase.getLines().stream().anyMatch(line -> !submittedLineIds.contains(line.getId()))) {
            throw new IllegalArgumentException("Los conceptos enviados no corresponden a esta factura");
        }

        for (int index = 0; index < lineIds.size(); index++) {
            Long submittedLineId = lineIds.get(index);
            IngredientPurchaseLine line = purchase.getLines().stream()
                    .filter(candidate -> candidate.getId().equals(submittedLineId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Concepto de factura inválido"));
            Ingredient ingredient = ingredientService.findById(ingredientIds.get(index))
                    .orElseThrow(() -> new IllegalArgumentException("El ingrediente seleccionado no existe"));
            BigDecimal conversionFactor = conversionFactors.get(index);
            if (conversionFactor == null || conversionFactor.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("La conversión debe ser mayor que cero");
            }
            if (!Boolean.TRUE.equals(ingredient.getActive())) {
                throw new IllegalArgumentException("No se puede agregar stock a un ingrediente inactivo");
            }
            if (!sameCurrency(purchase.getCurrency(), ingredient.getCurrency())) {
                throw new IllegalArgumentException("La moneda de la factura debe coincidir con la del ingrediente "
                        + ingredient.getName());
            }

            BigDecimal stockQuantity = line.getPurchasedQuantity()
                    .multiply(conversionFactor)
                    .setScale(2, RoundingMode.HALF_UP);
            if (stockQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("La cantidad convertida debe ser mayor que cero");
            }
            if ("UN".equalsIgnoreCase(ingredient.getUnitOfMeasure())
                    && stockQuantity.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("El ingrediente " + ingredient.getName()
                        + " se mide en unidades enteras; revisa el factor de conversión");
            }

            BigDecimal netAmount = line.getAmount().subtract(line.getDiscount());
            if (netAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El descuento no puede superar el importe del concepto");
            }
            BigDecimal vatAmount = line.getVatAmount() == null ? BigDecimal.ZERO : line.getVatAmount();
            BigDecimal acquisitionCost = includeVat ? netAmount.add(vatAmount) : netAmount;
            BigDecimal costPerStockUnit = acquisitionCost.divide(stockQuantity, 2, RoundingMode.HALF_UP);

            line.setIngredient(ingredient);
            line.setConversionFactor(conversionFactor);
            line.setStockQuantity(stockQuantity);
            line.setCostPerStockUnit(costPerStockUnit);
            line.setStockHistory(ingredientService.addStockWithHistory(
                    ingredient.getIdIngredient(), stockQuantity, costPerStockUnit, confirmedBy));
        }

        purchase.setStatus(IngredientPurchaseStatus.CONFIRMED);
        purchase.setConfirmedAt(LocalDateTime.now());
        purchase.setIncludeVat(includeVat);
        return purchaseRepository.save(purchase);
    }

    @Transactional
    public IngredientPurchase cancel(Long purchaseId, Employee cancelledBy) {
        Company company = CompanyContext.requireCurrentCompany();
        IngredientPurchase purchase = purchaseRepository.findByIdAndCompany(purchaseId, company)
                .orElseThrow(() -> new IllegalArgumentException("La compra no existe"));
        if (purchase.getStatus() != IngredientPurchaseStatus.CONFIRMED) {
            throw new IllegalStateException("Solo se pueden cancelar compras confirmadas");
        }
        if (!purchase.canBeCancelled()) {
            throw new IllegalStateException("Esta factura no tiene vinculados sus movimientos de stock; "
                    + "no se puede cancelar automáticamente. Revisa y anula sus entradas desde el historial.");
        }

        String reason = "Cancelación de factura CFDI " + purchase.getUuid();
        for (IngredientPurchaseLine line : purchase.getLines()) {
            ingredientService.voidStockHistory(line.getIngredient().getIdIngredient(),
                    line.getStockHistory().getId(), reason, cancelledBy);
        }

        purchase.setStatus(IngredientPurchaseStatus.CANCELLED);
        purchase.setCancelledAt(LocalDateTime.now());
        purchase.setCancelledBy(cancelledBy);
        purchase.setCancellationReason(reason);
        return purchaseRepository.save(purchase);
    }

    private boolean sameCurrency(String invoiceCurrency, String ingredientCurrency) {
        String normalizedInvoice = invoiceCurrency == null ? "MXN" : invoiceCurrency;
        String normalizedIngredient = ingredientCurrency == null ? "MXN" : ingredientCurrency;
        return normalizedInvoice.equalsIgnoreCase(normalizedIngredient);
    }
}