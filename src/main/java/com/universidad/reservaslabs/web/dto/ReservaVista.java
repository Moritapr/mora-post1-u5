package com.universidad.reservaslabs.web.dto;

import java.time.format.DateTimeFormatter;

import com.universidad.reservaslabs.model.Reserva;

/**
 * Modelo de vista de una reserva con los datos ya planos y formateados.
 * Libera a la plantilla de usar #temporals y de navegar asociaciones LAZY
 * fuera de la transaccion.
 */
public record ReservaVista(Long id, String laboratorio, String solicitante, String correo,
                           String inicio, String fin, String estado, boolean cancelable) {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Proyecta una reserva ya cargada con su laboratorio a datos de presentacion. */
    public static ReservaVista desde(Reserva reserva) {
        return new ReservaVista(
                reserva.getId(),
                reserva.getLaboratorio().getNombre(),
                reserva.getNombreSolicitante(),
                reserva.getCorreoSolicitante(),
                FORMATO_FECHA.format(reserva.getInicio()),
                FORMATO_FECHA.format(reserva.getFin()),
                reserva.getEstado().name(),
                reserva.estaActiva());
    }
}
