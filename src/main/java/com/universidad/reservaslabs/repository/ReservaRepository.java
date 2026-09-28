package com.universidad.reservaslabs.repository;

import com.universidad.reservaslabs.model.Reserva;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Puerto de persistencia de las reservas.
 * Resuelve el laboratorio asociado con @EntityGraph porque la sesion se cierra
 * antes de la vista (spring.jpa.open-in-view=false).
 */
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    @EntityGraph(attributePaths = "laboratorio")
    List<Reserva> findAllByOrderByInicioAsc();

    @EntityGraph(attributePaths = "laboratorio")
    List<Reserva> findByLaboratorioIdOrderByInicioAsc(Long laboratorioId);

    @EntityGraph(attributePaths = "laboratorio")
    @Override
    Optional<Reserva> findById(Long id);

    /**
     * Reservas ACTIVAS del laboratorio que chocan con el rango [inicio, fin).
     * Dos intervalos semiabiertos se solapan si inicioExistente < finNuevo && finExistente > inicioNuevo.
     */
    @Query("""
            SELECT r FROM Reserva r
            WHERE r.laboratorio.id = :laboratorioId
              AND r.estado = com.universidad.reservaslabs.model.EstadoReserva.ACTIVA
              AND r.inicio < :fin
              AND r.fin > :inicio
            """)
    List<Reserva> buscarSolapamientos(@Param("laboratorioId") Long laboratorioId,
                                      @Param("inicio") LocalDateTime inicio,
                                      @Param("fin") LocalDateTime fin);
}
