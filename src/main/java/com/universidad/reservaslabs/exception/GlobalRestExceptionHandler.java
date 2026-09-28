package com.universidad.reservaslabs.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de dominio y de la capa web a respuestas JSON uniformes.
 * Solo se aplica a los controladores REST, de modo que las vistas MVC conservan su propio manejo.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalRestExceptionHandler {

    private static final String MENSAJE_JSON_INVALIDO = "El cuerpo de la peticion no es un JSON valido";

    private static final String MENSAJE_CAMPO_INVALIDO = "El valor indicado no es valido";

    private static final String MENSAJE_INTEGRIDAD =
            "La operacion choca con una restriccion de los datos ya registrados";

    /**
     * Recurso inexistente.
     *
     * @param ex       excepcion capturada
     * @param peticion peticion HTTP en curso
     * @return 404 con el detalle del recurso
     */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarNoEncontrado(RecursoNoEncontradoException ex,
                                                             HttpServletRequest peticion) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), peticion);
    }

    /**
     * Conflictos de estado: solapamientos, cancelaciones repetidas o nombres duplicados.
     *
     * @param ex       excepcion capturada
     * @param peticion peticion HTTP en curso
     * @return 409 con el motivo del conflicto
     */
    @ExceptionHandler({ReservaConflictException.class, RecursoDuplicadoException.class})
    public ResponseEntity<ErrorResponse> manejarConflicto(RuntimeException ex,
                                                          HttpServletRequest peticion) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), peticion);
    }

    /**
     * Reglas de horario, duracion o datos obligatorios incumplidas en el servicio.
     *
     * @param ex       excepcion capturada
     * @param peticion peticion HTTP en curso
     * @return 400 con el motivo del rechazo
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> manejarArgumentoInvalido(IllegalArgumentException ex,
                                                                  HttpServletRequest peticion) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), peticion);
    }

    /**
     * Fallos de las anotaciones de validacion sobre el cuerpo de la peticion.
     *
     * @param ex       excepcion capturada
     * @param peticion peticion HTTP en curso
     * @return 400 con el desglose campo a campo
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex,
                                                           HttpServletRequest peticion) {
        ErrorResponse cuerpo = ErrorResponse.deValidacion(peticion.getRequestURI(), extraerDetalles(ex));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    /**
     * Cuerpo ilegible o JSON mal formado.
     *
     * @param ex       excepcion capturada
     * @param peticion peticion HTTP en curso
     * @return 400 con un mensaje generico
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarCuerpoIlegible(HttpMessageNotReadableException ex,
                                                               HttpServletRequest peticion) {
        return construir(HttpStatus.BAD_REQUEST, MENSAJE_JSON_INVALIDO, peticion);
    }

    /**
     * Restriccion de integridad de la base de datos, por ejemplo dos altas
     * simultaneas del mismo nombre de laboratorio que burlan la comprobacion previa.
     *
     * @param ex       excepcion capturada
     * @param peticion peticion HTTP en curso
     * @return 409 en lugar del 500 que produciria el fallo sin traducir
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> manejarIntegridad(DataIntegrityViolationException ex,
                                                           HttpServletRequest peticion) {
        return construir(HttpStatus.CONFLICT, MENSAJE_INTEGRIDAD, peticion);
    }

    private static Map<String, String> extraerDetalles(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        GlobalRestExceptionHandler::mensajeDe,
                        (primero, siguiente) -> primero));
    }

    private static String mensajeDe(FieldError error) {
        return Objects.requireNonNullElse(error.getDefaultMessage(), MENSAJE_CAMPO_INVALIDO);
    }

    private static ResponseEntity<ErrorResponse> construir(HttpStatus estado, String mensaje,
                                                           HttpServletRequest peticion) {
        return ResponseEntity.status(estado)
                .body(ErrorResponse.de(estado, mensaje, peticion.getRequestURI()));
    }
}
