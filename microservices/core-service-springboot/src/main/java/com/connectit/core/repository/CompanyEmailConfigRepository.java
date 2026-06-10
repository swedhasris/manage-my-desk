package com.connectit.core.repository;
import com.connectit.core.model.CompanyEmailConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
@Repository
public interface CompanyEmailConfigRepository extends JpaRepository<CompanyEmailConfig, Long> {
    List<CompanyEmailConfig> findByIsActiveTrueOrderByIsDefaultDescCompanyNameAsc();
    Optional<CompanyEmailConfig> findFirstByIsActiveTrueAndIsDefaultTrue();
    Optional<CompanyEmailConfig> findFirstByIsActiveTrue();
    Optional<CompanyEmailConfig> findByEmailAddress(String emailAddress);
}
