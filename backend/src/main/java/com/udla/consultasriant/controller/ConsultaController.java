package com.udla.consultasriant.controller;

import com.udla.consultasriant.cache.CacheStore;
import com.udla.consultasriant.client.SriContribuyenteClient;
import com.udla.consultasriant.client.SriVehiculoClient;
import com.udla.consultasriant.dto.CacheSeedRequest;
import com.udla.consultasriant.dto.ContribuyenteResponse;
import com.udla.consultasriant.dto.LicenciaResponse;
import com.udla.consultasriant.dto.VehiculoResponse;
import com.udla.consultasriant.service.AntLicenciaCacheService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Componente C4: Controlador REST principal.
 * Orquesta las consultas hacia Cliente SRI Contribuyente, Cliente SRI Vehículo y Servicio ANT + Caché Cloud.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ConsultaController {

    private final SriContribuyenteClient contribuyenteClient;
    private final SriVehiculoClient vehiculoClient;
    private final AntLicenciaCacheService antService;
    private final CacheStore cacheStore;

    public ConsultaController(
            SriContribuyenteClient contribuyenteClient,
            SriVehiculoClient vehiculoClient,
            AntLicenciaCacheService antService,
            CacheStore cacheStore
    ) {
        this.contribuyenteClient = contribuyenteClient;
        this.vehiculoClient = vehiculoClient;
        this.antService = antService;
        this.cacheStore = cacheStore;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "cacheProvider", cacheStore.getProviderName(),
                "upstashCloudConnected", cacheStore.isCloudConnected()
        );
    }

    @GetMapping("/consulta/contribuyente")
    public ContribuyenteResponse consultarContribuyente(
            @RequestParam String email,
            @RequestParam String ruc
    ) {
        return contribuyenteClient.consultarPersonaNatural(email, ruc);
    }

    @GetMapping("/consulta/vehiculo")
    public VehiculoResponse consultarVehiculo(@RequestParam String placa) {
        return vehiculoClient.consultarVehiculo(placa);
    }

    @GetMapping("/consulta/licencia")
    public LicenciaResponse consultarLicencia(
            @RequestParam String cedula,
            @RequestParam(required = false, defaultValue = "") String placa,
            @RequestParam(required = false, defaultValue = "false") boolean forceRefresh,
            @RequestParam(required = false, defaultValue = "false") boolean simularFalloAnt
    ) {
        return antService.consultarPuntosLicencia(cedula, placa, forceRefresh, simularFalloAnt);
    }

    @PostMapping("/consulta/licencia/cache")
    public LicenciaResponse registrarRespuestaValidaEnCache(@RequestBody CacheSeedRequest request) {
        return antService.guardarRespuestaValidaEnCache(request);
    }
}
