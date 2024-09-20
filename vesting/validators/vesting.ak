use aiken/collection/list
use aiken/crypto.{VerificationKeyHash}
use aiken/interval.{Finite}
use cardano/transaction.{OutputReference, Transaction, ValidityRange}

pub type Datum {
  /// POSIX time in millisecond, e.g. 1672843961000
  lock_until: Int,
  /// Owner's credentials
  owner: VerificationKeyHash,
  /// Beneficiary's credentials
  beneficiary: VerificationKeyHash,
}

fn must_be_signed_by(transaction: Transaction, vk: VerificationKeyHash) {
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
