package org.goldenport.cncf

import cats.free.Free
import org.goldenport.{Consequence, ConsequenceT}

/*
 * CNCF execution kernel type definitions.
 *
 * This file defines the canonical type vocabulary for CNCF execution:
 * - Program   : declarative intent (Free over UnitOfWorkOp)
 * - UowM      : intent combined with semantic result (ConsequenceT)
 * - UowResult : finalized semantic result after execution
 *
 * These definitions are intentionally free of IO and runtime concerns.
 *
 * @since   Jan. 10, 2026
 * @version Jan. 10, 2026
 * @author  ASAMI, Tomoharu
 */
// Abstract algebra for execution programs (defined by the execution layer)
type Program[F[_], A] = Free[F, A]

// Canonical return type for CNCF Action / Command
type UowM[F[_], A] = ConsequenceT[[X] =>> Program[F, X], A]

// Canonical execution result produced by UnitOfWork
type UowResult[A] = Consequence[A]
