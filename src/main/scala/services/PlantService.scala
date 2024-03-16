package services

import scala.concurrent.duration.*

import cats.Semigroup
import cats.effect.{Ref, Temporal}
import cats.implicits.*
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Meter

import plants.PlantsService

enum PlantState:
  case Ok, NoWater, Notified

final case class PlantHealthCheck(
    lastChecked: Duration,
    state: PlantState,
    waterLevel: Int
)

given Semigroup[PlantHealthCheck] = (x: PlantHealthCheck, y: PlantHealthCheck) =>
  PlantHealthCheck(
    lastChecked = x.lastChecked.max(y.lastChecked),
    state = (x.state, y.state) match {
      case (PlantState.Notified, _) | (_, PlantState.Notified) => PlantState.Notified
      case (PlantState.NoWater, _) | (_, PlantState.NoWater)   => PlantState.NoWater
      case (PlantState.Ok, _) | (_, PlantState.Ok)             => PlantState.Ok
    },
    waterLevel = x.waterLevel.max(y.waterLevel)
  )

object PlantsService {
  def apply[F[_]: Temporal: Meter]: F[PlantsService[F]] =
    for {
      ref <- Ref.empty[F, Map[Int, PlantHealthCheck]]
      waterLevelHist <- Meter[F].histogram("waterLevel").withUnit("%").create
    } yield new PlantsService[F] {
      import PlantState.*

      private val waterLevelThreshold = 10
      private val noHealthMaxDuration = 60.seconds

      override def health(id: Int, waterLevel: Int): F[Unit] =
        for {
          now <- Temporal[F].monotonic
          newState = if (waterLevel <= waterLevelThreshold) NoWater else Ok
          currentHealth = PlantHealthCheck(
            lastChecked = now,
            state = newState,
            waterLevel = waterLevel
          )
          _ <- waterLevelHist.record(waterLevel, Attribute[Long]("id", id))
          _ <- ref.update(_.updated(1, currentHealth))
        } yield ()

      def check: F[Unit] = ((for {
        states <- ref.get
        now <- Temporal[F].monotonic
        _ <- states.toList.traverse_ { (id, health) =>
          if (now - health.lastChecked >= noHealthMaxDuration)
            ref.update(_.updated(id, health.copy(state = Notified))) *>
              Temporal[F].unit
          else
            health.state match {
              case PlantState.Ok | Notified => Temporal[F].unit
              case PlantState.NoWater =>
                ref.update(_.updated(id, health.copy(state = Notified))) *>
                  Temporal[F].unit
            }
        }
      } yield ()) *> Temporal[F].sleep(30.seconds)).foreverM
    }
}
