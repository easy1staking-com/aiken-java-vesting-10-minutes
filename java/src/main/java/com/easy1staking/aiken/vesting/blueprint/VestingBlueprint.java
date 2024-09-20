package com.easy1staking.aiken.vesting.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

@Blueprint(file = "../vesting/plutus.json", packageName = "com.easy1staking.aiken.vesting.contract")
public interface VestingBlueprint {
}
