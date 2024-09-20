package com.easy1staking.com.aiken.vesting.contract;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.util.HexUtil;
import com.easy1staking.aiken.vesting.contract.vesting.VestingValidator;
import com.easy1staking.aiken.vesting.contract.vesting.model.impl.DatumData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static com.bloxbean.cardano.client.common.model.Networks.preprod;

@Slf4j
public class VestingTest {

    private static final String OWNER_MNEMONIC = "gesture fold viable expire prosper stick jewel nerve replace tomorrow elbow dwarf rate escape object venture magnet nuclear tube cinnamon mistake expand food lion";

    private static final String BENEFICIARY_MNEMONIC = "interest prepare reduce noise baby moral vicious convince soft melt sunset hurry manage canal make orange tattoo early stumble suffer yard hybrid blade body";

    // This is our entrypoint to work w/ the contract
    private final VestingValidator vestingValidator = new VestingValidator(preprod());

    // Owner account
    private final Account owner = new Account(preprod(), OWNER_MNEMONIC);

    // Beneficiary account
    private final Account beneficiary = new Account(preprod(), BENEFICIARY_MNEMONIC);

    // Blockfrost API
    private final BFBackendService bfBackendService = new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, "preprod9v5e2dLyUa88oRBNQctiIX469J204eeO");
//    private BFBackendService bfBackendService = new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, "<enter api key here>");

    @Test
    public void payToContract() throws Exception {

        var ownerPublicKeyHash = owner.getBaseAddress().getPaymentCredentialHash().get();
        log.info("Owner public key hash Hex Encoded: {}", HexUtil.encodeHexString(ownerPublicKeyHash));

        var beneficiaryPublicKeyHash = beneficiary.getBaseAddress().getPaymentCredentialHash().get();
        log.info("Beneficiary public key hash Hex Encoded: {}", HexUtil.encodeHexString(beneficiaryPublicKeyHash));

        // Set vesting time in 10 minutes
        // Please note that:
        // 1. the chain only understand UTC time.
        // 2. the time is in POSIX Time in Milliseconds
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var in10Minutes = now.plusMinutes(10).toEpochSecond(ZoneOffset.UTC) * 1_000;

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

        var transaction = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(owner))
                .feePayer(owner.baseAddress())
                .buildAndSign();

        log.info("tx: {}", HexUtil.encodeHexString(transaction.serialize()));

    }

    @Test
    public void withdraw() throws Exception {

        var transactionHash = "cd1ba539157384ed1db549164cf2a19d71197ce86475dc0d746e0cfc4f2a2da1";
        var outputIndex = 0;

        var utxo = bfBackendService.getUtxoService().getTxOutput(transactionHash, outputIndex).getValue();
        log.info("utxo: {}", utxo);

        var tx = new ScriptTx()
                .collectFrom(utxo, ConstrPlutusData.of(0))
                .payToAddress(owner.baseAddress(), utxo.getAmount())
                .attachSpendingValidator(vestingValidator.getPlutusScript());

        var recentSlot = bfBackendService.getBlockService().getLatestBlock().getValue().getSlot();

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(bfBackendService);

        quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(owner))
                .withRequiredSigners(owner.getBaseAddress())
                .feePayer(owner.baseAddress())
                .validFrom(recentSlot - 60)
                .validTo(recentSlot + 120)
                .completeAndWait();


    }

    @Test
    public void collect() throws Exception {

        var transactionHash = "b05f283816b5baec9e791909196d40c713c01d647423dc1638fb129ca478cf50";
        var outputIndex = 0;

        var utxo = bfBackendService.getUtxoService().getTxOutput(transactionHash, outputIndex).getValue();
        log.info("utxo: {}", utxo);

        var tx = new ScriptTx()
                .collectFrom(utxo, ConstrPlutusData.of(0))
                .payToAddress(beneficiary.baseAddress(), utxo.getAmount())
                .attachSpendingValidator(vestingValidator.getPlutusScript());

        var recentSlot = bfBackendService.getBlockService().getLatestBlock().getValue().getSlot();

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(bfBackendService);

        quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(beneficiary))
                .withRequiredSigners(beneficiary.getBaseAddress())
                .feePayer(beneficiary.baseAddress())
                .validFrom(recentSlot - 60)
                .validTo(recentSlot + 120)
                .completeAndWait();


    }

}
