package com.universidad.reservaslabs.service;

import com.universidad.reservaslabs.exception.RecursoNoEncontradoException;
import com.universidad.reservaslabs.exception.ReservaConflictException;
import com.universidad.reservaslabs.model.EstadoReserva;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.Reserva;
import com.universidad.reservaslabs.model.TipoLaboratorio;
import com.universidad.reservaslabs.repository.LaboratorioRepository;
import com.universidad.reservaslabs.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de la capa de servicio con los repositorios simulados:
 * concentran las reglas de negocio de reserva (jornada, duracion, solapamiento
 * y cancelacion) sin tocar la base de datos ni la capa web.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Servicio de reservas")
class ReservaServiceTest {

    private static final Long ID_LABORATORIO = 1L;
    private static final Long ID_RESERVA = 10L;
    private static final String NOMBRE_LABORATORIO = "Lab Computo 1";
    private static final String NOMBRE_SOLICITANTE = "Ana Torres";
    private static final String CORREO_SOLICITANTE = "ana.torres@universidad.edu";
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 3, 10, 9, 0);
    private static final LocalDateTime FIN = LocalDateTime.of(2026, 3, 10, 11, 0);

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private LaboratorioRepository laboratorioRepository;

    @InjectMocks
    private ReservaService reservaService;

    private static Laboratorio laboratorio() {
        return Laboratorio.builder()
                .id(ID_LABORATORIO)
                .nombre(NOMBRE_LABORATORIO)
                .ubicacion("Bloque A - Piso 2")
                .capacidad(30)
                .tipo(TipoLaboratorio.COMPUTO)
                .build();
    }

    private static NuevaReservaCommand comandoEntre(LocalDateTime inicio, LocalDateTime fin) {
        return new NuevaReservaCommand(ID_LABORATORIO, NOMBRE_SOLICITANTE, CORREO_SOLICITANTE, inicio, fin);
    }

    private static NuevaReservaCommand comandoValido() {
        return comandoEntre(INICIO, FIN);
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

    @Nested
    @DisplayName("Creacion de reservas")
    class Creacion {

        @Test
        @DisplayName("Guarda la reserva con estado ACTIVA y la devuelve")
        void creaLaReservaConEstadoActiva() {
            when(laboratorioRepository.findById(ID_LABORATORIO)).thenReturn(Optional.of(laboratorio()));
            when(reservaRepository.buscarSolapamientos(ID_LABORATORIO, INICIO, FIN)).thenReturn(List.of());
            when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

            Reserva creada = reservaService.crear(comandoValido());

            assertThat(creada.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
            verify(reservaRepository).save(any(Reserva.class));
        }

        @Test
        @DisplayName("Guarda el laboratorio, el rango, el estado y los textos recortados")
        void guardaLaReservaConLosDatosLimpios() {
            Laboratorio laboratorioEsperado = laboratorio();
            NuevaReservaCommand comando = new NuevaReservaCommand(ID_LABORATORIO, "  Ana Perez  ",
                    "  ana@udes.edu.co  ", INICIO, FIN);
            when(laboratorioRepository.findById(ID_LABORATORIO)).thenReturn(Optional.of(laboratorioEsperado));
            when(reservaRepository.buscarSolapamientos(ID_LABORATORIO, INICIO, FIN)).thenReturn(List.of());
            when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

            reservaService.crear(comando);

            ArgumentCaptor<Reserva> capturador = ArgumentCaptor.forClass(Reserva.class);
            verify(reservaRepository).save(capturador.capture());
            Reserva guardada = capturador.getValue();
            assertThat(guardada.getLaboratorio()).isSameAs(laboratorioEsperado);
            assertThat(guardada.getInicio()).isEqualTo(INICIO);
            assertThat(guardada.getFin()).isEqualTo(FIN);
            assertThat(guardada.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
            assertThat(guardada.getNombreSolicitante()).isEqualTo("Ana Perez");
            assertThat(guardada.getCorreoSolicitante()).isEqualTo("ana@udes.edu.co");
        }

        @Test
        @DisplayName("Rechaza la reserva cuando el laboratorio no existe")
        void laboratorioInexistenteLanzaRecursoNoEncontrado() {
            when(laboratorioRepository.findById(ID_LABORATORIO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reservaService.crear(comandoValido()))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("laboratorio");
        }

        @Test
        @DisplayName("No consulta solapamientos ni guarda si el laboratorio no existe")
        void laboratorioInexistenteNoConsultaSolapamientos() {
            when(laboratorioRepository.findById(ID_LABORATORIO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reservaService.crear(comandoValido()))
                    .isInstanceOf(RecursoNoEncontradoException.class);

            verifyNoInteractions(reservaRepository);
        }

        @Test
        @DisplayName("Rechaza la reserva que se cruza con otra activa y no guarda nada")
        void solapamientoLanzaConflictoYNoGuarda() {
            when(laboratorioRepository.findById(ID_LABORATORIO)).thenReturn(Optional.of(laboratorio()));
            when(reservaRepository.buscarSolapamientos(ID_LABORATORIO, INICIO, FIN))
                    .thenReturn(List.of(reservaCon(EstadoReserva.ACTIVA)));

            assertThatThrownBy(() -> reservaService.crear(comandoValido()))
                    .isInstanceOf(ReservaConflictException.class)
                    .hasMessageContaining(NOMBRE_LABORATORIO);

            verify(reservaRepository, never()).save(any(Reserva.class));
        }
    }

    @Nested
    @DisplayName("Datos obligatorios del comando")
    class DatosObligatorios {

        @Test
        @DisplayName("Rechaza un comando nulo")
        void comandoNuloLanzaNullPointer() {
            assertThatThrownBy(() -> reservaService.crear(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("El comando de reserva es obligatorio");

            verifyNoInteractions(laboratorioRepository, reservaRepository);
        }

        @Test
        @DisplayName("Rechaza un comando sin laboratorio")
        void laboratorioNuloEsInvalido() {
            NuevaReservaCommand comando = new NuevaReservaCommand(null, NOMBRE_SOLICITANTE, CORREO_SOLICITANTE,
                    INICIO, FIN);

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El laboratorio es obligatorio");

            verifyNoInteractions(laboratorioRepository, reservaRepository);
        }

        @Test
        @DisplayName("Rechaza un comando sin hora de inicio")
        void inicioNuloEsInvalido() {
            NuevaReservaCommand comando = comandoEntre(null, FIN);

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("La fecha y hora de inicio es obligatoria");

            verifyNoInteractions(laboratorioRepository, reservaRepository);
        }

        @Test
        @DisplayName("Rechaza un comando sin hora de fin")
        void finNuloEsInvalido() {
            NuevaReservaCommand comando = comandoEntre(INICIO, null);

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("La fecha y hora de fin es obligatoria");

            verifyNoInteractions(laboratorioRepository, reservaRepository);
        }

        @Test
        @DisplayName("Rechaza un nombre de solicitante en blanco")
        void nombreEnBlancoEsInvalido() {
            NuevaReservaCommand comando = new NuevaReservaCommand(ID_LABORATORIO, "   ", CORREO_SOLICITANTE,
                    INICIO, FIN);

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El nombre del solicitante es obligatorio");

            verifyNoInteractions(laboratorioRepository, reservaRepository);
        }

        @Test
        @DisplayName("Rechaza un correo de solicitante en blanco")
        void correoEnBlancoEsInvalido() {
            NuevaReservaCommand comando = new NuevaReservaCommand(ID_LABORATORIO, NOMBRE_SOLICITANTE, "   ",
                    INICIO, FIN);

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El correo del solicitante es obligatorio");

            verifyNoInteractions(laboratorioRepository, reservaRepository);
        }
    }

    @Nested
    @DisplayName("Bordes validos de jornada y duracion")
    class BordesValidos {

        private void prepararFranjaLibre(LocalDateTime inicio, LocalDateTime fin) {
            when(laboratorioRepository.findById(ID_LABORATORIO)).thenReturn(Optional.of(laboratorio()));
            when(reservaRepository.buscarSolapamientos(ID_LABORATORIO, inicio, fin)).thenReturn(List.of());
            when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        }

        @Test
        @DisplayName("Acepta una reserva que empieza justo en la apertura")
        void reservaQueAbreALaSieteEsValida() {
            LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 7, 0);
            LocalDateTime fin = LocalDateTime.of(2026, 3, 10, 8, 0);
            prepararFranjaLibre(inicio, fin);

            Reserva creada = reservaService.crear(comandoEntre(inicio, fin));

            assertThat(creada.getInicio()).isEqualTo(inicio);
            assertThat(creada.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
            verify(reservaRepository).save(any(Reserva.class));
        }

        @Test
        @DisplayName("Acepta una reserva que termina justo en el cierre")
        void reservaQueCierraALasNueveDeLaNocheEsValida() {
            LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 20, 0);
            LocalDateTime fin = LocalDateTime.of(2026, 3, 10, 21, 0);
            prepararFranjaLibre(inicio, fin);

            Reserva creada = reservaService.crear(comandoEntre(inicio, fin));

            assertThat(creada.getFin()).isEqualTo(fin);
            assertThat(creada.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
            verify(reservaRepository).save(any(Reserva.class));
        }

        @Test
        @DisplayName("Acepta una duracion de exactamente treinta minutos")
        void duracionDeTreintaMinutosEsValida() {
            LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 9, 0);
            LocalDateTime fin = LocalDateTime.of(2026, 3, 10, 9, 30);
            prepararFranjaLibre(inicio, fin);

            Reserva creada = reservaService.crear(comandoEntre(inicio, fin));

            assertThat(creada.getInicio()).isEqualTo(inicio);
            assertThat(creada.getFin()).isEqualTo(fin);
            verify(reservaRepository).save(any(Reserva.class));
        }

        @Test
        @DisplayName("Acepta una duracion de exactamente tres horas")
        void duracionDeTresHorasEsValida() {
            LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 9, 0);
            LocalDateTime fin = LocalDateTime.of(2026, 3, 10, 12, 0);
            prepararFranjaLibre(inicio, fin);

            Reserva creada = reservaService.crear(comandoEntre(inicio, fin));

            assertThat(creada.getInicio()).isEqualTo(inicio);
            assertThat(creada.getFin()).isEqualTo(fin);
            verify(reservaRepository).save(any(Reserva.class));
        }
    }

    @Nested
    @DisplayName("Reglas de jornada y duracion")
    class ReglasDeJornadaYDuracion {

        @BeforeEach
        void prepararLaboratorioExistente() {
            lenient().when(laboratorioRepository.findById(ID_LABORATORIO)).thenReturn(Optional.of(laboratorio()));
        }

        @Test
        @DisplayName("Rechaza un inicio anterior a la apertura")
        void inicioAntesDeLaAperturaEsInvalido() {
            NuevaReservaCommand comando = comandoEntre(LocalDateTime.of(2026, 3, 10, 6, 0),
                    LocalDateTime.of(2026, 3, 10, 8, 0));

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("07:00");

            verifyNoInteractions(reservaRepository);
        }

        @Test
        @DisplayName("Rechaza un fin posterior al cierre")
        void finDespuesDelCierreEsInvalido() {
            NuevaReservaCommand comando = comandoEntre(LocalDateTime.of(2026, 3, 10, 20, 0),
                    LocalDateTime.of(2026, 3, 10, 21, 30));

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("21:00");

            verifyNoInteractions(reservaRepository);
        }

        @Test
        @DisplayName("Rechaza una duracion menor a media hora")
        void duracionDeQuinceMinutosEsInvalida() {
            NuevaReservaCommand comando = comandoEntre(INICIO, LocalDateTime.of(2026, 3, 10, 9, 15));

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("30 minutos");

            verifyNoInteractions(reservaRepository);
        }

        @Test
        @DisplayName("Rechaza una duracion de veintinueve minutos")
        void duracionDeVeintinueveMinutosEsInvalida() {
            NuevaReservaCommand comando = comandoEntre(INICIO, LocalDateTime.of(2026, 3, 10, 9, 29));

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("30 minutos");

            verifyNoInteractions(reservaRepository);
        }

        @Test
        @DisplayName("Rechaza una duracion mayor a tres horas")
        void duracionDeCuatroHorasEsInvalida() {
            NuevaReservaCommand comando = comandoEntre(INICIO, LocalDateTime.of(2026, 3, 10, 13, 0));

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("3 horas");

            verifyNoInteractions(reservaRepository);
        }

        @Test
        @DisplayName("Rechaza una duracion de tres horas y un minuto")
        void duracionDeTresHorasYUnMinutoEsInvalida() {
            NuevaReservaCommand comando = comandoEntre(INICIO, LocalDateTime.of(2026, 3, 10, 12, 1));

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("3 horas");

            verifyNoInteractions(reservaRepository);
        }

        @Test
        @DisplayName("Rechaza un inicio posterior al fin")
        void inicioPosteriorAlFinEsInvalido() {
            NuevaReservaCommand comando = comandoEntre(FIN, INICIO);

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("anterior a la hora de fin");

            verifyNoInteractions(reservaRepository);
        }

        @Test
        @DisplayName("Rechaza una reserva con inicio igual al fin")
        void inicioIgualAlFinEsInvalido() {
            NuevaReservaCommand comando = comandoEntre(INICIO, INICIO);

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("anterior a la hora de fin");

            verifyNoInteractions(reservaRepository);
        }

        @Test
        @DisplayName("Rechaza una reserva a caballo entre dos dias")
        void reservaEntreDosDiasEsInvalida() {
            NuevaReservaCommand comando = comandoEntre(LocalDateTime.of(2026, 3, 10, 20, 0),
                    LocalDateTime.of(2026, 3, 11, 8, 0));

            assertThatThrownBy(() -> reservaService.crear(comando))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mismo dia");

            verifyNoInteractions(reservaRepository);
        }
    }

    @Nested
    @DisplayName("Cancelacion de reservas")
    class Cancelacion {

        @Test
        @DisplayName("Deja en CANCELADA una reserva activa")
        void cancelarReservaActivaLaDejaCancelada() {
            when(reservaRepository.findById(ID_RESERVA)).thenReturn(Optional.of(reservaCon(EstadoReserva.ACTIVA)));

            Reserva cancelada = reservaService.cancelar(ID_RESERVA);

            assertThat(cancelada.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        }

        @Test
        @DisplayName("Rechaza cancelar una reserva ya cancelada")
        void cancelarReservaYaCanceladaLanzaConflicto() {
            when(reservaRepository.findById(ID_RESERVA)).thenReturn(Optional.of(reservaCon(EstadoReserva.CANCELADA)));

            assertThatThrownBy(() -> reservaService.cancelar(ID_RESERVA))
                    .isInstanceOf(ReservaConflictException.class)
                    .hasMessageContaining("ya fue cancelada");
        }

        @Test
        @DisplayName("Falla al cancelar una reserva inexistente")
        void cancelarReservaInexistenteLanzaRecursoNoEncontrado() {
            when(reservaRepository.findById(ID_RESERVA)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reservaService.cancelar(ID_RESERVA))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("reserva");
        }
    }

    @Nested
    @DisplayName("Consultas de reservas")
    class Consultas {

        @Test
        @DisplayName("Falla al buscar una reserva inexistente")
        void findByIdInexistenteLanzaRecursoNoEncontrado() {
            when(reservaRepository.findById(ID_RESERVA)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reservaService.findById(ID_RESERVA))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("reserva");
        }

        @Test
        @DisplayName("findAll delega en el orden por inicio ascendente")
        void findAllDevuelveLasReservasOrdenadas() {
            Reserva reserva = reservaCon(EstadoReserva.ACTIVA);
            when(reservaRepository.findAllByOrderByInicioAsc()).thenReturn(List.of(reserva));

            List<Reserva> resultado = reservaService.findAll();

            assertThat(resultado).containsExactly(reserva);
            verify(reservaRepository).findAllByOrderByInicioAsc();
        }

        @Test
        @DisplayName("findByLaboratorio falla cuando el laboratorio no existe")
        void findByLaboratorioInexistenteLanzaRecursoNoEncontrado() {
            when(laboratorioRepository.existsById(ID_LABORATORIO)).thenReturn(false);

            assertThatThrownBy(() -> reservaService.findByLaboratorio(ID_LABORATORIO))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("laboratorio");

            verifyNoInteractions(reservaRepository);
        }

        @Test
        @DisplayName("findByLaboratorio delega en el repositorio cuando el laboratorio existe")
        void findByLaboratorioExistenteDevuelveSusReservas() {
            Reserva reserva = reservaCon(EstadoReserva.ACTIVA);
            when(laboratorioRepository.existsById(ID_LABORATORIO)).thenReturn(true);
            when(reservaRepository.findByLaboratorioIdOrderByInicioAsc(ID_LABORATORIO)).thenReturn(List.of(reserva));

            List<Reserva> resultado = reservaService.findByLaboratorio(ID_LABORATORIO);

            assertThat(resultado).containsExactly(reserva);
            verify(reservaRepository).findByLaboratorioIdOrderByInicioAsc(ID_LABORATORIO);
        }
    }
}
