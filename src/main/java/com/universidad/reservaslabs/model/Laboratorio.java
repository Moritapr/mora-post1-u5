package com.universidad.reservaslabs.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Entidad que representa un laboratorio reservable de la universidad.
 * Es inmutable desde fuera: se crea con el builder y solo expone getters.
 */
@Entity
@Table(name = "laboratorios")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Laboratorio {

    private static final int LONGITUD_TEXTO = 120;
    private static final int LONGITUD_TIPO = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = LONGITUD_TEXTO)
    private String nombre;

    @Column(nullable = false, length = LONGITUD_TEXTO)
    private String ubicacion;

    @Column(nullable = false)
    private int capacidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = LONGITUD_TIPO)
    private TipoLaboratorio tipo;
}
