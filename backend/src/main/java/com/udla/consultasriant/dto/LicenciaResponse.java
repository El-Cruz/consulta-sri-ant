package com.udla.consultasriant.dto;

import java.util.List;

public record LicenciaResponse(
        String cedula,
        String placaConsultada,
        String nombreConductor,
        Integer puntos,
        Integer puntosMaximos,
        int totalCitaciones,
        List<String> detalleCitaciones,
        String fuenteDatos,
        String proveedorCache,
        String cacheKey,
        boolean desdeCache,
        boolean almacenadoEnCache,
        String estadoAnt,
        String obtenidoEn,
        String mensajeDetalle
) {
}
