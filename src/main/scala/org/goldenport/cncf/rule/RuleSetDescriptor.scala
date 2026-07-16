package org.goldenport.cncf.rule

import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * Restricted Record decoder for declarative RuleSet identity and rule metadata.
 * Executable expressions and runtime action admission are deliberately outside
 * this decoder; descriptors cannot embed host-language code or ActionCall.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
object RuleSetDescriptor {
  def decodeC(record: Record): Consequence[RuleSet] =
    for {
      id <- _required_string_c(record, "id")
      version <- _required_string_c(record, "version")
      rulesrecord <- _required_records_c(record, "rules")
      rules <- rulesrecord.foldLeft(Consequence.success(Vector.empty[Rule])) { (z, value) =>
        z.flatMap(values => _rule_c(value).map(values :+ _))
      }
      inputfacts <- _fact_names_c(record, "inputFacts")
      outputfacts <- _fact_names_c(record, "outputFacts")
      metadata <- _optional_record_c(record, "metadata")
      ruleset <- RuleSet.createC(
        RuleSetIdentity(RuleSetId(id), RuleSetVersion(version)),
        rules,
        inputfacts,
        outputfacts,
        metadata
      )
    } yield ruleset

  private def _rule_c(record: Record): Consequence[Rule] =
    for {
      id <- _required_string_c(record, "id")
      familytoken <- _required_string_c(record, "family")
      family <- RuleFamily.parseC(familytoken)
      priority <- _priority_c(record)
      conditions <- _optional_record_c(record, "conditions")
      outputs <- _optional_record_c(record, "outputs")
      explanationattributes <- _optional_record_c(record, "explanationAttributes")
    } yield Rule(
      RuleId(id),
      family,
      priority,
      RuleDefinition(
        conditions,
        outputs
      ),
      RuleExplanationTemplate(
        record.getString("explanation").getOrElse(""),
        explanationattributes
      )
    )

  private def _priority_c(record: Record): Consequence[RulePriority] =
    record.getAny("priority") match {
      case None => Consequence.success(RulePriority.Default)
      case Some(value: Byte) => Consequence.success(RulePriority(value.toInt))
      case Some(value: Short) => Consequence.success(RulePriority(value.toInt))
      case Some(value: Int) => Consequence.success(RulePriority(value))
      case Some(value: Long) if value.isValidInt => Consequence.success(RulePriority(value.toInt))
      case Some(value: BigInt) if value.isValidInt => Consequence.success(RulePriority(value.toInt))
      case Some(value: BigDecimal) if value.isValidInt => Consequence.success(RulePriority(value.toInt))
      case Some(value: java.math.BigDecimal) if value.scale <= 0 && value.intValueExact == value.intValue =>
        Consequence.success(RulePriority(value.intValueExact))
      case Some(value) => Consequence.argumentFormatError("priority", "integral Rule priority", value)
    }

  private def _fact_names_c(record: Record, key: String): Consequence[Vector[FactName]] =
    record.getAny(key) match {
      case None => Consequence.success(Vector.empty)
      case Some(values: Seq[?]) => _fact_names_c(values.toVector, key)
      case Some(values: java.util.List[?]) => _fact_names_c(values.asScala.toVector, key)
      case Some(value) => Consequence.argumentFormatError(key, "sequence of fact names", value)
    }

  private def _fact_names_c(values: Vector[?], key: String): Consequence[Vector[FactName]] =
    if (values.exists(value => Option(value).forall(_.toString.trim.isEmpty)))
      Consequence.argumentInvalid(key, "non-empty fact names", values)
    else
      Consequence.success(values.map(value => FactName(value.toString.trim)))

  private def _required_records_c(record: Record, key: String): Consequence[Vector[Record]] =
    record.getAny(key) match {
      case Some(values: Seq[?]) => _records_c(values.toVector, key)
      case Some(values: java.util.List[?]) => _records_c(values.asScala.toVector, key)
      case Some(value) => Consequence.argumentFormatError(key, "sequence of Rule records", value)
      case None => Consequence.argumentMissing(key)
    }

  private def _records_c(values: Vector[?], key: String): Consequence[Vector[Record]] =
    values.foldLeft(Consequence.success(Vector.empty[Record])) { (z, value) =>
      z.flatMap { records =>
        _to_record(value) match {
          case Some(record) => Consequence.success(records :+ record)
          case None => Consequence.argumentFormatError(key, "Rule record", value)
        }
      }
    }

  private def _required_string_c(record: Record, key: String): Consequence[String] =
    record.getString(key).map(_.trim).filter(_.nonEmpty).map(Consequence.success).getOrElse(
      Consequence.argumentMissing(key)
    )

  private def _optional_record_c(record: Record, key: String): Consequence[Record] =
    record.getAny(key) match {
      case None => Consequence.success(Record.empty)
      case Some(value) =>
        _to_record(value).map(Consequence.success).getOrElse(
          Consequence.argumentFormatError(key, "Record", value)
        )
    }

  private def _to_record(value: Any): Option[Record] =
    value match {
      case record: Record => Some(record)
      case values: Map[?, ?] => Some(Record.data(values.toVector.map { case (key, value) => key.toString -> value }*))
      case values: java.util.Map[?, ?] => Some(Record.data(values.asScala.toVector.map { case (key, value) => key.toString -> value }*))
      case _ => None
    }
}
