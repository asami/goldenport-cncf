package org.goldenport.cncf.provider.record

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.provider.{ProviderCall, ProviderRequest}
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 24, 2026
 * @version May. 24, 2026
 * @author  ASAMI, Tomoharu
 */
class RecordProviderSpec extends AnyWordSpec with Matchers {
  "RecordImportProvider" should {
    "import records through ProviderEngine" in {
      val context = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
      val core = ProviderCall.Core(ProviderRequest("test", "record-import"), context, None, None)
      val request = RecordImportProviderRequest(
        format = "csv",
        text = Some("id,name\np1,taro")
      )

      val result = RecordImportProvider.default.importRecords(request, core)

      result.toOption.map(_.records.head.getString("name")) shouldBe Some(Some("taro"))
      val calltree = context.observability.callTreeContext.build().getOrElse(fail("calltree missing"))
      val text = calltree.toRecord.print
      text should include("provider:record-import.csv")
      text should include("record_count=1")
    }
  }

  "RecordExportProvider" should {
    "export Excel bytes through ProviderEngine" in {
      val context = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
      val core = ProviderCall.Core(ProviderRequest("test", "record-export"), context, None, None)
      val request = RecordExportProviderRequest(
        format = "xlsx",
        records = Vector(Record.dataAuto("id" -> "p1", "name" -> "taro"))
      )

      val result = RecordExportProvider.default.exportRecords(request, core)

      result match {
        case Consequence.Success(exportresult) =>
          exportresult.extension shouldBe "xlsx"
          exportresult.bytes.length should be > 0
        case Consequence.Failure(conclusion) =>
          fail(conclusion.toString)
      }
      val calltree = context.observability.callTreeContext.build().getOrElse(fail("calltree missing"))
      val text = calltree.toRecord.print
      text should include("provider:record-export.xlsx")
      text should include("record_count=1")
    }
  }
}
