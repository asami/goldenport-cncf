package org.goldenport.cncf.cli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import org.goldenport.Consequence
import org.goldenport.configuration.Configuration
import org.goldenport.configuration.ConfigurationOrigin
import org.goldenport.configuration.ConfigurationResolutionSnapshot
import org.goldenport.configuration.ConfigurationResolver
import org.goldenport.configuration.ConfigurationSources
import org.goldenport.configuration.ConfigurationTrace
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.configuration.source.ConfigurationSource
import org.goldenport.configuration.source.ProjectRootFinder
import org.goldenport.cncf.config.CncfConfigurationArgumentBindingAdmission
import org.goldenport.cncf.config.CncfConfigurationArgumentBindingAssignment
import org.goldenport.cncf.config.CncfConfigurationArgumentBindingCodec
import org.goldenport.cncf.config.CncfConfigurationParameterCatalog
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.config.RuntimeFileConfigLoader
import org.goldenport.cncf.config.RuntimeTestDescriptor

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 *  version Feb.  5, 2026
 *  version Apr. 30, 2026
 *  version May. 25, 2026
 *  version Jun. 29, 2026
 *  version Jul. 30, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[cli] trait CncfRuntimeConfigurationPart {
  this: CncfRuntimeBootstrapPart & CncfRuntimeDiscoveryPart =>
  private[cli] def _resolve_configuration(
    cwd: Path,
    args: Array[String] = Array.empty
  ): ResolvedConfiguration = {
    _resolve_configuration_snapshot(cwd, args)
      .map(_.resolved)
      .getOrElse(ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty))
  }

  private[cli] def _resolve_configuration_snapshot(
    cwd: Path,
    args: Array[String] = Array.empty,
    environment: Map[String, String] = sys.env
  ): Option[ConfigurationResolutionSnapshot] = {
    val configargs = _config_args(_admit_configuration_binding_arguments(args).residualArguments.toArray)
    val initialbasesources = _runtime_standard_config_sources(
      cwd,
      applicationname = _configuration_application_name,
      args = Map.empty,
      environment = environment
    )
    val explicitconfigs = _explicit_config_sources(cwd, configargs)
    val argsource = ConfigurationSource.args(configargs).toSeq
    val initialsources = ConfigurationSources(_deduplicate_runtime_sources(
      initialbasesources.sources ++ explicitconfigs ++ argsource
    ))
    val testconfigs = _test_descriptor_config_sources(initialsources, cwd)
    val effectiveargs = testconfigs.configmap ++ configargs ++ testconfigs.normalizedpathconfig
    val basesources = _runtime_standard_config_sources(
      cwd,
      applicationname = _configuration_application_name,
      args = effectiveargs,
      environment = environment
    )
    val testhomedefaults = _test_home_default_config_source(cwd, effectiveargs)
    val sources = ConfigurationSources(_deduplicate_runtime_sources(
      basesources.sources ++ explicitconfigs ++ testhomedefaults.toVector
    ))
    ConfigurationResolver.default.resolveSnapshot(sources.sources) match {
      case Consequence.Success(snapshot) =>
        Some(snapshot)
      case Consequence.Failure(conclusion) =>
        throw new IllegalStateException(s"runtime configuration resolution failed: ${conclusion.show}")
    }
  }

  private[cli] def _test_descriptor_config_sources(
    sources: ConfigurationSources,
    cwd: Path
  ): TestDescriptorConfigSources =
    ConfigurationResolver.default.resolve(sources) match {
      case Consequence.Success(configuration) =>
        RuntimeTestDescriptor.path(configuration).map { path =>
          val normalized = if (path.isAbsolute) path.normalize else cwd.resolve(path).normalize
          RuntimeTestDescriptor.load(normalized) match {
            case Consequence.Success(descriptor) =>
              TestDescriptorConfigSources(
                descriptor.config,
                Map(RuntimeConfig.TEST_DESCRIPTOR_KEY -> normalized.toString)
              )
            case Consequence.Failure(conclusion) =>
              throw new IllegalArgumentException(conclusion.display)
          }
        }.getOrElse(TestDescriptorConfigSources.empty)
      case Consequence.Failure(_) =>
        TestDescriptorConfigSources.empty
    }

  private[cli] final case class TestDescriptorConfigSources(
    configmap: Map[String, String],
    normalizedpathconfig: Map[String, String]
  )

  private[cli] object TestDescriptorConfigSources {
    val empty: TestDescriptorConfigSources = TestDescriptorConfigSources(Map.empty, Map.empty)
  }

  private[cli] def _explicit_config_sources(
    cwd: Path,
    configargs: Map[String, String]
  ): Vector[ConfigurationSource] = {
    val files =
      _split_config_paths(configargs.get("cncf.config.file")) ++
        _split_config_paths(configargs.get("cncf.config.files")) ++
        _split_config_paths(configargs.get("textus.config.file")) ++
        _split_config_paths(configargs.get("textus.config.files"))
    files.distinct.map { path =>
      val p = _normalize_config_path(cwd, path)
      ConfigurationSource.File(
        origin = ConfigurationOrigin.Arguments,
        path = p,
        rank = ConfigurationSource.Rank.Arguments,
        loader = new RuntimeFileConfigLoader
      )
    }.toVector
  }

  private[cli] def _runtime_standard_config_sources(
    cwd: Path,
    applicationname: String,
    args: Map[String, String],
    environment: Map[String, String]
  ): ConfigurationSources = {
    val loader = new RuntimeFileConfigLoader
    val names = _configuration_application_names(applicationname)
    val testhome = _test_home_runtime_config(cwd, args)
    val inheritedhome =
      if (testhome.exists(_.inheritruntime))
        sys.props.get("user.home").toVector
      else if (testhome.nonEmpty)
        Vector.empty
      else
        sys.props.get("user.home").toVector
    val home = inheritedhome.flatMap { home =>
      names.flatMap { name =>
        _runtime_standard_file_sources(
          Paths.get(home).resolve(_configuration_dir_name(name)),
          ConfigurationOrigin.Home,
          ConfigurationSource.Rank.Home,
          loader
        )
      }
    }
    val testhomesources = testhome.toVector.flatMap { homeconfig =>
      homeconfig.path.toVector.flatMap { home =>
        names.flatMap { name =>
          _runtime_standard_file_sources(
            home.resolve(_configuration_dir_name(name)),
            ConfigurationOrigin.Home,
            ConfigurationSource.Rank.Home + 1,
            loader
          )
        }
      }
    }
    val project = names.flatMap { name =>
      ProjectRootFinder.find(cwd, name).toVector.flatMap { root =>
        _runtime_standard_file_sources(
          root.resolve(_configuration_dir_name(name)),
          ConfigurationOrigin.Project,
          ConfigurationSource.Rank.Project,
          loader
        )
      }
    }
    val current = names.flatMap { name =>
      _runtime_standard_file_sources(
        cwd.resolve(_configuration_dir_name(name)),
        ConfigurationOrigin.Cwd,
        ConfigurationSource.Rank.Cwd,
        loader
      )
    }
    val currenttextuscompat =
      ConfigurationSource.File(
        origin = ConfigurationOrigin.Cwd,
        path = cwd.resolve(".textus.conf"),
        rank = ConfigurationSource.Rank.Cwd,
        loader = loader
      )
    val envsource = ConfigurationSource.env(environment, applicationname).toVector
    val argsource = ConfigurationSource.args(args).toVector
    ConfigurationSources(_deduplicate_runtime_sources(
      home ++ testhomesources ++ project ++ current ++ Vector(currenttextuscompat) ++ envsource ++ argsource
    ))
  }

  private[cli] def _deduplicate_runtime_sources(
    sources: Seq[ConfigurationSource]
  ): Vector[ConfigurationSource] =
    sources.toVector.foldRight(Vector.empty[ConfigurationSource]) { (source, acc) =>
      source.location match {
        case Some(location) if acc.exists(_.location.contains(location)) => acc
        case _ => source +: acc
      }
    }

  private[cli] final case class TestHomeRuntimeConfig(
    path: Option[Path],
    inheritruntime: Boolean,
    inheritrepositories: Boolean,
    inheritlocaldata: Boolean
  )

  private[cli] def _test_home_runtime_config(
    cwd: Path,
    args: Map[String, String]
  ): Option[TestHomeRuntimeConfig] = {
    val path = _test_home_path(cwd, args)
    val mode = _test_home_arg(args, RuntimeConfig.TEST_HOME_MODE_KEY, RuntimeConfig.RUNTIME_TEST_HOME_MODE_KEY)
      .map(_.trim.toLowerCase(java.util.Locale.ROOT))
    val temporary = _test_home_arg(args, RuntimeConfig.TEST_HOME_TEMPORARY_KEY, RuntimeConfig.RUNTIME_TEST_HOME_TEMPORARY_KEY)
      .exists(_truthy)
    if (path.isEmpty && mode.isEmpty && !temporary) {
      None
    } else {
      Some(TestHomeRuntimeConfig(
        path = path,
        inheritruntime = _test_home_boolean(args, RuntimeConfig.TEST_HOME_INHERIT_RUNTIME_KEY, RuntimeConfig.RUNTIME_TEST_HOME_INHERIT_RUNTIME_KEY, default = true),
        inheritrepositories = _test_home_boolean(args, RuntimeConfig.TEST_HOME_INHERIT_REPOSITORIES_KEY, RuntimeConfig.RUNTIME_TEST_HOME_INHERIT_REPOSITORIES_KEY, default = true),
        inheritlocaldata = _test_home_boolean(args, RuntimeConfig.TEST_HOME_INHERIT_LOCAL_DATA_KEY, RuntimeConfig.RUNTIME_TEST_HOME_INHERIT_LOCAL_DATA_KEY, default = false)
      ))
    }
  }

  private[cli] def _test_home_path(
    cwd: Path,
    args: Map[String, String]
  ): Option[Path] =
    _test_home_arg(args, RuntimeConfig.TEST_HOME_PATH_KEY, RuntimeConfig.RUNTIME_TEST_HOME_PATH_KEY)
      .map(Paths.get(_))
      .map(path => if (path.isAbsolute) path.normalize else cwd.resolve(path).normalize)

  private[cli] def _test_home_default_config_source(
    cwd: Path,
    args: Map[String, String]
  ): Option[ConfigurationSource] =
    _test_home_runtime_config(cwd, args).flatMap { homeconfig =>
      if (homeconfig.inheritlocaldata) {
        None
      } else {
        homeconfig.path.flatMap { path =>
          ConfigurationSource.args(Map("textus.local-data.root" -> path.resolve(".cncf").toString))
        }
      }
    }

  private[cli] def _test_home_boolean(
    args: Map[String, String],
    key: String,
    runtimekey: String,
    default: Boolean
  ): Boolean =
    _test_home_arg(args, key, runtimekey).map(_truthy).getOrElse(default)

  private[cli] def _test_home_arg(
    args: Map[String, String],
    key: String,
    runtimekey: String
  ): Option[String] =
    Vector(
      key,
      runtimekey,
      _cncf_key(key),
      _cncf_key(runtimekey)
    ).iterator.flatMap(args.get).toSeq.headOption.map(_.trim).filter(_.nonEmpty)

  private[cli] def _cncf_key(key: String): String =
    if (key.startsWith("textus.")) "cncf." + key.stripPrefix("textus.")
    else key

  private[cli] def _configuration_application_names(
    applicationname: String
  ): Vector[String] =
    Vector(applicationname, "cncf").distinct

  private[cli] def _runtime_standard_file_sources(
    dir: Path,
    origin: ConfigurationOrigin,
    rank: Int,
    loader: RuntimeFileConfigLoader
  ): Vector[ConfigurationSource] = {
    val standard = Vector("conf", "props", "properties", "json", "yaml", "xml").map { ext =>
      ConfigurationSource.File(
        origin = origin,
        path = dir.resolve(s"config.$ext"),
        rank = rank,
        loader = loader
      )
    }
    standard ++ _runtime_split_file_sources(dir, origin, rank, loader)
  }

  private[cli] def _runtime_split_file_sources(
    dir: Path,
    origin: ConfigurationOrigin,
    rank: Int,
    loader: RuntimeFileConfigLoader
  ): Vector[ConfigurationSource] =
    if (dir == null || Option(dir.getFileName).forall(_.toString != ".textus") || !Files.isDirectory(dir))
      Vector.empty
    else {
      val stream = Files.walk(dir)
      try {
        stream.iterator.asScala
          .filter(Files.isRegularFile(_))
          .filter(_is_canonical_split_file(dir, _))
          .toVector
          .sortBy(_.toString)
          .map(path => ConfigurationSource.File(origin, path, rank, loader))
      } finally {
        stream.close()
      }
    }

  private[cli] def _is_canonical_split_file(
    dir: Path,
    path: Path
  ): Boolean = {
    val parts = dir.relativize(path).iterator().asScala.map(_.toString).toVector
    parts match {
      case Vector("components", _, "config.yaml") => true
      case Vector("subsystems", _, "instances", _, "config.yaml") => true
      case Vector("subsystems", _, "instances", _, "components", _, "instances", _, "config.yaml") => true
      case _ => false
    }
  }

  private[cli] def _configuration_dir_name(applicationname: String): String = {
    val name = applicationname.trim.stripPrefix(".")
    if (name.isEmpty) ".cncf" else s".$name"
  }

  private[cli] def _split_config_paths(
    value: Option[String]
  ): Vector[String] =
    value.toVector.flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))

  private[cli] def _normalize_config_path(
    cwd: Path,
    path: String
  ): Path = {
    val p = Paths.get(path)
    if (p.isAbsolute) p.normalize else cwd.resolve(p).normalize
  }

  private[cli] def _config_args(
    args: Array[String]
  ): Map[String, String] = {
    val entries = scala.collection.mutable.Map.empty[String, String]
    val alias = Map(
      "log-level" -> "textus.logging.level",
      "log-backend" -> "textus.logging.backend",
      "format" -> "textus.output.format"
    )
    var i = 0
    var stop = false
    while (i < args.length && !stop) {
      val current = args(i)
      if (current == "--") {
        stop = true
      } else if (current.startsWith("--") && current.contains("=")) {
        val raw = current.drop(2)
        val parts = raw.split("=", 2)
        if (parts.length == 2) {
          val key = parts(0)
          val value = parts(1)
          if ((key.startsWith("textus.") || key.startsWith("cncf.")) && key != "textus.binding") {
            entries.update(key, value)
          }
        }
      } else if (current.startsWith("--")) {
        val key = current.drop(2)
        if (key != "textus.binding" && (key.startsWith("textus.") || key.startsWith("cncf.")) && i + 1 < args.length && !args(i + 1).startsWith("-")) {
          entries.update(key, args(i + 1))
          i = i + 1
        }
      } else if (current.startsWith("-") && !current.startsWith("--")) {
        val raw = current.drop(1)
        if (raw.contains("=")) {
          val parts = raw.split("=", 2)
          val key = parts(0)
          val value = if (parts.length > 1) parts(1) else ""
          alias.get(key).foreach(entries.update(_, value))
        } else {
          alias.get(raw).foreach { key =>
            if (i + 1 < args.length && !args(i + 1).startsWith("-")) {
              entries.update(key, args(i + 1))
              i = i + 1
            }
          }
        }
      }
      i = i + 1
    }
    entries.toMap
  }

  private[cli] def _runtime_config(
    configuration: ResolvedConfiguration
  ): RuntimeConfig =
    RuntimeConfig.from(configuration)

  private[cli] def _strip_configuration_args(
    args: Array[String]
  ): Array[String] = {
    val builder = Vector.newBuilder[String]
    var i = 0
    var aftersentinel = false
    while (i < args.length) {
      val current = args(i)
      if (aftersentinel) {
        builder += current
      } else if (current == "--") {
        builder += current
        aftersentinel = true
      } else if (current.startsWith("--cncf.") || current.startsWith("--textus.")) {
        if (!current.contains("=") && i + 1 < args.length && !args(i + 1).startsWith("-")) {
          i = i + 1
        }
      } else {
        builder += current
      }
      i = i + 1
    }
    builder.result().toArray
  }

  private[cli] def _admit_configuration_binding_arguments(
    args: Array[String]
  ): CncfConfigurationArgumentBindingAdmission =
    _admit_configuration_binding_arguments_c(args)
      .getOrElse(throw new IllegalArgumentException("CNCF configuration binding argument is invalid"))

  private[cli] def _admit_configuration_binding_arguments_c(
    args: Array[String]
  ): Consequence[CncfConfigurationArgumentBindingAdmission] =
    CncfConfigurationArgumentBindingCodec
      .create(CncfConfigurationParameterCatalog.closed)
      .flatMap(_.admit(args.toVector))

  private[cli] def _split_command_args(
    args: Array[String]
  ): (Array[String], Array[String]) = {
    val index = args.indexOf("--")
    if (index < 0)
      args -> Array.empty[String]
    else
      args.take(index) -> args.drop(index)
  }

  private[cli] def _insert_framework_args(
    args: Array[String],
    additions: Array[String]
  ): Array[String] = {
    val (frameworkargs, commandtail) = _split_command_args(args)
    frameworkargs ++ additions ++ commandtail
  }

  private[cli] def _trace_safe_args(
    args: Seq[String]
  ): Seq[String] =
    args.map { arg =>
      if (arg.startsWith("--textus.binding=")) "--textus.binding=<redacted>" else arg
    }

  private[cncf] def restoreConfigurationArgumentBindings(
    args: Array[String],
    bindings: Vector[CncfConfigurationArgumentBindingAssignment]
  ): Array[String] = {
    val encoded = CncfConfigurationArgumentBindingCodec
      .create(CncfConfigurationParameterCatalog.closed)
      .flatMap { codec =>
        bindings.foldLeft(Consequence.success(Vector.empty[String])) { case (acc, binding) =>
          for {
            values <- acc
            value <- codec.encode(binding)
          } yield values :+ value
        }
      }
      .getOrElse(throw new IllegalArgumentException("CNCF configuration binding argument is invalid"))
    val index = args.indexOf("--")
    if (index < 0)
      args ++ encoded
    else
      args.take(index) ++ encoded ++ args.drop(index)
  }

}
