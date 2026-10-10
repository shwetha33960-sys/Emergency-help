
package com.emergencyhelp.emergency_help;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/nearby")
public class NearbyServiceController {

    private final RestClient restClient = RestClient.builder()
            .defaultHeader(
                    "User-Agent",
                    "EmergencyHelpStudentProject/1.0"
            )
            .defaultHeader(
                    "Accept",
                    "application/json"
            )
            .build();

    private final List<String> servers = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
    );

    @GetMapping
    public Map<String, Object> getNearbyServices(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "hospital") String type) {

        if (!Double.isFinite(lat) || lat < -90 || lat > 90
                || !Double.isFinite(lon) || lon < -180 || lon > 180) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid coordinates");
        }

        if (!List.of("hospital", "pharmacy", "police").contains(type)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid service type");
        }

        String query = "[out:json][timeout:25];"
                + "nwr(around:5000," + lat + "," + lon + ")"
                + "[amenity=" + type + "];"
                + "out center tags;";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("data", query);

        for (String server : servers) {
            try {
                Map<?, ?> response = restClient.post()
                        .uri(server)
                        .contentType(
                                MediaType.APPLICATION_FORM_URLENCODED)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(form)
                        .retrieve()
                        .body(Map.class);

                if (response != null
                        && response.get("elements") instanceof List<?> elements) {
                    return Map.of("elements", elements);
                }

            } catch (RestClientException ex) {
                System.err.println(
                        "Failed Overpass server: " + server);
                System.err.println(
                        "Error type: " + ex.getClass().getName());
                System.err.println(
                        "Error details: " + ex.getMessage());
            }
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Both map servers failed. Check the application terminal.");
    }
}
