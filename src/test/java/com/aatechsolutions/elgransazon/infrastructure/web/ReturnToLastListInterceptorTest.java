package com.aatechsolutions.elgransazon.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas del interceptor que devuelve al usuario a la pantalla donde estaba.
 *
 * <p>Lo importante aquí son dos cosas: que un guardado vuelva a la lista con su página y sus
 * filtros, y que <strong>nunca</strong> se pueda usar para desviar la navegación a otra pantalla o
 * a un sitio externo. Las dos se fijan en estas pruebas.</p>
 */
class ReturnToLastListInterceptorTest {

    private static final String HOST = "quinta.localhost";
    private static final String INGREDIENTS = "/admin/ingredients";

    private ReturnToLastListInterceptor interceptor;
    private HttpServletResponse response;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        interceptor = new ReturnToLastListInterceptor();
        response = mock(HttpServletResponse.class);
        session = new MockHttpSession();
    }

    // ========== Volver a la misma pantalla ==========

    @Test
    @DisplayName("una acción lanzada desde la lista vuelve a esa lista con su página y sus filtros")
    void anActionLaunchedFromTheListReturnsToThatList() {
        String view = redirectFrom(referer(INGREDIENTS + "?search=tomate&page=3"), null, INGREDIENTS);

        assertThat(view).isEqualTo("redirect:" + INGREDIENTS + "?search=tomate&page=3");
    }

    @Test
    @DisplayName("al guardar un formulario se vuelve a la lista desde la que se abrió")
    void savingAFormReturnsToTheListTheFormWasOpenedFrom() {
        session.setAttribute(ReturnToLastListInterceptor.CAME_FROM_ATTR, INGREDIENTS + "?search=tomate");

        String view = redirectFrom(referer(INGREDIENTS + "/24/edit"), null, INGREDIENTS);

        assertThat(view).isEqualTo("redirect:" + INGREDIENTS + "?search=tomate");
    }

    @Test
    @DisplayName("sin referer se usa la última lista visitada, si es la misma pantalla")
    void withoutARefererTheLastVisitedListIsUsed() {
        session.setAttribute(ReturnToLastListInterceptor.LAST_LIST_ATTR, INGREDIENTS + "?active=true");

        String view = redirectFrom(null, null, INGREDIENTS);

        assertThat(view).isEqualTo("redirect:" + INGREDIENTS + "?active=true");
    }

    @Test
    @DisplayName("lo que pida el formulario en returnTo tiene prioridad")
    void theExplicitReturnToWins() {
        session.setAttribute(ReturnToLastListInterceptor.LAST_LIST_ATTR, INGREDIENTS + "?page=9");

        String view = redirectFrom(null, INGREDIENTS + "?sortBy=stock-asc", INGREDIENTS);

        assertThat(view).isEqualTo("redirect:" + INGREDIENTS + "?sortBy=stock-asc");
    }

    // ========== Nunca desviar a otra pantalla ni a otro sitio ==========

    @Test
    @DisplayName("no se reescribe si la redirección va a otra pantalla")
    void aRedirectToAnotherScreenIsLeftAlone() {
        session.setAttribute(ReturnToLastListInterceptor.LAST_LIST_ATTR, "/admin/menu-items?page=2");
        session.setAttribute(ReturnToLastListInterceptor.CAME_FROM_ATTR, "/admin/menu-items?page=2");

        String view = redirectFrom(referer("/admin/menu-items?page=2"), "/admin/menu-items?page=2", INGREDIENTS);

        assertThat(view).isEqualTo("redirect:" + INGREDIENTS);
    }

    @Test
    @DisplayName("se respeta la URL concreta que pidió el controlador")
    void aConcreteRedirectUrlIsRespected() {
        session.setAttribute(ReturnToLastListInterceptor.LAST_LIST_ATTR, INGREDIENTS + "?page=3");

        String view = redirectFrom(null, null, INGREDIENTS + "?search=otro");

        assertThat(view).isEqualTo("redirect:" + INGREDIENTS + "?search=otro");
    }

    @Test
    @DisplayName("una URL externa no puede desviar la navegación fuera del sistema")
    void anExternalUrlCannotTakeTheUserOffSite() {
        String view = redirectFrom(null, "https://sitio-malicioso.com" + INGREDIENTS, INGREDIENTS);

        assertThat(view).doesNotContain("sitio-malicioso");
        assertThat(view).isEqualTo("redirect:" + INGREDIENTS);
    }

    @Test
    @DisplayName("una ruta protocolo-relativa se ignora")
    void aProtocolRelativePathIsIgnored() {
        String view = redirectFrom(null, "//sitio-malicioso.com" + INGREDIENTS, INGREDIENTS);

        assertThat(view).doesNotContain("sitio-malicioso");
        assertThat(view).isEqualTo("redirect:" + INGREDIENTS);
    }

    @Test
    @DisplayName("un valor con caracteres de control no llega a la redirección")
    void controlCharactersAreRejected() {
        String view = redirectFrom(null, INGREDIENTS + "?x=1\r\nSet-Cookie: a=b", INGREDIENTS);

        assertThat(view).isEqualTo("redirect:" + INGREDIENTS);
    }

    @Test
    @DisplayName("una ruta con backslashes se ignora")
    void backslashesAreRejected() {
        String view = redirectFrom(null, INGREDIENTS + "\\..\\otra", INGREDIENTS);

        assertThat(view).isEqualTo("redirect:" + INGREDIENTS);
    }

    // ========== Qué se recuerda ==========

    @Test
    @DisplayName("al abrir una lista con filtros se recuerda tal cual llegó")
    void visitingAListWithFiltersRemembersIt() {
        HttpServletRequest request = get(INGREDIENTS, "search=tomate");

        interceptor.postHandle(request, response, new Object(), new ModelAndView("admin/ingredients/list"));

        assertThat(session.getAttribute(ReturnToLastListInterceptor.LAST_LIST_ATTR))
                .isEqualTo(INGREDIENTS + "?search=tomate");
    }

    @Test
    @DisplayName("una lista sin filtros no gasta memoria de sesión")
    void visitingAPlainListRemembersNothing() {
        HttpServletRequest request = get(INGREDIENTS, null);

        interceptor.postHandle(request, response, new Object(), new ModelAndView("admin/ingredients/list"));

        assertThat(session.getAttribute(ReturnToLastListInterceptor.LAST_LIST_ATTR)).isNull();
        verify(request, never()).getSession();
    }

    @Test
    @DisplayName("al abrir un formulario se recuerda la lista desde la que se llegó")
    void openingAFormRemembersWhereItCameFrom() {
        HttpServletRequest request = get(INGREDIENTS + "/24/edit", null, referer("/admin/menu-items?page=2"));

        interceptor.postHandle(request, response, new Object(), new ModelAndView("admin/ingredients/form"));

        assertThat(session.getAttribute(ReturnToLastListInterceptor.CAME_FROM_ATTR))
                .isEqualTo("/admin/menu-items?page=2");
    }

    @Test
    @DisplayName("un referer de otro sitio no se recuerda")
    void aRefererFromAnotherSiteIsNotRemembered() {
        HttpServletRequest request = get(INGREDIENTS + "/24/edit", null,
                "https://otro-sitio.com/admin/menu-items?page=2");

        interceptor.postHandle(request, response, new Object(), new ModelAndView("admin/ingredients/form"));

        assertThat(session.getAttribute(ReturnToLastListInterceptor.CAME_FROM_ATTR)).isNull();
    }

    @Test
    @DisplayName("las respuestas de APIs no recuerdan nada")
    void apiResponsesAreIgnored() {
        HttpServletRequest request = get(INGREDIENTS, "search=tomate");

        interceptor.postHandle(request, response, new Object(), null);

        assertThat(session.getAttribute(ReturnToLastListInterceptor.LAST_LIST_ATTR)).isNull();
    }

    // ========== Utilidades ==========

    private String redirectFrom(String referer, String returnTo, String target) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getServerName()).thenReturn(HOST);
        when(request.getRequestURI()).thenReturn(INGREDIENTS + "/24/activate");
        when(request.getHeader("Referer")).thenReturn(referer);
        when(request.getParameter("returnTo")).thenReturn(returnTo);
        when(request.getSession()).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);

        ModelAndView modelAndView = new ModelAndView("redirect:" + target);
        interceptor.postHandle(request, response, new Object(), modelAndView);
        return modelAndView.getViewName();
    }

    private HttpServletRequest get(String uri, String query) {
        return get(uri, query, null);
    }

    private HttpServletRequest get(String uri, String query, String referer) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getServerName()).thenReturn(HOST);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getQueryString()).thenReturn(query);
        when(request.getHeader("Referer")).thenReturn(referer);
        when(request.getSession()).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);
        return request;
    }

    private String referer(String path) {
        return "http://" + HOST + path;
    }
}
