package com.schoolerp.fees.infrastructure;

import com.schoolerp.fees.domain.StudentWallet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentWalletRepository extends JpaRepository<StudentWallet, Long> {
    Optional<StudentWallet> findByStudentIdAndCurrency(Long studentId, String currency);
}

