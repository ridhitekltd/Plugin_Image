package com.ridhitek.image.repository;

import com.ridhitek.image.entity.VerificationOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VerificationOverrideRepository extends JpaRepository<VerificationOverride, Long> {
    List<VerificationOverride> findByCandidateIdOrderByCreatedAtDesc(String candidateId);
}
