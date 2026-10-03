package org.goldenport.cncf.display

import java.time.Instant

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
/** Closed runtime candidates; payload and role validation belongs to Display.createC. */
sealed abstract class DisplayValue {
  def kind: GeneratedLogicalUiAbi.ValueKind
}

object DisplayValue {
  final case class Text(value: String) extends DisplayValue {
    override val kind: GeneratedLogicalUiAbi.ValueKind = GeneratedLogicalUiAbi.ValueKind.Text
  }
  final case class Number(value: BigDecimal) extends DisplayValue {
    override val kind: GeneratedLogicalUiAbi.ValueKind = GeneratedLogicalUiAbi.ValueKind.Number
  }
  final case class Boolean(value: scala.Boolean) extends DisplayValue {
    override val kind: GeneratedLogicalUiAbi.ValueKind = GeneratedLogicalUiAbi.ValueKind.Boolean
  }
  final case class Timestamp(value: Instant) extends DisplayValue {
    override val kind: GeneratedLogicalUiAbi.ValueKind = GeneratedLogicalUiAbi.ValueKind.Timestamp
  }
  case object Missing extends DisplayValue {
    override val kind: GeneratedLogicalUiAbi.ValueKind = GeneratedLogicalUiAbi.ValueKind.Missing
  }
}
