package com.clothstore.repository;

import com.clothstore.entity.Order;
import com.clothstore.entity.OrderStatus;
import com.clothstore.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByStatus(OrderStatus status);

    long countByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status NOT IN ('CANCELLED', 'RETURNED')")
    BigDecimal getTotalSales();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'DELIVERED'")
    BigDecimal getDeliveredSales();

    /**
     * Admin order filter: status, keyword (order # / username), date range.
     * Null filters are ignored.
     *
     * Native SQL (not JPQL) so we can give every bind parameter an explicit type
     * via CAST — without that, PostgreSQL can't infer the type of a NULL bind
     * ("could not determine data type of parameter $N"), and the same query
     * also works on H2 because both engines accept CAST(... AS ...).
     */
    @Query(value = """
            SELECT o.* FROM orders o
            JOIN users u ON u.id = o.user_id
            WHERE (CAST(:status AS VARCHAR) IS NULL OR o.status = CAST(:status AS VARCHAR))
              AND (CAST(:keyword AS VARCHAR) IS NULL
                   OR LOWER(o.order_number) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%'))
                   OR LOWER(u.username)        LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%')))
              AND (CAST(:fromDate AS TIMESTAMP) IS NULL OR o.created_at >= CAST(:fromDate AS TIMESTAMP))
              AND (CAST(:toDate   AS TIMESTAMP) IS NULL OR o.created_at <= CAST(:toDate   AS TIMESTAMP))
            ORDER BY o.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM orders o
            JOIN users u ON u.id = o.user_id
            WHERE (CAST(:status AS VARCHAR) IS NULL OR o.status = CAST(:status AS VARCHAR))
              AND (CAST(:keyword AS VARCHAR) IS NULL
                   OR LOWER(o.order_number) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%'))
                   OR LOWER(u.username)        LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%')))
              AND (CAST(:fromDate AS TIMESTAMP) IS NULL OR o.created_at >= CAST(:fromDate AS TIMESTAMP))
              AND (CAST(:toDate   AS TIMESTAMP) IS NULL OR o.created_at <= CAST(:toDate   AS TIMESTAMP))
            """,
            nativeQuery = true)
    Page<Order> findFiltered(
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );

    /**
     * Same filter semantics as {@link #findFiltered}, but un-paginated — used by
     * the PDF export. Kept separate so we don't conflate Page/List return types
     * and so the cast workaround carries over verbatim.
     */
    @Query(value = """
            SELECT o.* FROM orders o
            JOIN users u ON u.id = o.user_id
            WHERE (CAST(:status AS VARCHAR) IS NULL OR o.status = CAST(:status AS VARCHAR))
              AND (CAST(:keyword AS VARCHAR) IS NULL
                   OR LOWER(o.order_number) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%'))
                   OR LOWER(u.username)        LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%')))
              AND (CAST(:fromDate AS TIMESTAMP) IS NULL OR o.created_at >= CAST(:fromDate AS TIMESTAMP))
              AND (CAST(:toDate   AS TIMESTAMP) IS NULL OR o.created_at <= CAST(:toDate   AS TIMESTAMP))
            ORDER BY o.created_at DESC
            """,
            nativeQuery = true)
    List<Order> findAllFiltered(
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );

    /**
     * Force-load the items + product associations for a batch of orders whose
     * ids come from a native query (which doesn't honour {@code FetchType.EAGER}).
     * Returns the items in id-order so callers can zip them back onto the orders.
     *
     * <p>This is the reliable path — relying on Hibernate's lazy load through a
     * detached entity returned from a native query is fragile (sometimes returns
     * an empty PersistentBag, sometimes throws {@code LazyInitializationException}).</p>
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE o.id IN :ids " +
           "ORDER BY o.id")
    List<Order> findWithItemsByIds(@Param("ids") List<Long> ids);
}
