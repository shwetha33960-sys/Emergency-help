
package com.emergencyhelp.emergency_help;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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

    private final RestClient restClient;

    
        private final List<String> servers = List.of(
                "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
                "https://overpass-api.de/api/interpreter",
                "https://overpass.kumi.systems/api/interpreter",
                "https://overpass.private.coffee/api/interpreter"
        );


    public NearbyServiceController() {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(
                        "User-Agent",
                        "EmergencyHelpStudentProject/1.0"
                )
                .defaultHeader(
                        "Accept",
                        "application/json"
                )
                .build();
    }

    @GetMapping
    public Map<String, Object> getNearbyServices(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "hospital") String type) {

        // Validate coordinates.
        if (!Double.isFinite(lat)
                || lat < -90 || lat > 90
                || !Double.isFinite(lon)
                || lon < -180 || lon > 180) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid latitude or longitude."
            );
        }

        // Validate the requested category.
        if (!List.of("hospital", "pharmacy", "police").contains(type)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid service type. Use hospital, pharmacy, or police."
            );
        }

        // Search within 3 km to reduce the work required from Overpass.
        String query =
                "[out:json][timeout:20];"
                + "("
                + "node(around:1500," + lat + "," + lon + ")[amenity=" + type + "];"
                + "way(around:1500," + lat + "," + lon + ")[amenity=" + type + "];"
                + "relation(around:1500," + lat + "," + lon + ")[amenity=" + type + "];"
                + ");"
                + "out center tags;";

        MultiValueMap<String, String> form =
                new LinkedMultiValueMap<>();

        form.add("data", query);

        // Try each provider until one returns valid JSON results.
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

        // Return a clear error only if all providers fail.
        System.err.println(
                "All nearby-services servers failed."
        );

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Nearby map services are temporarily unavailable. "
                        + "Please try again later."
        );
    }
}
