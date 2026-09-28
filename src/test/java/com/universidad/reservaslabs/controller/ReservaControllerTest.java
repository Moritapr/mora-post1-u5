package com.universidad.reservaslabs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universidad.reservaslabs.controller.dto.CrearReservaRequest;
import com.universidad.reservaslabs.exception.RecursoNoEncontradoException;
import com.universidad.reservaslabs.exception.ReservaConflictException;
import com.universidad.reservaslabs.model.EstadoReserva;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.Reserva;
import com.universidad.reservaslabs.model.TipoLaboratorio;
import com.universidad.reservaslabs.service.NuevaReservaCommand;
import com.universidad.reservaslabs.service.ReservaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la frontera REST con el servicio simulado: verifican codigos HTTP,
 * el JSON de respuesta y la traduccion de excepciones de negocio que hace el
 * manejador global.
 */
@WebMvcTest(ReservaController.class)
@DisplayName("API REST de reservas")
class ReservaControllerTest {

    private static final Long ID_RESERVA = 1L;
    private static final Long ID_LABORATORIO = 1L;
    private static final Long ID_LABORATORIO_FILTRO = 7L;
    private static final Long ID_INEXISTENTE = 99L;
    private static final String NOMBRE_LABORATORIO = "Lab Computo 1";
    private static final String NOMBRE_SOLICITANTE = "Ana Torres";
    private static final String CORREO_SOLICITANTE = "ana.torres@universidad.edu";
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 3, 10, 9, 0);
    private static final LocalDateTime FIN = LocalDateTime.of(2026, 3, 10, 11, 0);
    private static final String RUTA_RESERVAS = "/api/reservas";
    private static final String MENSAJE_FUERA_DE_JORNADA =
            "El laboratorio solo atiende entre las 07:00 y las 21:00";
    private static final String MENSAJE_JSON_INVALIDO = "El cuerpo de la peticion no es un JSON valido";
    private static final String MENSAJE_VALIDACION = "La solicitud contiene datos invalidos";
    private static final String JSON_ROTO = "{\"laboratorioId\": 1, \"nombreSolicitante\": ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReservaService reservaService;

    private static Laboratorio laboratorioCon(Long id) {
        return Laboratorio.builder()
                .id(id)
                .nombre(NOMBRE_LABORATORIO)
                .ubicacion("Bloque A - Piso 2")
                .capacidad(30)
                .tipo(TipoLaboratorio.COMPUTO)
                .build();
    }

    private static Reserva reservaDe(Laboratorio laboratorio, EstadoReserva estado) {
        return Reserva.builder()
                .id(ID_RESERVA)
                .laboratorio(laboratorio)
                .nombreSolicitante(NOMBRE_SOLICITANTE)
                .correoSolicitante(CORREO_SOLICITANTE)
                .inicio(INICIO)
                .fin(FIN)
                .estado(estado)
                .build();
    }

    private static Reserva reservaCon(EstadoReserva estado) {
        return reservaDe(laboratorioCon(ID_LABORATORIO), estado);
    }

    private static CrearReservaRequest solicitudValida() {
        return new CrearReservaRequest(ID_LABORATORIO, NOMBRE_SOLICITANTE, CORREO_SOLICITANTE, INICIO, FIN);
    }

    private String comoJson(CrearReservaRequest solicitud) throws Exception {
        return objectMapper.writeValueAsString(solicitud);
    }

    @Nested
    @DisplayName("Consultas de reservas")
    class Consultas {

        @Test
        @DisplayName("GET /api/reservas devuelve 200 y la lista de reservas")
        void listarDevuelveLasReservasEnJson() throws Exception {
            when(reservaService.findAll()).thenReturn(List.of(reservaCon(EstadoReserva.ACTIVA)));

            mockMvc.perform(get(RUTA_RESERVAS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(ID_RESERVA.intValue()))
                    .andExpect(jsonPath("$[0].laboratorioNombre").value(NOMBRE_LABORATORIO))
                    .andExpect(jsonPath("$[0].estado").value(EstadoReserva.ACTIVA.name()));
        }

        @Test
        @DisplayName("GET /api/reservas?laboratorioId=7 filtra por laboratorio y no pide el listado completo")
        void listarConFiltroDelegaEnLaBusquedaPorLaboratorio() throws Exception {
            when(reservaService.findByLaboratorio(ID_LABORATORIO_FILTRO))
                    .thenReturn(List.of(reservaDe(laboratorioCon(ID_LABORATORIO_FILTRO), EstadoReserva.ACTIVA)));

            mockMvc.perform(get(RUTA_RESERVAS).param("laboratorioId", String.valueOf(ID_LABORATORIO_FILTRO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].laboratorioId").value(ID_LABORATORIO_FILTRO.intValue()));

            verify(reservaService).findByLaboratorio(ID_LABORATORIO_FILTRO);
            verify(reservaService, never()).findAll();
        }

        @Test
        @DisplayName("GET /api/reservas/{id} devuelve 200 con el detalle de la reserva")
        void obtenerReservaDevuelveSuDetalle() throws Exception {
            when(reservaService.findById(ID_RESERVA)).thenReturn(reservaCon(EstadoReserva.ACTIVA));

            mockMvc.perform(get(RUTA_RESERVAS + "/{id}", ID_RESERVA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ID_RESERVA.intValue()))
                    .andExpect(jsonPath("$.correoSolicitante").value(CORREO_SOLICITANTE))
                    .andExpect(jsonPath("$.inicio").value(startsWith("2026-03-10T09:00")));
        }

        @Test
        @DisplayName("GET /api/reservas/{id} inexistente devuelve 404")
        void obtenerReservaInexistenteDevuelve404() throws Exception {
            when(reservaService.findById(ID_INEXISTENTE))
                    .thenThrow(RecursoNoEncontradoException.de("reserva", ID_INEXISTENTE));

            mockMvc.perform(get(RUTA_RESERVAS + "/{id}", ID_INEXISTENTE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.estado").value(HttpStatus.NOT_FOUND.value()));
        }
    }

    @Nested
    @DisplayName("Alta de reservas")
    class Alta {

        @Test
        @DisplayName("POST /api/reservas valido devuelve 201 con cabecera Location")
        void crearReservaValidaDevuelve201ConLocation() throws Exception {
            when(reservaService.crear(any(NuevaReservaCommand.class))).thenReturn(reservaCon(EstadoReserva.ACTIVA));

            mockMvc.perform(post(RUTA_RESERVAS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(comoJson(solicitudValida())))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, endsWith(RUTA_RESERVAS + "/" + ID_RESERVA)));
        }

        @Test
        @DisplayName("POST /api/reservas con datos invalidos devuelve 400 con el detalle por campo")
        void crearReservaInvalidaDevuelve400ConDetalles() throws Exception {
            CrearReservaRequest solicitud = new CrearReservaRequest(null, "", "correo-invalido", null, null);

            mockMvc.perform(post(RUTA_RESERVAS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(comoJson(solicitud)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.estado").value(HttpStatus.BAD_REQUEST.value()))
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                    .andExpect(jsonPath("$.detalles.correoSolicitante").exists())
                    .andExpect(jsonPath("$.detalles.laboratorioId").exists())
                    .andExpect(jsonPath("$.detalles.nombreSolicitante").exists())
                    .andExpect(jsonPath("$.detalles.inicio").exists())
                    .andExpect(jsonPath("$.detalles.fin").exists());

            verify(reservaService, never()).crear(any(NuevaReservaCommand.class));
        }

        @Test
        @DisplayName("POST /api/reservas fuera de la jornada devuelve 400 con el mensaje de la regla")
        void crearReservaFueraDeJornadaDevuelve400() throws Exception {
            when(reservaService.crear(any(NuevaReservaCommand.class)))
                    .thenThrow(new IllegalArgumentException(MENSAJE_FUERA_DE_JORNADA));

            mockMvc.perform(post(RUTA_RESERVAS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(comoJson(solicitudValida())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.estado").value(HttpStatus.BAD_REQUEST.value()))
                    .andExpect(jsonPath("$.error").value(HttpStatus.BAD_REQUEST.getReasonPhrase()))
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_FUERA_DE_JORNADA))
                    .andExpect(jsonPath("$.ruta").value(RUTA_RESERVAS));
        }

        @Test
        @DisplayName("POST /api/reservas con un cuerpo JSON roto devuelve 400 sin llegar al servicio")
        void cuerpoJsonRotoDevuelve400() throws Exception {
            mockMvc.perform(post(RUTA_RESERVAS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON_ROTO))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.estado").value(HttpStatus.BAD_REQUEST.value()))
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_JSON_INVALIDO));

            verify(reservaService, never()).crear(any(NuevaReservaCommand.class));
        }

        @Test
        @DisplayName("POST /api/reservas con solapamiento devuelve 409")
        void crearReservaSolapadaDevuelve409() throws Exception {
            when(reservaService.crear(any(NuevaReservaCommand.class)))
                    .thenThrow(new ReservaConflictException("El laboratorio " + NOMBRE_LABORATORIO
                            + " ya tiene una reserva activa que se cruza con el horario solicitado"));

            mockMvc.perform(post(RUTA_RESERVAS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(comoJson(solicitudValida())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.estado").value(HttpStatus.CONFLICT.value()));
        }
    }

    @Nested
    @DisplayName("Cancelacion de reservas")
    class Cancelacion {

        @Test
        @DisplayName("PATCH /api/reservas/{id}/cancelar devuelve 200 con la reserva cancelada")
        void cancelarReservaDevuelve200() throws Exception {
            when(reservaService.cancelar(ID_RESERVA)).thenReturn(reservaCon(EstadoReserva.CANCELADA));

            mockMvc.perform(patch(RUTA_RESERVAS + "/{id}/cancelar", ID_RESERVA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value(EstadoReserva.CANCELADA.name()));
        }

        @Test
        @DisplayName("PATCH /api/reservas/{id}/cancelar de una reserva inexistente devuelve 404")
        void cancelarReservaInexistenteDevuelve404() throws Exception {
            when(reservaService.cancelar(ID_INEXISTENTE))
                    .thenThrow(RecursoNoEncontradoException.de("reserva", ID_INEXISTENTE));

            mockMvc.perform(patch(RUTA_RESERVAS + "/{id}/cancelar", ID_INEXISTENTE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.estado").value(HttpStatus.NOT_FOUND.value()))
                    .andExpect(jsonPath("$.mensaje").value("No existe reserva con id " + ID_INEXISTENTE));
        }

        @Test
        @DisplayName("PATCH /api/reservas/{id}/cancelar de una reserva ya cancelada devuelve 409")
        void cancelarReservaYaCanceladaDevuelve409() throws Exception {
            when(reservaService.cancelar(ID_RESERVA))
                    .thenThrow(new ReservaConflictException("La reserva " + ID_RESERVA + " ya fue cancelada"));

            mockMvc.perform(patch(RUTA_RESERVAS + "/{id}/cancelar", ID_RESERVA))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.estado").value(HttpStatus.CONFLICT.value()))
                    .andExpect(jsonPath("$.mensaje").value("La reserva " + ID_RESERVA + " ya fue cancelada"));
        }
    }
}
