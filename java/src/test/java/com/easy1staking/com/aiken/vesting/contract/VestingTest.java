package com.easy1staking.com.aiken.vesting.contract;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.util.HexUtil;
import com.easy1staking.aiken.vesting.contract.vesting.VestingValidator;
import com.easy1staking.aiken.vesting.contract.vesting.model.Redeemer;
import com.easy1staking.aiken.vesting.contract.vesting.model.impl.DatumData;
import com.easy1staking.aiken.vesting.contract.vesting.model.impl.RedeemerData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static com.bloxbean.cardano.client.common.model.Networks.preprod;

@Slf4j
public class VestingTest {

    // The wallet mnemonic of the owner wallet
    private static final String OWNER_MNEMONIC = "possible rural suit royal identify capital nose since affair hamster cancel hat hire gravity make advice kiwi improve glory camera crisp prepare fortune pottery";

    // The wallet mnemonic of the beneficiary wallet
    private static final String BENEFICIARY_MNEMONIC = "interest prepare reduce noise baby moral vicious convince soft melt sunset hurry manage canal make orange tattoo early stumble suffer yard hybrid blade body";

    // This class provides all the facilities to work with the Vesting Contract
    private final VestingValidator vestingValidator = new VestingValidator(preprod());

    // Owner account
    private final Account owner = new Account(preprod(), OWNER_MNEMONIC);

    // Beneficiary account
    private final Account beneficiary = new Account(preprod(), BENEFICIARY_MNEMONIC);

    // Blockfrost API - NOTE please replace api key here below
    private final BFBackendService bfBackendService = new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, "<enter api key here>");

    @Test
    public void payToContract() throws Exception {

        var ownerPublicKeyHash = owner.getBaseAddress().getPaymentCredentialHash().get();
        log.info("Owner public key hash Hex Encoded: {}", HexUtil.encodeHexString(ownerPublicKeyHash));
        log.info("Owner base address: {}", owner.baseAddress());

        var beneficiaryPublicKeyHash = beneficiary.getBaseAddress().getPaymentCredentialHash().get();
        log.info("Beneficiary public key hash Hex Encoded: {}", HexUtil.encodeHexString(beneficiaryPublicKeyHash));
        log.info("Beneficiary base address: {}", beneficiary.baseAddress());

        // Set vesting time in 15 minutes
        // Please note that:
        // 1. the chain only understand UTC time.
        // 2. the time is in POSIX Time in Milliseconds
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var in10Minutes = now.plusMinutes(15).toEpochSecond(ZoneOffset.UTC) * 1_000;

        var datum = new DatumData();
        datum.setOwner(ownerPublicKeyHash);
        datum.setBeneficiary(beneficiaryPublicKeyHash);
        datum.setLock_until(BigInteger.valueOf(in10Minutes));

        var scriptAddress = vestingValidator.getScriptAddress();
        log.info("script address: {}", scriptAddress);

        var tx = new Tx()
                .from(owner.baseAddress())
                .payToContract(scriptAddress, Amount.ada(10), datum.toPlutusData());

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(bfBackendService);

        quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(owner))
                .feePayer(owner.baseAddress())
                .completeAndWait();

    }

    @Test
    public void withdraw() throws Exception {

        var transactionHash = "<insert tx hash here>";
        var outputIndex = 0;

        var scriptAddress = vestingValidator.getScriptAddress();
        log.info("script address: {}", scriptAddress);

        var utxo = bfBackendService.getUtxoService().getTxOutput(transactionHash, outputIndex).getValue();
        log.info("utxo: {}", utxo);

        var redeeemer = RedeemerData.of(Redeemer.Withdraw);

        var tx = new ScriptTx()
                .collectFrom(utxo, redeeemer.toPlutusData())
                .payToAddress(owner.baseAddress(), utxo.getAmount())
                .attachSpendingValidator(vestingValidator.getPlutusScript());

        var recentSlot = bfBackendService.getBlockService().getLatestBlock().getValue().getSlot();

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(bfBackendService);

        quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(owner))
                .withRequiredSigners(owner.getBaseAddress())
                .feePayer(owner.baseAddress())
                .validFrom(recentSlot)
                .validTo(recentSlot + 120)
                .completeAndWait();


    }

    @Test
    public void collect() throws Exception {

        var transactionHash = "<insert tx hash here>";
        var outputIndex = 0;

        var utxo = bfBackendService.getUtxoService().getTxOutput(transactionHash, outputIndex).getValue();
        log.info("utxo: {}", utxo);

        var collect = RedeemerData.of(Redeemer.Collect);

        var tx = new ScriptTx()
                .collectFrom(utxo, collect.toPlutusData())
                .payToAddress(beneficiary.baseAddress(), utxo.getAmount())
                .attachSpendingValidator(vestingValidator.getPlutusScript());

        var recentSlot = bfBackendService.getBlockService().getLatestBlock().getValue().getSlot();

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(bfBackendService);

        quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(beneficiary))
                .withRequiredSigners(beneficiary.getBaseAddress())
                .feePayer(beneficiary.baseAddress())
                .validFrom(recentSlot)
                .validTo(recentSlot + 120)
                .completeAndWait();


    }

}
