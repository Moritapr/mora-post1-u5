package com.universidad.reservaslabs.controller;

import com.universidad.reservaslabs.controller.dto.CrearLaboratorioRequest;
import com.universidad.reservaslabs.controller.dto.LaboratorioResponse;
import com.universidad.reservaslabs.controller.dto.ReservaResponse;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.service.LaboratorioService;
import com.universidad.reservaslabs.service.ReservaService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Punto de entrada REST del catalogo de laboratorios.
 * Expone consultas y alta de laboratorios, y el listado de reservas de uno de ellos,
 * apoyandose siempre en la capa de servicio.
 */
@RestController
@RequestMapping(LaboratorioController.RUTA_BASE)
@RequiredArgsConstructor
public class LaboratorioController {

    static final String RUTA_BASE = "/api/laboratorios";

    private final LaboratorioService laboratorioService;

    private final ReservaService reservaService;

    /**
     * Lista los laboratorios disponibles.
     *
     * @return laboratorios ordenados por nombre
     */
    @GetMapping
    public List<LaboratorioResponse> listar() {
        return laboratorioService.findAll().stream().map(LaboratorioResponse::desde).toList();
    }

    /**
     * Devuelve un laboratorio concreto.
     *
     * @param id identificador del laboratorio
     * @return laboratorio encontrado
     */
    @GetMapping("/{id}")
    public LaboratorioResponse obtener(@PathVariable Long id) {
        return LaboratorioResponse.desde(laboratorioService.findById(id));
    }

    /**
     * Registra un nuevo laboratorio.
     *
     * @param solicitud datos validados del laboratorio
     * @return 201 con el laboratorio creado y la cabecera Location
     */
    @PostMapping
    public ResponseEntity<LaboratorioResponse> crear(@Valid @RequestBody CrearLaboratorioRequest solicitud) {
        Laboratorio guardado = laboratorioService.crear(solicitud.aEntidad());
        LaboratorioResponse creado = LaboratorioResponse.desde(guardado);
        return ResponseEntity.created(URI.create(RUTA_BASE + "/" + creado.id())).body(creado);
    }

    /**
     * Lista las reservas asociadas a un laboratorio.
     *
     * @param id identificador del laboratorio
     * @return reservas ordenadas por fecha de inicio
     */
    @GetMapping("/{id}/reservas")
    public List<ReservaResponse> reservasDelLaboratorio(@PathVariable Long id) {
        return reservaService.findByLaboratorio(id).stream().map(ReservaResponse::desde).toList();
    }
}
