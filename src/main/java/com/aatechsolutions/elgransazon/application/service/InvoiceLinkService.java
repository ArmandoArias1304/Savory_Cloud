package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates the self-invoice (autofactura) link of an already PAID sale.
 *
 * <p>The key is normally created while collecting the payment, but only when the
 * establishment already had Facturama enabled: restaurants that contracted the billing
 * service later have paid tickets without a link, so their tickets reprint with no QR and
 * the client cannot self-invoice. The admin/manager can generate the missing links from the
 * sales view with one click per sale.</p>
 *
 * <p>One sale is one invoiceable unit for a whole-order ticket, but each account is its own
 * unit for a split bill (each account prints its own ticket, so it carries its own QR).
 * Units that already have a link, that were invoiced individually or that were amparadas by
 * a factura global are silently skipped, and the result tells how many of each.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceLinkService {

    /** Ticket type of a whole-order link (used in the JSON payloads and logs). */
    public static final String TYPE_ORDER = "ORDER";

    /** Ticket type of a split-account link. */
    public static final String TYPE_PAYMENT = "PAYMENT";

    /** Public path of the self-invoice page. */
    public static final String AUTOFACTURA_PATH = "/autofactura/";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    /**
     * One generated link: a whole-order ticket or one account of a split bill.
     */
    public record GeneratedInvoiceLink(String type, Long id, String folio, String url) {}

    /**
     * Outcome of a generation: how many links were created and how many units were skipped
     * because they could not be linked (already linked, already invoiced or excluded).
     */
    public record InvoiceLinkResult(int generated, int skipped, List<GeneratedInvoiceLink> links) {
        public boolean hasLinks() {
            return !links.isEmpty();
        }
    }

    /**
     * Creates the self-invoice link of every invoiceable unit of the sale that does not have
     * one yet. The entities are re-read inside the transaction, so the changes are flushed
     * with dirty checking and the caller can simply return the generated links.
     *
     * @param orderId id of the sale (PAID order)
     * @param company establishment the sale belongs to (never trust the id alone)
     * @param baseUrl public base URL of the establishment, e.g. {@code https://resto.example.com}
     */
    @Transactional
    public InvoiceLinkResult generateForSale(Long orderId, Company company, String baseUrl) {
        if (orderId == null || company == null) {
            throw new IllegalArgumentException("La venta y el establecimiento son requeridos");
        }

        Order order = orderRepository.findByIdOrderAndCompany(orderId, company)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la venta solicitada"));

        String publicBaseUrl = normalizeBaseUrl(baseUrl);
        List<Payment> accounts = paymentRepository.findByOrderIdOrderByAccountNumberAsc(order.getIdOrder());
        List<GeneratedInvoiceLink> links = new ArrayList<>();
        int skipped = 0;

        if (accounts.isEmpty()) {
            // Whole-order ticket: the order itself is the invoiceable unit.
            if (order.canGenerateInvoiceLink()) {
                String key = newAutofacturaKey();
                String url = publicBaseUrl + AUTOFACTURA_PATH + key;
                order.setAutofacturaKey(key);
                order.setSelfInvoiceUrl(url);
                links.add(new GeneratedInvoiceLink(TYPE_ORDER, order.getIdOrder(), order.getOrderNumber(), url));
            } else {
                skipped++;
            }
        } else {
            // Split bill: each account prints its own ticket, so each one gets its own link.
            for (Payment account : accounts) {
                if (!account.canGenerateInvoiceLink()) {
                    skipped++;
                    continue;
                }
                String key = newAutofacturaKey();
                String url = publicBaseUrl + AUTOFACTURA_PATH + key;
                account.setAutofacturaKey(key);
                account.setSelfInvoiceUrl(url);
                links.add(new GeneratedInvoiceLink(TYPE_PAYMENT, account.getIdPayment(),
                        account.getPaymentFolio(), url));
            }
        }

        log.info("Self-invoice links generated for sale {} (company={}): {} created, {} skipped",
                order.getOrderNumber(), company.getSlug(), links.size(), skipped);

        return new InvoiceLinkResult(links.size(), skipped, links);
    }

    /** Fresh key of the self-invoice URL (same format used when collecting the payment). */
    public static String newAutofacturaKey() {
        return UUID.randomUUID().toString();
    }

    /** Removes a trailing slash so the URL never ends up with a double slash. */
    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("No se pudo determinar la dirección pública del sistema");
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
