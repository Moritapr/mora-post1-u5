package com.universidad.reservaslabs.exception;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Cuerpo JSON uniforme de los errores de la API REST.
 * El mapa de detalles queda vacio salvo en los fallos de validacion, donde asocia
 * cada campo rechazado con su mensaje.
 */
public record ErrorResponse(LocalDateTime marcaTiempo,
                            int estado,
                            String error,
                            String mensaje,
                            String ruta,
                            Map<String, String> detalles) {

    private static final String MENSAJE_VALIDACION = "La solicitud contiene datos invalidos";

    /**
     * Construye un error simple, sin detalles por campo.
     *
     * @param estado  estado HTTP de la respuesta
     * @param mensaje descripcion del fallo
     * @param ruta    URI de la peticion que fallo
     * @return cuerpo del error
     */
    public static ErrorResponse de(HttpStatus estado, String mensaje, String ruta) {
        return new ErrorResponse(LocalDateTime.now(), estado.value(), estado.getReasonPhrase(),
                mensaje, ruta, Map.of());
    }

    /**
     * Construye un error 400 con el desglose de los campos rechazados.
     *
     * @param ruta     URI de la peticion que fallo
     * @param detalles mapa campo a mensaje de validacion
     * @return cuerpo del error
     */
    public static ErrorResponse deValidacion(String ruta, Map<String, String> detalles) {
        HttpStatus estado = HttpStatus.BAD_REQUEST;
        return new ErrorResponse(LocalDateTime.now(), estado.value(), estado.getReasonPhrase(),
                MENSAJE_VALIDACION, ruta, Map.copyOf(detalles));
    }
}
