import cats.effect.*
import cats.effect.implicits.effectResourceOps
import com.comcast.ip4s.{ipv4, port}
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import fs2.io.net.Network

object Server extends IOApp {

  private def app[F[_]: Async: Network: LoggerFactory]: Resource[F, Unit] = {
    val logger = LoggerFactory[F].getLogger
    for {
      server <- EmberServerBuilder
        .default[F]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .build
      _ <- logger
        .info(s"Server started and listing to port ${server.address.getPort}")
        .toResource
    } yield ()
  }

  override def run(args: List[String]): IO[ExitCode] = {
    given LoggerFactory[IO] = Slf4jFactory.create[IO]
    app[IO].useForever.as(ExitCode.Success)
  }
}
