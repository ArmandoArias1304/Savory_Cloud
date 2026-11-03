package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.dto.OccupiedTableInfoDTO;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.TableStatus;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.util.TableOccupancySupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Con mesas unidas, la misma ficha de ocupación debe explicar las DOS mesas.
 * Antes solo la mesa principal sabía quién la tenía; la secundaria aparecía
 * ocupada sin explicación (y el modal no podía mostrar el grupo).
 */
class TableOccupancySupportTest {

    @AfterEach
    void clearCompany() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("La ficha del pedido se asocia a la mesa principal y a las unidas")
    void occupancyIsMappedForEveryTableOfTheGroup() {
        Company company = Company.builder().idCompany(1L).name("El Gran Sazón").build();
        CompanyContext.setCurrentCompany(company);

        RestaurantTable table4 = RestaurantTable.builder()
                .id(4L).tableNumber(4).capacity(6).status(TableStatus.OCCUPIED).build();
        RestaurantTable table7 = RestaurantTable.builder()
                .id(7L).tableNumber(7).capacity(6).status(TableStatus.OCCUPIED).build();

        Order order = new Order();
        order.setIdOrder(501L);
        order.setOrderNumber("ORD-20261102-014");
        order.setTable(table7);
        order.addJoinedTable(table4);
        order.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        order.setStatus(com.aatechsolutions.elgransazon.domain.entity.OrderStatus.IN_PREPARATION);

        OrderRepository repository = mock(OrderRepository.class);
        when(repository.findActiveOrdersWithDetailsByTableIds(company, List.of(4L, 7L)))
                .thenReturn(List.of(order));

        DateTimeService dateTimeService = mock(DateTimeService.class);
        when(dateTimeService.toCompanyTime(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dateTimeService.nowLocal()).thenReturn(LocalDateTime.now());

        Map<Long, OccupiedTableInfoDTO> occupancy = TableOccupancySupport.build(
                List.of(table4, table7), "waiter", "juan", false, repository, dateTimeService);

        assertThat(occupancy).containsKeys(4L, 7L);
        assertThat(occupancy.get(4L).tableLabel()).isEqualTo("4+7");
        assertThat(occupancy.get(7L).tableLabel()).isEqualTo("4+7");
        assertThat(occupancy.get(4L).orderNumber()).isEqualTo("ORD-20261102-014");
    }
}
