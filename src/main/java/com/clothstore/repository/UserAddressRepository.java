package com.clothstore.repository;

import com.clothstore.entity.User;
import com.clothstore.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    List<UserAddress> findByUserOrderByDefaultAddressDescCreatedAtDesc(User user);

    Optional<UserAddress> findByIdAndUser(Long id, User user);

    Optional<UserAddress> findFirstByUserAndDefaultAddressTrue(User user);

    @Modifying
    @Query("UPDATE UserAddress a SET a.defaultAddress = false WHERE a.user = :user")
    void clearDefaultForUser(@Param("user") User user);

    long countByUser(User user);
}
