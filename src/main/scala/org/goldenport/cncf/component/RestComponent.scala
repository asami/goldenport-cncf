package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse}

/*
 * @since   Feb.  6, 2026
 * @version Feb.  6, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class RestComponent() extends Component() {
  // protected def rest_executor: RestExecutor

  // def restExecutor: RestExecutor = rest_executor
}

// trait RestExecutor {
//   def execute(request: HttpRequest): Consequence[HttpResponse]
// }
