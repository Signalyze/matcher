package com.wavesplatform.dex.db

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.Files
import java.util.Base64

import cats.syntax.either._
import com.google.common.primitives.{Bytes, Ints}
import com.wavesplatform.dex.crypto.Enigma
import com.wavesplatform.dex.db.AccountStorage.Settings.EncryptedFile
import com.wavesplatform.dex.domain.account.KeyPair
import com.wavesplatform.dex.domain.bytes.ByteStr
import com.wavesplatform.dex.domain.crypto
import net.ceedubs.ficus.readers.ValueReader

import scala.collection.mutable.ArrayBuffer

case class AccountStorage(keyPair: KeyPair)

object AccountStorage {

  sealed trait Settings

  object Settings {

    case class InMem(seed: ByteStr)                        extends Settings
    case class EncryptedFile(path: File, password: String) extends Settings

    implicit val valueReader: ValueReader[Settings] = ValueReader.relative[Settings] { config =>
      config.getString("type") match {
        case "in-mem" => InMem(Base64.getDecoder.decode(config.getString("in-mem.seed-in-base64")))
        case "encrypted-file" =>
          EncryptedFile(
            path = new File(config.getString("encrypted-file.path")),
            password = config.getString("encrypted-file.password")
          )
        case x => throw new IllegalArgumentException(s"The type of account storage '$x' is unknown. Please update your settings.")
      }
    }
  }

  def load(settings: Settings): Either[String, AccountStorage] = settings match {
    case Settings.InMem(seed) => Right(AccountStorage(KeyPair(seed)))
    case Settings.EncryptedFile(file, password) =>
      if (file.isFile) {
        val encryptedSeedBytes = readFile(file)
        val key                = Enigma.prepareDefaultKey(password)
        val decryptedBytes     = Enigma.decrypt(key, encryptedSeedBytes)
        AccountStorage(KeyPair(decryptedBytes)).asRight
      } else s"A file '${file.getAbsolutePath}' doesn't exist".asLeft
  }

  def save(seed: ByteStr, to: EncryptedFile): Unit = {
    Files.createDirectories(to.path.getParentFile.toPath)
    val key                = Enigma.prepareDefaultKey(to.password)
    val encryptedSeedBytes = Enigma.encrypt(key, seed.arr)
    writeFile(to.path, encryptedSeedBytes)
  }

  def getAccountSeed(baseSeed: ByteStr, nonce: Int): ByteStr = ByteStr(crypto.secureHash(Bytes.concat(Ints.toByteArray(nonce), baseSeed)))

  def readFile(file: File): Array[Byte] = {
    val reader = new FileInputStream(file)
    try {
      val buff = new Array[Byte](1024)
      val r    = new ArrayBuffer[Byte]
      while (reader.available() > 0) {
        val read = reader.read(buff)
        if (read > 0) {
          r.appendAll(buff.iterator.take(read))
        }
      }
      r.toArray
    } finally {
      reader.close()
    }
  }

  def writeFile(file: File, bytes: Array[Byte]): Unit = {
    val writer = new FileOutputStream(file, false)
    try writer.write(bytes)
    finally writer.close()
  }
}
