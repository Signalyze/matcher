package com.wavesplatform.dex.it.dex

import java.util.Properties
import java.util.concurrent.ThreadLocalRandom

import cats.Functor
import com.typesafe.config.{Config, ConfigFactory}
import com.wavesplatform.dex.it.api.BaseContainersKit
import com.wavesplatform.dex.it.docker.DexContainer
import com.wavesplatform.dex.it.fp.CanExtract
import mouse.any._
import org.apache.kafka.clients.admin.{AdminClient, NewTopic}

import scala.jdk.CollectionConverters._

trait HasDex { self: BaseContainersKit =>
  private val defaultTag = Option(System.getenv("DEX_TAG")).getOrElse("latest")

  protected implicit def toDexExplicitGetOps[F[_]: CanExtract: Functor](self: DexApi[F]): DexApiOps.ExplicitGetDexApiOps[F] = {
    new DexApiOps.ExplicitGetDexApiOps[F](self)
  }

  protected def dexInitialSuiteConfig: Config = ConfigFactory.empty()

  protected lazy val dexRunConfig: Config = dexQueueConfig(ThreadLocalRandom.current.nextInt(0, Int.MaxValue))

  protected def kafkaServer: Option[String] = Option { System.getenv("KAFKA_SERVER") }

  protected def dexQueueConfig(queueId: Int): Config = {
    kafkaServer.fold { ConfigFactory.empty() } { kafkaServer =>
      ConfigFactory.parseString(s"""waves.dex.events-queue {
                                   |  type = kafka
                                   |  kafka {
                                   |    servers = "$kafkaServer"
                                   |    topic = "dex-$queueId"
                                   |  }
                                   |}""".stripMargin)
    }
  }

  protected def createDex(name: String,
                          runConfig: Config = dexRunConfig,
                          suiteInitialConfig: Config = dexInitialSuiteConfig,
                          tag: String = defaultTag): DexContainer =
    DexContainer(name, networkName, network, getIp(name), runConfig, suiteInitialConfig, localLogsDir, tag) unsafeTap addKnownContainer

  lazy val dex1: DexContainer = createDex("dex-1")

  protected def createKafkaTopic(name: String): Unit = kafkaServer.foreach { server =>
    val properties = new Properties()
    properties.putAll(
      Map(
        "bootstrap.servers"  -> server,
        "group.id"           -> s"create-$name",
        "key.deserializer"   -> "org.apache.kafka.common.serialization.StringDeserializer",
        "value.deserializer" -> "org.apache.kafka.common.serialization.StringDeserializer"
      ).asJava
    )

    val adminClient = AdminClient.create(properties)

    try {
      val newTopic = new NewTopic(name, 1, 1.toShort)
      adminClient.createTopics(java.util.Collections.singletonList(newTopic))
    } finally {
      adminClient.close()
    }
  }
}
