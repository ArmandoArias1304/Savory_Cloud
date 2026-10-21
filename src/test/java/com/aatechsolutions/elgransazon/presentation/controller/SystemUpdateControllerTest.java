package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.SystemUpdateService;
import com.aatechsolutions.elgransazon.domain.entity.SystemUpdate;
import com.aatechsolutions.elgransazon.domain.entity.SystemUpdateRole;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Version history for employees: the latest version first and every older
 * published version available to open.
 */
@ExtendWith(MockitoExtension.class)
class SystemUpdateControllerTest {

    @Mock
    private SystemUpdateService systemUpdateService;

    @Mock
    private EmployeeRepository employeeRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SystemUpdateController(systemUpdateService, employeeRepository))
                .build();
    }

    @Test
    @DisplayName("el historial separa la versión actual de las anteriores")
    void historySplitsTheLatestFromTheOlderVersions() throws Exception {
        SystemUpdate latest = update(7L, "1.4.2", "Mejoras para meseros");
        SystemUpdate older = update(6L, "1.4.1", "Ajustes de caja");
        SystemUpdate oldest = update(5L, "1.4.0", "Primera versión pública");
        List<SystemUpdate> published = List.of(latest, older, oldest);
        when(systemUpdateService.findAllPublished()).thenReturn(published);

        mockMvc.perform(get("/novedades"))
                .andExpect(status().isOk())
                .andExpect(view().name("updates/history"))
                .andExpect(model().attribute("updates", published))
                .andExpect(model().attribute("latest", latest))
                .andExpect(model().attribute("previousUpdates", List.of(older, oldest)));

        verify(systemUpdateService).findAllPublished();
    }

    @Test
    @DisplayName("con una sola versión no hay versiones anteriores")
    void singleVersionHasNoPreviousUpdates() throws Exception {
        SystemUpdate latest = update(7L, "1.4.2", "Mejoras para meseros");
        when(systemUpdateService.findAllPublished()).thenReturn(List.of(latest));

        mockMvc.perform(get("/novedades"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("latest", latest))
                .andExpect(model().attribute("previousUpdates", List.of()));
    }

    @Test
    @DisplayName("sin versiones publicadas el historial queda vacío")
    void withoutPublishedUpdatesTheHistoryIsEmpty() throws Exception {
        when(systemUpdateService.findAllPublished()).thenReturn(List.of());

        mockMvc.perform(get("/novedades"))
                .andExpect(status().isOk())
                .andExpect(view().name("updates/history"))
                .andExpect(model().attribute("latest", (Object) null))
                .andExpect(model().attribute("previousUpdates", List.of()));
    }

    private SystemUpdate update(Long id, String version, String title) {
        return SystemUpdate.builder()
                .id(id)
                .version(version)
                .title(title)
                .changes("Nuevo filtro de pedidos\nCorrección del total")
                .targetRoles(Set.of(SystemUpdateRole.WAITER))
                .published(true)
                .publishedAt(LocalDateTime.of(2026, 10, 19, 10, 30))
                .build();
    }
}
