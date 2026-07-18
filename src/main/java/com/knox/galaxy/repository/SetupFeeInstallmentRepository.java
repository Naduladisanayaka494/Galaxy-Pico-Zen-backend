package com.knox.galaxy.repository;

import com.knox.galaxy.model.SetupFeeInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface SetupFeeInstallmentRepository extends JpaRepository<SetupFeeInstallment, Long> {
    List<SetupFeeInstallment> findByClientIdOrderByInstallmentNoAsc(Long clientId);
    List<SetupFeeInstallment> findByClientIdIn(List<Long> clientIds);
    Optional<SetupFeeInstallment> findByClientIdAndInstallmentNo(Long clientId, Short installmentNo);

    // Custom derived deletes default to SimpleJpaRepository's class-level
    // @Transactional(readOnly=true) unless an outer writable transaction is
    // already open (which callers like update() provide, but create()'s
    // compensating cleanup deliberately does not — see ClientService). Marked
    // explicitly so this method works standalone too.
    @Transactional
    void deleteByClientId(Long clientId);
}
