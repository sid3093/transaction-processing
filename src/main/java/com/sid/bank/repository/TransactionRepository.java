package com.sid.bank.repository;

import com.sid.bank.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransactionRepository extends JpaRepository<BankTransaction, Long> {

    @Query("""
            select t from BankTransaction t
            left join t.fromAccount f
            left join t.toAccount d
            where f.id = :accountId or d.id = :accountId
            order by t.createdAt desc, t.id desc
            """)
    List<BankTransaction> findHistory(@Param("accountId") Long accountId);
}
