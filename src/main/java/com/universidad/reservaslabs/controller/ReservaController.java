package com.universidad.reservaslabs.controller;

import com.universidad.reservaslabs.controller.dto.CrearReservaRequest;
import com.universidad.reservaslabs.controller.dto.ReservaResponse;
import com.universidad.reservaslabs.model.Reserva;
import com.universidad.reservaslabs.service.ReservaService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Punto de entrada REST de las reservas.
 * Solo traduce DTOs a comandos y entidades a respuestas: toda regla de negocio
 * queda delegada en {@link ReservaService}.
 */
@RestController
@RequestMapping(ReservaController.RUTA_BASE)
@RequiredArgsConstructor
public class ReservaController {

    static final String RUTA_BASE = "/api/reservas";

    private final ReservaService reservaService;

    /**
     * Lista las reservas, opcionalmente filtradas por laboratorio.
     *
     * @param laboratorioId identificador del laboratorio, o null para listar todas
     * @return reservas ordenadas por fecha de inicio
     */
    @GetMapping
    public List<ReservaResponse> listar(@RequestParam(required = false) Long laboratorioId) {
        List<Reserva> reservas = laboratorioId == null
                ? reservaService.findAll()
                : reservaService.findByLaboratorio(laboratorioId);
        return reservas.stream().map(ReservaResponse::desde).toList();
    }

    /**
     * Devuelve una reserva concreta.
     *
     * @param id identificador de la reserva
     * @return reserva encontrada
     */
    @GetMapping("/{id}")
    public ReservaResponse obtener(@PathVariable Long id) {
        return ReservaResponse.desde(reservaService.findById(id));
    }

    /**
     * Registra una nueva reserva.
     *
     * @param solicitud datos validados de la reserva
     * @return 201 con la reserva creada y la cabecera Location
     */
    @PostMapping
    public ResponseEntity<ReservaResponse> crear(@Valid @RequestBody CrearReservaRequest solicitud) {
        ReservaResponse creada = ReservaResponse.desde(reservaService.crear(solicitud.aComando()));
        return ResponseEntity.created(URI.create(RUTA_BASE + "/" + creada.id())).body(creada);
    }

    /**
     * Cancela una reserva activa.
     *
     * @param id identificador de la reserva
     * @return reserva ya cancelada
     */
    @PatchMapping("/{id}/cancelar")
    public ReservaResponse cancelar(@PathVariable Long id) {
        return ReservaResponse.desde(reservaService.cancelar(id));
    }
}
