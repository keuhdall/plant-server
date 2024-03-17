package config

import cats.MonadThrow
import cats.effect.Resource
import cats.effect.implicits.effectResourceOps
import cats.implicits.catsSyntaxEither
import org.http4s.Uri
import org.http4s.implicits.uri

import errors.DiscordConfigFailure
import pureconfig.generic.derivation.default.*
import pureconfig.{ConfigReader, ConfigSource}

final case class DiscordConfig(
    webhookId: String,
    webhookToken: String,
    userId: String
) derives ConfigReader {
  private val baseDiscordUri = uri"https://discord.com/api/webhooks/"
  def getWebhookUri: Uri = baseDiscordUri.addPath(webhookId).addPath(webhookToken)
}

object DiscordConfig {
  def load[F[_]: MonadThrow]: Resource[F, DiscordConfig] =
    ConfigSource.default
      .at("discord")
      .load[DiscordConfig]
      .leftMap(DiscordConfigFailure.apply)
      .liftTo[F]
      .toResource
}
