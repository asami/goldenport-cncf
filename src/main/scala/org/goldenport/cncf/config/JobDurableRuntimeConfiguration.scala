package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{CanonicalParameterId, ConfigurationBindingCollection, ConfigurationParameter, ConfigurationValue, ConfigurationValueCodec}

/*
 * Value-only durable runtime admission; provider identity is never interpreted.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final case class JobDurableRuntimeConfiguration(
  providerid: Option[String],
  periodmillis: Long,
  maxcandidates: Int
)

private[cncf] object JobDurableRuntimeConfiguration {
  val default: JobDurableRuntimeConfiguration = JobDurableRuntimeConfiguration(None, 60000L, 100)
  val providerKey = "textus.job.durable.provider"
  val periodKey = "textus.job.durable.maintenance-period-millis"
  val candidatesKey = "textus.job.durable.max-candidates"

  def validProvider(value: String): Boolean =
    value != null && value.length >= 1 && value.length <= 64 &&
      _letter(value.head) && value.tail.forall(x => _letter(x) || x >= '0' && x <= '9' || x == '_')

  val provider: ConfigurationParameter[String] = _parameter(providerKey, new ConfigurationValueCodec[String] {
    def decode(value: ConfigurationValue): Consequence[String] = value match {
      case ConfigurationValue.StringValue(text) if validProvider(text) => Consequence.success(text)
      case _ => _refused(providerKey)
    }
    def encode(value: String): Consequence[ConfigurationValue] =
      if (validProvider(value)) Consequence.success(ConfigurationValue.StringValue(value)) else _refused(providerKey)
  })
  val period: ConfigurationParameter[Long] = _parameter(periodKey, _integer_codec(periodKey, 1000L, 86400000L, 8))
  val candidates: ConfigurationParameter[Int] = _parameter(candidatesKey, new ConfigurationValueCodec[Int] {
    private val _codec = _integer_codec(candidatesKey, 1L, 1024L, 4)
    def decode(value: ConfigurationValue): Consequence[Int] = _codec.decode(value).map(_.toInt)
    def encode(value: Int): Consequence[ConfigurationValue] = _codec.encode(value.toLong)
  })

  val definitions: Vector[CncfConfigurationParameterDefinition] = Vector(
    _definition(provider, true), _definition(period, false), _definition(candidates, false)
  )

  def from(bindings: ConfigurationBindingCollection[CncfConfigurationTarget]): Consequence[JobDurableRuntimeConfiguration] =
    if (bindings == null) _refused(providerKey)
    else for {
      selected <- bindings.value(provider)
      interval <- bindings.value(period)
      bound <- bindings.value(candidates)
      result <- if (selected.isEmpty && (interval.nonEmpty || bound.nonEmpty)) _refused(providerKey)
        else Consequence.success(JobDurableRuntimeConfiguration(selected, interval.getOrElse(60000L), bound.getOrElse(100)))
    } yield result

  private def _letter(character: Char): Boolean =
    character >= 'A' && character <= 'Z' || character >= 'a' && character <= 'z'

  private def _integer_codec(key: String, minimum: Long, maximum: Long, digits: Int): ConfigurationValueCodec[Long] =
    new ConfigurationValueCodec[Long] {
      def decode(value: ConfigurationValue): Consequence[Long] = value match {
        case ConfigurationValue.StringValue(text) if text != null && text.nonEmpty && text.length <= digits &&
          text.forall(x => x >= '0' && x <= '9') => _admit(text.toLong)
        case ConfigurationValue.NumberValue(number) if number != null && number.isWhole && number.isValidLong => _admit(number.toLong)
        case _ => _refused(key)
      }
      def encode(value: Long): Consequence[ConfigurationValue] = _admit(value).map(x => ConfigurationValue.NumberValue(BigDecimal(x)))
      private def _admit(value: Long): Consequence[Long] =
        if (value >= minimum && value <= maximum) Consequence.success(value) else _refused(key)
    }

  private def _parameter[A](key: String, codec: ConfigurationValueCodec[A]): ConfigurationParameter[A] =
    ConfigurationParameter.create(CanonicalParameterId.parse(key).TAKE, codec).TAKE

  private def _definition[A](parameter: ConfigurationParameter[A], confidential: Boolean): CncfConfigurationParameterDefinition = {
    val suffix = parameter.id.value.stripPrefix("textus.")
    CncfConfigurationParameterDefinition.registered(parameter.id.value,
      Vector(s"textus.runtime.$suffix", s"cncf.$suffix", s"cncf.runtime.$suffix"), parameter,
      Vector("phase-69.7: configured durable runtime"), isConfidential = confidential,
      Set(CncfConfigurationTargetKind.SubsystemInstance)).TAKE
  }

  private def _refused[A](key: String): Consequence[A] = Consequence.configurationInvalid(key)
}
