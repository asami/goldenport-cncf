package org.goldenport.cncf.unitofwork

import org.goldenport.cncf.{Program, UowM}

/*
 * Concrete execution-layer type aliases.
 *
 * These aliases bind the abstract kernel vocabulary (Program / UowM)
 * to the concrete UnitOfWork algebra (UnitOfWorkOp) used by the
 * execution layer.
 *
 * @since   Jan. 10, 2026
 * @version Jan. 10, 2026
 * @author  ASAMI, Tomoharu
 */
// Concrete execution algebra
type ExecProgram[A] = Program[UnitOfWorkOp, A]

// Concrete execution monad (intent + semantic result)
type ExecUowM[A] = UowM[UnitOfWorkOp, A]
