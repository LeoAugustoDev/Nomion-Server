package com.nomion.setup.repository;

import com.nomion.setup.domain.InitialSetupState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InitialSetupStateRepository
        extends JpaRepository<InitialSetupState, Short> {


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT state FROM InitialSetupState state WHERE state.id = :id")
    Optional<InitialSetupState> findByIdForUpdate(@Param("id") short id);
}
