package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.ComandaEscPosService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.PrintClaimService;
import com.aatechsolutions.elgransazon.application.service.PrinterService;
import com.aatechsolutions.elgransazon.application.service.TicketEscPosService;
import com.aatechsolutions.elgransazon.application.service.WebSocketNotificationService;
import com.aatechsolutions.elgransazon.domain.entity.Printer;
import com.aatechsolutions.elgransazon.domain.entity.PrinterType;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for POST /admin/printers/save: the printer form may omit the
 * optional ipAddress field entirely, so the controller must never NPE on it.
 */
class PrinterControllerSaveTest {

    private PrinterService printerService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        printerService = mock(PrinterService.class);
        PrinterController controller = new PrinterController(
                printerService,
                mock(ComandaEscPosService.class),
                mock(TicketEscPosService.class),
                mock(OrderService.class),
                new PrintClaimService(),
                mock(PaymentRepository.class),
                mock(WebSocketNotificationService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void savesNewPrinterWhenIpAddressParameterIsMissing() throws Exception {
        mockMvc.perform(post("/admin/printers/save")
                        .param("name", "POS-80 Cocina")
                        .param("printerType", "KITCHEN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/printers"));

        assertSaved("POS-80 Cocina", PrinterType.KITCHEN, null);
    }

    @Test
    void savesNewPrinterWhenIpAddressIsEmptyString() throws Exception {
        mockMvc.perform(post("/admin/printers/save")
                        .param("name", "POS-80 Cocina")
                        .param("printerType", "KITCHEN")
                        .param("ipAddress", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/printers"));

        assertSaved("POS-80 Cocina", PrinterType.KITCHEN, null);
    }

    @Test
    void trimsIncomingIpAddress() throws Exception {
        mockMvc.perform(post("/admin/printers/save")
                        .param("id", "7")
                        .param("name", "  POS-80 Cocina  ")
                        .param("printerType", "KITCHEN")
                        .param("ipAddress", "  192.168.1.100  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/printers"));

        assertSaved("POS-80 Cocina", PrinterType.KITCHEN, "192.168.1.100");
    }

    private void assertSaved(String expectedName, PrinterType expectedType, String expectedIp) {
        ArgumentCaptor<Printer> captor = ArgumentCaptor.forClass(Printer.class);
        verify(printerService).save(captor.capture());
        Printer saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo(expectedName);
        assertThat(saved.getPrinterType()).isEqualTo(expectedType);
        assertThat(saved.getIpAddress()).isEqualTo(expectedIp);
    }
}
