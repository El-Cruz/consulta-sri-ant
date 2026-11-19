package com.udla.consultasriant.client;

import com.udla.consultasriant.dto.ContribuyenteResponse;
import com.udla.consultasriant.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Componente C4: Cliente SRI Contribuyente.
 * Verifica existencia de RUC y valida que corresponda a una Persona Natural.
 */
@Component
public class SriContribuyenteClient {

    private static final Pattern EMAIL_REGEX = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern DIGITS_REGEX = Pattern.compile("^\\d+$");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final String existeRucUrl;
    private final String obtenerRucUrl;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SriContribuyenteClient(
            @Value("${app.sri.existe-ruc-url:https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/existePorNumeroRuc}") String existeRucUrl,
            @Value("${app.sri.obtener-ruc-url:https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/obtenerPorNumerosRuc}") String obtenerRucUrl,
            @Value("${app.sri.timeout-seconds:10}") int timeoutSeconds
    ) {
        this.existeRucUrl = existeRucUrl;
        this.obtenerRucUrl = obtenerRucUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        this.objectMapper = JsonMapper.builder().build();
    }

    public ContribuyenteResponse consultarPersonaNatural(String email, String rawRucOCedula) {
        String cleanEmail = email == null ? "" : email.trim();
        if (!EMAIL_REGEX.matcher(cleanEmail).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_INVALIDO", "Ingrese un correo electrónico válido.");
        }

        String normalizedRuc = normalizarRuc(rawRucOCedula);
        String cedula = normalizedRuc.substring(0, 10);

        boolean existe = existePorNumeroRuc(normalizedRuc);
        if (!existe) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "CONTRIBUYENTE_NO_EXISTE",
                    "El RUC " + normalizedRuc + " no figura como contribuyente registrado en el SRI."
            );
        }

        String jsonBody = obtenerPorNumerosRuc(normalizedRuc);
        return parseContribuyenteJson(cleanEmail, normalizedRuc, cedula, jsonBody);
    }

    public String normalizarRuc(String rawInput) {
        String clean = rawInput == null ? "" : rawInput.replaceAll("[\\s-]", "");
        if (!DIGITS_REGEX.matcher(clean).matches() || (clean.length() != 10 && clean.length() != 13)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "RUC_INVALIDO",
                    "Ingrese una cédula de 10 dígitos o un RUC de 13 dígitos numérico."
            );
        }
        return clean.length() == 10 ? clean + "001" : clean;
    }

    public ContribuyenteResponse parseContribuyenteJson(String email, String ruc, String cedula, String jsonBody) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode item = root.isArray() && !root.isEmpty() ? root.get(0) : root;
            if (item == null || item.isNull() || item.isMissingNode() || item.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "SIN_DATOS_SRI", "El SRI no devolvió información para el RUC " + ruc + ".");
            }

            String tipoContribuyente = text(item, "tipoContribuyente");
            boolean esPersonaNatural = isNaturalPerson(tipoContribuyente, ruc);
            if (!esPersonaNatural) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "NO_ES_PERSONA_NATURAL",
                        "El RUC " + ruc + " (" + text(item, "razonSocial") + ") está registrado como '" + tipoContribuyente + "', no como Persona Natural."
                );
            }

            JsonNode fechas = item.get("informacionFechasContribuyente");
            return new ContribuyenteResponse(
                    email,
                    firstNonBlank(text(item, "numeroRuc"), ruc),
                    cedula,
                    true,
                    true,
                    text(item, "razonSocial"),
                    text(item, "estadoContribuyenteRuc"),
                    text(item, "actividadEconomicaPrincipal"),
                    tipoContribuyente,
                    text(item, "regimen"),
                    text(item, "obligadoLlevarContabilidad"),
                    text(item, "agenteRetencion"),
                    text(item, "contribuyenteEspecial"),
                    text(fechas, "fechaInicioActividades"),
                    text(fechas, "fechaActualizacion")
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SRI_PARSE_ERROR", "No se pudo interpretar la respuesta del SRI: " + ex.getMessage());
        }
    }

    private boolean isNaturalPerson(String tipoContribuyente, String ruc) {
        if (tipoContribuyente != null && !tipoContribuyente.isBlank()) {
            return tipoContribuyente.toUpperCase().contains("PERSONA NATURAL");
        }
        int thirdDigit = Character.getNumericValue(ruc.charAt(2));
        return thirdDigit >= 0 && thirdDigit < 6;
    }

    private boolean existePorNumeroRuc(String ruc) {
        String url = existeRucUrl + "?numeroRuc=" + ruc;
        String body = getFromSri(url);
        return "true".equalsIgnoreCase(body.trim());
    }

    private String obtenerPorNumerosRuc(String ruc) {
        String url = obtenerRucUrl + "?&ruc=" + ruc;
        return getFromSri(url);
    }

    protected String getFromSri(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "SRI_HTTP_ERROR", "El servicio del SRI respondió con estado HTTP " + response.statusCode());
            }
            return response.body();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "SRI_TIMEOUT", "No se pudo conectar con la API del SRI: " + ex.getMessage());
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asString();
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }
}
