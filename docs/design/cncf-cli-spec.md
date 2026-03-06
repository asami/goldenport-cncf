# CNCF CLI Specification

Status: draft

This document defines the CNCF command-line interface at the top-level command layer.

## CLI Structure

Top-level command:

cncf

Top-level subcommands:

cncf command
cncf server
cncf client

General syntax:

cncf <command> [arguments]

Commands:

command   Execute component operations
server    Control CNCF runtime server
client    Call operations on a remote server

## CLI Help System

CLI help commands:

cncf help
cncf command help
cncf server help
cncf client help

Selector help:

cncf command help <selector>

## Help Aliases

Standard aliases:

--help
-h

Supported examples:

cncf --help
cncf -h
cncf command --help
cncf command -h
cncf server --help
cncf server -h
cncf client --help
cncf client -h

Selector help alias:

cncf command <selector> --help
cncf command <selector> -h

These are normalized to:

cncf command help <selector>

## Command Protocol Syntax

cncf command <selector> [args...]

Notation:

<name>      required argument
[name]      optional argument
[name...]   repeated argument

## Runtime Flags for command

The following runtime flags are removed before selector resolution:

--json
--debug
--no-exit

These flags are treated as runtime options, not operation arguments.
