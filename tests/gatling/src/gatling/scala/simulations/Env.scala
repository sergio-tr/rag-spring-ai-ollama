package simulations

object Env {

  def env(key: String, default: String): String =
    Option(System.getenv(key)).map(_.trim).filter(_.nonEmpty).getOrElse(default)

  def envInt(key: String, default: Int): Int =
    Option(System.getenv(key))
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(s => scala.util.Try(s.toInt).toOption)
      .getOrElse(default)

  def envDouble(key: String, default: Double): Double =
    Option(System.getenv(key))
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(s => scala.util.Try(s.toDouble).toOption)
      .getOrElse(default)

  def stripTrailingSlash(path: String): String =
    if (path.endsWith("/") && path.length > 1) path.dropRight(1) else path
}
