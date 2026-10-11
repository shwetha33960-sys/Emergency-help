
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

    // Try multiple Overpass servers if one is unavailable.
    private final List<String> servers = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
    );

    @GetMapping
    public Map<String, Object> getNearbyServices(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "hospital") String type) {

        // Validate latitude and longitude.
        if (!Double.isFinite(lat)
                || lat < -90 || lat > 90
                || !Double.isFinite(lon)
                || lon < -180 || lon > 180) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid latitude or longitude."
            );
        }

        // Allow only supported service categories.
        if (!List.of("hospital", "pharmacy", "police").contains(type)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid service type. Use hospital, pharmacy, or police."
            );
        }

        // Search for services within 5 km of the user's location.
        String query =
                "[out:json][timeout:40];"
                + "nwr(around:5000,"
                + lat + "," + lon + ")"
                + "[amenity=" + type + "];"
                + "out center tags;";

        MultiValueMap<String, String> form =
                new LinkedMultiValueMap<>();

        form.add("data", query);

        // Try each server until one returns valid results.
        for (String server : servers) {
            try {
                System.out.println(
                        "Trying nearby-services server: " + server
                );

                Map<?, ?> response = restClient.post()
                        .uri(server)
                        .contentType(
                                MediaType.APPLICATION_FORM_URLENCODED
                        )
                        .accept(MediaType.APPLICATION_JSON)
                        .body(form)
                        .retrieve()
                        .body(Map.class);

                if (response != null
                        && response.get("elements") instanceof List<?> elements) {

                    System.out.println(
                            "Nearby search successful. Results: "
                            + elements.size()
                    );

                    return Map.of("elements", elements);
                }

                System.err.println(
                        "Invalid response from server: " + server
                );

            } catch (RestClientException ex) {
                System.err.println(
                        "Nearby server failed: " + server
                );

                System.err.println(
                        "Reason: " + ex.getMessage()
                );
            }
        }

        // All external servers failed.
        System.err.println(
                "All nearby-services servers failed."
        );

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Nearby map services are temporarily unavailable. Please try again later."
        );
    }
}
