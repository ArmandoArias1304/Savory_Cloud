package com.aatechsolutions.elgransazon.gaps;

import com.aatechsolutions.elgransazon.application.service.EmailService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.repository.IngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.support.TestDataHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * GAP-FILL — Tests de integración para:
 * 1) Validación de transiciones de estado de pedidos
 * 2) Inmutabilidad de pedidos PAID y CANCELLED
 * 3) Idempotencia (doble pago, doble cancelación)
 * 4) Deducción de stock al crear pedido con ingredientes
 * 5) Prevención de stock negativo
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("GAP — Order State Transitions, Idempotency & Stock Integration Tests")
class OrderStateIdempotencyStockIntegrationTest {

    private static final String SLUG = "gap-state-stock-c1";

    @Autowired private TestDataHelper helper;
    @Autowired @Qualifier("adminOrderService") private OrderService adminOrderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private IngredientRepository ingredientRepository;
    @MockBean  private EmailService emailService;

    private Company company;
    private Employee admin;
    private Category category;
    private IngredientCategory ingredientCategory;
    private RestaurantTable table;

    @BeforeEach
    void setUp() {
        helper.cleanUpBySlug(SLUG);
        company = helper.createActiveCompany(SLUG, "America/Mexico_City");
        admin = helper.createEmployee(company, "gap-state-admin", "Pass1234!", "ROLE_ADMIN");
        category = helper.createCategory(company, "Gap Cat");
        ingredientCategory = helper.createIngredientCategory(company, "Gap Ing Cat");
        table = helper.createRestaurantTable(company, 99, 4);
        CompanyContext.setCurrentCompany(company);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        helper.cleanUpBySlug(SLUG);
    }

    // ================================================================
    //  State Transition Validation
    // ================================================================

