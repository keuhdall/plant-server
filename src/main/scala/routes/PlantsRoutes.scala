package routes

import cats.effect.{Async, Resource}
import cats.implicits.toSemigroupKOps
import org.http4s.HttpRoutes

import plants.PlantsService
import smithy4s.http4s.SimpleRestJsonBuilder

object PlantsRoutes {
  def apply[F[_]: Async](
      plantsService: PlantsService[F]
  ): Resource[F, HttpRoutes[F]] = SimpleRestJsonBuilder
    .routes(plantsService)
    .resource
    .map(_ <+> smithy4s.http4s.swagger.docs[F](PlantsService))
}
