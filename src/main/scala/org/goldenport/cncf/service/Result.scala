package org.goldenport.cncf.service

/*
 * @since   Apr. 12, 2025
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Result() {
}

/** Base runtime contract for CML operation Results. */
abstract class OperationResult() extends Result

/** Successful operation Result without a payload. */
final case class UnitResult() extends OperationResult

/** Successful operation Result carrying one integer value. */
final case class IntResult(value: Int) extends OperationResult
