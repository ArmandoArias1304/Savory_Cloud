package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.CashRegisterService;
import com.aatechsolutions.elgransazon.application.service.CashierOrderServiceImpl;
import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.FacturamaService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.PromotionService;
import com.aatechsolutions.elgransazon.application.service.ReservationService;
import com.aatechsolutions.elgransazon.application.service.RestaurantTableService;
import com.aatechsolutions.elgransazon.application.service.SplitPaymentService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.application.service.WebSocketNotificationService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Los menús visuales del personal —cajero, cocina (chef, barista y parrillero) y
 * reparto— reciben los tamaños de cada platillo para su diálogo, y no como
 * recuadros: los hijos salen del grid.
 *
 * <p>Es el mismo reparto que el menú público ({@link PublicMenuVariantsModelTest})
 * y el del mesero ({@link WaiterMenuVariantsModelTest}), pero cada controlador lo
 * pide por su cuenta: si uno se quedara sin pedirlo, su pantalla volvería a
 * mostrar platillos con tamaños que no llevan a ninguna parte.</p>
 */
class StaffMenuVariantsModelTest {

    private static final Long PIZZA = 1L;
    private static final Long MEDIANA = 3L;
    private static final Long GRANDE = 2L;

    private final ItemMenuService itemMenuService = mock(ItemMenuService.class);
    private final CategoryService categoryService = mock(CategoryService.class);
    private final SystemConfigurationService configurationService =
            mock(SystemConfigurationService.class);

    @Test
    @DisplayName("el menú del cajero manda los tamaños de cada platillo")
    @SuppressWarnings("unchecked")
    void theCashierMenuSendsTheSizes() {
        CashierController controller = new CashierController(
                mock(CashierOrderServiceImpl.class),
                mock(OrderService.class),
                mock(RestaurantTableService.class),
                itemMenuService,
                mock(EmployeeService.class),
                configurationService,
                categoryService,
                mock(OrderRepository.class),
                mock(PromotionService.class),
                mock(BusinessHoursService.class),
                mock(WebSocketNotificationService.class),
                mock(DateTimeService.class),
                mock(ReservationService.class),
                mock(CashRegisterService.class));
        prepare();

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.viewMenu(model, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("cashier/menu/view");
        assertSizes(model, view);
    }

    @Test
    @DisplayName("el menú de cocina manda los tamaños de cada platillo")
    void theKitchenMenuSendsTheSizes() {
        ChefController controller = new ChefController(
                mock(OrderService.class),
                mock(OrderService.class),
                mock(OrderService.class),
                mock(EmployeeService.class),
                mock(OrderRepository.class),
                itemMenuService,
                categoryService,
                configurationService,
                mock(DateTimeService.class),
                mock(BusinessHoursService.class));
        prepare();

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.viewMenu(
                "chef", chef(), model, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("chef/menu/view");
        assertSizes(model, view);
    }

    @Test
    @DisplayName("el menú de reparto manda los tamaños de cada platillo")
    void theDeliveryMenuSendsTheSizes() {
        DeliveryController controller = new DeliveryController(
                mock(OrderService.class),
                mock(EmployeeService.class),
                configurationService,
                mock(DateTimeService.class),
                mock(BusinessHoursService.class),
                categoryService,
                itemMenuService,
                mock(FacturamaService.class),
                mock(OrderRepository.class),
                mock(SplitPaymentService.class),
                new com.fasterxml.jackson.databind.ObjectMapper());
        prepare();

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.viewMenu(chef(), model, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("delivery/menu/view");
        assertSizes(model, view);
    }

    @SuppressWarnings("unchecked")
    private void assertSizes(ExtendedModelMap model, String view) {
        Map<Long, List<ItemMenu>> byCategory =
                (Map<Long, List<ItemMenu>>) model.getAttribute("itemsByCategory");
        assertThat(byCategory.get(1L))
                .as(view + " debe dibujar sólo el item padre")
                .extracting(ItemMenu::getIdItemMenu)
                .containsExactly(PIZZA);

        Map<Long, List<Map<String, Object>>> sizes =
                (Map<Long, List<Map<String, Object>>>) model.getAttribute("sizeItemsDto");
        assertThat(sizes).as(view + " debe llevar los tamaños al diálogo").containsOnlyKeys(PIZZA);
        assertThat(sizes.get(PIZZA))
                .extracting(dto -> dto.get("id"))
                .containsExactly(MEDIANA, GRANDE);
    }

    private void prepare() {
        Category pizzas = Category.builder().idCategory(1L).name("Pizzas").build();
        ItemMenu pizza = item(PIZZA, "Pizza", "149.00", pizzas);
        ItemMenu mediana = size(MEDIANA, "Pizza mediana", "Mediana", "199.00", pizza, pizzas);
        ItemMenu grande = size(GRANDE, "Pizza grande", "Grande", "249.00", pizza, pizzas);

        when(itemMenuService.findAvailableItems()).thenReturn(List.of(pizza, mediana, grande));
        when(categoryService.getAllActiveCategories()).thenReturn(List.of(pizzas));
        when(configurationService.getConfiguration())
                .thenReturn(SystemConfiguration.builder().restaurantName("El Gran Sazon").build());
    }

    /** Un usuario de cocina sin rol de barista ni parrillero: se le espera en /chef. */
    private Authentication chef() {
        return new UsernamePasswordAuthenticationToken(
                "cocinero", "n/a", List.of(new SimpleGrantedAuthority("ROLE_CHEF")));
    }

    private ItemMenu item(Long id, String name, String price, Category category) {
        return ItemMenu.builder()
                .idItemMenu(id)
                .name(name)
                .description("Descripción de " + name)
                .price(new BigDecimal(price))
                .available(true)
                .category(category)
                .build();
    }

    private ItemMenu size(
            Long id, String name, String sizeName, String price, ItemMenu parent, Category category) {
        ItemMenu size = item(id, name, price, category);
        size.setSizeName(sizeName);
        size.setParentItem(parent);
        return size;
    }
}
