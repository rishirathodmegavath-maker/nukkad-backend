package com.nukkad.user.repository;

import com.nukkad.user.entity.MutedAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MutedAccountRepository extends JpaRepository<MutedAccount, String> {
    boolean existsByMuterIdAndMutedId(String muterId, String mutedId);
    void deleteByMuterIdAndMutedId(String muterId, String mutedId);
    List<MutedAccount> findByMuterId(String muterId);
}
