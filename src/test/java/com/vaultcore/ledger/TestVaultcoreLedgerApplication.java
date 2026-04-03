package com.vaultcore.ledger;

import org.springframework.boot.SpringApplication;

public class TestVaultcoreLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.from(VaultcoreLedgerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
