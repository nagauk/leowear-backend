package com.clothstore.config;

import com.clothstore.entity.*;
import com.clothstore.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final StoreSettingsRepository storeSettingsRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            if (storeSettingsRepository.count() == 0) {
                storeSettingsRepository.save(StoreSettings.builder()
                        .deliveryCharge(new BigDecimal("49.00"))
                        .freeDeliveryMinAmount(new BigDecimal("999.00"))
                        .build());
            }
            return;
        }

        storeSettingsRepository.save(StoreSettings.builder()
                .deliveryCharge(new BigDecimal("49.00"))
                .freeDeliveryMinAmount(new BigDecimal("999.00"))
                .build());

        userRepository.save(User.builder()
                .username("admin")
                .email("admin@leowear.in")
                .password(passwordEncoder.encode("admin123"))
                .fullName("Leo Wear Admin")
                .role(Role.ADMIN)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        User customer = userRepository.save(User.builder()
                .username("customer")
                .email("customer@example.com")
                .password(passwordEncoder.encode("Customer@1"))
                .fullName("Rahul Sharma")
                .phone("9876543210")
                .address("42 MG Road, Bengaluru, Karnataka 560001")
                .role(Role.CUSTOMER)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        userAddressRepository.save(UserAddress.builder()
                .user(customer).label("Home").fullName("Rahul Sharma")
                .line1("42 MG Road").line2("Near Metro Station")
                .city("Bengaluru").state("Karnataka").pincode("560001")
                .phone("9876543210").defaultAddress(true).build());
        userAddressRepository.save(UserAddress.builder()
                .user(customer).label("Work").fullName("Rahul Sharma")
                .line1("12th Floor, Tech Park")
                .city("Bengaluru").state("Karnataka").pincode("560100")
                .phone("9876543210").defaultAddress(false).build());

        // ---- Category tree ----
        Category men = saveRoot("Men", "Men's fashion", "APPAREL");
        Category women = saveRoot("Women", "Women's fashion", "APPAREL");
        Category kids = saveRoot("Kids", "Kids wear", "KIDS");
        Category accessories = saveRoot("Accessories", "Bags, belts & more", "ACCESSORY");

        Category menTees = saveChild(men, "T-Shirts", "Casual & graphic tees", "APPAREL");
        Category menHoodies = saveChild(men, "Hoodies", "Hoodies & sweatshirts", "APPAREL");
        Category menShirts = saveChild(men, "Shirts", "Formal & casual shirts", "APPAREL");
        Category menPants = saveChild(men, "Pants", "Chinos, cargos & trousers", "PANTS");
        Category menJeans = saveChild(men, "Jeans", "Denim jeans", "PANTS");

        Category womenTops = saveChild(women, "Tops", "Tops & blouses", "APPAREL");
        Category womenDresses = saveChild(women, "Dresses", "Casual & party dresses", "APPAREL");
        Category womenPants = saveChild(women, "Pants", "Trousers & leggings", "PANTS");
        Category womenJeans = saveChild(women, "Jeans", "Women's denim", "PANTS");

        Category kidsBoys = saveChild(kids, "Boys", "Boys clothing", "KIDS");
        Category kidsGirls = saveChild(kids, "Girls", "Girls clothing", "KIDS");


    }

    private Category saveRoot(String name, String desc, String guide) {
        return categoryRepository.save(Category.builder()
                .name(name).description(desc).sizeGuide(guide).build());
    }

    private Category saveChild(Category parent, String name, String desc, String guide) {
        return categoryRepository.save(Category.builder()
                .name(name).description(desc).parent(parent).sizeGuide(guide).build());
    }
}
