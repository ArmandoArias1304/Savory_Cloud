package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.SystemUpdate;
import com.aatechsolutions.elgransazon.domain.entity.SystemUpdateRole;
import com.aatechsolutions.elgransazon.domain.repository.SystemUpdateRepository;
import com.aatechsolutions.elgransazon.domain.repository.SystemUpdateViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemUpdateServiceTest {

    @Mock
    private SystemUpdateRepository updateRepository;

    @Mock
    private SystemUpdateViewRepository viewRepository;

    @InjectMocks
    private SystemUpdateServiceImpl service;

    private SystemUpdate update() {
        return SystemUpdate.builder()
                .id(5L)
                .version("1.2.0")
                .title("Solo para meseros")
                .changes("Nuevo tablero")
                .targetRoles(Set.of(SystemUpdateRole.WAITER))
                .published(true)
                .publishedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void pendingIsReturnedForATargetedRoleThatHasNotSeenIt() {
        SystemUpdate update = update();
        when(updateRepository.findTopByPublishedTrueOrderByPublishedAtDescIdDesc())
                .thenReturn(Optional.of(update));
        when(viewRepository.existsByUpdateIdAndEmployeeId(5L, 10L)).thenReturn(false);

        Optional<SystemUpdate> pending = service.findPendingFor(10L, Set.of(SystemUpdateRole.WAITER));

        assertEquals(Optional.of(update), pending);
    }

    @Test
    void pendingIsEmptyWhenTheEmployeeHasAnotherRole() {
        when(updateRepository.findTopByPublishedTrueOrderByPublishedAtDescIdDesc())
                .thenReturn(Optional.of(update()));

        assertTrue(service.findPendingFor(10L, Set.of(SystemUpdateRole.CASHIER)).isEmpty());
    }

    @Test
    void pendingIsEmptyAfterTheEmployeeSawIt() {
        when(updateRepository.findTopByPublishedTrueOrderByPublishedAtDescIdDesc())
                .thenReturn(Optional.of(update()));
        when(viewRepository.existsByUpdateIdAndEmployeeId(5L, 10L)).thenReturn(true);

        assertTrue(service.findPendingFor(10L, Set.of(SystemUpdateRole.WAITER)).isEmpty());
    }

    @Test
    void pendingIsEmptyWithoutAPublishedUpdate() {
        when(updateRepository.findTopByPublishedTrueOrderByPublishedAtDescIdDesc())
                .thenReturn(Optional.empty());

        assertTrue(service.findPendingFor(10L, Set.of(SystemUpdateRole.WAITER)).isEmpty());
    }

    @Test
    void pendingIsEmptyWithoutAnEmployee() {
        assertTrue(service.findPendingFor(null, Set.of(SystemUpdateRole.WAITER)).isEmpty());
    }

    @Test
    void publishedHistoryComesStraightFromTheRepository() {
        SystemUpdate update = update();
        when(updateRepository.findByPublishedTrueOrderByPublishedAtDescIdDesc())
                .thenReturn(List.of(update));

        assertEquals(List.of(update), service.findAllPublished());
    }
}
