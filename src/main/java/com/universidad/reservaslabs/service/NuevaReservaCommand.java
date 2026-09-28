package com.universidad.reservaslabs.service;

import java.time.LocalDateTime;

/**
 * Comando de entrada para crear una reserva.
 * Es el unico contrato que aceptan las capas de entrega (REST y MVC),
 * de modo que el servicio no depende de ningun DTO de transporte.
 */
public record NuevaReservaCommand(Long laboratorioId,
                                  String nombreSolicitante,
                                  String correoSolicitante,
                                  LocalDateTime inicio,
                                  LocalDateTime fin) {
}
