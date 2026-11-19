package com.udla.consultasriant.dto;

public record ContribuyenteResponse(
        String email,
        String numeroRuc,
        String cedula,
        boolean existeContribuyente,
        boolean esPersonaNatural,
        String razonSocial,
        String estadoContribuyenteRuc,
        String actividadEconomicaPrincipal,
        String tipoContribuyente,
        String regimen,
        String obligadoLlevarContabilidad,
        String agenteRetencion,
        String contribuyenteEspecial,
        String fechaInicioActividades,
        String fechaActualizacion
) {
}
