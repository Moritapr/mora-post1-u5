package com.universidad.reservaslabs.web.dto;

import java.time.format.DateTimeFormatter;

import com.universidad.reservaslabs.service.ReservaService;

/**
 * Limites de reserva ya formateados para mostrarlos en el formulario.
 * Se derivan de las constantes de {@link ReservaService}, de modo que la
 * plantilla nunca repita a mano unas reglas que son del negocio.
 */
public record ReglasDeReserva(String horaApertura, String horaCierre,
                              long duracionMinimaMinutos, long duracionMaximaHoras) {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Proyecta los limites que aplica hoy la capa de servicio. */
    public static ReglasDeReserva vigentes() {
        return new ReglasDeReserva(
                FORMATO_HORA.format(ReservaService.HORA_APERTURA),
                FORMATO_HORA.format(ReservaService.HORA_CIERRE),
                ReservaService.DURACION_MINIMA.toMinutes(),
                ReservaService.DURACION_MAXIMA.toHours());
    }
}
