package com.wavesplatform.transaction.transfer

import com.google.common.primitives.Bytes
import com.wavesplatform.account.{AddressOrAlias, PrivateKeyAccount, PublicKeyAccount}
import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.common.utils.EitherExt2
import com.wavesplatform.crypto
import com.wavesplatform.transaction._
import com.wavesplatform.transaction.description._
import monix.eval.Coeval

import scala.util.Try

case class TransferTransactionV2 private (sender: PublicKeyAccount,
                                          recipient: AddressOrAlias,
                                          assetId: Option[AssetId],
                                          amount: Long,
                                          timestamp: Long,
                                          feeAssetId: Option[AssetId],
                                          fee: Long,
                                          attachment: Array[Byte],
                                          proofs: Proofs)
    extends TransferTransaction
    with ProvenTransaction
    with FastHashId {

  override val builder: TransactionParser     = TransferTransactionV2
  override val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce(Array(builder.typeId, version) ++ bytesBase())
  override val bytes: Coeval[Array[Byte]]     = Coeval.evalOnce(Bytes.concat(Array(0: Byte), bodyBytes(), proofs.bytes()))

  override def version: Byte = 2
}

object TransferTransactionV2 extends TransactionParserFor[TransferTransactionV2] with TransactionParser.MultipleVersions {

  override val typeId: Byte                 = TransferTransaction.typeId
  override val supportedVersions: Set[Byte] = Set(2)

  override protected def parseTail(bytes: Array[Byte]): Try[TransactionT] = {
    byteTailDescription.deserializeFromByteArray(bytes).flatMap { tx =>
      TransferTransaction
        .validate(tx)
        .map(_ => tx)
        .foldToTry
    }
  }

  def create(assetId: Option[AssetId],
             sender: PublicKeyAccount,
             recipient: AddressOrAlias,
             amount: Long,
             timestamp: Long,
             feeAssetId: Option[AssetId],
             feeAmount: Long,
             attachment: Array[Byte],
             proofs: Proofs): Either[ValidationError, TransactionT] = {
    for {
      _ <- TransferTransaction.validate(amount, feeAmount, attachment)
    } yield TransferTransactionV2(sender, recipient, assetId, amount, timestamp, feeAssetId, feeAmount, attachment, proofs)
  }

  def signed(assetId: Option[AssetId],
             sender: PublicKeyAccount,
             recipient: AddressOrAlias,
             amount: Long,
             timestamp: Long,
             feeAssetId: Option[AssetId],
             feeAmount: Long,
             attachment: Array[Byte],
             signer: PrivateKeyAccount): Either[ValidationError, TransactionT] = {
    create(assetId, sender, recipient, amount, timestamp, feeAssetId, feeAmount, attachment, Proofs.empty).right.map { unsigned =>
      unsigned.copy(proofs = Proofs.create(Seq(ByteStr(crypto.sign(signer, unsigned.bodyBytes())))).explicitGet())
    }
  }

  def selfSigned(assetId: Option[AssetId],
                 sender: PrivateKeyAccount,
                 recipient: AddressOrAlias,
                 amount: Long,
                 timestamp: Long,
                 feeAssetId: Option[AssetId],
                 feeAmount: Long,
                 attachment: Array[Byte]): Either[ValidationError, TransactionT] = {
    signed(assetId, sender, recipient, amount, timestamp, feeAssetId, feeAmount, attachment, sender)
  }

  val byteTailDescription: ByteEntity[TransferTransactionV2] = {
    (PublicKeyAccountBytes(tailIndex(1), "Sender's public key") ~
      OptionBytes[AssetId](index = tailIndex(2), name = "Asset ID", nestedByteEntity = AssetIdBytes(tailIndex(2), "Asset ID")) ~
      OptionBytes[AssetId](index = tailIndex(3), name = "Fee's asset ID", nestedByteEntity = AssetIdBytes(tailIndex(3), "Fee's asset ID")) ~
      LongBytes(tailIndex(4), "Timestamp") ~
      LongBytes(tailIndex(5), "Amount") ~
      LongBytes(tailIndex(6), "Fee") ~
      AddressOrAliasBytes(tailIndex(7), "Recipient") ~
      BytesArrayUndefinedLength(tailIndex(8), "Attachment") ~
      ProofsBytes(tailIndex(9))).map {
      case ((((((((senderPublicKey, assetId), feeAssetId), timestamp), amount), fee), recipient), attachments), proofs) =>
        TransferTransactionV2(
          senderPublicKey,
          recipient,
          assetId,
          amount,
          timestamp,
          feeAssetId,
          fee,
          attachments,
          proofs
        )
    }
  }
}
