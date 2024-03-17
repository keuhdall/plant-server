package errors

import pureconfig.error.ConfigReaderFailures

sealed trait ConfigFailure extends Throwable

final case class DiscordConfigFailure(failures: ConfigReaderFailures)
    extends ConfigFailure {
  override def getMessage: String =
    s"failed to load discord config: ${failures.prettyPrint}"
}
