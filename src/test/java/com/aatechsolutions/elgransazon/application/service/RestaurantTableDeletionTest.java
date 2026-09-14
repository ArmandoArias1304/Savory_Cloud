package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.TableStatus;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.repository.ReservationRepository;
import com.aatechsolutions.elgransazon.domain.repository.RestaurantTableRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A table can be physically deleted only while it has no history: as soon as it
 * has orders or reservations it must be kept for reporting and only inactivated
 * (OUT_OF_SERVICE).
 */
class RestaurantTableDeletionTest {

    private static final Long TABLE_ID = 7L;

    private RestaurantTableRepository tableRepository;
    private ReservationRepository reservationRepository;
    private OrderRepository orderRepository;
    private RestaurantTableServiceImpl service;
    private RestaurantTable table;

    @BeforeEach
    void setUp() {
        tableRepository = mock(RestaurantTableRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        orderRepository = mock(OrderRepository.class);
        service = new RestaurantTableServiceImpl(
                tableRepository,
                reservationRepository,
                orderRepository,
                mock(SystemConfigurationService.class),
                mock(DateTimeService.class));

        Company company = Company.builder().idCompany(1L).name("Test").slug("test").build();
        CompanyContext.setCurrentCompany(company);

        table = RestaurantTable.builder()
                .id(TABLE_ID)
                .tableNumber(7)
                .capacity(4)
                .status(TableStatus.AVAILABLE)
                .build();
        when(tableRepository.findByIdAndCompany(eq(TABLE_ID), any(Company.class)))
                .thenReturn(Optional.of(table));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("Una mesa sin pedidos ni reservaciones se elimina")
    void tableWithoutRecordsIsDeleted() {
        when(orderRepository.countOrdersByTableId(TABLE_ID)).thenReturn(0L);
        when(reservationRepository.countReservationsByTableId(TABLE_ID)).thenReturn(0L);

        service.deleteTable(TABLE_ID, "admin");

        verify(tableRepository).delete(table);
    }

    @Test
    @DisplayName("Una mesa con pedidos no se elimina y solo se puede inactivar")
    void tableWithOrdersIsNotDeleted() {
        when(orderRepository.countOrdersByTableId(TABLE_ID)).thenReturn(3L);
        when(reservationRepository.countReservationsByTableId(TABLE_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.deleteTable(TABLE_ID, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 pedidos")
                .hasMessageContaining("inactivar");

        verify(tableRepository, never()).delete(any(RestaurantTable.class));
    }

    @Test
    @DisplayName("Una mesa con reservaciones no se elimina aunque no tenga pedidos")
    void tableWithReservationsIsNotDeleted() {
        when(orderRepository.countOrdersByTableId(TABLE_ID)).thenReturn(0L);
        when(reservationRepository.countReservationsByTableId(TABLE_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteTable(TABLE_ID, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 reservación")
                .hasMessageContaining("inactivar");

        verify(tableRepository, never()).delete(any(RestaurantTable.class));
    }

    @Test
    @DisplayName("hasRelatedRecords refleja si la mesa ya tiene historial")
    void hasRelatedRecordsReflectsHistory() {
        when(orderRepository.countOrdersByTableId(TABLE_ID)).thenReturn(0L);
        when(reservationRepository.countReservationsByTableId(TABLE_ID)).thenReturn(0L);
        assertThat(service.hasRelatedRecords(TABLE_ID)).isFalse();

        when(orderRepository.countOrdersByTableId(TABLE_ID)).thenReturn(2L);
        assertThat(service.hasRelatedRecords(TABLE_ID)).isTrue();
    }

    @Test
    @DisplayName("Una mesa de otra empresa no se puede eliminar")
    void unknownTableCannotBeDeleted() {
        when(tableRepository.findByIdAndCompany(eq(99L), any(Company.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTable(99L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no encontrada");

        verify(tableRepository, never()).delete(any(RestaurantTable.class));
    }
}
