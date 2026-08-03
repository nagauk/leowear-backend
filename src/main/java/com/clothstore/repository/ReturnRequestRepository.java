package com.clothstore.repository;

import com.clothstore.entity.ReturnRequest;
import com.clothstore.entity.ReturnStatus;
import com.clothstore.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    Page<ReturnRequest> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    Page<ReturnRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(ReturnStatus status);
}
