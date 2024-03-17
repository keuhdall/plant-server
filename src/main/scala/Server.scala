import cats.effect.*
import cats.effect.implicits.{effectResourceOps, genSpawnOps}
import cats.effect.kernel.{Async, Resource}
import com.comcast.ip4s.{ipv4, port}
import io.opentelemetry.api.GlobalOpenTelemetry
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.otel4s.java.OtelJava
import org.typelevel.otel4s.metrics.Meter

import config.DiscordConfig
import fs2.io.net.Network
import routes.PlantsRoutes
import services.PlantsService

object Server extends IOApp {

  private def app[F[_]: Async: LiftIO: Network: LoggerFactory]: Resource[F, Unit] = {
    val logger = LoggerFactory[F].getLogger
    for {
      otel <- Resource
        .eval(Async[F].delay(GlobalOpenTelemetry.get))
        .evalMap(OtelJava.forAsync[F])
      given Meter[F] <- otel.meterProvider.get("plantServer").toResource
      discordConfig <- DiscordConfig.load[F]
      client <- EmberClientBuilder.default.build
      plantsService <- PlantsService[F](client, discordConfig)
      _ <- plantsService.check.start.toResource
      plantsRoutes <- PlantsRoutes[F](plantsService)
      server <- EmberServerBuilder
        .default[F]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(plantsRoutes.orNotFound)
        .build
      _ <- logger
        .info(s"Server started and listening to port ${server.address.getPort}")
        .toResource
    } yield ()
  }

  override def run(args: List[String]): IO[ExitCode] = {
    given LoggerFactory[IO] = Slf4jFactory.create[IO]
    app[IO].useForever.as(ExitCode.Success)
  }
}
