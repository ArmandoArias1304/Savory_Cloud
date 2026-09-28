package com.aatechsolutions.elgransazon;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The printed-menu dialog previews the generated PDF inside an iframe of the same
 * application, so the app must allow same-origin framing: Spring Security's default
 * (DENY) blocked it and the preview showed up empty even though the PDF was fine.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityFrameOptionsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void allowsSameOriginFramingOfItsOwnPages() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    void registersTheCartaAndCategoryOrderEndpoints() {
        Set<String> paths = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPathPatternsCondition() == null
                        ? Stream.empty()
                        : info.getPathPatternsCondition().getPatternValues().stream())
                .collect(Collectors.toSet());

        assertTrue(paths.contains("/admin/menu-items/menu-pdf"),
                "falta la ruta que genera la carta");
        assertTrue(paths.contains("/admin/menu-items/menu-pdf/style"),
                "falta la ruta que guarda el estilo de la carta");
        assertTrue(paths.contains("/admin/categories/reorder"),
                "falta la ruta que guarda el orden de las categorías");
    }
}
