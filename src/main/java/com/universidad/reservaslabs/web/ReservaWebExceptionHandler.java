package com.universidad.reservaslabs.web;

import java.util.Arrays;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.universidad.reservaslabs.exception.RecursoNoEncontradoException;
import com.universidad.reservaslabs.exception.ReservaConflictException;
import com.universidad.reservaslabs.web.dto.ReservaFormulario;

/**
 * Traduce los errores de negocio de la capa MVC a mensajes flash y
 * redirecciones, para que el usuario nunca vea una pagina de error tecnica.
 * Solo atiende al controlador web; la API REST tiene su propio manejador.
 */
@ControllerAdvice(assignableTypes = ReservaWebController.class)
public class ReservaWebExceptionHandler {

    private static final String ATRIBUTO_MENSAJE_ERROR = "mensajeError";
    private static final String REDIRECCION_FORMULARIO = "redirect:/reservas/nueva";
    private static final String REDIRECCION_LISTA = "redirect:/reservas";

    /** Choque de horarios o intento de cancelar una reserva ya cancelada. */
    @ExceptionHandler(ReservaConflictException.class)
    public String manejar(ReservaConflictException ex, HandlerMethod origen,
                          RedirectAttributes atributos) {
        return redirigirCon(ex.getMessage(), destinoDe(origen), atributos);
    }

    /** Regla de horario, duracion o dato obligatorio incumplida. */
    @ExceptionHandler(IllegalArgumentException.class)
    public String manejar(IllegalArgumentException ex, HandlerMethod origen,
                          RedirectAttributes atributos) {
        return redirigirCon(ex.getMessage(), destinoDe(origen), atributos);
    }

    /** Recurso inexistente: no hay formulario que corregir, se vuelve al listado. */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public String manejar(RecursoNoEncontradoException ex, RedirectAttributes atributos) {
        return redirigirCon(ex.getMessage(), REDIRECCION_LISTA, atributos);
    }

    /**
     * Devuelve al formulario solo si la peticion que fallo era el alta de una
     * reserva; cualquier otra accion, como cancelar, vuelve al listado.
     */
    private String destinoDe(HandlerMethod origen) {
        return recibiaFormulario(origen) ? REDIRECCION_FORMULARIO : REDIRECCION_LISTA;
    }

    private boolean recibiaFormulario(HandlerMethod origen) {
        return origen != null && Arrays.stream(origen.getMethodParameters())
                .anyMatch(parametro -> ReservaFormulario.class.equals(parametro.getParameterType()));
    }

    private String redirigirCon(String mensaje, String destino, RedirectAttributes atributos) {
        atributos.addFlashAttribute(ATRIBUTO_MENSAJE_ERROR, mensaje);
        return destino;
    }
}
