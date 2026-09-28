package com.universidad.reservaslabs.repository;

import com.universidad.reservaslabs.model.EstadoReserva;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.Reserva;
import com.universidad.reservaslabs.model.TipoLaboratorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Pruebas de la consulta JPQL de solapamientos sobre H2: comprueban que el
 * intervalo se trata como semiabierto en sus dos bordes y que solo compiten las
 * reservas activas del mismo laboratorio y del mismo dia.
 */
@DataJpaTest
@DisplayName("Repositorio de reservas")
class ReservaRepositoryTest {

    private static final LocalDateTime INICIO_SOLICITADO = LocalDateTime.of(2026, 3, 10, 9, 0);
    private static final LocalDateTime FIN_SOLICITADO = LocalDateTime.of(2026, 3, 10, 11, 0);
    private static final LocalDateTime INICIO_EXISTENTE = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final LocalDateTime FIN_EXISTENTE = LocalDateTime.of(2026, 3, 10, 12, 0);
    private static final LocalDateTime INICIO_CONTIGUO = LocalDateTime.of(2026, 3, 10, 7, 0);
    private static final LocalDateTime FIN_TARDIO = LocalDateTime.of(2026, 3, 10, 13, 0);
    private static final LocalDateTime INICIO_DIA_SIGUIENTE = LocalDateTime.of(2026, 3, 11, 9, 0);
    private static final LocalDateTime FIN_DIA_SIGUIENTE = LocalDateTime.of(2026, 3, 11, 11, 0);
    private static final int CAPACIDAD = 30;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReservaRepository reservaRepository;

    private Laboratorio persistirLaboratorio(String nombre) {
        return entityManager.persistAndFlush(Laboratorio.builder()
                .nombre(nombre)
                .ubicacion("Bloque A - Piso 2")
                .capacidad(CAPACIDAD)
                .tipo(TipoLaboratorio.COMPUTO)
                .build());
    }

    private void persistirReserva(Laboratorio laboratorio, LocalDateTime inicio, LocalDateTime fin,
                                  EstadoReserva estado) {
        entityManager.persistAndFlush(Reserva.builder()
                .laboratorio(laboratorio)
                .nombreSolicitante("Ana Torres")
                .correoSolicitante("ana.torres@universidad.edu")
                .inicio(inicio)
                .fin(fin)
                .estado(estado)
                .build());
    }

    private List<Reserva> buscarSolapamientosDe(Laboratorio laboratorio) {
        return buscarSolapamientosDe(laboratorio, INICIO_SOLICITADO, FIN_SOLICITADO);
    }

    private List<Reserva> buscarSolapamientosDe(Laboratorio laboratorio, LocalDateTime inicio,
                                                LocalDateTime fin) {
        return reservaRepository.buscarSolapamientos(laboratorio.getId(), inicio, fin);
    }

    @Nested
    @DisplayName("Rangos que compiten por el laboratorio")
    class RangosQueSolapan {

        @Test
        @DisplayName("Detecta el cruce parcial con una reserva activa")
        void cruceParcialSeDetectaComoSolapamiento() {
            Laboratorio laboratorio = persistirLaboratorio("Lab cruce parcial");
            persistirReserva(laboratorio, INICIO_EXISTENTE, FIN_EXISTENTE, EstadoReserva.ACTIVA);

            assertThat(buscarSolapamientosDe(laboratorio)).hasSize(1);
        }

        @Test
        @DisplayName("Detecta la reserva existente que queda envuelta por el rango nuevo")
        void reservaEnvueltaPorElRangoNuevoSolapa() {
            Laboratorio laboratorio = persistirLaboratorio("Lab envolvente");
            persistirReserva(laboratorio, INICIO_EXISTENTE, FIN_SOLICITADO, EstadoReserva.ACTIVA);

            assertThat(buscarSolapamientosDe(laboratorio, INICIO_SOLICITADO, FIN_TARDIO))
                    .extracting(Reserva::getInicio, Reserva::getFin)
                    .containsExactly(tuple(INICIO_EXISTENTE, FIN_SOLICITADO));
        }

