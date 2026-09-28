package com.universidad.reservaslabs.exception;

import java.io.Serial;

/**
 * Se lanza al intentar crear un recurso que rompe una restriccion de unicidad,
 * como un laboratorio con un nombre ya registrado. La capa REST la traduce a HTTP 409.
 */
public class RecursoDuplicadoException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RecursoDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
