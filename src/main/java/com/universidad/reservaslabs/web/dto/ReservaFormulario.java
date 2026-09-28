package com.universidad.reservaslabs.web.dto;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.universidad.reservaslabs.service.NuevaReservaCommand;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Objeto de enlace del formulario de alta de reservas de la capa MVC.
 * Es mutable a proposito: th:field necesita getters y setters para leer
 * y repoblar cada campo tras un error de validacion.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReservaFormulario {

    @NotNull(message = "El laboratorio es obligatorio")
    private Long laboratorioId;

    @NotBlank(message = "El nombre del solicitante es obligatorio")
    @Size(max = 120)
    private String nombreSolicitante;

    @NotBlank(message = "El correo del solicitante es obligatorio")
    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 160)
    private String correoSolicitante;

    @NotNull(message = "La fecha y hora de inicio es obligatoria")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime inicio;

    @NotNull(message = "La fecha y hora de fin es obligatoria")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime fin;

    /**
     * Traduce el formulario al unico contrato de entrada del servicio,
     * de modo que la capa de negocio no conozca la capa de entrega.
     */
    public NuevaReservaCommand aComando() {
        return new NuevaReservaCommand(laboratorioId, nombreSolicitante, correoSolicitante, inicio, fin);
    }
}
