package com.ridhitek.image.repository;

import com.ridhitek.image.entity.VerificationResult;
import com.ridhitek.image.entity.VerificationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VerificationRepository extends JpaRepository<VerificationResult, String> {
    List<VerificationResult> findByVerificationStatus(String status);
    List<VerificationResult> findByVerificationStatusNot(String status);
    
    @Query("SELECT v.verificationStatus, COUNT(v) FROM VerificationResult v GROUP BY v.verificationStatus")
    List<Object[]> getVerificationStatusCounts();
}
