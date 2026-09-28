package com.universidad.reservaslabs.exception;

import java.io.Serial;

/**
 * Se lanza cuando un recurso solicitado no existe en la base de datos.
 * La capa REST la traduce a HTTP 404 y la capa MVC a un mensaje flash.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String FORMATO_MENSAJE = "No existe %s con id %s";

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    /**
     * Construye la excepcion con el mensaje estandar del recurso y su identificador.
     */
    public static RecursoNoEncontradoException de(String recurso, Long id) {
        return new RecursoNoEncontradoException(FORMATO_MENSAJE.formatted(recurso, id));
    }
}
