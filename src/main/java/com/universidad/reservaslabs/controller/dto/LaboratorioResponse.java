package com.universidad.reservaslabs.controller.dto;

import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.TipoLaboratorio;

/**
 * Representacion JSON de un laboratorio.
 * Copia los datos de la entidad para que la respuesta no dependa del ciclo de vida de JPA.
 */
public record LaboratorioResponse(Long id,
                                  String nombre,
                                  String ubicacion,
                                  int capacidad,
                                  TipoLaboratorio tipo) {

    /**
     * Proyecta un laboratorio de dominio en la respuesta de la API.
     *
     * @param laboratorio laboratorio recuperado por el servicio
     * @return respuesta lista para serializar
     */
    public static LaboratorioResponse desde(Laboratorio laboratorio) {
        return new LaboratorioResponse(
                laboratorio.getId(),
                laboratorio.getNombre(),
                laboratorio.getUbicacion(),
                laboratorio.getCapacidad(),
                laboratorio.getTipo());
    }
}
