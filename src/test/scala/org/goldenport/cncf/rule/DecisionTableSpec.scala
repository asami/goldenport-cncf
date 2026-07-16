package org.goldenport.cncf.rule

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for deterministic engine-neutral decision tables.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class DecisionTableSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _priorities =
    Gen.listOfN(6, Gen.chooseNum(-100, 100))

  "DecisionTableEvaluator" should {
    "select an exact-match price-list row and preserve its Record output" in {
      Given("a product price-list decision table")
      val table = _table(
        "price-list",
        Vector("product"),
        Vector(
          _row("book", conditions = Map("product" -> _equals("book")), output = Record.data("unitPrice" -> BigDecimal(1200))),
          _row("pen", conditions = Map("product" -> _equals("pen")), output = Record.data("unitPrice" -> BigDecimal(150)))
        )
      )
      val inputs = _inputs("product" -> RuleFactValue.scalar("book"))

      When("the product is evaluated")
      val result = _success(DecisionTableEvaluator.evaluateC(table, inputs))

      Then("the price-list row is selected without evaluating a general expression")
      result.selected.map(_.row.id.value) shouldBe Some("book")
      result.selected.map(_.row.output) shouldBe Some(Record.data("unitPrice" -> BigDecimal(1200)))
      result.selected.map(_.matchedColumns) shouldBe Some(Vector("product"))
    }

    "select a consumption-tax rate from a table-oriented jurisdiction rule" in {
      Given("a tax-rate decision table")
      val table = _table(
        "tax-rate",
        Vector("jurisdiction"),
        Vector(
          _row("jp-standard", conditions = Map("jurisdiction" -> _equals("JP")), output = Record.data("taxRate" -> BigDecimal("0.10"))),
          _row("us-standard", conditions = Map("jurisdiction" -> _equals("US")), output = Record.data("taxRate" -> BigDecimal("0.00")))
        )
      )
      val inputs = _inputs("jurisdiction" -> RuleFactValue.scalar("JP"))

      When("the jurisdiction is evaluated")
      val result = _success(DecisionTableEvaluator.evaluateC(table, inputs))

      Then("the table returns the jurisdiction's declared tax rate")
      result.selected.map(_.row.id.value) shouldBe Some("jp-standard")
      result.selected.map(_.row.output) shouldBe Some(Record.data("taxRate" -> BigDecimal("0.10")))
    }

    "select half-open numeric tiers for a quantity discount" in {
      Given("a tiered-discount decision table")
      val table = _table(
        "discount",
        Vector("amount"),
        Vector(
          _row("base", conditions = Map("amount" -> _range(Some(0), Some(10000))), output = Record.data("discountRate" -> BigDecimal(0))),
          _row("standard", conditions = Map("amount" -> _range(Some(10000), Some(50000))), output = Record.data("discountRate" -> BigDecimal("0.10"))),
          _row("volume", conditions = Map("amount" -> _range(Some(50000), None)), output = Record.data("discountRate" -> BigDecimal("0.20")))
        )
      )
      val inputs = _inputs("amount" -> RuleFactValue.scalar(BigDecimal("10000.00")))

      When("a boundary amount is evaluated")
      val result = _success(DecisionTableEvaluator.evaluateC(table, inputs))

      Then("the lower bound is inclusive and the previous tier's upper bound is exclusive")
      result.selected.map(_.row.id.value) shouldBe Some("standard")
      result.selected.map(_.row.output) shouldBe Some(Record.data("discountRate" -> BigDecimal("0.10")))
    }

    "resolve overlapping candidates independently of row declaration order" in {
      Given("generated always-matching rows with stable identifiers and priorities")
      val property = Prop.forAll(_priorities) { priorities =>
        val rows = priorities.zipWithIndex.map { case (priority, index) =>
          _row(
            s"row-$index",
            priority = priority,
            output = Record.data("value" -> index),
            explanation = s"explanation-$index"
          )
        }.toVector
        val left = _table("priority", Vector("input"), rows)
        val right = _table("priority", Vector("input"), rows.reverse)
        val inputs = _inputs("input" -> RuleFactValue.scalar("value"))

        val leftresult = _success(DecisionTableEvaluator.evaluateC(left, inputs))
        val rightresult = _success(DecisionTableEvaluator.evaluateC(right, inputs))

        leftresult.candidates.map(_.row.id.value) == rightresult.candidates.map(_.row.id.value) &&
          leftresult.selected.map(_.row.id.value) == rightresult.selected.map(_.row.id.value) &&
          leftresult.candidates.map(_.row.explanation.summary) ==
            rightresult.candidates.map(_.row.explanation.summary)
      }

      When("equivalent tables are checked across generated row orders")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("priority, specificity, and row identifier provide one canonical selection order")
      checked.passed shouldBe true
    }

    "give explicit row priority precedence over specificity" in {
      Given("an overlapping fallback row with higher priority than a specific row")
      val table = _table(
        "priority",
        Vector("product"),
        Vector(
          _row("fallback", priority = 10, output = Record.data("price" -> BigDecimal(100))),
          _row("specific", conditions = Map("product" -> _equals("book")), output = Record.data("price" -> BigDecimal(120)))
        )
      )
      val inputs = _inputs("product" -> RuleFactValue.scalar("book"))

      When("both rows match the same input")
      val result = _success(DecisionTableEvaluator.evaluateC(table, inputs))

      Then("priority is applied before specificity and row identifier tie-breaking")
      result.candidates.map(_.row.id.value) shouldBe Vector("fallback", "specific")
      result.selected.map(_.row.output) shouldBe Some(Record.data("price" -> BigDecimal(100)))
    }

    "accept exact Scala and JVM decimal and integral values for numeric ranges" in {
      Given("a numeric table and supported exact numeric input values")
      val table = _table(
        "numeric",
        Vector("amount"),
        Vector(_row("accepted", conditions = Map("amount" -> _range(Some(0), Some(2)))))
      )
      val values = Vector[Any](
        BigDecimal(1),
        new java.math.BigDecimal("1.00"),
        BigInt(1),
        java.math.BigInteger.ONE,
        1
      )

      When("each exact numeric representation is evaluated")
      val selections = values.map { value =>
        _success(DecisionTableEvaluator.evaluateC(table, _inputs("amount" -> RuleFactValue.scalar(value))))
          .selected
          .map(_.row.id.value)
      }

      Then("all supported representations select the same numeric range")
      selections shouldBe Vector.fill(values.size)(Some("accepted"))
    }

    "return a successful empty selection when no row matches" in {
      Given("a price-list table with no matching product")
      val table = _table(
        "price-list",
        Vector("product"),
        Vector(_row("book", conditions = Map("product" -> _equals("book"))))
      )
      val inputs = _inputs("product" -> RuleFactValue.scalar("unknown"))

      When("the unmatched product is evaluated")
      val result = _success(DecisionTableEvaluator.evaluateC(table, inputs))

      Then("the evaluation remains successful and exposes no implicit fallback row")
      result.candidates shouldBe Vector.empty
      result.selected shouldBe None
    }

    "reject invalid table ranges and non-financial numeric input types" in {
      Given("a table with an empty numeric range and a valid numeric table")
      val invalid = DecisionTable.createC(
        DecisionTableId("invalid"),
        Vector(DecisionColumn("amount")),
        Vector(_row("range", conditions = Map("amount" -> _range(None, None))))
      )
      val unknown = DecisionTable.createC(
        DecisionTableId("unknown"),
        Vector(DecisionColumn("amount")),
        Vector(_row("range", conditions = Map("missing" -> _range(Some(0), None))))
      )
      val table = _table(
        "discount",
        Vector("amount"),
        Vector(_row("standard", conditions = Map("amount" -> _range(Some(0), None))))
      )
      val inputs = _inputs("amount" -> RuleFactValue.scalar(1.5d))

      When("configuration and runtime numeric values are validated")
      val result = DecisionTableEvaluator.evaluateC(table, inputs)

      Then("invalid ranges and floating-point inputs are structured Consequence failures")
      _is_failure(invalid) shouldBe true
      _is_failure(unknown) shouldBe true
      _is_failure(result) shouldBe true
    }
  }

  private def _table(
    id: String,
    columns: Vector[String],
    rows: Vector[DecisionTableRow]
  ): DecisionTable =
    _success(DecisionTable.createC(DecisionTableId(id), columns.map(DecisionColumn.apply), rows))

  private def _row(
    id: String,
    priority: Int = 0,
    conditions: Map[String, DecisionCondition] = Map.empty,
    output: Record = Record.empty,
    explanation: String = ""
  ): DecisionTableRow =
    DecisionTableRow(
      DecisionTableRowId(id),
      RulePriority(priority),
      conditions,
      output,
      RuleExplanationTemplate(explanation)
    )

  private def _inputs(values: (String, RuleFactValue)*): DecisionTableInputs =
    _success(DecisionTableInputs.fromInputsC(values.map(value => DecisionTableInput(value._1, value._2))))

  private def _equals(value: Any): DecisionCondition =
    DecisionCondition.Equals(RuleFactValue.scalar(value))

  private def _range(
    minimum: Option[Int],
    maximum: Option[Int]
  ): DecisionCondition =
    DecisionCondition.NumberRange(minimum.map(BigDecimal.apply), maximum.map(BigDecimal.apply))

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }

  private def _is_failure[A](result: Consequence[A]): Boolean =
    result match {
      case Consequence.Success(_) => false
      case Consequence.Failure(_) => true
    }
}
