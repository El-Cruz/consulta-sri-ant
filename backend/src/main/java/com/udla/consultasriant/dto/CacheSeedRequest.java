package com.udla.consultasriant.dto;

public record CacheSeedRequest(
        String cedula,
        String placa,
        String nombreConductor,
        Integer puntos,
        Integer totalCitaciones
) {
}
