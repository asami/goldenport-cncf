package org.goldenport.cncf.component

import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.io.RecordSourceLoader

/*
 * @since   Apr.  8, 2026
 * @version Apr.  8, 2026
 * @author  ASAMI, Tomoharu
 */
object DescriptorRecordLoader {
  def load(path: Path): Consequence[Vector[Record]] =
    RecordSourceLoader.loadRecords(path)
}
