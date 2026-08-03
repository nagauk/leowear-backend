package com.clothstore.repository;

import com.clothstore.entity.Complaint;
import com.clothstore.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    Page<Complaint> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    Page<Complaint> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
