package com.universidad.reservaslabs.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Prueba de la puerta de entrada del sitio: la raiz no muestra pagina propia,
 * solo lleva al listado de reservas para que el usuario no aterrice en un 404.
 */
@WebMvcTest(InicioWebController.class)
@DisplayName("Entrada web de la aplicacion")
class InicioWebControllerTest {

    private static final String RUTA_RAIZ = "/";
    private static final String RUTA_RESERVAS = "/reservas";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET / redirige al listado de reservas")
    void raizRedirigeAlListadoDeReservas() throws Exception {
        mockMvc.perform(get(RUTA_RAIZ))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:" + RUTA_RESERVAS))
                .andExpect(redirectedUrl(RUTA_RESERVAS));
    }
}
