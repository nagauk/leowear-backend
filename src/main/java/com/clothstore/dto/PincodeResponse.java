package com.clothstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PincodeResponse {
    private boolean valid;
    private String pincode;
    private String message;
    private String district;
    private String state;
    private String country;
    private List<String> postOffices;
}
