package org.goldenport.cncf.entity

import scala.reflect.ClassTag
import io.circe.{Decoder, Encoder}
import org.goldenport.Consequence
import org.goldenport.schema.{DataType, ValueDomain, XString, XJson, XSemiStructuredData}

/*
 * Authoritative logical field declaration and explicitly captured storage binding.
 *
 * @since   Oct.  5, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityStoreField(
  logicalName: String,
  storageName: String,
  domain: ValueDomain,
  binding: EntityStoreFieldBinding
)

sealed abstract class EntityStoreFieldBinding private (
  private val _codec: EntityStoreValueCodec[?]
) {
  def datatype: DataType
  def storageCodecIdentity: String = _codec.identity
  def elementBinding: Option[EntityStoreFieldBinding]
  def applicationClassName: Option[String]

  private[entity] def _validate_binding: Consequence[Unit] = _codec._validate_binding
  private[entity] def _encode(value: Any): Consequence[Any] = _codec.encode(value).map(x => x: Any)
  private[entity] def _decode(value: Any): Consequence[Any] = _codec.decode(value).map(x => x: Any)
  private[entity] def _is_array: Boolean
}

object EntityStoreFieldBinding {
  val string: EntityStoreFieldBinding = new ScalarBinding(XString, EntityStoreValueCodec.string, None)
  val json: EntityStoreFieldBinding = new ScalarBinding(XJson, EntityStoreValueCodec.json, None)
  val semiStructuredData: EntityStoreFieldBinding =
    new ScalarBinding(XSemiStructuredData, EntityStoreValueCodec.semiStructuredData, None)

  def application[A](codecIdentity: String)(using
    classTag: ClassTag[A],
    encoder: Encoder[A],
    decoder: Decoder[A]
  ): EntityStoreFieldBinding = {
    val classname = Option(classTag).flatMap(x => Option(x.runtimeClass)).map(_.getName)
    new ScalarBinding(DataType.Named(classname.getOrElse("<missing>")),
      EntityStoreValueCodec.application[A](codecIdentity), classname)
  }

  def nativeScalar[A](
    datatype: DataType,
    representation: EntityStoreScalarRepresentation,
    codecIdentity: String
  )(using
    classTag: ClassTag[A],
    encoder: Encoder[A],
    decoder: Decoder[A]
  ): EntityStoreFieldBinding = {
    val classname = Option(classTag).flatMap(x => Option(x.runtimeClass)).map(_.getName)
    new NativeScalarBinding(datatype, representation,
      EntityStoreValueCodec._native_scalar[A](datatype, representation, codecIdentity), classname)
  }

  def array(element: EntityStoreFieldBinding): EntityStoreFieldBinding = {
    val codec = Option(element).map(_._codec.asInstanceOf[EntityStoreValueCodec[Any]]).orNull
    new ArrayBinding(element, EntityStoreValueCodec.array(codec))
  }

  private final class ScalarBinding(
    declaredtype: DataType,
    codec: EntityStoreValueCodec[?],
    classname: Option[String]
  ) extends EntityStoreFieldBinding(codec) {
    def datatype: DataType = declaredtype
    def elementBinding: Option[EntityStoreFieldBinding] = None
    def applicationClassName: Option[String] = classname
    private[entity] def _is_array: Boolean = false
  }

  private final class ArrayBinding(
    element: EntityStoreFieldBinding,
    codec: EntityStoreValueCodec[?]
  ) extends EntityStoreFieldBinding(codec) {
    def datatype: DataType = Option(element).map(_.datatype).orNull
    def elementBinding: Option[EntityStoreFieldBinding] = Option(element)
    def applicationClassName: Option[String] = None
    private[entity] def _is_array: Boolean = true
  }

  private final class NativeScalarBinding(
    declaredtype: DataType,
    representation: EntityStoreScalarRepresentation,
    codec: EntityStoreValueCodec[?],
    classname: Option[String]
  ) extends EntityStoreFieldBinding(codec) {
    def datatype: DataType = declaredtype
    def elementBinding: Option[EntityStoreFieldBinding] = None
    def applicationClassName: Option[String] = classname
    private[entity] def _is_array: Boolean = false

    override private[entity] def _encode(value: Any): Consequence[Any] = {
      codec._validate_binding.flatMap { _ =>
        codec._to_json(value).flatMap(EntityStoreScalarRepresentation._to_physical(
          declaredtype, representation, _, codec.identity))
      }
    }

    override private[entity] def _decode(value: Any): Consequence[Any] = {
      codec._validate_binding.flatMap { _ =>
        EntityStoreScalarRepresentation._from_physical(declaredtype, representation, value, codec.identity)
          .flatMap(codec._from_json).map(x => x: Any)
      }
    }
  }
}
