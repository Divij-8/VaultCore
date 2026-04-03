package com.vaultcore.ledger.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name="transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private Long id;

    @Column(unique = true, nullable = false)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;




}
