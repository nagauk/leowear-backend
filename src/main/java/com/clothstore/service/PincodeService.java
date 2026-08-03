package com.clothstore.service;

import com.clothstore.dto.PincodeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates Indian PIN codes via the public Postal PIN Code API:
 * https://api.postalpincode.in/pincode/{pincode}
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PincodeService {

    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public PincodeResponse validate(String pincode) {
        if (pincode == null || !pincode.matches("^[1-9][0-9]{5}$")) {
            return PincodeResponse.builder()
                    .valid(false)
                    .pincode(pincode)
                    .message("Enter a valid 6-digit Indian PIN code")
                    .build();
        }

        try {
            RestClient client = restClientBuilder.build();
            String body = client.get()
                    .uri("https://api.postalpincode.in/pincode/{pin}", pincode)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray() || root.isEmpty()) {
                return invalid(pincode, "Unable to verify PIN code");
            }

            JsonNode first = root.get(0);
            String status = first.path("Status").asText("");
            if (!"Success".equalsIgnoreCase(status)) {
                return invalid(pincode, "Invalid PIN code — not found in India Post records");
            }

            JsonNode offices = first.path("PostOffice");
            List<String> names = new ArrayList<>();
            String district = null;
            String state = null;
            String country = null;

            if (offices.isArray() && !offices.isEmpty()) {
                JsonNode po = offices.get(0);
                district = text(po, "District");
                state = text(po, "State");
                country = text(po, "Country");
                for (JsonNode n : offices) {
                    String name = text(n, "Name");
                    if (name != null) names.add(name);
                }
            }

            return PincodeResponse.builder()
                    .valid(true)
                    .pincode(pincode)
                    .message("Valid PIN code")
                    .district(district)
                    .state(state)
                    .country(country != null ? country : "India")
                    .postOffices(names)
                    .build();
        } catch (Exception e) {
            log.warn("PIN code API error for {}: {}", pincode, e.getMessage());
            // Soft-fail: allow order if API is down, but flag as unverified format-ok
            // Format is valid; don't surface temporary API issues to the customer
            return PincodeResponse.builder()
                    .valid(true)
                    .pincode(pincode)
                    .message("")
                    .country("India")
                    .build();
        }
    }

    private PincodeResponse invalid(String pin, String msg) {
        return PincodeResponse.builder().valid(false).pincode(pin).message(msg).build();
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }
}