        @Test
        @DisplayName("Detecta el rango nuevo contenido dentro de una reserva existente")
        void rangoNuevoContenidoEnLaExistenteSolapa() {
            Laboratorio laboratorio = persistirLaboratorio("Lab contenido");
            persistirReserva(laboratorio, INICIO_SOLICITADO, FIN_TARDIO, EstadoReserva.ACTIVA);

            assertThat(buscarSolapamientosDe(laboratorio, INICIO_EXISTENTE, FIN_SOLICITADO))
                    .extracting(Reserva::getInicio, Reserva::getFin)
                    .containsExactly(tuple(INICIO_SOLICITADO, FIN_TARDIO));
        }

        @Test
        @DisplayName("Detecta la reserva con el mismo rango exacto")
        void rangoIdenticoSolapa() {
            Laboratorio laboratorio = persistirLaboratorio("Lab identico");
            persistirReserva(laboratorio, INICIO_SOLICITADO, FIN_SOLICITADO, EstadoReserva.ACTIVA);

            assertThat(buscarSolapamientosDe(laboratorio))
                    .extracting(Reserva::getInicio, Reserva::getFin)
                    .containsExactly(tuple(INICIO_SOLICITADO, FIN_SOLICITADO));
        }
    }

    @Nested
    @DisplayName("Rangos que no compiten por el laboratorio")
    class RangosQueNoSolapan {

        @Test
        @DisplayName("Ignora la reserva contigua que termina cuando empieza la nueva")
        void reservaContiguaNoSolapa() {
            Laboratorio laboratorio = persistirLaboratorio("Lab contiguo");
            persistirReserva(laboratorio, INICIO_CONTIGUO, INICIO_SOLICITADO, EstadoReserva.ACTIVA);

            assertThat(buscarSolapamientosDe(laboratorio)).isEmpty();
        }

        @Test
        @DisplayName("Ignora la reserva contigua que empieza cuando termina la nueva")
        void reservaContiguaPorElInicioNoSolapa() {
            Laboratorio laboratorio = persistirLaboratorio("Lab contiguo posterior");
            persistirReserva(laboratorio, FIN_SOLICITADO, FIN_TARDIO, EstadoReserva.ACTIVA);

            assertThat(buscarSolapamientosDe(laboratorio)).isEmpty();
        }

        @Test
        @DisplayName("Ignora el mismo rango horario cuando cae en otro dia")
        void mismoRangoEnOtroDiaNoSolapa() {
            Laboratorio laboratorio = persistirLaboratorio("Lab otro dia");
            persistirReserva(laboratorio, INICIO_DIA_SIGUIENTE, FIN_DIA_SIGUIENTE, EstadoReserva.ACTIVA);

            assertThat(buscarSolapamientosDe(laboratorio)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Filtros de estado y de laboratorio")
    class FiltrosDeLaConsulta {

        @Test
        @DisplayName("Ignora las reservas canceladas")
        void reservaCanceladaNoSolapa() {
            Laboratorio laboratorio = persistirLaboratorio("Lab cancelado");
            persistirReserva(laboratorio, INICIO_EXISTENTE, FIN_EXISTENTE, EstadoReserva.CANCELADA);

            assertThat(buscarSolapamientosDe(laboratorio)).isEmpty();
        }

        @Test
        @DisplayName("Ignora las reservas de otro laboratorio")
        void reservaDeOtroLaboratorioNoSolapa() {
            Laboratorio laboratorioConsultado = persistirLaboratorio("Lab consultado");
            Laboratorio otroLaboratorio = persistirLaboratorio("Lab ajeno");
            persistirReserva(otroLaboratorio, INICIO_EXISTENTE, FIN_EXISTENTE, EstadoReserva.ACTIVA);

            assertThat(buscarSolapamientosDe(laboratorioConsultado)).isEmpty();
        }
    }
}
