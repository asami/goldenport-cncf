package org.goldenport.cncf.adapter

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class DefaultAdapterResolverSpec extends AnyWordSpec with Matchers {
  "DefaultAdapterResolver" should {
    "resolve an adapter by exact key" in {
      val registry = AdapterRegistry
        .empty[String]
        .register(StringAdapterFactory("view-adapter", "view"))
      val resolver = new DefaultAdapterResolver(registry)

      resolver.resolve(AdapterKey("view")) shouldBe Right("view-adapter")
    }

    "treat keys case-insensitively by default" in {
      val registry = AdapterRegistry
        .empty[String]
        .register(StringAdapterFactory("rdb-adapter", "rdb"))
      val resolver = new DefaultAdapterResolver(registry)

      resolver.resolve(AdapterKey("RDB")) shouldBe Right("rdb-adapter")
    }

    "return not found when no adapter matches" in {
      val registry = AdapterRegistry
        .empty[String]
        .register(StringAdapterFactory("view-adapter", "view"))
      val resolver = new DefaultAdapterResolver(registry)

      resolver.resolve(AdapterKey("api")).left.toOption shouldBe Some(AdapterNotFound(AdapterKey("api")))
    }

    "return ambiguous resolution when multiple adapters match" in {
      val registry = AdapterRegistry
        .empty[String]
        .register(PermissiveStringAdapterFactory("one", "view"))
        .register(PermissiveStringAdapterFactory("two", "view"))
      val resolver = new DefaultAdapterResolver(registry)

      val result = resolver.resolve(AdapterKey("view"))
      result.isLeft shouldBe true
      result.left.toOption.collect {
        case AmbiguousAdapterResolution(_, keys) => keys
      } shouldBe Some(Vector("view", "view"))
    }
  }

  private final case class StringAdapterFactory(
    adapter: String,
    supportedKey: String
  ) extends AdapterFactory[String] {
    val key: AdapterKey = AdapterKey(supportedKey)

    override def create(context: AdapterContext): String =
      adapter
  }

  private final case class PermissiveStringAdapterFactory(
    adapter: String,
    supportedKey: String
  ) extends AdapterFactory[String] {
    val key: AdapterKey = AdapterKey(supportedKey)

    override def supports(requested: AdapterKey, context: AdapterContext): Boolean =
      requested.normalized == supportedKey

    override def create(context: AdapterContext): String =
      adapter
  }
}
