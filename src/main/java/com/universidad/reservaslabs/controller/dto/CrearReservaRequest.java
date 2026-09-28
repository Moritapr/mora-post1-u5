package com.universidad.reservaslabs.controller.dto;

import com.universidad.reservaslabs.service.NuevaReservaCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Datos de entrada de la API REST para registrar una reserva.
 * Solo declara validaciones de formato: las reglas de negocio viven en la capa de servicio,
 * a la que se traduce mediante {@link #aComando()}.
 */
public record CrearReservaRequest(

        @NotNull(message = "El laboratorio es obligatorio")
        Long laboratorioId,

        @NotBlank(message = "El nombre del solicitante es obligatorio")
        @Size(max = 120)
        String nombreSolicitante,

        @NotBlank(message = "El correo del solicitante es obligatorio")
        @Email(message = "El correo no tiene un formato valido")
        @Size(max = 160)
        String correoSolicitante,

        @NotNull(message = "La fecha y hora de inicio es obligatoria")
        LocalDateTime inicio,

        @NotNull(message = "La fecha y hora de fin es obligatoria")
        LocalDateTime fin) {

    /**
     * Convierte la solicitud HTTP en el comando que entiende la capa de servicio.
     *
     * @return comando de creacion equivalente
     */
    public NuevaReservaCommand aComando() {
        return new NuevaReservaCommand(laboratorioId, nombreSolicitante, correoSolicitante, inicio, fin);
    }
}
