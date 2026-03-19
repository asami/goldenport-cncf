package org.goldenport.cncf

package object statemachine {
  type Guard[S, E] = org.goldenport.statemachine.Guard[S, E]
  type ActionEffect[S, E] = org.goldenport.statemachine.Effect[S, E]
}
