package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CashRegisterService;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.ReportPdfService;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterSession;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterStatus;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.presentation.dto.CashRegisterSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.AbstractView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caja lives in the area of the role that opens it: /cashier/cash-register for a cashier,
 * /admin/cash-register for an admin and /manager/cash-register for a manager. Opening it from
 * another area (an old bookmark, the shared admin/manager shortcut of the sidebar) is
 * redirected to the user's own area, so the URL never says a role the user is not.
 */
class CashRegisterRoleAreaTest {

    private static final long COMPANY_ID = 3L;

    private MockMvc mockMvc;
    private CashRegisterService cashRegisterService;
    private EmployeeService employeeService;
    private DateTimeService dateTimeService;

    @BeforeEach
    void setUp() {
        cashRegisterService = mock(CashRegisterService.class);
        employeeService = mock(EmployeeService.class);
        dateTimeService = mock(DateTimeService.class);
        CashRegisterSession openSession = session();

        CashRegisterController controller = new CashRegisterController(
                cashRegisterService, employeeService, dateTimeService, mock(ReportPdfService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                // The assertions read the view name, so HTML is never rendered.
                .setViewResolvers((viewName, locale) -> noOpView(viewName))
                .build();

        Company company = new Company();
        company.setIdCompany(COMPANY_ID);
        CompanyContext.setCurrentCompany(company);

        when(employeeService.findByUsername(anyString())).thenReturn(Optional.of(employee()));
        when(dateTimeService.todayLocal()).thenReturn(LocalDate.of(2026, 9, 16));
        when(cashRegisterService.findOpenSession(any(), any())).thenReturn(openSession);
        when(cashRegisterService.listSessionsForDate(any(), any(), any())).thenReturn(List.of());
        when(cashRegisterService.buildSummary(any())).thenReturn(CashRegisterSummary.builder().build());
        when(cashRegisterService.getMovements(any())).thenReturn(List.of());
        when(cashRegisterService.openSession(any(), any(), any(), any(), anyString()))
                .thenReturn(openSession);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    /** The request principal Spring injects as the handler's {@link Authentication}. */
    private Authentication auth(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(username, "n/a",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private Employee employee() {
        return Employee.builder()
                .idEmpleado(1L)
                .nombre("Ana")
                .apellido("Lopez")
                .username("ana")
                .build();
    }

    private CashRegisterSession session() {
        return CashRegisterSession.builder()
                .id(1L)
                .cashier(employee())
                .status(CashRegisterStatus.OPEN)
                .openedAt(LocalDateTime.now())
                .initialAmount(new BigDecimal("200.00"))
                .build();
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, Authentication authentication)
            throws Exception {
        return mockMvc.perform(request.principal(authentication)).andReturn();
    }

    private String viewNameOf(String uri, Authentication authentication) throws Exception {
        return perform(get(uri), authentication).getModelAndView().getViewName();
    }

    // ---------- Each role opens Caja in its own area ----------

    @Test
    @DisplayName("El cajero abre Caja en /cashier/cash-register")
    void cashierOpensHisOwnArea() throws Exception {
        Authentication cashier = auth("ana", "ROLE_CASHIER");

        MvcResult result = perform(get("/cashier/cash-register"), cashier);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getModelAndView().getViewName()).isEqualTo("cashier/cash-register/view");
        assertThat(result.getModelAndView().getModel().get("rolePrefix")).isEqualTo("cashier");
    }

    @Test
    @DisplayName("El admin abre Caja en /admin/cash-register")
    void adminOpensHisOwnArea() throws Exception {
        Authentication admin = auth("ana", "ROLE_ADMIN");

        MvcResult result = perform(get("/admin/cash-register"), admin);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getModelAndView().getModel().get("rolePrefix")).isEqualTo("admin");
    }

    @Test
    @DisplayName("El gerente abre Caja en /manager/cash-register")
    void managerOpensHisOwnArea() throws Exception {
        Authentication manager = auth("gerente", "ROLE_MANAGER");

        MvcResult result = perform(get("/manager/cash-register"), manager);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getModelAndView().getModel().get("rolePrefix")).isEqualTo("manager");
    }

    @Test
    @DisplayName("El historial también vive en el área del rol")
    void historyLivesInTheRoleArea() throws Exception {
        MvcResult result = perform(get("/manager/cash-register/history"), auth("gerente", "ROLE_MANAGER"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getModelAndView().getViewName()).isEqualTo("cashier/cash-register/history");
        assertThat(result.getModelAndView().getModel().get("rolePrefix")).isEqualTo("manager");
    }

    // ---------- Opening another area self-corrects ----------

    @Test
    @DisplayName("Un admin que abre /cashier/cash-register va a su propia área")
    void adminOnTheCashierUrlGoesToHisOwnArea() throws Exception {
        assertThat(viewNameOf("/cashier/cash-register", auth("ana", "ROLE_ADMIN")))
                .isEqualTo("redirect:/admin/cash-register");
    }

    @Test
    @DisplayName("Un gerente que abre /admin/cash-register va a /manager/cash-register")
    void managerOnTheAdminUrlGoesToHisOwnArea() throws Exception {
        assertThat(viewNameOf("/admin/cash-register", auth("gerente", "ROLE_MANAGER")))
                .isEqualTo("redirect:/manager/cash-register");
    }

    @Test
    @DisplayName("Un cajero que abre otra área va a /cashier/cash-register")
    void cashierOnAnotherAreaGoesToHisOwnArea() throws Exception {
        Authentication cashier = auth("ana", "ROLE_CASHIER");

        assertThat(viewNameOf("/admin/cash-register", cashier))
                .isEqualTo("redirect:/cashier/cash-register");
        assertThat(viewNameOf("/manager/cash-register/history", cashier))
                .isEqualTo("redirect:/cashier/cash-register");
    }

    @Test
    @DisplayName("Un admin con también rol de cajero nunca queda en la URL del cajero")
    void adminWhoIsAlsoCashierGoesToTheAdminArea() throws Exception {
        assertThat(viewNameOf("/cashier/cash-register", auth("ana", "ROLE_CASHIER", "ROLE_ADMIN")))
                .isEqualTo("redirect:/admin/cash-register");
    }

    @Test
    @DisplayName("Un POST desde otra área vuelve a la caja del rol que lo hizo")
    void postFromAnotherAreaReturnsToTheUserOwnArea() throws Exception {
        MvcResult result = perform(
                post("/admin/cash-register/open").param("initialAmount", "200.00"),
                auth("gerente", "ROLE_MANAGER"));

        assertThat(result.getModelAndView().getViewName())
                .isEqualTo("redirect:/manager/cash-register");
    }

    @Test
    @DisplayName("Caja solo existe en las áreas cashier, admin y manager")
    void unknownAreaDoesNotExist() throws Exception {
        mockMvc.perform(get("/waiter/cash-register").principal(auth("mesero", "ROLE_WAITER")))
                .andExpect(status().isNotFound());
    }

    /** Records nothing: the assertions read the view name, this only avoids rendering HTML. */
    private static AbstractView noOpView(String beanName) {
        AbstractView noOp = new AbstractView() {
            @Override
            protected void renderMergedOutputModel(Map<String, Object> model,
                    HttpServletRequest request, HttpServletResponse response) {
                // no rendering needed
            }
        };
        noOp.setBeanName(beanName);
        return noOp;
    }
}
