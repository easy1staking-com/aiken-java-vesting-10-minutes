use aiken/hash.{Blake2b_224, Hash}
use aiken/interval.{Finite}
use aiken/list
use aiken/time.{PosixTime}
use aiken/transaction.{ScriptContext, Spend, Transaction, ValidityRange}
use aiken/transaction/credential.{VerificationKey}

type Datum {
  /// POSIX time in millisecond, e.g. 1672843961000
  lock_until: PosixTime,
  /// Owner's credentials
  owner: Hash<Blake2b_224, VerificationKey>,
  /// Beneficiary's credentials
  beneficiary: Hash<Blake2b_224, VerificationKey>,
}

type Redeemer {
  Withdraw
  Collect
}

validator {
  fn vesting(datum: Datum, redeemer: Redeemer, ctx: ScriptContext) {
    when ctx.purpose is {
      Spend(_) ->
        when redeemer is {
          Withdraw -> must_be_signed_by(ctx.transaction, datum.owner)
          Collect -> and {
              must_be_signed_by(ctx.transaction, datum.beneficiary),
              must_start_after(ctx.transaction.validity_range, datum.lock_until),
            }
        }
      _ -> False
    }
  }
}

fn must_be_signed_by(
  transaction: Transaction,
  vk: Hash<Blake2b_224, VerificationKey>,
) {
  list.has(transaction.extra_signatories, vk)
}

fn must_start_after(range: ValidityRange, lock_expiration_time: PosixTime) {
  when range.lower_bound.bound_type is {
    Finite(tx_earliest_time) -> lock_expiration_time <= tx_earliest_time
    _ -> False
  }
}
