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
                .email("admin@leowear.com")
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

        // ---- Sample Leo Wear products ----
        seedTee(menTees);
        seedHoodie(menHoodies);
        seedPants(menPants);
        seedWomenTop(womenTops);
        seedKids(kidsBoys);
    }

    private Category saveRoot(String name, String desc, String guide) {
        return categoryRepository.save(Category.builder()
                .name(name).description(desc).sizeGuide(guide).build());
    }

    private Category saveChild(Category parent, String name, String desc, String guide) {
        return categoryRepository.save(Category.builder()
                .name(name).description(desc).parent(parent).sizeGuide(guide).build());
    }

    private void seedTee(Category cat) {
        Product p = Product.builder()
                .name("ActiveFit Performance Tee")
                .description("Moisture-wicking performance t-shirt for workouts and everyday wear.")
                .price(new BigDecimal("1499"))
                .originalPrice(new BigDecimal("1999"))
                .brand("Leo Wear")
                .material("Polyester blend, Moisture-wicking fabric")
                .features("Stretchable, Quick Dry, Anti-Odour, Regular Fit")
                .category(cat)
                .active(true)
                .stock(0)
                .build();
        addImg(p, "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=600", "Black", true);
        addImg(p, "https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?w=600", "Navy", false);
        addImg(p, "https://images.unsplash.com/photo-1562157873-818bc0726f68?w=600", "White", false);
        for (String size : List.of("S", "M", "L", "XL", "XXL")) {
            for (String color : List.of("Black", "Navy", "White")) {
                addVar(p, size, color, 12 + size.charAt(0) % 7);
            }
        }
        p.recalculateStockFromVariants();
        p.syncPrimaryImageUrl();
        productRepository.save(p);
    }

    private void seedHoodie(Category cat) {
        Product p = Product.builder()
                .name("Urban Fleece Hoodie")
                .description("Soft fleece hoodie with kangaroo pocket. Leo Wear signature comfort.")
                .price(new BigDecimal("2499"))
                .originalPrice(new BigDecimal("2999"))
                .brand("Leo Wear")
                .material("Cotton Fleece, 320 GSM")
                .features("Warm, Soft Touch, Kangaroo Pocket, Ribbed Cuffs")
                .category(cat)
                .active(true)
                .stock(0)
                .build();
        addImg(p, "https://images.unsplash.com/photo-1556821840-3a63f95609a7?w=600", "Black", true);
        addImg(p, "https://images.unsplash.com/photo-1578587018452-892bacefd3f2?w=600", "Grey", false);
        for (String size : List.of("S", "M", "L", "XL", "XXL")) {
            for (String color : List.of("Black", "Grey")) {
                addVar(p, size, color, 10);
            }
        }
        p.recalculateStockFromVariants();
        p.syncPrimaryImageUrl();
        productRepository.save(p);
    }

    private void seedPants(Category cat) {
        Product p = Product.builder()
                .name("Classic Chino Pants")
                .description("Tailored chinos with stretch. Perfect for office and weekend.")
                .price(new BigDecimal("2199"))
                .originalPrice(new BigDecimal("2699"))
                .brand("Leo Wear")
                .material("Cotton Twill with Elastane")
                .features("Stretchable, Slim Fit, Pencil Cut, Wrinkle Resistant")
                .category(cat)
                .active(true)
                .stock(0)
                .build();
        addImg(p, "https://images.unsplash.com/photo-1473966968600-fa801b869a1a?w=600", "Khaki", true);
        addImg(p, "https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=600", "Navy", false);
        for (String size : List.of("30", "32", "34", "36", "38", "40", "42", "44", "46")) {
            for (String color : List.of("Khaki", "Navy", "Black")) {
                addVar(p, size, color, 8);
            }
        }
        p.recalculateStockFromVariants();
        p.syncPrimaryImageUrl();
        productRepository.save(p);
    }

    private void seedWomenTop(Category cat) {
        Product p = Product.builder()
                .name("Everyday Soft Top")
                .description("Breathable everyday top with a flattering drape.")
                .price(new BigDecimal("1299"))
                .brand("Leo Wear")
                .material("Viscose blend")
                .features("Breathable, Soft Handfeel, Relaxed Fit")
                .category(cat)
                .active(true)
                .stock(0)
                .build();
        addImg(p, "https://images.unsplash.com/photo-1434389677669-e08b4cac3105?w=600", "White", true);
        for (String size : List.of("XS", "S", "M", "L", "XL")) {
            addVar(p, size, "White", 15);
            addVar(p, size, "Pink", 10);
        }
        p.recalculateStockFromVariants();
        p.syncPrimaryImageUrl();
        productRepository.save(p);
    }

    private void seedKids(Category cat) {
        Product p = Product.builder()
                .name("Kids Play Tee")
                .description("Durable cotton tee for active kids.")
                .price(new BigDecimal("799"))
                .brand("Leo Wear")
                .material("100% Cotton")
                .features("Soft, Durable, Easy Wash")
                .category(cat)
                .active(true)
                .stock(0)
                .build();
        addImg(p, "https://images.unsplash.com/photo-1503919545889-aef636e10ad4?w=600", "Blue", true);
        for (String size : List.of("4-5Y", "6-7Y", "8-9Y", "10-11Y")) {
            addVar(p, size, "Blue", 12);
            addVar(p, size, "Red", 12);
        }
        p.recalculateStockFromVariants();
        p.syncPrimaryImageUrl();
        productRepository.save(p);
    }

    private void addImg(Product p, String url, String color, boolean primary) {
        p.getImages().add(ProductImage.builder()
                .product(p).url(url).color(color).primary(primary)
                .sortOrder(p.getImages().size()).build());
    }

    private void addVar(Product p, String size, String color, int stock) {
        p.getVariants().add(ProductVariant.builder()
                .product(p).size(size).color(color).stock(stock).active(true).build());
    }
}
