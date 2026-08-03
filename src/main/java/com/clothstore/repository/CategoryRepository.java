package com.clothstore.repository;

import com.clothstore.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<Category> findByParentIsNullOrderByNameAsc();
    List<Category> findByParentIdOrderByNameAsc(Long parentId);

    @Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.children WHERE c.parent IS NULL ORDER BY c.name")
    List<Category> findRootsWithChildren();
}
