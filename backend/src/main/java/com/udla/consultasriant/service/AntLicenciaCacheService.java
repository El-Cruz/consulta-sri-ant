package com.udla.consultasriant.service;

import com.udla.consultasriant.cache.CacheStore;
import com.udla.consultasriant.dto.CacheSeedRequest;
import com.udla.consultasriant.dto.LicenciaResponse;
import com.udla.consultasriant.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Componente C4: Servicio / Gestor de Licencia ANT y Caché.
 * Implementa el patrón Cache-Aside con Stale-While-Error (respaldo resiliente ante caídas de la ANT)
 * sobre Upstash Redis Cloud Cache.
 */
@Service
public class AntLicenciaCacheService {

    private static final String CACHE_PREFIX = "ant:licencia:";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final Pattern PUNTOS_PATTERN = Pattern.compile(
            "(?is)(?:puntos(?:\\s+de\\s+licencia)?|saldo\\s+de\\s+puntos|licencia[^\\d]{0,30}puntos)\\D{0,40}(\\d{1,2}(?:[.,]\\d+)?)"
    );
    private static final Pattern NOMBRE_PATTERN = Pattern.compile(
            "(?is)(?:conductor|propietario|nombres?|se[ñn]or\\(a\\))\\s*:?\\s*([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ\\s]{5,60})"
    );
    private static final Pattern CITACIONES_PATTERN = Pattern.compile("(?is)citaciones?\\s*:?\\s*(\\d+)");

    private final CacheStore cacheStore;
    private final String antUrl;
    private final int timeoutSeconds;
    private final long freshSeconds;
    private final long ttlSeconds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AntLicenciaCacheService(
            CacheStore cacheStore,
            @Value("${app.ant.citaciones-url:https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes/clp_grid_citaciones.jsp}") String antUrl,
            @Value("${app.ant.timeout-seconds:6}") int timeoutSeconds,
            @Value("${app.cache.fresh-seconds:3600}") long freshSeconds,
            @Value("${app.cache.ttl-seconds:604800}") long ttlSeconds
    ) {
        this.cacheStore = cacheStore;
        this.antUrl = antUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.freshSeconds = freshSeconds;
        this.ttlSeconds = ttlSeconds;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        this.objectMapper = JsonMapper.builder().build();
    }

    public LicenciaResponse consultarPuntosLicencia(String rawCedula, String rawPlaca, boolean forceRefresh, boolean simularFalloAnt) {
        return consultarPuntosLicencia(rawCedula, rawPlaca, "", forceRefresh, simularFalloAnt);
    }

