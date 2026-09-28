package com.universidad.reservaslabs.controller.dto;

import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.TipoLaboratorio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos de entrada de la API REST para dar de alta un laboratorio.
 * Traduce la solicitud a la entidad de dominio mediante {@link #aEntidad()}.
 */
public record CrearLaboratorioRequest(

        @NotBlank(message = "El nombre del laboratorio es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
        String nombre,

        @NotBlank(message = "La ubicacion es obligatoria")
        @Size(max = 120, message = "La ubicacion no puede superar los 120 caracteres")
        String ubicacion,

        @Positive(message = "La capacidad debe ser mayor a cero")
        int capacidad,

        @NotNull(message = "El tipo de laboratorio es obligatorio")
        TipoLaboratorio tipo) {

    /**
     * Construye la entidad que persistira la capa de servicio. Normaliza los
     * textos para que los espacios sobrantes no burlen la unicidad del nombre.
     *
     * @return laboratorio sin identificador, listo para guardarse
     */
    public Laboratorio aEntidad() {
        return Laboratorio.builder()
                .nombre(nombre.trim())
                .ubicacion(ubicacion.trim())
                .capacidad(capacidad)
                .tipo(tipo)
                .build();
    }
}
