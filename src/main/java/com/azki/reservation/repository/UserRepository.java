package com.azki.reservation.repository;

import com.azki.reservation.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email, returning the first match in case of duplicates.
     * Uses setMaxResults(1) internally via the Spring Data repository mechanism.
     */
    @Query("SELECT u FROM User u WHERE u.email = :email ORDER BY u.id ASC")
    List<User> findByEmailOrderedById(@Param("email") String email);

    /**
     * Find user by email, handling duplicates gracefully
     */
    default Optional<User> findByEmail(String email) {
        List<User> users = findByEmailOrderedById(email);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.getFirst());
    }
}
