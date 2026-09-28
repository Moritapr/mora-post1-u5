package com.universidad.reservaslabs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universidad.reservaslabs.controller.dto.CrearLaboratorioRequest;
import com.universidad.reservaslabs.exception.RecursoDuplicadoException;
import com.universidad.reservaslabs.exception.RecursoNoEncontradoException;
import com.universidad.reservaslabs.model.EstadoReserva;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.Reserva;
import com.universidad.reservaslabs.model.TipoLaboratorio;
import com.universidad.reservaslabs.service.LaboratorioService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la frontera REST del catalogo de laboratorios con los servicios
 * simulados: comprueban el JSON del catalogo, la cabecera Location del alta y
 * la traduccion a 404, 409 y 400 que hace el manejador global.
 */
@WebMvcTest(LaboratorioController.class)
@DisplayName("API REST de laboratorios")
class LaboratorioControllerTest {

    private static final Long ID_LABORATORIO = 1L;
    private static final Long ID_INEXISTENTE = 99L;
    private static final Long ID_RESERVA = 5L;
    private static final String NOMBRE_LABORATORIO = "Lab Computo 1";
    private static final String UBICACION_LABORATORIO = "Bloque A - Piso 2";
    private static final int CAPACIDAD_LABORATORIO = 30;
    private static final String NOMBRE_SOLICITANTE = "Ana Torres";
    private static final String CORREO_SOLICITANTE = "ana.torres@universidad.edu";
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 3, 10, 9, 0);
    private static final LocalDateTime FIN = LocalDateTime.of(2026, 3, 10, 11, 0);
    private static final String RUTA_LABORATORIOS = "/api/laboratorios";
    private static final String MENSAJE_VALIDACION = "La solicitud contiene datos invalidos";
    private static final String MENSAJE_DUPLICADO =
            "Ya existe un laboratorio con el nombre " + NOMBRE_LABORATORIO;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LaboratorioService laboratorioService;

    @MockBean
    private ReservaService reservaService;

    private static Laboratorio laboratorio() {
        return Laboratorio.builder()
                .id(ID_LABORATORIO)
                .nombre(NOMBRE_LABORATORIO)
                .ubicacion(UBICACION_LABORATORIO)
                .capacidad(CAPACIDAD_LABORATORIO)
                .tipo(TipoLaboratorio.COMPUTO)
                .build();
    }

    private static Reserva reservaDelLaboratorio() {
        return Reserva.builder()
                .id(ID_RESERVA)
                .laboratorio(laboratorio())
                .nombreSolicitante(NOMBRE_SOLICITANTE)
                .correoSolicitante(CORREO_SOLICITANTE)
                .inicio(INICIO)
                .fin(FIN)
                .estado(EstadoReserva.ACTIVA)
                .build();
    }

    private static CrearLaboratorioRequest solicitudCon(int capacidad) {
        return new CrearLaboratorioRequest(NOMBRE_LABORATORIO, UBICACION_LABORATORIO,
                capacidad, TipoLaboratorio.COMPUTO);
    }

    private static CrearLaboratorioRequest solicitudValida() {
        return solicitudCon(CAPACIDAD_LABORATORIO);
    }

    private String comoJson(CrearLaboratorioRequest solicitud) throws Exception {
        return objectMapper.writeValueAsString(solicitud);
    }

    @Nested
    @DisplayName("Consultas del catalogo")
    class Consultas {

        @Test
        @DisplayName("GET /api/laboratorios devuelve 200 y la lista de laboratorios")
        void listarDevuelveLosLaboratoriosEnJson() throws Exception {
            when(laboratorioService.findAll()).thenReturn(List.of(laboratorio()));

            mockMvc.perform(get(RUTA_LABORATORIOS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(ID_LABORATORIO.intValue()))
                    .andExpect(jsonPath("$[0].nombre").value(NOMBRE_LABORATORIO))
                    .andExpect(jsonPath("$[0].ubicacion").value(UBICACION_LABORATORIO))
                    .andExpect(jsonPath("$[0].capacidad").value(CAPACIDAD_LABORATORIO))
                    .andExpect(jsonPath("$[0].tipo").value(TipoLaboratorio.COMPUTO.name()));
        }

        @Test
        @DisplayName("GET /api/laboratorios/{id} devuelve 200 con el detalle del laboratorio")
        void obtenerLaboratorioDevuelveSuDetalle() throws Exception {
            when(laboratorioService.findById(ID_LABORATORIO)).thenReturn(laboratorio());

            mockMvc.perform(get(RUTA_LABORATORIOS + "/{id}", ID_LABORATORIO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ID_LABORATORIO.intValue()))
                    .andExpect(jsonPath("$.nombre").value(NOMBRE_LABORATORIO));
        }

        @Test
        @DisplayName("GET /api/laboratorios/{id} inexistente devuelve 404")
        void obtenerLaboratorioInexistenteDevuelve404() throws Exception {
            when(laboratorioService.findById(ID_INEXISTENTE))
                    .thenThrow(RecursoNoEncontradoException.de("laboratorio", ID_INEXISTENTE));

            mockMvc.perform(get(RUTA_LABORATORIOS + "/{id}", ID_INEXISTENTE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.estado").value(HttpStatus.NOT_FOUND.value()))
                    .andExpect(jsonPath("$.mensaje").value("No existe laboratorio con id " + ID_INEXISTENTE));
        }
    }

    @Nested
    @DisplayName("Reservas de un laboratorio")
    class ReservasDelLaboratorio {

        @Test
        @DisplayName("GET /api/laboratorios/{id}/reservas delega en el servicio de reservas")
        void reservasDelLaboratorioDeleganEnElServicioDeReservas() throws Exception {
            when(reservaService.findByLaboratorio(ID_LABORATORIO)).thenReturn(List.of(reservaDelLaboratorio()));

            mockMvc.perform(get(RUTA_LABORATORIOS + "/{id}/reservas", ID_LABORATORIO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(ID_RESERVA.intValue()))
                    .andExpect(jsonPath("$[0].laboratorioId").value(ID_LABORATORIO.intValue()))
                    .andExpect(jsonPath("$[0].estado").value(EstadoReserva.ACTIVA.name()));

            verify(reservaService).findByLaboratorio(ID_LABORATORIO);
        }

        @Test
        @DisplayName("GET /api/laboratorios/{id}/reservas de un laboratorio inexistente devuelve 404")
        void reservasDeLaboratorioInexistenteDevuelve404() throws Exception {
            when(reservaService.findByLaboratorio(ID_INEXISTENTE))
                    .thenThrow(RecursoNoEncontradoException.de("laboratorio", ID_INEXISTENTE));

            mockMvc.perform(get(RUTA_LABORATORIOS + "/{id}/reservas", ID_INEXISTENTE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.estado").value(HttpStatus.NOT_FOUND.value()));
        }
    }

    @Nested
    @DisplayName("Alta de laboratorios")
    class Alta {

        @Test
        @DisplayName("POST /api/laboratorios valido devuelve 201 con cabecera Location")
        void crearLaboratorioValidoDevuelve201ConLocation() throws Exception {
            when(laboratorioService.crear(any(Laboratorio.class))).thenReturn(laboratorio());

            mockMvc.perform(post(RUTA_LABORATORIOS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(comoJson(solicitudValida())))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            endsWith(RUTA_LABORATORIOS + "/" + ID_LABORATORIO)))
                    .andExpect(jsonPath("$.id").value(ID_LABORATORIO.intValue()))
                    .andExpect(jsonPath("$.nombre").value(NOMBRE_LABORATORIO));
        }

        @Test
        @DisplayName("POST /api/laboratorios con nombre repetido devuelve 409")
        void crearLaboratorioDuplicadoDevuelve409() throws Exception {
            when(laboratorioService.crear(any(Laboratorio.class)))
                    .thenThrow(new RecursoDuplicadoException(MENSAJE_DUPLICADO));

            mockMvc.perform(post(RUTA_LABORATORIOS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(comoJson(solicitudValida())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.estado").value(HttpStatus.CONFLICT.value()))
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_DUPLICADO));
        }

        @Test
        @DisplayName("POST /api/laboratorios con capacidad cero devuelve 400 con el detalle del campo")
        void crearLaboratorioConCapacidadCeroDevuelve400() throws Exception {
            mockMvc.perform(post(RUTA_LABORATORIOS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(comoJson(solicitudCon(0))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.estado").value(HttpStatus.BAD_REQUEST.value()))
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                    .andExpect(jsonPath("$.detalles.capacidad").exists());

            verify(laboratorioService, never()).crear(any(Laboratorio.class));
        }

        @Test
        @DisplayName("POST /api/laboratorios con capacidad negativa devuelve 400 con el detalle del campo")
        void crearLaboratorioConCapacidadNegativaDevuelve400() throws Exception {
            mockMvc.perform(post(RUTA_LABORATORIOS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(comoJson(solicitudCon(-5))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.estado").value(HttpStatus.BAD_REQUEST.value()))
                    .andExpect(jsonPath("$.detalles.capacidad").exists());

            verify(laboratorioService, never()).crear(any(Laboratorio.class));
        }

        @Test
        @DisplayName("POST /api/laboratorios sin nombre ni ubicacion devuelve 400 con ambos campos")
        void crearLaboratorioSinTextosObligatoriosDevuelve400() throws Exception {
            CrearLaboratorioRequest solicitud = new CrearLaboratorioRequest("  ", "",
                    CAPACIDAD_LABORATORIO, TipoLaboratorio.COMPUTO);

            mockMvc.perform(post(RUTA_LABORATORIOS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(comoJson(solicitud)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detalles.nombre").exists())
                    .andExpect(jsonPath("$.detalles.ubicacion").exists());

            verify(laboratorioService, never()).crear(any(Laboratorio.class));
        }
    }
}
