package com.universidad.reservaslabs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universidad.reservaslabs.controller.dto.CrearLaboratorioRequest;
import com.universidad.reservaslabs.controller.dto.CrearReservaRequest;
import com.universidad.reservaslabs.model.EstadoReserva;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.TipoLaboratorio;
import com.universidad.reservaslabs.repository.LaboratorioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Unica prueba de extremo a extremo del proyecto: levanta el contexto completo y
 * recorre controlador, servicio, repositorio y H2 real sin ningun simulacro, de
 * modo que solo pasa si las capas encajan de verdad. En particular comprueba que
 * el repositorio sigue resolviendo el laboratorio por adelantado (EntityGraph)
 * pese a que la sesion se cierra antes de la vista (open-in-view=false): si esa
 * carga desaparece, la respuesta ya no puede aplanar el nombre del laboratorio.
 *
 * Cada prueba es autonoma y trabaja sobre su propio dia del calendario, porque
 * todas comparten el mismo contexto y la misma base en memoria. La base se
 * aisla con un nombre propio para que ningun otro contexto de prueba que use
 * create-drop borre sus datos a mitad de la suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:reservas_labs_e2e_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@DisplayName("Integracion de extremo a extremo")
class ReservasLabsIntegracionTest {

    private static final String RUTA_API_RESERVAS = "/api/reservas";
    private static final String RUTA_API_LABORATORIOS = "/api/laboratorios";
    private static final String RUTA_VISTA_RESERVAS = "/reservas";
    private static final String VISTA_LISTA = "reservas/lista";

    private static final String NOMBRE_LABORATORIO = "Lab Computo 1";
    private static final String UBICACION_LABORATORIO = "Bloque A - Piso 2";
    private static final int CAPACIDAD_LABORATORIO = 30;

    private static final String NOMBRE_SOLICITANTE = "Ana Torres";
    private static final String NOMBRE_SOLICITANTE_CON_ESPACIOS = "   Ana Torres   ";
    private static final String NOMBRE_SOLICITANTE_VISTA = "Bruno Vista";
    private static final String CORREO_SOLICITANTE = "ana.torres@universidad.edu";

    private static final int ANIO = 2026;
    private static final int MES = 4;
    private static final int DIA_ALTA = 6;
    private static final int DIA_SOLAPAMIENTO = 7;
    private static final int DIA_CONTIGUO = 8;
    private static final int DIA_CANCELACION = 9;
    private static final int DIA_LIBERACION = 10;
    private static final int DIA_VISTA = 13;

    private static final int HORA_INICIO = 9;
    private static final int HORA_FIN = 11;
    private static final int HORA_FIN_CONTIGUA = 13;
    private static final int HORA_INICIO_SOLAPADA = 10;
    private static final int HORA_FIN_SOLAPADA = 12;

    private static final String FECHA_VISTA_FORMATEADA = "13/04/2026 09:00";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LaboratorioRepository laboratorioRepository;

    private static LocalDateTime aLas(int dia, int hora) {
        return LocalDateTime.of(ANIO, MES, dia, hora, 0);
    }

