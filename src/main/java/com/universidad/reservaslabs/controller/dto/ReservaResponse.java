package com.universidad.reservaslabs.controller.dto;

import com.universidad.reservaslabs.model.EstadoReserva;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.Reserva;
import java.time.LocalDateTime;

/**
 * Representacion JSON de una reserva.
 * Aplana el laboratorio asociado para que la frontera HTTP nunca exponga entidades gestionadas.
 */
public record ReservaResponse(Long id,
                              Long laboratorioId,
                              String laboratorioNombre,
                              String nombreSolicitante,
                              String correoSolicitante,
                              LocalDateTime inicio,
                              LocalDateTime fin,
                              EstadoReserva estado) {

    /**
     * Proyecta una reserva ya cargada con su laboratorio en la respuesta de la API.
     *
     * @param reserva reserva de dominio recuperada por el servicio
     * @return respuesta lista para serializar
     */
    public static ReservaResponse desde(Reserva reserva) {
        Laboratorio laboratorio = reserva.getLaboratorio();
        return new ReservaResponse(
                reserva.getId(),
                laboratorio.getId(),
                laboratorio.getNombre(),
                reserva.getNombreSolicitante(),
                reserva.getCorreoSolicitante(),
                reserva.getInicio(),
                reserva.getFin(),
                reserva.getEstado());
    }
}
