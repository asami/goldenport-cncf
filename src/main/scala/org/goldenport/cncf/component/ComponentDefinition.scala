package org.goldenport.cncf.component

import org.goldenport.protocol.Protocol

trait ComponentDefinition {
  def name: String
  def protocol: Protocol
}
