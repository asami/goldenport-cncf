package org.goldenport.cncf.protocol.spec

import java.nio.file.Path

import org.goldenport.cncf.component.CommandParameterMappingRule

final case class ShellCommandSpecification(
  baseCommand: Vector[String],
  mappingRule: CommandParameterMappingRule = CommandParameterMappingRule.Default,
  workDirHint: Option[Path] = None,
  envHint: Map[String, String] = Map.empty
)
