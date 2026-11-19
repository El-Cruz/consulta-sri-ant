package com.udla.consultasriant;

import com.udla.consultasriant.cache.CacheStore;
import com.udla.consultasriant.client.SriContribuyenteClient;
import com.udla.consultasriant.client.SriVehiculoClient;
import com.udla.consultasriant.controller.ConsultaController;
import com.udla.consultasriant.dto.ContribuyenteResponse;
import com.udla.consultasriant.dto.LicenciaResponse;
import com.udla.consultasriant.dto.VehiculoResponse;
import com.udla.consultasriant.exception.ApiException;
import com.udla.consultasriant.service.AntLicenciaCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsultaSriAntTests {

    private static class InMemoryTestCache implements CacheStore {
        private final Map<String, String> store = new HashMap<>();

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(store.get(key));
        }

        @Override
        public void set(String key, String value, long ttlSeconds) {
            store.put(key, value);
        }

        @Override
        public boolean isCloudConnected() {
            return true;
        }

        @Override
        public String getProviderName() {
            return "Upstash Redis Test Double";
        }
    }

    @Test
    void aceptaPersonaNaturalYRechazaSociedad() {
        SriContribuyenteClient client = new SriContribuyenteClient("", "", 5);

        assertEquals("1710034065001", client.normalizarRuc("1710034065"));
        assertEquals("1710034065001", client.normalizarRuc("1710034065001"));

        String personaNaturalJson = """
                [{
                  "numeroRuc": "1710034065001",
                  "razonSocial": "PEREZ GOMEZ JUAN CARLOS",
                  "estadoContribuyenteRuc": "ACTIVO",
                  "actividadEconomicaPrincipal": "ACTIVIDADES DE PROGRAMACION INFORMATICA",
                  "tipoContribuyente": "PERSONA NATURAL",
                  "regimen": "RIMPE",
                  "obligadoLlevarContabilidad": "NO",
                  "agenteRetencion": "NO",
                  "contribuyenteEspecial": "NO",
                  "informacionFechasContribuyente": {
                    "fechaInicioActividades": "2020-01-15 00:00:00.0",
                    "fechaActualizacion": "2026-05-01 10:00:00.0"
                  }
                }]
                """;

        ContribuyenteResponse res = client.parseContribuyenteJson(
                "juan@udla.edu.ec", "1710034065001", "1710034065", personaNaturalJson
        );
        assertTrue(res.esPersonaNatural());
        assertEquals("PEREZ GOMEZ JUAN CARLOS", res.razonSocial());
        assertEquals("juan@udla.edu.ec", res.email());

        String sociedadJson = """
                [{
                  "numeroRuc": "1790016919001",
                  "razonSocial": "CORPORACION FAVORITA C.A.",
                  "tipoContribuyente": "SOCIEDAD"
                }]
                """;

        ApiException ex = assertThrows(ApiException.class, () ->
                client.parseContribuyenteJson("a@udla.edu.ec", "1790016919001", "1790016919", sociedadJson));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("NO_ES_PERSONA_NATURAL", ex.getCode());
    }

    @Test
    void parseaVehiculoSriCorrectamente() {
        SriVehiculoClient client = new SriVehiculoClient("", 5);
        String json = """
                {
                  "codigoVehiculo": 1411625,
                  "numeroPlaca": "PBA1234",
                  "numeroCamvCpn": "U00352181",
                  "descripcionMarca": "FORD",
                  "descripcionModelo": "EXPLORER EDDIE BAUER 4X4",
                  "anioAuto": 2006,
                  "descripcionPais": "ESTADOS UNIDOS",
                  "ultimoAnioPagado": 2026,
                  "estadoExoneracion": "NO"
                }
                """;

        VehiculoResponse v = client.parseVehiculoJson("PBA1234", json);
        assertEquals("PBA1234", v.numeroPlaca());
        assertEquals("FORD", v.descripcionMarca());
        assertEquals(2006, v.anioAuto());
        assertEquals(2026, v.ultimoAnioPagado());
    }

    @Test
    void patronCacheAsideYFallbackCuandoAntCae() {
        InMemoryTestCache cache = new InMemoryTestCache();
        AtomicBoolean antDisponible = new AtomicBoolean(true);
        AtomicInteger llamadasAnt = new AtomicInteger(0);

        AntLicenciaCacheService service = new AntLicenciaCacheService(cache, "https://ant.test", 5, 3600, 604800) {
            @Override
            protected String fetchAntHtml(String cedula, String placa) {
                llamadasAnt.incrementAndGet();
                if (!antDisponible.get()) {
                    throw new IllegalStateException("HTTP 502 Bad Gateway desde portal ANT");
                }
                return """
                        <html><body>
                          <table>
                            <tr><td>Conductor:</td><td>CRUZ NICOLAS</td></tr>
                            <tr><td>Saldo de Puntos:</td><td>28</td></tr>
                            <tr><td>Citaciones:</td><td>1</td></tr>
                          </table>
                        </body></html>
                        """;
            }
        };

        // 1. Primera llamada: ANT responde bien y guarda en caché
        LicenciaResponse primera = service.consultarPuntosLicencia("1710034065", "PBA1234", false, false);
        assertEquals("ANT_ONLINE", primera.fuenteDatos());
        assertEquals(28, primera.puntos());
        assertFalse(primera.desdeCache());
        assertTrue(primera.almacenadoEnCache());
        assertEquals(1, llamadasAnt.get());

        // 2. Segunda llamada: devuelve desde caché sin llamar a la ANT
        LicenciaResponse segunda = service.consultarPuntosLicencia("1710034065", "PBA1234", false, false);
        assertEquals("CACHE_CLOUD", segunda.fuenteDatos());
        assertTrue(segunda.desdeCache());
        assertEquals(28, segunda.puntos());
        assertEquals(1, llamadasAnt.get());

        // 3. Tercera llamada con forceRefresh cuando ANT está caída -> Fallback a caché
        antDisponible.set(false);
        LicenciaResponse fallback = service.consultarPuntosLicencia("1710034065", "PBA1234", true, false);
        assertEquals("CACHE_CLOUD_FALLBACK", fallback.fuenteDatos());
        assertTrue(fallback.desdeCache());
        assertEquals(28, fallback.puntos());
        assertEquals(2, llamadasAnt.get());

        // 4. Cédula sin caché previa cuando ANT está caída -> genera respaldo válido, guarda en caché y reusa en siguiente llamada
        int llamadasAntes = llamadasAnt.get();
        LicenciaResponse respaldoInicial = service.consultarPuntosLicencia("1799999999", "", "ANA PEREZ", false, false);
        assertEquals("CACHE_CLOUD_FALLBACK", respaldoInicial.fuenteDatos());
        assertEquals(30, respaldoInicial.puntos());
        assertEquals("ANA PEREZ", respaldoInicial.nombreConductor());
        assertTrue(respaldoInicial.almacenadoEnCache());
        assertEquals(llamadasAntes + 1, llamadasAnt.get());

        LicenciaResponse desdeCacheLuego = service.consultarPuntosLicencia("1799999999", "", false, false);
        assertEquals("CACHE_CLOUD", desdeCacheLuego.fuenteDatos());
        assertTrue(desdeCacheLuego.desdeCache());
        assertEquals(30, desdeCacheLuego.puntos());
        assertEquals(llamadasAntes + 1, llamadasAnt.get());
    }

    @Test
    void controladorPermiteRegistrarYConsultarDesdeCache() throws Exception {
        InMemoryTestCache cache = new InMemoryTestCache();
        AntLicenciaCacheService antService = new AntLicenciaCacheService(cache, "https://ant.test", 5, 3600, 604800) {
            @Override
            protected String fetchAntHtml(String cedula, String placa) {
                throw new IllegalStateException("HTTP 502");
            }
        };
        ConsultaController controller = new ConsultaController(
                new SriContribuyenteClient("", "", 5),
                new SriVehiculoClient("", 5),
                antService,
                cache
        );
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mvc.perform(post("/api/consulta/licencia/cache")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cedula\":\"1710034065\",\"placa\":\"PBA1234\",\"nombreConductor\":\"NICOLAS CRUZ\",\"puntos\":30,\"totalCitaciones\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntos").value(30));

        mvc.perform(get("/api/consulta/licencia")
                        .param("cedula", "1710034065")
                        .param("simularFalloAnt", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuenteDatos").value("CACHE_CLOUD_FALLBACK"))
                .andExpect(jsonPath("$.desdeCache").value(true))
                .andExpect(jsonPath("$.puntos").value(30));
    }
}
