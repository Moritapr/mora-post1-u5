package com.universidad.reservaslabs.service;

import com.universidad.reservaslabs.exception.RecursoDuplicadoException;
import com.universidad.reservaslabs.exception.RecursoNoEncontradoException;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.repository.LaboratorioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de los laboratorios: consulta ordenada y alta con nombre unico.
 * Concentra la regla de unicidad para que ninguna capa de entrega la repita.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class LaboratorioService {

    private static final String RECURSO = "laboratorio";
    private static final String MENSAJE_NOMBRE_DUPLICADO = "Ya existe un laboratorio con el nombre %s";

    private final LaboratorioRepository laboratorioRepository;

    @Transactional(readOnly = true)
    public List<Laboratorio> findAll() {
        return laboratorioRepository.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public Laboratorio findById(Long id) {
        return laboratorioRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de(RECURSO, id));
    }

    public Laboratorio crear(Laboratorio laboratorio) {
        validarNombreDisponible(laboratorio.getNombre());
        return laboratorioRepository.save(laboratorio);
    }

    private void validarNombreDisponible(String nombre) {
        if (laboratorioRepository.existsByNombreIgnoreCase(nombre)) {
            throw new RecursoDuplicadoException(MENSAJE_NOMBRE_DUPLICADO.formatted(nombre));
        }
    }
}
