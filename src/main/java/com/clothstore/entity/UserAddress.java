package com.clothstore.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Home, Work, Other */
    @Column(length = 40)
    private String label;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(nullable = false, length = 255)
    private String line1;

    @Column(length = 255)
    private String line2;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String state;

    @Column(length = 12)
    private String pincode;

    @Column(length = 20)
    private String phone;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultAddress = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Single-line format for order shippingAddress field */
    public String toShippingLine() {
        StringBuilder sb = new StringBuilder();
        if (fullName != null && !fullName.isBlank()) sb.append(fullName).append(", ");
        sb.append(line1);
        if (line2 != null && !line2.isBlank()) sb.append(", ").append(line2);
        if (city != null && !city.isBlank()) sb.append(", ").append(city);
        if (state != null && !state.isBlank()) sb.append(", ").append(state);
        if (pincode != null && !pincode.isBlank()) sb.append(" - ").append(pincode);
        return sb.toString();
    }
}
