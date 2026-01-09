package org.goldenport.cncf.http

import org.goldenport.http.HttpResponse

trait HttpDriver {
  def get(path: String): HttpResponse
  def post(path: String, body: Option[String], headers: Map[String, String]): HttpResponse
}
