package com.clothstore.repository;

import com.clothstore.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByActiveTrueAndCategoryId(Long categoryId, Pageable pageable);

    /**
     * Products in this category OR any direct subcategory (parent filter).
     */
    @Query("""
        SELECT p FROM Product p
        WHERE p.active = true
          AND p.category IS NOT NULL
          AND (p.category.id = :categoryId OR p.category.parent.id = :categoryId)
        """)
    Page<Product> findActiveByCategoryIncludingChildren(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        WHERE p.active = true
          AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    Page<Product> searchActive(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        WHERE p.active = true
          AND p.category IS NOT NULL
          AND (p.category.id = :categoryId OR p.category.parent.id = :categoryId)
          AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    Page<Product> searchActiveInCategory(@Param("keyword") String keyword,
                                         @Param("categoryId") Long categoryId,
                                         Pageable pageable);

    List<Product> findByStockLessThanAndActiveTrue(Integer threshold);

    long countByActiveTrue();

    long countByStockLessThanEqualAndActiveTrue(Integer stock);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.id = :id")
    Optional<Product> findDetailedById(@Param("id") Long id);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.id IN :ids")
    List<Product> findAllDetailedByIdIn(@Param("ids") List<Long> ids);

    @Query(value = """
        SELECT p.* FROM products p
        JOIN (
          SELECT oi.product_id AS pid, SUM(oi.quantity) AS qty
          FROM order_items oi
          JOIN orders o ON o.id = oi.order_id
          WHERE o.status NOT IN ('CANCELLED')
          GROUP BY oi.product_id
        ) s ON s.pid = p.id
        WHERE p.active = true
        ORDER BY s.qty DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Product> findBestsellers(@Param("limit") int limit);
}
