package com.universidad.reservaslabs.exception;

import java.io.Serial;

/**
 * Se lanza cuando una reserva choca con el estado actual del sistema:
 * solapamiento de horarios o cancelacion repetida. La capa REST la traduce a HTTP 409.
 */
public class ReservaConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReservaConflictException(String mensaje) {
        super(mensaje);
    }
}