    /** Resuelve el id real del laboratorio sembrado, que cambia en cada arranque. */
    private long idDelLaboratorioSembrado() {
        return laboratorioRepository.findAllByOrderByNombreAsc().stream()
                .filter(laboratorio -> NOMBRE_LABORATORIO.equals(laboratorio.getNombre()))
                .map(Laboratorio::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "DatosIniciales no sembro el laboratorio " + NOMBRE_LABORATORIO));
    }

    private ResultActions solicitarReserva(String solicitante, LocalDateTime inicio, LocalDateTime fin)
            throws Exception {
        CrearReservaRequest solicitud = new CrearReservaRequest(
                idDelLaboratorioSembrado(), solicitante, CORREO_SOLICITANTE, inicio, fin);
        return mockMvc.perform(post(RUTA_API_RESERVAS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(solicitud)));
    }

    /** Crea una reserva por la API exigiendo 201 y devuelve el id que asigno la base. */
    private long crearReserva(String solicitante, LocalDateTime inicio, LocalDateTime fin) throws Exception {
        String cuerpo = solicitarReserva(solicitante, inicio, fin)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }

    private ResultActions cancelarReserva(long id) throws Exception {
        return mockMvc.perform(patch(RUTA_API_RESERVAS + "/{id}/cancelar", id));
    }

    @Test
    @DisplayName("POST /api/reservas persiste la reserva y el GET posterior la devuelve con el laboratorio resuelto")
    void altaYConsultaDevuelvenLaReservaPersistidaConSuLaboratorio() throws Exception {
        LocalDateTime inicio = aLas(DIA_ALTA, HORA_INICIO);
        LocalDateTime fin = aLas(DIA_ALTA, HORA_FIN);

        long idCreada = crearReserva(NOMBRE_SOLICITANTE_CON_ESPACIOS, inicio, fin);

        mockMvc.perform(get(RUTA_API_RESERVAS + "/{id}", idCreada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) idCreada))
                .andExpect(jsonPath("$.laboratorioId").value((int) idDelLaboratorioSembrado()))
                .andExpect(jsonPath("$.laboratorioNombre").value(NOMBRE_LABORATORIO))
                .andExpect(jsonPath("$.nombreSolicitante").value(NOMBRE_SOLICITANTE))
                .andExpect(jsonPath("$.correoSolicitante").value(CORREO_SOLICITANTE))
                .andExpect(jsonPath("$.inicio", startsWith(inicio.toString())))
                .andExpect(jsonPath("$.fin", startsWith(fin.toString())))
                .andExpect(jsonPath("$.estado").value(EstadoReserva.ACTIVA.name()));
    }

    @Test
    @DisplayName("POST /api/reservas que se cruza con una reserva activa devuelve 409")
    void reservaSolapadaDevuelveConflicto() throws Exception {
        crearReserva(NOMBRE_SOLICITANTE,
                aLas(DIA_SOLAPAMIENTO, HORA_INICIO), aLas(DIA_SOLAPAMIENTO, HORA_FIN));

        solicitarReserva(NOMBRE_SOLICITANTE,
                aLas(DIA_SOLAPAMIENTO, HORA_INICIO_SOLAPADA), aLas(DIA_SOLAPAMIENTO, HORA_FIN_SOLAPADA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.mensaje", containsString(NOMBRE_LABORATORIO)))
                .andExpect(jsonPath("$.mensaje", containsString("se cruza con el horario solicitado")));
    }

    @Test
    @DisplayName("POST /api/reservas que empieza justo al terminar otra devuelve 201")
    void reservaContiguaPorElFinalSeAcepta() throws Exception {
        crearReserva(NOMBRE_SOLICITANTE, aLas(DIA_CONTIGUO, HORA_INICIO), aLas(DIA_CONTIGUO, HORA_FIN));

        solicitarReserva(NOMBRE_SOLICITANTE, aLas(DIA_CONTIGUO, HORA_FIN), aLas(DIA_CONTIGUO, HORA_FIN_CONTIGUA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value(EstadoReserva.ACTIVA.name()))
                .andExpect(jsonPath("$.inicio", startsWith(aLas(DIA_CONTIGUO, HORA_FIN).toString())));
    }

    @Test
    @DisplayName("PATCH /api/reservas/{id}/cancelar guarda el cambio de estado en la base")
    void cancelarPersisteElEstadoCancelada() throws Exception {
        long idCreada = crearReserva(NOMBRE_SOLICITANTE,
                aLas(DIA_CANCELACION, HORA_INICIO), aLas(DIA_CANCELACION, HORA_FIN));

        cancelarReserva(idCreada)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) idCreada))
                .andExpect(jsonPath("$.estado").value(EstadoReserva.CANCELADA.name()));

        mockMvc.perform(get(RUTA_API_RESERVAS + "/{id}", idCreada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(EstadoReserva.CANCELADA.name()));
    }

    @Test
    @DisplayName("Cancelar una reserva libera su franja para una reserva identica")
    void cancelarLiberaLaFranjaOcupada() throws Exception {
        LocalDateTime inicio = aLas(DIA_LIBERACION, HORA_INICIO);
        LocalDateTime fin = aLas(DIA_LIBERACION, HORA_FIN);
        long idOcupante = crearReserva(NOMBRE_SOLICITANTE, inicio, fin);

        solicitarReserva(NOMBRE_SOLICITANTE, inicio, fin)
                .andExpect(status().isConflict());

        cancelarReserva(idOcupante).andExpect(status().isOk());

        long idRecreada = crearReserva(NOMBRE_SOLICITANTE, inicio, fin);

        assertThat(idRecreada).isNotEqualTo(idOcupante);
    }

    @Test
    @DisplayName("GET /reservas muestra en el HTML la reserva creada por la API")
    void laVistaDeReservasMuestraLaReservaCreada() throws Exception {
        crearReserva(NOMBRE_SOLICITANTE_VISTA, aLas(DIA_VISTA, HORA_INICIO), aLas(DIA_VISTA, HORA_FIN));

        mockMvc.perform(get(RUTA_VISTA_RESERVAS))
                .andExpect(status().isOk())
                .andExpect(view().name(VISTA_LISTA))
                .andExpect(content().string(containsString(NOMBRE_LABORATORIO)))
                .andExpect(content().string(containsString(NOMBRE_SOLICITANTE_VISTA)))
                .andExpect(content().string(containsString(FECHA_VISTA_FORMATEADA)));
    }

    @Test
    @DisplayName("POST /api/laboratorios con un nombre ya sembrado devuelve 409")
    void laboratorioConNombreRepetidoDevuelveConflicto() throws Exception {
        CrearLaboratorioRequest solicitud = new CrearLaboratorioRequest(
                NOMBRE_LABORATORIO, UBICACION_LABORATORIO, CAPACIDAD_LABORATORIO, TipoLaboratorio.COMPUTO);

        mockMvc.perform(post(RUTA_API_LABORATORIOS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solicitud)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.mensaje", containsString("Ya existe un laboratorio")))
                .andExpect(jsonPath("$.mensaje", containsString(NOMBRE_LABORATORIO)));
    }
}
