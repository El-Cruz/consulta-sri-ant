package com.udla.consultasriant.client;

import com.udla.consultasriant.dto.VehiculoResponse;
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

/**
 * Componente C4: Cliente SRI Vehículo.
 */
@Component
public class SriVehiculoClient {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final String vehiculoUrl;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SriVehiculoClient(
            @Value("${app.sri.vehiculo-url:https://srienlinea.sri.gob.ec/sri-matriculacion-vehicular-recaudacion-servicio-internet/rest/BaseVehiculo/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn}") String vehiculoUrl,
            @Value("${app.sri.timeout-seconds:10}") int timeoutSeconds
    ) {
        this.vehiculoUrl = vehiculoUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        this.objectMapper = JsonMapper.builder().build();
    }

    public VehiculoResponse consultarVehiculo(String rawPlaca) {
        String placa = normalizarPlaca(rawPlaca);
        String body = getFromSri(vehiculoUrl + "?numeroPlacaCampvCpn=" + placa);
        if (body == null || body.isBlank() || "null".equalsIgnoreCase(body.trim())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "VEHICULO_NO_ENCONTRADO", "No existe vehículo registrado en el SRI para la matrícula " + placa + ".");
        }
        return parseVehiculoJson(placa, body);
    }

    public String normalizarPlaca(String rawPlaca) {
        String placa = rawPlaca == null ? "" : rawPlaca.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (placa.length() < 4 || placa.length() > 15) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLACA_INVALIDA", "Ingrese una matrícula/placa válida (por ejemplo: PBA1234).");
        }
        return placa;
    }

    public VehiculoResponse parseVehiculoJson(String placa, String jsonBody) {
        try {
            JsonNode node = objectMapper.readTree(jsonBody);
            if (node == null || node.isNull() || node.isMissingNode() || node.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "VEHICULO_NO_ENCONTRADO", "No se encontraron datos del vehículo " + placa + ".");
            }
            return new VehiculoResponse(
                    node.hasNonNull("codigoVehiculo") ? node.get("codigoVehiculo").asLong() : null,
                    firstNonBlank(text(node, "numeroPlaca"), placa),
                    text(node, "numeroCamvCpn"),
                    text(node, "descripcionMarca"),
                    text(node, "descripcionModelo"),
                    node.hasNonNull("anioAuto") ? node.get("anioAuto").asInt() : null,
                    text(node, "descripcionPais"),
                    node.hasNonNull("ultimoAnioPagado") ? node.get("ultimoAnioPagado").asInt() : null,
                    text(node, "fechaUltimaMatricula"),
                    text(node, "fechaCaducidadMatricula"),
                    text(node, "descripcionCanton"),
                    text(node, "descripcionServicio"),
                    text(node, "nombreClase"),
                    text(node, "cilindraje"),
                    text(node, "colorVehiculo1"),
                    text(node, "prohibidoEnajenar"),
                    text(node, "estadoExoneracion")
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SRI_VEHICULO_PARSE_ERROR", "No se pudo interpretar la respuesta vehicular del SRI.");
        }
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
            if (response.statusCode() == 204 || response.statusCode() == 404) {
                return "";
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "SRI_HTTP_ERROR", "El servicio vehicular del SRI respondió HTTP " + response.statusCode());
            }
            return response.body();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "SRI_TIMEOUT", "No se pudo conectar con el servicio vehicular del SRI: " + ex.getMessage());
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asString();
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }
}
