package com.clothstore.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories", uniqueConstraints = {
        @UniqueConstraint(name = "uk_category_parent_name", columnNames = {"parent_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 255)
    private String description;

    /** null = top-level category (Men, Women, Kids, …) */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    @JsonIgnoreProperties({"parent", "children"})
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @Builder.Default
    @OrderBy("name ASC")
    @JsonIgnoreProperties({"parent", "children"})
    private List<Category> children = new ArrayList<>();

    /** Size guide key: APPAREL, PANTS, KIDS, ACCESSORY, FOOTWEAR */
    @Column(name = "size_guide", length = 30)
    private String sizeGuide;
}
