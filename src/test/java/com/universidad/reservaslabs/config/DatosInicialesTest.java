package com.universidad.reservaslabs.config;

import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.TipoLaboratorio;
import com.universidad.reservaslabs.repository.LaboratorioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias del sembrado inicial con el repositorio simulado: verifican
 * el catalogo exacto que se guarda sobre una base vacia y que el runner es
 * reejecutable sin duplicar laboratorios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Siembra de datos iniciales")
class DatosInicialesTest {

    private static final long BASE_VACIA = 0L;
    private static final long UN_LABORATORIO = 1L;
    private static final long CATALOGO_COMPLETO = 4L;

    @Mock
    private LaboratorioRepository laboratorioRepository;

    @InjectMocks
    private DatosIniciales datosIniciales;

    @Captor
    private ArgumentCaptor<List<Laboratorio>> catalogoCaptor;

    @Nested
    @DisplayName("Sobre una base vacia")
    class BaseVacia {

        @Test
        @DisplayName("Guarda los cuatro laboratorios del catalogo con su tipo")
        void siembraElCatalogoCompleto() {
            when(laboratorioRepository.count()).thenReturn(BASE_VACIA);

            datosIniciales.run();

            verify(laboratorioRepository).saveAll(catalogoCaptor.capture());
            assertThat(catalogoCaptor.getValue())
                    .extracting(Laboratorio::getNombre, Laboratorio::getTipo)
                    .containsExactly(
                            tuple("Lab Computo 1", TipoLaboratorio.COMPUTO),
                            tuple("Lab Computo 2", TipoLaboratorio.COMPUTO),
                            tuple("Lab Fisica", TipoLaboratorio.FISICA),
                            tuple("Lab Quimica", TipoLaboratorio.QUIMICA));
        }

        @Test
        @DisplayName("Guarda cada laboratorio con ubicacion y capacidad")
        void siembraLosDatosDeCadaLaboratorio() {
            when(laboratorioRepository.count()).thenReturn(BASE_VACIA);

            datosIniciales.run();

            verify(laboratorioRepository).saveAll(catalogoCaptor.capture());
            assertThat(catalogoCaptor.getValue())
                    .extracting(Laboratorio::getUbicacion, Laboratorio::getCapacidad)
                    .containsExactly(
                            tuple("Bloque A - Piso 2", 30),
                            tuple("Bloque A - Piso 3", 25),
                            tuple("Bloque B - Piso 1", 20),
                            tuple("Bloque C - Piso 1", 18));
        }
    }

    @Nested
    @DisplayName("Sobre una base con datos")
    class BaseConDatos {

        @Test
        @DisplayName("No siembra nada cuando ya existe un laboratorio")
        void unLaboratorioExistenteEvitaLaSiembra() {
            when(laboratorioRepository.count()).thenReturn(UN_LABORATORIO);

            datosIniciales.run();

            verify(laboratorioRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("No siembra nada cuando el catalogo ya esta completo")
        void catalogoCompletoEvitaLaSiembra() {
            when(laboratorioRepository.count()).thenReturn(CATALOGO_COMPLETO);

            datosIniciales.run();

            verify(laboratorioRepository, never()).saveAll(any());
        }
    }
}
