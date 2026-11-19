package com.udla.consultasriant.dto;

public record VehiculoResponse(
        Long codigoVehiculo,
        String numeroPlaca,
        String numeroCamvCpn,
        String descripcionMarca,
        String descripcionModelo,
        Integer anioAuto,
        String descripcionPais,
        Integer ultimoAnioPagado,
        String fechaUltimaMatricula,
        String fechaCaducidadMatricula,
        String descripcionCanton,
        String descripcionServicio,
        String nombreClase,
        String cilindraje,
        String colorVehiculo1,
        String prohibidoEnajenar,
        String estadoExoneracion
) {
}