    @Test
    @DisplayName("Cadena de transiciones válida: PENDING → IN_PREPARATION → READY → DELIVERED → PAID")
    void validTransitionChain_succeeds() {
        Order order = helper.createPendingOrder(company, admin,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "ST-001");

        order = adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.IN_PREPARATION, admin.getUsername());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PREPARATION);

        order = adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.READY, admin.getUsername());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);

        order = adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.DELIVERED, admin.getUsername());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        order = adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.PAID, admin.getUsername());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("Transición inválida: PENDING → PAID (saltar estados) lanza excepción")
    void invalidTransition_pendingToPaid_throws() {
        Order order = helper.createPendingOrder(company, admin,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "ST-002");

        assertThatThrownBy(() ->
                adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.PAID, admin.getUsername()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Transición inválida: PENDING → READY (saltar IN_PREPARATION) lanza excepción")
    void invalidTransition_pendingToReady_throws() {
        Order order = helper.createPendingOrder(company, admin,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "ST-003");

        assertThatThrownBy(() ->
                adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.READY, admin.getUsername()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Transición inválida: PENDING → DELIVERED lanza excepción")
    void invalidTransition_pendingToDelivered_throws() {
        Order order = helper.createPendingOrder(company, admin,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "ST-004");

        assertThatThrownBy(() ->
                adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.DELIVERED, admin.getUsername()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ON_THE_WAY sólo válido para OrderType.DELIVERY")
    void onTheWay_validOnlyForDelivery() {
        // DINE_IN → ON_THE_WAY should fail
        Order dineIn = helper.createPendingOrder(company, admin,
                OrderType.DINE_IN, PaymentMethodType.CASH, "ST-005A");
        adminOrderService.changeStatus(dineIn.getIdOrder(), OrderStatus.IN_PREPARATION, admin.getUsername());
        adminOrderService.changeStatus(dineIn.getIdOrder(), OrderStatus.READY, admin.getUsername());

        assertThatThrownBy(() ->
                adminOrderService.changeStatus(dineIn.getIdOrder(), OrderStatus.ON_THE_WAY, admin.getUsername()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EN CAMINO");
    }

    @Test
    @DisplayName("ON_THE_WAY → DELIVERED válido para DELIVERY")
    void onTheWay_toDelivered_validForDelivery() {
        Order delivery = helper.createPendingOrder(company, admin,
                OrderType.DELIVERY, PaymentMethodType.CASH, "ST-005B");
        adminOrderService.changeStatus(delivery.getIdOrder(), OrderStatus.IN_PREPARATION, admin.getUsername());
        adminOrderService.changeStatus(delivery.getIdOrder(), OrderStatus.READY, admin.getUsername());
        adminOrderService.changeStatus(delivery.getIdOrder(), OrderStatus.ON_THE_WAY, admin.getUsername());

        Order result = adminOrderService.changeStatus(delivery.getIdOrder(), OrderStatus.DELIVERED, admin.getUsername());
        assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    // ================================================================
    //  PAID / CANCELLED Immutability (Idempotency)
    // ================================================================

    @Test
    @DisplayName("Pedido PAID: no se puede cambiar estado")
    void paidOrder_cannotChangeStatus() {
        Order order = helper.createDeliveredOrder(company, admin,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "IDP-001");
        adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.PAID, admin.getUsername());

        assertThatThrownBy(() ->
                adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.DELIVERED, admin.getUsername()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pagado");
    }

    @Test
    @DisplayName("Pedido PAID: no se puede cancelar")
    void paidOrder_cannotCancel() {
        Order order = helper.createDeliveredOrder(company, admin,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "IDP-002");
        adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.PAID, admin.getUsername());

        assertThatThrownBy(() ->
                adminOrderService.cancel(order.getIdOrder(), admin.getUsername()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Doble pago: pagar un pedido ya PAID lanza excepción")
    void doublePayment_throws() {
        Order order = helper.createDeliveredOrder(company, admin,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "IDP-003");
        adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.PAID, admin.getUsername());

        // Second payment attempt
        assertThatThrownBy(() ->
                adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.PAID, admin.getUsername()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pagado");
    }

    @Test
    @DisplayName("Pedido CANCELLED: no se puede cambiar estado")
    void cancelledOrder_cannotChangeStatus() {
        Order order = helper.createPendingOrder(company, admin,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "IDP-004");
        adminOrderService.cancel(order.getIdOrder(), admin.getUsername());

        assertThatThrownBy(() ->
                adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.IN_PREPARATION, admin.getUsername()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancelado");
    }

    @Test
    @DisplayName("Doble cancelación: cancelar un pedido ya CANCELLED lanza excepción")
    void doubleCancellation_throws() {
        Order order = helper.createPendingOrder(company, admin,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "IDP-005");
        adminOrderService.cancel(order.getIdOrder(), admin.getUsername());

        assertThatThrownBy(() ->
                adminOrderService.cancel(order.getIdOrder(), admin.getUsername()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Pedido CANCELLED: no se puede modificar (update)")
    void cancelledOrder_cannotUpdate() {
        Order order = helper.createPendingOrder(company, admin,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "IDP-006");
        adminOrderService.cancel(order.getIdOrder(), admin.getUsername());

        assertThatThrownBy(() ->
                adminOrderService.update(order.getIdOrder(), order, List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Pedido PAID: no se puede modificar (update)")
    void paidOrder_cannotUpdate() {
        Order order = helper.createDeliveredOrder(company, admin,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "IDP-007");
        adminOrderService.changeStatus(order.getIdOrder(), OrderStatus.PAID, admin.getUsername());

        assertThatThrownBy(() ->
                adminOrderService.update(order.getIdOrder(), order, List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ================================================================
    //  Stock Deduction & Negative Prevention
    // ================================================================

    @Test
    @DisplayName("Crear pedido deduce stock del ingrediente según receta")
    void createOrder_deductsIngredientStock() {
        Ingredient ingredient = helper.createIngredient(company, ingredientCategory, "Harina Gap");
        // ingredient starts with stock=10 KG
        BigDecimal initialStock = ingredient.getCurrentStock(); // 10

        // Item uses 0.5 KG of Harina per unit
        ItemMenu item = helper.createItemMenuWithIngredient(company, category,
                "Pan Gap", new BigDecimal("25.00"), ingredient, new BigDecimal("0.500"));

        // Create order with qty=2 → should deduct 2 * 0.5 = 1.0 KG
        Order order = Order.builder()
                .orderType(OrderType.DINE_IN)
                .table(table)
                .paymentMethod(PaymentMethodType.CASH)
                .createdBy(admin.getUsername())
                .build();

        OrderDetail detail = OrderDetail.builder()
                .itemMenu(item)
                .quantity(2)
                .unitPrice(item.getPrice())
                .build();

        adminOrderService.create(order, List.of(detail));

        // Verify stock decreased
        Ingredient updated = ingredientRepository.findById(ingredient.getIdIngredient()).orElseThrow();
        BigDecimal expectedStock = initialStock.subtract(new BigDecimal("1.000"));
        assertThat(updated.getCurrentStock().compareTo(expectedStock))
                .as("Stock should decrease from %s to %s", initialStock, expectedStock)
                .isEqualTo(0);
    }

    @Test
    @DisplayName("Stock insuficiente: crear pedido con más de lo disponible lanza excepción")
    void createOrder_insufficientStock_throws() {
        // Ingredient with very low stock: 0.1 KG
        Ingredient lowStockIngredient = Ingredient.builder()
                .company(company)
                .category(ingredientCategory)
                .name("Azucar Gap Low")
                .active(true)
                .unitOfMeasure("KG")
                .currency("MXN")
                .currentStock(new BigDecimal("0.100"))
                .minStock(BigDecimal.ONE)
                .build();
        lowStockIngredient = ingredientRepository.save(lowStockIngredient);

        // Item uses 1.0 KG per unit (more than stock)
        ItemMenu item = helper.createItemMenuWithIngredient(company, category,
                "Pastel Gap", new BigDecimal("50.00"), lowStockIngredient, new BigDecimal("1.000"));

        Order order = Order.builder()
                .orderType(OrderType.DINE_IN)
                .table(table)
                .paymentMethod(PaymentMethodType.CASH)
                .createdBy(admin.getUsername())
                .build();

        OrderDetail detail = OrderDetail.builder()
                .itemMenu(item)
                .quantity(1) // needs 1.0 KG but only 0.1 available
                .unitPrice(item.getPrice())
                .build();

        assertThatThrownBy(() ->
                adminOrderService.create(order, List.of(detail)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suficiente stock");
    }

    @Test
    @DisplayName("Cancelar pedido PENDING restaura stock de ingredientes")
    void cancelPendingOrder_restoresStock() {
        Ingredient ingredient = helper.createIngredient(company, ingredientCategory, "Leche Gap");
        BigDecimal initialStock = ingredient.getCurrentStock(); // 10

        ItemMenu item = helper.createItemMenuWithIngredient(company, category,
                "Cereal Gap", new BigDecimal("30.00"), ingredient, new BigDecimal("2.000"));

        // Create order qty=1 → deducts 2.0 KG
        Order order = Order.builder()
                .orderType(OrderType.DINE_IN)
                .table(table)
                .paymentMethod(PaymentMethodType.CASH)
                .createdBy(admin.getUsername())
                .build();

        OrderDetail detail = OrderDetail.builder()
                .itemMenu(item)
                .quantity(1)
                .unitPrice(item.getPrice())
                .build();

        Order created = adminOrderService.create(order, List.of(detail));

        // Stock should be 10 - 2 = 8
        Ingredient after = ingredientRepository.findById(ingredient.getIdIngredient()).orElseThrow();
        assertThat(after.getCurrentStock().compareTo(new BigDecimal("8.000"))).isEqualTo(0);

        // Cancel the order → stock should return to 10
        adminOrderService.cancel(created.getIdOrder(), admin.getUsername());

        Ingredient restored = ingredientRepository.findById(ingredient.getIdIngredient()).orElseThrow();
        assertThat(restored.getCurrentStock().compareTo(initialStock))
                .as("Stock should be restored to %s after cancel, got %s", initialStock, restored.getCurrentStock())
                .isEqualTo(0);
    }

    @Test
    @DisplayName("Múltiples pedidos deducen stock correctamente acumulado")
    void multipleOrders_accumulateStockDeduction() {
        Ingredient ingredient = helper.createIngredient(company, ingredientCategory, "Queso Gap");
        // Stock starts at 10 KG

        ItemMenu item = helper.createItemMenuWithIngredient(company, category,
                "Quesadilla Gap", new BigDecimal("20.00"), ingredient, new BigDecimal("0.250"));

        // Each DINE_IN order needs its own table
        RestaurantTable t1 = helper.createRestaurantTable(company, 71, 4);
        RestaurantTable t2 = helper.createRestaurantTable(company, 72, 4);

        // Order 1: qty=2 → deducts 0.5 KG → stock=9.5
        createSimpleOrder(item, 2, "MO-001", t1);

        Ingredient after1 = ingredientRepository.findById(ingredient.getIdIngredient()).orElseThrow();
        assertThat(after1.getCurrentStock().compareTo(new BigDecimal("9.500"))).isEqualTo(0);

        // Order 2: qty=4 → deducts 1.0 KG → stock=8.5
        createSimpleOrder(item, 4, "MO-002", t2);

        Ingredient after2 = ingredientRepository.findById(ingredient.getIdIngredient()).orElseThrow();
        assertThat(after2.getCurrentStock().compareTo(new BigDecimal("8.500"))).isEqualTo(0);
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private Order createSimpleOrder(ItemMenu item, int quantity, String orderNumber, RestaurantTable t) {
        Order order = Order.builder()
                .orderType(OrderType.DINE_IN)
                .table(t)
                .paymentMethod(PaymentMethodType.CASH)
                .createdBy(admin.getUsername())
                .build();

        OrderDetail detail = OrderDetail.builder()
                .itemMenu(item)
                .quantity(quantity)
                .unitPrice(item.getPrice())
                .build();

        return adminOrderService.create(order, List.of(detail));
    }
}
