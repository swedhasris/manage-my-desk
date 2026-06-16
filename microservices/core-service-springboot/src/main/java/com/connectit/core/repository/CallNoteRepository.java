package com.connectit.core.repository;

import com.connectit.core.model.CallNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CallNoteRepository extends JpaRepository<CallNote, Long> {
    List<CallNote> findByCallIdOrderByCreatedAtDesc(Long callId);
}
