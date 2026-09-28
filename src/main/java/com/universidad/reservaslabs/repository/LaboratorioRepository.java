package com.universidad.reservaslabs.repository;

import com.universidad.reservaslabs.model.Laboratorio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Puerto de persistencia de los laboratorios.
 * Spring Data genera la implementacion a partir de los nombres de los metodos.
 */
public interface LaboratorioRepository extends JpaRepository<Laboratorio, Long> {

    boolean existsByNombreIgnoreCase(String nombre);

    List<Laboratorio> findAllByOrderByNombreAsc();
}
