package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.CloudflareImagesUrlHelper;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.GlobalSystemConfigService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.application.service.TicketEscPosService;
import com.aatechsolutions.elgransazon.application.service.TicketPdfService;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentDetail;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Courtesy items (cortesía): a menu item may be priced at $0.00, it still deducts
 * its recipe stock, it may only be sold inside the restaurant, and it is labelled
 * as "Cortesía" in the comanda while the printed ticket keeps the $0.00 and adds
 * the note.
 */
class CourtesyItemTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private SystemConfigurationService systemConfigurationService;
    private GlobalSystemConfigService globalSystemConfigService;
    private DateTimeService dateTimeService;

    @BeforeEach
    void setUp() {
        systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfiguration()).thenReturn(SystemConfiguration.builder()
                .restaurantName("El Gran Sazón")
                .address("Calle 1 #23, Querétaro")
                .phone("4421234567")
                .rfc("AAA010101AAA")
                .taxRate(new BigDecimal("16.00"))
                .build());

        globalSystemConfigService = mock(GlobalSystemConfigService.class);
        when(globalSystemConfigService.getConfiguration()).thenReturn(
                GlobalSystemConfig.builder().systemName("SavoryCloud").build());

        dateTimeService = mock(DateTimeService.class);
        when(dateTimeService.formatToCompanyTime(any(LocalDateTime.class), anyString()))
                .thenReturn("12/09/2026 09:35");
        when(dateTimeService.nowLocal()).thenReturn(LocalDateTime.of(2026, 9, 12, 9, 35));
    }

    // ========== Menu item: $0.00 is allowed ==========

    @Test
    void menuItemAllowsZeroPrice() {
        ItemMenu courtesy = ItemMenu.builder().name("Café cortesía").price(BigDecimal.ZERO).build();
        assertFalse(hasPriceViolation(courtesy), "price $0.00 must be accepted");

        ItemMenu negative = ItemMenu.builder().name("Café").price(new BigDecimal("-1.00")).build();
        assertTrue(hasPriceViolation(negative), "a negative price must be rejected");
    }

    private boolean hasPriceViolation(ItemMenu item) {
        return validator.validate(item).stream()
                .anyMatch(v -> "price".equals(v.getPropertyPath().toString()));
    }

    // ========== Courtesy items must stay inside the restaurant ==========

    @Test
    void courtesyItemIsOnlyValidWhenDineInOnly() {
        ItemMenu courtesy = ItemMenu.builder().name("Café cortesía").price(BigDecimal.ZERO).build();
        assertTrue(courtesy.isCourtesy());
        assertFalse(courtesy.isCourtesyProperlyRestricted(), "a $0.00 item must be dine-in only");

        courtesy.setDineInOnly(true);
        assertTrue(courtesy.isCourtesyProperlyRestricted(), "marked dine-in only it is valid");
    }

    @Test
    void paidItemsNeedNoDineInRestriction() {
        ItemMenu paid = ItemMenu.builder().name("Café americano").price(new BigDecimal("35.00")).build();
        assertFalse(paid.isCourtesy());
        assertTrue(paid.isCourtesyProperlyRestricted());
    }

    // ========== Comanda: labels instead of $0.00 ==========

    @Test
    void comandaLabelsCourtesyLines() {
        OrderDetail courtesy = OrderDetail.builder()
                .itemName("Café de cortesía")
                .quantity(2)
                .unitPrice(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO)
                .build();
        assertTrue(courtesy.isCourtesy());
        assertTrue("Cortesía".equals(courtesy.getFormattedUnitPrice()), courtesy.getFormattedUnitPrice());

        OrderDetail paid = OrderDetail.builder()
                .itemName("Café americano")
                .quantity(1)
                .unitPrice(new BigDecimal("35.00"))
                .subtotal(new BigDecimal("35.00"))
                .build();
        assertFalse(paid.isCourtesy());
        assertTrue("$35.00".equals(paid.getFormattedUnitPrice()), paid.getFormattedUnitPrice());
    }

    @Test
    void splitAccountLinesDetectCourtesy() {
        PaymentDetail courtesy = PaymentDetail.builder()
                .itemName("Café de cortesía")
                .quantity(BigDecimal.ONE)
                .unitPrice(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .build();
        assertTrue(courtesy.isCourtesy());

        PaymentDetail paid = PaymentDetail.builder()
                .itemName("Café americano")
                .quantity(BigDecimal.ONE)
                .unitPrice(new BigDecimal("35.00"))
                .subtotal(new BigDecimal("35.00"))
                .total(new BigDecimal("35.00"))
                .build();
        assertFalse(paid.isCourtesy());
    }

    // ========== Tickets: keep the $0.00 and add "Cortesía" ==========

    @Test
    void thermalTicketKeepsZeroAndAddsCourtesyNote() throws Exception {
        TicketEscPosService service = new TicketEscPosService(
                systemConfigurationService, globalSystemConfigService, dateTimeService,
                mock(CloudflareImagesUrlHelper.class));

        byte[] bytes = service.generateTicket(courtesyOrder());
        String ticket = new String(bytes, Charset.forName("Cp1252"));

        assertTrue(ticket.contains("$0.00"), "the printed total should keep the $0.00");
        assertTrue(ticket.contains("Cortesia"), "the courtesy line should be labelled in the ticket");
    }

    @Test
    void pdfTicketIsGeneratedForCourtesyOnlyOrder() throws Exception {
        TicketPdfService service = new TicketPdfService(
                systemConfigurationService, globalSystemConfigService, dateTimeService,
                mock(CloudflareImagesUrlHelper.class));

        assertTrue(service.generateTicket(courtesyOrder()).length > 1000);
    }

    private Order courtesyOrder() {
        OrderDetail courtesy = OrderDetail.builder()
                .itemName("Café de cortesía")
                .quantity(1)
                .unitPrice(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO)
                .build();

        return Order.builder()
                .orderNumber("ORD-20260912-001")
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.PAID)
                .createdAt(LocalDateTime.of(2026, 9, 12, 9, 30))
                .paidAt(LocalDateTime.of(2026, 9, 12, 9, 35))
                .subtotal(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .taxRate(new BigDecimal("16.00"))
                .total(BigDecimal.ZERO)
                .orderDetails(new ArrayList<>(List.of(courtesy)))
                .build();
    }
}
