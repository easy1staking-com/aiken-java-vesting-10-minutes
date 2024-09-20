use aiken/collection/list
use aiken/crypto.{Blake2b_224, Hash, VerificationKey}
use aiken/interval.{Finite}
use cardano/transaction.{OutputReference, Transaction, ValidityRange}

pub type Datum {
  /// POSIX time in millisecond, e.g. 1672843961000
  lock_until: Int,
  /// Owner's credentials
  owner: Hash<Blake2b_224, VerificationKey>,
  /// Beneficiary's credentials
  beneficiary: Hash<Blake2b_224, VerificationKey>,
}

fn must_be_signed_by(
  transaction: Transaction,
  vk: Hash<Blake2b_224, VerificationKey>,
) {
  list.has(transaction.extra_signatories, vk)
}

fn must_start_after(range: ValidityRange, lock_expiration_time: Int) {
  when range.lower_bound.bound_type is {
    Finite(tx_earliest_time) -> lock_expiration_time <= tx_earliest_time
    _ -> False
  }
}

validator vesting {
  spend(
    datum: Option<Datum>,
    _redeemer: Void,
    _utxo: OutputReference,
    self: Transaction,
  ) {
    expect Some(vesting_datum) = datum
    or {
      must_be_signed_by(self, vesting_datum.owner),
      and {
        must_be_signed_by(self, vesting_datum.beneficiary),
        must_start_after(self.validity_range, vesting_datum.lock_until),
      },
    }
  }

  else(_) {
    fail
  }
}
