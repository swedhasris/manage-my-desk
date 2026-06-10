package com.connectit.core.repository;

import com.connectit.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUid(String uid);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCaseAndIsActiveTrue(String email);
    List<User> findByIsActiveTrueOrderByNameAsc();
    List<User> findByRoleInAndIsActiveTrue(List<String> roles);
    @Query("SELECT u FROM User u WHERE u.isActive = true ORDER BY u.name")
    List<User> findAllActiveOrderByName();
    boolean existsByUid(String uid);
    boolean existsByEmail(String email);
}
