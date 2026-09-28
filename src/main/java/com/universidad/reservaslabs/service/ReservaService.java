package com.universidad.reservaslabs.service;

import com.universidad.reservaslabs.exception.RecursoNoEncontradoException;
import com.universidad.reservaslabs.exception.ReservaConflictException;
import com.universidad.reservaslabs.model.EstadoReserva;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.Reserva;
import com.universidad.reservaslabs.repository.LaboratorioRepository;
import com.universidad.reservaslabs.repository.ReservaRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nucleo de negocio de las reservas: jornada de atencion, duracion permitida y
 * ausencia de solapamientos. Es la unica capa que conoce estas reglas, de modo
 * que la API REST y las vistas MVC comparten exactamente el mismo comportamiento.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReservaService {

    public static final LocalTime HORA_APERTURA = LocalTime.of(7, 0);
    public static final LocalTime HORA_CIERRE = LocalTime.of(21, 0);
    public static final Duration DURACION_MINIMA = Duration.ofMinutes(30);
    public static final Duration DURACION_MAXIMA = Duration.ofHours(3);

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final String RECURSO_RESERVA = "reserva";
    private static final String RECURSO_LABORATORIO = "laboratorio";

    private static final String MENSAJE_COMANDO_NULO = "El comando de reserva es obligatorio";
    private static final String MENSAJE_LABORATORIO_NULO = "El laboratorio es obligatorio";
    private static final String MENSAJE_INICIO_NULO = "La fecha y hora de inicio es obligatoria";
    private static final String MENSAJE_FIN_NULO = "La fecha y hora de fin es obligatoria";
    private static final String MENSAJE_NOMBRE_OBLIGATORIO = "El nombre del solicitante es obligatorio";
    private static final String MENSAJE_CORREO_OBLIGATORIO = "El correo del solicitante es obligatorio";
    private static final String MENSAJE_ORDEN_INVALIDO = "La hora de inicio debe ser anterior a la hora de fin";
    private static final String MENSAJE_DIAS_DISTINTOS = "La reserva debe iniciar y terminar el mismo dia";
    private static final String MENSAJE_FUERA_DE_JORNADA = "El laboratorio solo atiende entre las %s y las %s";
    private static final String MENSAJE_DURACION_MINIMA = "La reserva debe durar al menos %d minutos";
    private static final String MENSAJE_DURACION_MAXIMA = "La reserva no puede superar las %d horas";
    private static final String MENSAJE_SOLAPAMIENTO =
            "El laboratorio %s ya tiene una reserva activa que se cruza con el horario solicitado";
    private static final String MENSAJE_YA_CANCELADA = "La reserva %s ya fue cancelada";

    private final ReservaRepository reservaRepository;
    private final LaboratorioRepository laboratorioRepository;

    @Transactional(readOnly = true)
    public List<Reserva> findAll() {
        return reservaRepository.findAllByOrderByInicioAsc();
    }

    @Transactional(readOnly = true)
    public List<Reserva> findByLaboratorio(Long laboratorioId) {
        exigirLaboratorioExistente(laboratorioId);
        return reservaRepository.findByLaboratorioIdOrderByInicioAsc(laboratorioId);
    }

    @Transactional(readOnly = true)
    public Reserva findById(Long id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de(RECURSO_RESERVA, id));
    }

    /**
     * Registra una reserva activa tras validar, en orden, los datos obligatorios,
     * la existencia del laboratorio, el rango temporal, la jornada, la duracion
     * y la ausencia de solapamientos.
     */
    public Reserva crear(NuevaReservaCommand comando) {
        Objects.requireNonNull(comando, MENSAJE_COMANDO_NULO);
        validarDatosObligatorios(comando);
        String nombreSolicitante = exigirTexto(comando.nombreSolicitante(), MENSAJE_NOMBRE_OBLIGATORIO);
        String correoSolicitante = exigirTexto(comando.correoSolicitante(), MENSAJE_CORREO_OBLIGATORIO);
        Laboratorio laboratorio = buscarLaboratorio(comando.laboratorioId());
        validarRangoTemporal(comando.inicio(), comando.fin());
        validarJornada(comando.inicio(), comando.fin());
        validarDuracion(comando.inicio(), comando.fin());
        validarDisponibilidad(laboratorio, comando.inicio(), comando.fin());
        return reservaRepository.save(construirReserva(comando, laboratorio, nombreSolicitante, correoSolicitante));
    }

    /**
     * Cancela una reserva activa; la entidad gestionada se sincroniza por dirty checking.
     */
    public Reserva cancelar(Long id) {
        Reserva reserva = findById(id);
        if (reserva.estaCancelada()) {
            throw new ReservaConflictException(MENSAJE_YA_CANCELADA.formatted(id));
        }
        reserva.cancelar();
        return reserva;
    }

    private void validarDatosObligatorios(NuevaReservaCommand comando) {
        exigirNoNulo(comando.laboratorioId(), MENSAJE_LABORATORIO_NULO);
        exigirNoNulo(comando.inicio(), MENSAJE_INICIO_NULO);
        exigirNoNulo(comando.fin(), MENSAJE_FIN_NULO);
    }

    private void exigirNoNulo(Object valor, String mensaje) {
        if (valor == null) {
            throw new IllegalArgumentException(mensaje);
        }
    }

    private String exigirTexto(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.trim();
    }

    private Laboratorio buscarLaboratorio(Long laboratorioId) {
        return laboratorioRepository.findById(laboratorioId)
                .orElseThrow(() -> RecursoNoEncontradoException.de(RECURSO_LABORATORIO, laboratorioId));
    }

    private void exigirLaboratorioExistente(Long laboratorioId) {
        if (!laboratorioRepository.existsById(laboratorioId)) {
            throw RecursoNoEncontradoException.de(RECURSO_LABORATORIO, laboratorioId);
        }
    }

    private void validarRangoTemporal(LocalDateTime inicio, LocalDateTime fin) {
        if (!inicio.isBefore(fin)) {
            throw new IllegalArgumentException(MENSAJE_ORDEN_INVALIDO);
        }
        if (!inicio.toLocalDate().equals(fin.toLocalDate())) {
            throw new IllegalArgumentException(MENSAJE_DIAS_DISTINTOS);
        }
    }

    private void validarJornada(LocalDateTime inicio, LocalDateTime fin) {
        if (inicio.toLocalTime().isBefore(HORA_APERTURA) || fin.toLocalTime().isAfter(HORA_CIERRE)) {
            throw new IllegalArgumentException(MENSAJE_FUERA_DE_JORNADA.formatted(
                    FORMATO_HORA.format(HORA_APERTURA), FORMATO_HORA.format(HORA_CIERRE)));
        }
    }

    private void validarDuracion(LocalDateTime inicio, LocalDateTime fin) {
        Duration duracion = Duration.between(inicio, fin);
        if (duracion.compareTo(DURACION_MINIMA) < 0) {
            throw new IllegalArgumentException(MENSAJE_DURACION_MINIMA.formatted(DURACION_MINIMA.toMinutes()));
        }
        if (duracion.compareTo(DURACION_MAXIMA) > 0) {
            throw new IllegalArgumentException(MENSAJE_DURACION_MAXIMA.formatted(DURACION_MAXIMA.toHours()));
        }
    }

    private void validarDisponibilidad(Laboratorio laboratorio, LocalDateTime inicio, LocalDateTime fin) {
        if (!reservaRepository.buscarSolapamientos(laboratorio.getId(), inicio, fin).isEmpty()) {
            throw new ReservaConflictException(MENSAJE_SOLAPAMIENTO.formatted(laboratorio.getNombre()));
        }
    }

    private Reserva construirReserva(NuevaReservaCommand comando, Laboratorio laboratorio,
                                     String nombreSolicitante, String correoSolicitante) {
        return Reserva.builder()
                .laboratorio(laboratorio)
                .nombreSolicitante(nombreSolicitante)
                .correoSolicitante(correoSolicitante)
                .inicio(comando.inicio())
                .fin(comando.fin())
                .estado(EstadoReserva.ACTIVA)
                .build();
    }
}
