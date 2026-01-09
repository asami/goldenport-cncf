package org.goldenport.protocol

/*
 * @since   Oct.  4, 2018
 *  version Oct. 21, 2018
 *  version Feb. 24, 2019
 *  version Mar.  4, 2019
 *  version Feb. 16, 2020
 *  version May. 19, 2020
 *  version Apr. 25, 2021
 *  version Jan. 30, 2022
 *  version Feb.  1, 2022
 *  version Jan. 30, 2023
 *  version Jul. 23, 2023
 *  version Mar. 16, 2025
 *  version Apr.  2, 2025
 *  version Jun. 10, 2025
 * @version Dec. 24, 2025
 * @author  ASAMI, Tomoharu
 */
case class Request(
  service: Option[String],
  operation: String,
  arguments: List[Argument],
  switches: List[Switch],
  properties: List[Property]
) {
}