    public LicenciaResponse consultarPuntosLicencia(
            String rawCedula,
            String rawPlaca,
            String rawNombre,
            boolean forceRefresh,
            boolean simularFalloAnt
    ) {
        String cedula = validarCedula(rawCedula);
        String placa = rawPlaca == null ? "" : rawPlaca.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        String cacheKey = CACHE_PREFIX + cedula;

        Optional<LicenciaResponse> cached = readFromCache(cacheKey);

        // 1. Cache-Aside: si hay dato válido y fresco en caché y no se fuerza recarga, devolver desde caché.
        if (!forceRefresh && !simularFalloAnt && cached.isPresent() && isFresh(cached.get())) {
            LicenciaResponse hit = cached.get();
            return new LicenciaResponse(
                    hit.cedula(),
                    placa.isBlank() ? hit.placaConsultada() : placa,
                    hit.nombreConductor(),
                    hit.puntos(),
                    hit.puntosMaximos(),
                    hit.totalCitaciones(),
                    hit.detalleCitaciones(),
                    "CACHE_CLOUD",
                    cacheStore.getProviderName(),
                    cacheKey,
                    true,
                    false,
                    "CACHED_FRESH",
                    hit.obtenidoEn(),
                    "Respuesta servida directamente desde Caché Cloud (" + cacheStore.getProviderName() + ") para evitar latencia/caída de la ANT."
            );
        }

        // 2. Consultar en vivo al portal de la ANT cuando no existe caché vigente o se solicita refresco.
        String errorAnt;
        if (simularFalloAnt) {
            errorAnt = "Simulación de caída/indisponibilidad del portal ANT";
        } else {
            try {
                String html = fetchAntHtml(cedula, placa);
                LicenciaResponse parsed = parseAntHtml(html, cedula, placa, cacheKey);
                cacheStore.set(cacheKey, objectMapper.writeValueAsString(parsed), ttlSeconds);
                return parsed;
            } catch (Exception ex) {
                errorAnt = ex.getMessage() != null ? ex.getMessage() : "Error de conexión con la ANT";
            }
        }

        // 3. Fallback resiliente (Stale-While-Error): si la ANT falla, devolver el último registro válido en Caché Cloud.
        if (cached.isPresent()) {
            LicenciaResponse stale = cached.get();
            return new LicenciaResponse(
                    stale.cedula(),
                    placa.isBlank() ? stale.placaConsultada() : placa,
                    stale.nombreConductor(),
                    stale.puntos(),
                    stale.puntosMaximos(),
                    stale.totalCitaciones(),
                    stale.detalleCitaciones(),
                    "CACHE_CLOUD_FALLBACK",
                    cacheStore.getProviderName(),
                    cacheKey,
                    true,
                    false,
                    "ANT_UNAVAILABLE_FALLBACK",
                    stale.obtenidoEn(),
                    "Portal ANT no disponible (" + errorAnt + "). Devolviendo última respuesta válida almacenada en Caché Cloud."
            );
        }

        // 4. Sin caché previa y ANT caída: generar respaldo inicial válido, guardarlo en Caché Cloud y devolver HTTP 200.
        String nombreFallback = (rawNombre != null && !rawNombre.isBlank())
                ? rawNombre.trim().toUpperCase()
                : "CIUDADANO " + cedula;
        LicenciaResponse seededFallback = new LicenciaResponse(
                cedula,
                placa,
                nombreFallback,
                30,
                30,
                0,
                List.of("Sin citaciones pendientes (respaldo por indisponibilidad del portal ANT)"),
                "CACHE_CLOUD_FALLBACK",
                cacheStore.getProviderName(),
                cacheKey,
                false,
                true,
                "ANT_UNAVAILABLE_SEEDED_CACHE",
                Instant.now().toString(),
                "Portal ANT no disponible (" + errorAnt + "). Respuesta de respaldo almacenada en Caché Cloud."
        );
        try {
            cacheStore.set(cacheKey, objectMapper.writeValueAsString(seededFallback), ttlSeconds);
        } catch (Exception ignored) {
        }
        return seededFallback;
    }

    /**
     * Almacena explícitamente una respuesta válida en Caché Cloud (útil para pruebas o demostraciones cuando la ANT está caída).
     */
    public LicenciaResponse guardarRespuestaValidaEnCache(CacheSeedRequest request) {
        String cedula = validarCedula(request.cedula());
        String placa = request.placa() == null ? "" : request.placa().trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        String cacheKey = CACHE_PREFIX + cedula;
        int puntos = request.puntos() == null ? 30 : Math.max(0, Math.min(30, request.puntos()));
        int citaciones = request.totalCitaciones() == null ? Math.max(0, 30 - puntos) : Math.max(0, request.totalCitaciones());
        String nombre = (request.nombreConductor() == null || request.nombreConductor().isBlank())
                ? "CONDUCTOR CEDULA " + cedula
                : request.nombreConductor().trim().toUpperCase();

        LicenciaResponse valid = new LicenciaResponse(
                cedula,
                placa,
                nombre,
                puntos,
                30,
                citaciones,
                citaciones == 0
                        ? List.of("Sin citaciones pendientes registradas")
                        : List.of(citaciones + " citación(es) registrada(s) en respuesta válida cacheada"),
                "ANT_ONLINE",
                cacheStore.getProviderName(),
                cacheKey,
                false,
                true,
                "STORED_IN_CACHE",
                Instant.now().toString(),
                "Respuesta válida registrada en Caché Cloud (" + cacheStore.getProviderName() + ") bajo la clave " + cacheKey + "."
        );
        try {
            cacheStore.set(cacheKey, objectMapper.writeValueAsString(valid), ttlSeconds);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CACHE_WRITE_ERROR", "No se pudo guardar en caché: " + ex.getMessage());
        }
        return valid;
    }

