package com.universidad.reservaslabs.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Entidad que representa la reserva de un laboratorio en una franja horaria.
 * Protege su propio invariante de estado: no tiene setters y solo cambia
 * a traves de los metodos de dominio que expone.
 */
@Entity
@Table(name = "reservas")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Reserva {

    private static final int LONGITUD_NOMBRE = 120;
    private static final int LONGITUD_CORREO = 160;
    private static final int LONGITUD_ESTADO = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratorio_id", nullable = false)
    private Laboratorio laboratorio;

    @Column(nullable = false, length = LONGITUD_NOMBRE)
    private String nombreSolicitante;

    @Column(nullable = false, length = LONGITUD_CORREO)
    private String correoSolicitante;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = LONGITUD_ESTADO)
    @Builder.Default
    private EstadoReserva estado = EstadoReserva.ACTIVA;

    /**
     * Indica si la reserva sigue vigente.
     */
    public boolean estaActiva() {
        return estado == EstadoReserva.ACTIVA;
    }

    /**
     * Indica si la reserva ya fue anulada.
     */
    public boolean estaCancelada() {
        return estado == EstadoReserva.CANCELADA;
    }

    /**
     * Anula la reserva. Falla si ya estaba cancelada, para no perder el invariante.
     */
    public void cancelar() {
        if (estaCancelada()) {
            throw new IllegalStateException("La reserva ya se encuentra cancelada");
        }
        estado = EstadoReserva.CANCELADA;
    }
}
