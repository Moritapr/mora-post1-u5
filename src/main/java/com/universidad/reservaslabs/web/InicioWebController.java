package com.universidad.reservaslabs.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Punto de entrada de la aplicacion web: lleva la raiz al listado de reservas
 * para que el usuario no aterrice en un 404 al abrir el servidor.
 */
@Controller
public class InicioWebController {

    /** Redirige la raiz del sitio al listado de reservas. */
    @GetMapping("/")
    public String inicio() {
        return "redirect:/reservas";
    }
}
