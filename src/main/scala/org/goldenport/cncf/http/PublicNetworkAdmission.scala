package org.goldenport.cncf.http

import java.net.{Inet4Address, Inet6Address, InetAddress}
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * DNS and literal-address admission shared by static Web policy and transport.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
object PublicNetworkAdmission {
  def admitHostC(host: String): Consequence[Unit] =
    try {
      val addresses = InetAddress.getAllByName(host).toVector
      if (addresses.nonEmpty && addresses.forall(_is_public_address))
        Consequence.unit
      else
        Consequence.resourceUnsupported("Web access target is not on the public network")
    } catch {
      case NonFatal(_) =>
        Consequence.resourceInvalid("Web access target cannot be resolved")
    }

  private def _is_public_address(address: InetAddress): Boolean =
    !address.isAnyLocalAddress &&
      !address.isLoopbackAddress &&
      !address.isLinkLocalAddress &&
      !address.isSiteLocalAddress &&
      !address.isMulticastAddress &&
      (address match {
        case ipv4: Inet4Address => _is_public_ipv4(ipv4)
        case ipv6: Inet6Address => _is_public_ipv6(ipv6)
        case _ => false
      })

  private def _is_public_ipv4(address: Inet4Address): Boolean = {
    val bytes = address.getAddress.map(_ & 0xff)
    val first = bytes(0)
    val second = bytes(1)
    !(
      first == 0 ||
      first == 10 ||
      first == 127 ||
      (first == 100 && second >= 64 && second <= 127) ||
      (first == 169 && second == 254) ||
      (first == 172 && second >= 16 && second <= 31) ||
      (first == 192 && second == 0) ||
      (first == 192 && second == 88 && bytes(2) == 99) ||
      (first == 192 && second == 168) ||
      (first == 198 && (second == 18 || second == 19)) ||
      (first == 198 && second == 51 && bytes(2) == 100) ||
      (first == 203 && second == 0 && bytes(2) == 113) ||
      first >= 224
    )
  }

  private def _is_public_ipv6(address: Inet6Address): Boolean = {
    val bytes = address.getAddress.map(_ & 0xff)
    val uniquelocal = (bytes(0) & 0xfe) == 0xfc
    val documentation =
      bytes(0) == 0x20 && bytes(1) == 0x01 && bytes(2) == 0x0d && bytes(3) == 0xb8
    !uniquelocal && !documentation
  }
}
