package com.universidad.reservaslabs.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.universidad.reservaslabs.model.Reserva;
import com.universidad.reservaslabs.service.LaboratorioService;
import com.universidad.reservaslabs.service.ReservaService;
import com.universidad.reservaslabs.web.dto.ReglasDeReserva;
import com.universidad.reservaslabs.web.dto.ReservaFormulario;
import com.universidad.reservaslabs.web.dto.ReservaVista;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Capa de entrega MVC de las reservas: resuelve vistas Thymeleaf sobre la
 * misma capa de servicio que consume la API REST. No contiene reglas de
 * negocio, solo traduce entre formulario, comando y modelo de vista.
 */
@Controller
@RequestMapping("/reservas")
@RequiredArgsConstructor
public class ReservaWebController {

    private static final String VISTA_LISTA = "reservas/lista";
    private static final String VISTA_FORMULARIO = "reservas/nueva";
    private static final String REDIRECCION_LISTA = "redirect:/reservas";
    private static final String ATRIBUTO_RESERVAS = "reservas";
    private static final String ATRIBUTO_LABORATORIOS = "laboratorios";
    private static final String ATRIBUTO_FORMULARIO = "reservaFormulario";
    private static final String ATRIBUTO_MENSAJE_EXITO = "mensajeExito";
    private static final String ATRIBUTO_REGLAS = "reglasDeReserva";

    private final ReservaService reservaService;
    private final LaboratorioService laboratorioService;

    /** Muestra todas las reservas ordenadas por fecha de inicio. */
    @GetMapping
    public String listar(Model model) {
        model.addAttribute(ATRIBUTO_RESERVAS, aVistas(reservaService.findAll()));
        return VISTA_LISTA;
    }

    /** Prepara el formulario vacio junto con los laboratorios disponibles. */
    @GetMapping("/nueva")
    public String mostrarFormulario(Model model) {
        model.addAttribute(ATRIBUTO_FORMULARIO, new ReservaFormulario());
        cargarLaboratorios(model);
        return VISTA_FORMULARIO;
    }

    /** Registra la reserva o devuelve el formulario con los errores de validacion. */
    @PostMapping
    public String crear(@Valid @ModelAttribute(ATRIBUTO_FORMULARIO) ReservaFormulario formulario,
                        BindingResult resultado, Model model, RedirectAttributes atributos) {
        if (resultado.hasErrors()) {
            cargarLaboratorios(model);
            return VISTA_FORMULARIO;
        }
        Reserva reserva = reservaService.crear(formulario.aComando());
        atributos.addFlashAttribute(ATRIBUTO_MENSAJE_EXITO,
                "Reserva creada para " + reserva.getNombreSolicitante());
        return REDIRECCION_LISTA;
    }

    /** Cancela una reserva activa y vuelve al listado con el aviso de exito. */
    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable Long id, RedirectAttributes atributos) {
        reservaService.cancelar(id);
        atributos.addFlashAttribute(ATRIBUTO_MENSAJE_EXITO, "Reserva " + id + " cancelada");
        return REDIRECCION_LISTA;
    }

    private void cargarLaboratorios(Model model) {
        model.addAttribute(ATRIBUTO_LABORATORIOS, laboratorioService.findAll());
        model.addAttribute(ATRIBUTO_REGLAS, ReglasDeReserva.vigentes());
    }

    private List<ReservaVista> aVistas(List<Reserva> reservas) {
        return reservas.stream().map(ReservaVista::desde).toList();
    }
}
