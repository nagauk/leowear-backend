package com.clothstore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressDto {
    private Long id;

    @Size(max = 40)
    private String label;

    @Size(max = 100)
    private String fullName;

    @NotBlank(message = "Address line is required")
    @Size(max = 255)
    private String line1;

    @Size(max = 255)
    private String line2;

    @Size(max = 80)
    private String city;

    @Size(max = 80)
    private String state;

    @Size(max = 12)
    private String pincode;

    @Size(max = 20)
    private String phone;

    private boolean defaultAddress;

    /** Pre-formatted line for checkout display */
    private String formatted;
}
