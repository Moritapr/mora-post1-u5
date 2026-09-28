package com.universidad.reservaslabs.web;

import com.universidad.reservaslabs.exception.RecursoNoEncontradoException;
import com.universidad.reservaslabs.exception.ReservaConflictException;
import com.universidad.reservaslabs.model.EstadoReserva;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.Reserva;
import com.universidad.reservaslabs.model.TipoLaboratorio;
import com.universidad.reservaslabs.service.LaboratorioService;
import com.universidad.reservaslabs.service.NuevaReservaCommand;
import com.universidad.reservaslabs.service.ReservaService;
import com.universidad.reservaslabs.web.dto.ReglasDeReserva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Pruebas de la capa MVC con Thymeleaf renderizando de verdad: comprueban las
 * vistas devueltas, el HTML que llega al navegador, los atributos que necesitan
 * las plantillas y los mensajes flash de exito y de error de negocio, incluido
 * el destino al que redirige cada fallo segun la accion que lo provoco.
 */
@WebMvcTest(ReservaWebController.class)
@DisplayName("Vistas MVC de reservas")
class ReservaWebControllerTest {

    private static final Long ID_RESERVA = 1L;
    private static final Long ID_RESERVA_CANCELADA = 2L;
    private static final Long ID_INEXISTENTE = 99L;
    private static final Long ID_LABORATORIO = 1L;
    private static final Long ID_LABORATORIO_SECUNDARIO = 3L;
    private static final String NOMBRE_LABORATORIO = "Lab Computo 1";
    private static final String NOMBRE_LABORATORIO_SECUNDARIO = "Lab Fisica";
    private static final String NOMBRE_SOLICITANTE = "Ana Torres";
    private static final String CORREO_SOLICITANTE = "ana.torres@universidad.edu";
    private static final String NOMBRE_SEGUNDO_SOLICITANTE = "Beto Ramos";
    private static final String CORREO_SEGUNDO_SOLICITANTE = "beto.ramos@universidad.edu";
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 3, 10, 9, 0);
    private static final LocalDateTime FIN = LocalDateTime.of(2026, 3, 10, 11, 0);
    private static final String INICIO_PARAMETRO = "2026-03-10T09:00";
    private static final String FIN_PARAMETRO = "2026-03-10T11:00";
    private static final String INICIO_FUERA_DE_JORNADA = "2026-03-10T06:00";
    private static final String FIN_FUERA_DE_JORNADA = "2026-03-10T08:00";
    private static final LocalDateTime INICIO_LISTADO = LocalDateTime.of(2026, 10, 5, 9, 0);
    private static final LocalDateTime FIN_LISTADO = LocalDateTime.of(2026, 10, 5, 11, 0);
    private static final LocalDateTime INICIO_LISTADO_CANCELADA = LocalDateTime.of(2026, 10, 5, 15, 0);
    private static final LocalDateTime FIN_LISTADO_CANCELADA = LocalDateTime.of(2026, 10, 5, 17, 0);
    private static final String INICIO_LISTADO_FORMATEADO = "05/10/2026 09:00";
    private static final String FIN_LISTADO_FORMATEADO = "05/10/2026 11:00";
    private static final String RUTA_RESERVAS = "/reservas";
    private static final String RUTA_NUEVA = RUTA_RESERVAS + "/nueva";
    private static final String VISTA_LISTA = "reservas/lista";
    private static final String VISTA_NUEVA = "reservas/nueva";
    private static final String ATRIBUTO_MENSAJE_EXITO = "mensajeExito";
    private static final String ATRIBUTO_MENSAJE_ERROR = "mensajeError";
    private static final String MENSAJE_FUERA_DE_JORNADA = "El laboratorio solo atiende entre las 07:00 y las 21:00";
    private static final String MENSAJE_YA_CANCELADA = "La reserva " + ID_RESERVA_CANCELADA + " ya fue cancelada";
    private static final String MENSAJE_SOLAPAMIENTO = "El laboratorio " + NOMBRE_LABORATORIO
            + " ya tiene una reserva activa que se cruza con el horario solicitado";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservaService reservaService;

    @MockBean
    private LaboratorioService laboratorioService;

    private static Laboratorio laboratorio() {
        return Laboratorio.builder()
                .id(ID_LABORATORIO)
                .nombre(NOMBRE_LABORATORIO)
                .ubicacion("Bloque A - Piso 2")
                .capacidad(30)
                .tipo(TipoLaboratorio.COMPUTO)
                .build();
    }

    private static Laboratorio laboratorioSecundario() {
        return Laboratorio.builder()
                .id(ID_LABORATORIO_SECUNDARIO)
                .nombre(NOMBRE_LABORATORIO_SECUNDARIO)
                .ubicacion("Bloque B - Piso 1")
                .capacidad(20)
                .tipo(TipoLaboratorio.FISICA)
                .build();
    }

    private static Reserva reservaCon(EstadoReserva estado) {
        return Reserva.builder()
                .id(ID_RESERVA)
                .laboratorio(laboratorio())
                .nombreSolicitante(NOMBRE_SOLICITANTE)
                .correoSolicitante(CORREO_SOLICITANTE)
                .inicio(INICIO)
                .fin(FIN)
                .estado(estado)
                .build();
    }

    /** Listado con una reserva activa y otra cancelada, para contrastar la fila que ofrece cancelar. */
    private static List<Reserva> listadoDeReservas() {
        Reserva activa = Reserva.builder()
                .id(ID_RESERVA)
                .laboratorio(laboratorio())
                .nombreSolicitante(NOMBRE_SOLICITANTE)
                .correoSolicitante(CORREO_SOLICITANTE)
                .inicio(INICIO_LISTADO)
                .fin(FIN_LISTADO)
                .estado(EstadoReserva.ACTIVA)
                .build();
        Reserva cancelada = Reserva.builder()
                .id(ID_RESERVA_CANCELADA)
                .laboratorio(laboratorioSecundario())
                .nombreSolicitante(NOMBRE_SEGUNDO_SOLICITANTE)
                .correoSolicitante(CORREO_SEGUNDO_SOLICITANTE)
                .inicio(INICIO_LISTADO_CANCELADA)
                .fin(FIN_LISTADO_CANCELADA)
                .estado(EstadoReserva.CANCELADA)
                .build();
        return List.of(activa, cancelada);
    }

    private static MockHttpServletRequestBuilder altaEntre(String inicio, String fin) {
        return post(RUTA_RESERVAS)
                .param("laboratorioId", String.valueOf(ID_LABORATORIO))
                .param("nombreSolicitante", NOMBRE_SOLICITANTE)
                .param("correoSolicitante", CORREO_SOLICITANTE)
                .param("inicio", inicio)
                .param("fin", fin);
    }

    private static MockHttpServletRequestBuilder altaValida() {
        return altaEntre(INICIO_PARAMETRO, FIN_PARAMETRO);
    }

    private static MockHttpServletRequestBuilder altaSinDatosObligatorios() {
        return post(RUTA_RESERVAS)
                .param("laboratorioId", "")
                .param("nombreSolicitante", "")
                .param("correoSolicitante", "correo-invalido")
                .param("inicio", "")
                .param("fin", "");
    }

    private static String rutaDeCancelacion(Long id) {
        return RUTA_RESERVAS + "/" + id + "/cancelar";
    }

    @Nested
    @DisplayName("Listado de reservas")
    class Listado {

        @Test
        @DisplayName("GET /reservas muestra el listado con las reservas del servicio")
        void listarMuestraLaVistaDeReservas() throws Exception {
            when(reservaService.findAll()).thenReturn(List.of(reservaCon(EstadoReserva.ACTIVA)));

            mockMvc.perform(get(RUTA_RESERVAS))
                    .andExpect(status().isOk())
                    .andExpect(view().name(VISTA_LISTA))
                    .andExpect(model().attributeExists("reservas"));
        }

        @Test
        @DisplayName("GET /reservas pinta laboratorio, solicitante y fechas ya formateadas")
        void listadoRenderizaLosDatosDeCadaReserva() throws Exception {
            when(reservaService.findAll()).thenReturn(listadoDeReservas());

            mockMvc.perform(get(RUTA_RESERVAS))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(NOMBRE_LABORATORIO)))
                    .andExpect(content().string(containsString(NOMBRE_SOLICITANTE)))
                    .andExpect(content().string(containsString(CORREO_SOLICITANTE)))
                    .andExpect(content().string(containsString(INICIO_LISTADO_FORMATEADO)))
                    .andExpect(content().string(containsString(FIN_LISTADO_FORMATEADO)))
                    .andExpect(content().string(containsString(NOMBRE_LABORATORIO_SECUNDARIO)))
                    .andExpect(content().string(containsString(NOMBRE_SEGUNDO_SOLICITANTE)));
        }

        @Test
        @DisplayName("GET /reservas solo ofrece el boton de cancelar en las reservas activas")
        void listadoSoloOfreceCancelarLasReservasActivas() throws Exception {
            when(reservaService.findAll()).thenReturn(listadoDeReservas());

            mockMvc.perform(get(RUTA_RESERVAS))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(rutaDeCancelacion(ID_RESERVA))))
                    .andExpect(content().string(not(containsString(rutaDeCancelacion(ID_RESERVA_CANCELADA)))))
                    .andExpect(content().string(containsString("Sin acciones")));
        }
    }

    @Nested
    @DisplayName("Formulario de alta")
    class Formulario {

        @Test
        @DisplayName("GET /reservas/nueva muestra el formulario con los laboratorios")
        void formularioDeNuevaReservaTraeLaboratorios() throws Exception {
            when(laboratorioService.findAll()).thenReturn(List.of(laboratorio()));

            mockMvc.perform(get(RUTA_NUEVA))
                    .andExpect(status().isOk())
                    .andExpect(view().name(VISTA_NUEVA))
                    .andExpect(model().attributeExists("reservaFormulario", "laboratorios"))
                    .andExpect(content().string(containsString(NOMBRE_LABORATORIO)));
        }

        @Test
        @DisplayName("POST /reservas invalido vuelve al formulario con errores de campo")
        void formularioInvalidoVuelveAMostrarLaVista() throws Exception {
            when(laboratorioService.findAll()).thenReturn(List.of(laboratorio()));

            mockMvc.perform(altaSinDatosObligatorios())
                    .andExpect(status().isOk())
                    .andExpect(view().name(VISTA_NUEVA))
                    .andExpect(model().attributeHasFieldErrors("reservaFormulario",
                            "nombreSolicitante", "correoSolicitante"));
        }

        @Test
        @DisplayName("POST /reservas invalido recarga los laboratorios y las reglas de la plantilla")
        void formularioInvalidoRecargaLaboratoriosYReglas() throws Exception {
            when(laboratorioService.findAll()).thenReturn(List.of(laboratorio()));

            mockMvc.perform(altaSinDatosObligatorios())
                    .andExpect(status().isOk())
                    .andExpect(model().attributeExists("laboratorios"))
                    .andExpect(model().attribute("reglasDeReserva", ReglasDeReserva.vigentes()))
                    .andExpect(content().string(containsString(NOMBRE_LABORATORIO)))
                    .andExpect(content().string(containsString("07:00 a 21:00")));
        }
    }

    @Nested
    @DisplayName("Alta de reservas")
    class Alta {

        @Test
        @DisplayName("POST /reservas valido redirige al listado con mensaje de exito")
        void crearReservaValidaRedirigeConMensajeDeExito() throws Exception {
            when(reservaService.crear(any(NuevaReservaCommand.class))).thenReturn(reservaCon(EstadoReserva.ACTIVA));

            mockMvc.perform(altaValida())
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(RUTA_RESERVAS))
                    .andExpect(flash().attribute(ATRIBUTO_MENSAJE_EXITO,
                            "Reserva creada para " + NOMBRE_SOLICITANTE));
        }

        @Test
        @DisplayName("POST /reservas con solapamiento vuelve al formulario con mensaje de error")
        void errorDeNegocioRedirigeAlFormularioConMensaje() throws Exception {
            when(reservaService.crear(any(NuevaReservaCommand.class)))
                    .thenThrow(new ReservaConflictException(MENSAJE_SOLAPAMIENTO));

            mockMvc.perform(altaValida())
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(RUTA_NUEVA))
                    .andExpect(flash().attribute(ATRIBUTO_MENSAJE_ERROR, MENSAJE_SOLAPAMIENTO));
        }

        @Test
        @DisplayName("POST /reservas fuera de jornada vuelve al formulario con mensaje de error")
        void horarioFueraDeJornadaRedirigeAlFormularioConMensaje() throws Exception {
            when(reservaService.crear(any(NuevaReservaCommand.class)))
                    .thenThrow(new IllegalArgumentException(MENSAJE_FUERA_DE_JORNADA));

            mockMvc.perform(altaEntre(INICIO_FUERA_DE_JORNADA, FIN_FUERA_DE_JORNADA))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(RUTA_NUEVA))
                    .andExpect(flash().attribute(ATRIBUTO_MENSAJE_ERROR, MENSAJE_FUERA_DE_JORNADA));
        }
    }

    @Nested
    @DisplayName("Cancelacion de reservas")
    class Cancelacion {

        @Test
        @DisplayName("POST /reservas/{id}/cancelar redirige al listado")
        void cancelarRedirigeAlListado() throws Exception {
            when(reservaService.cancelar(ID_RESERVA)).thenReturn(reservaCon(EstadoReserva.CANCELADA));

            mockMvc.perform(post(RUTA_RESERVAS + "/{id}/cancelar", ID_RESERVA))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(RUTA_RESERVAS))
                    .andExpect(flash().attribute(ATRIBUTO_MENSAJE_EXITO, "Reserva " + ID_RESERVA + " cancelada"));

            verify(reservaService).cancelar(ID_RESERVA);
        }

        @Test
        @DisplayName("Cancelar una reserva ya cancelada vuelve al listado, no al formulario")
        void cancelarReservaYaCanceladaVuelveAlListado() throws Exception {
            when(reservaService.cancelar(ID_RESERVA_CANCELADA))
                    .thenThrow(new ReservaConflictException(MENSAJE_YA_CANCELADA));

            mockMvc.perform(post(RUTA_RESERVAS + "/{id}/cancelar", ID_RESERVA_CANCELADA))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(RUTA_RESERVAS))
                    .andExpect(flash().attribute(ATRIBUTO_MENSAJE_ERROR, MENSAJE_YA_CANCELADA));
        }

        @Test
        @DisplayName("Cancelar una reserva inexistente vuelve al listado con mensaje de error")
        void cancelarReservaInexistenteVuelveAlListado() throws Exception {
            when(reservaService.cancelar(ID_INEXISTENTE))
                    .thenThrow(RecursoNoEncontradoException.de("reserva", ID_INEXISTENTE));

            mockMvc.perform(post(RUTA_RESERVAS + "/{id}/cancelar", ID_INEXISTENTE))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(RUTA_RESERVAS))
                    .andExpect(flash().attribute(ATRIBUTO_MENSAJE_ERROR,
                            "No existe reserva con id " + ID_INEXISTENTE));
        }
    }
}