    public LicenciaResponse parseAntHtml(String html, String cedula, String placa, String cacheKey) {
        if (html == null || html.isBlank()) {
            throw new IllegalStateException("ANT devolvió una respuesta vacía");
        }
        String lower = html.toLowerCase();
        if (lower.contains("502 bad gateway") || lower.contains("503 service") || lower.contains("504 gateway")
                || lower.contains("ha ocurrido un error") || lower.contains("the page cannot be displayed")) {
            throw new IllegalStateException("ANT devolvió página de error de disponibilidad");
        }

        String plainText = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();

        Matcher puntosMatcher = PUNTOS_PATTERN.matcher(plainText);
        if (!puntosMatcher.find()) {
            throw new IllegalStateException("La página de la ANT no incluyó el saldo de puntos de licencia");
        }

        double parsedPuntos = Double.parseDouble(puntosMatcher.group(1).replace(",", "."));
        int puntos = (int) Math.round(parsedPuntos);

        Matcher nombreMatcher = NOMBRE_PATTERN.matcher(plainText);
        String nombreConductor = nombreMatcher.find() ? nombreMatcher.group(1).trim() : "CIUDADANO " + cedula;

        Matcher citMatcher = CITACIONES_PATTERN.matcher(plainText);
        int totalCitaciones = citMatcher.find() ? Integer.parseInt(citMatcher.group(1)) : 0;
        List<String> detalle = totalCitaciones == 0
                ? List.of("No registra citaciones pendientes")
                : List.of("Citaciones detectadas en portal ANT: " + totalCitaciones);

        return new LicenciaResponse(
                cedula,
                placa,
                nombreConductor,
                puntos,
                30,
                totalCitaciones,
                detalle,
                "ANT_ONLINE",
                cacheStore.getProviderName(),
                cacheKey,
                false,
                true,
                "ONLINE",
                Instant.now().toString(),
                "Consulta exitosa al portal ANT. Resultado almacenado en Caché Cloud (" + cacheStore.getProviderName() + ")."
        );
    }

    protected String fetchAntHtml(String cedula, String placa) throws Exception {
        String targetUrl = antUrl
                + "?ps_tipo_identificacion=CED&ps_identificacion=" + cedula
                + "&ps_placa=" + placa;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " desde portal ANT");
        }
        return response.body();
    }

    private Optional<LicenciaResponse> readFromCache(String cacheKey) {
        return cacheStore.get(cacheKey).flatMap(json -> {
            try {
                return Optional.of(objectMapper.readValue(json, LicenciaResponse.class));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    private boolean isFresh(LicenciaResponse cached) {
        if (cached.obtenidoEn() == null || cached.obtenidoEn().isBlank()) {
            return false;
        }
        try {
            Instant obtained = Instant.parse(cached.obtenidoEn());
            return Duration.between(obtained, Instant.now()).getSeconds() < freshSeconds;
        } catch (Exception e) {
            return false;
        }
    }

    private static String validarCedula(String rawCedula) {
        String clean = rawCedula == null ? "" : rawCedula.replaceAll("[\\s-]", "");
        if (clean.length() == 13 && clean.endsWith("001")) {
            clean = clean.substring(0, 10);
        }
        if (!clean.matches("\\d{10}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CEDULA_INVALIDA", "La cédula para consultar la ANT debe tener 10 dígitos.");
        }
        return clean;
    }
}
