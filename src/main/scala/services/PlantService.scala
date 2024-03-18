package services

import scala.concurrent.duration.*

import cats.Semigroup
import cats.effect.{Ref, Resource, Temporal}
import cats.implicits.*
import io.circe.derivation.{Configuration, ConfiguredCodec}
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.client.Client
import org.http4s.{Method, Request}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Meter

import config.DiscordConfig
import plants.PlantsService

enum PlantState:
  case Ok, NoWater, Notified

final case class PlantHealthCheck(
    lastChecked: Duration,
    state: PlantState,
    waterLevel: Int
)

given Configuration = Configuration.default
final case class DiscordMessage(content: String) derives ConfiguredCodec

given Semigroup[PlantHealthCheck] = (old: PlantHealthCheck, `new`: PlantHealthCheck) =>
  PlantHealthCheck(
    lastChecked = old.lastChecked.max(`new`.lastChecked),
    state = (old.state, `new`.state) match {
      case (_, PlantState.Ok)                                  => PlantState.Ok
      case (PlantState.Notified, _) | (_, PlantState.Notified) => PlantState.Notified
      case (_, PlantState.NoWater)                             => PlantState.NoWater
    },
    waterLevel = old.waterLevel.min(`new`.waterLevel)
  )

trait StatefulPlantService[F[_]] extends PlantsService[F] {
  def check: F[Unit]
}

object PlantsService {
  def apply[F[_]: Temporal: LoggerFactory: Meter](
      client: Client[F],
      discordConfig: DiscordConfig
  ): Resource[F, StatefulPlantService[F]] =
    Resource.eval(for {
      ref <- Ref.empty[F, Map[Int, PlantHealthCheck]]
      waterLevelHist <- Meter[F].histogram("waterLevel").withUnit("%").create
      logger = LoggerFactory[F].getLogger
    } yield new StatefulPlantService[F] {
      import PlantState.*

      private val waterLevelThreshold = 10
      private val noHealthMaxDuration = 60.seconds
      private val webhookUri = discordConfig.getWebhookUri

      private def noWaterNotification(id: Int) = Request[F](
        method = Method.POST,
        uri = webhookUri
      ).withEntity(
        DiscordMessage(
          s"<@${discordConfig.userId}> Il n'y a plus d'eau dans le reservoir #$id ! 💦🌻"
        )
      )

      private def koNotification(id: Int) = Request[F](
        method = Method.POST,
        uri = webhookUri
      ).withEntity(
        DiscordMessage(
          s"<@${discordConfig.userId}> ⚠️ L'arduino #$id ne répond plus ! ⚠️"
        )
      )

      private def setNotified(id: Int, health: PlantHealthCheck): F[Unit] =
        ref.update(_.updated(id, health.copy(state = Notified)))

      override def health(id: Int, waterLevel: Int): F[Unit] =
        for {
          now <- Temporal[F].monotonic
          newHealth = PlantHealthCheck(
            lastChecked = now,
            state = if (waterLevel <= waterLevelThreshold) NoWater else Ok,
            waterLevel = waterLevel
          )
          currentHealth <- ref.get.map(_.getOrElse(id, newHealth))
          _ <- waterLevelHist.record(waterLevel, Attribute[Long]("id", id))
          _ <- ref.update(_.updated(id, currentHealth |+| newHealth))
        } yield ()

      override def check: F[Unit] = ((for {
        states <- ref.get
        _ <- logger.info(s"checking state for ${states.size} devices...")
        now <- Temporal[F].monotonic
        _ <- states.toList.traverse_ { (id, health) =>
          if (now - health.lastChecked >= noHealthMaxDuration && health.state != Notified)
            client.expect[Unit](koNotification(id)) *> setNotified(id, health)
          else
            health.state match {
              case PlantState.Ok | Notified => Temporal[F].unit
              case PlantState.NoWater =>
                client.expect[Unit](noWaterNotification(id)) *> setNotified(id, health)
            }
        }
      } yield ()) *> Temporal[F].sleep(30.seconds)).foreverM
    })
}
