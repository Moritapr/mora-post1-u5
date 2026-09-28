package com.universidad.reservaslabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de humo de la aplicacion completa: levanta el contexto de Spring con
 * todas las capas (modelo, repositorios, servicios, REST y MVC) y verifica que
 * el cableado por constructor no deja ninguna dependencia sin resolver.
 */
@SpringBootTest
@DisplayName("Arranque de la aplicacion")
class ReservasLabsApplicationTests {

    @Autowired
    private ApplicationContext contexto;

    @Test
    @DisplayName("El contexto de Spring carga sin errores")
    void elContextoDeSpringCarga() {
        assertThat(contexto).isNotNull();
    }
}
