package com.connectit.core.repository;

import com.connectit.core.model.CallActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CallActivityRepository extends JpaRepository<CallActivity, Long> {
    List<CallActivity> findByCallIdOrderByCreatedAtDesc(Long callId);
}
