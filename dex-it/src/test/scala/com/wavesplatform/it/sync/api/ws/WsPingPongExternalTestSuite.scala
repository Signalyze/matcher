package com.wavesplatform.it.sync.api.ws

import com.typesafe.config.{Config, ConfigFactory}
import com.wavesplatform.dex.api.ws.protocol.WsError

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class WsPingPongExternalTestSuite extends WsPingPongBaseSuite {

  protected val maxConnectionLifetime = 6.seconds

  override protected lazy val wsStreamUri: String = getWsStreamUri(dex1)

  override protected val dexInitialSuiteConfig: Config = ConfigFactory
    .parseString(
      s"""waves.dex.web-sockets.external-client-handler {
        |    max-connection-lifetime = $maxConnectionLifetime
        |    health-check = {
        |      ping-interval = $pingInterval
        |      pong-timeout = $pongTimeout
        |    }
        | }
        |""".stripMargin
    )
    .withFallback(jwtPublicKeyConfig)

  "Web socket connection should be closed " - {
    s"by max-connection-lifetime = $maxConnectionLifetime" in {
      val wsac               = mkWsAddressConnection(alice, dex1)
      val connectionLifetime = Await.result(wsac.connectionLifetime, maxConnectionLifetime + delta)

      connectionLifetime should (be >= maxConnectionLifetime and be <= maxConnectionLifetime + delta)
      wsac.pings.size should be >= 5
      wsac.isClosed shouldBe true

      wsac.collectMessages[WsError].head should matchTo(
        WsError(
          timestamp = 0L, // ignored
          code = 109077767, // WsConnectionMaxLifetimeExceeded
          message = "WebSocket has reached max allowed lifetime"
        )
      )
    }
  }
}
