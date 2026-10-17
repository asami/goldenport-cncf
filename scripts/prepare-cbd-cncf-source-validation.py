#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Prepare the explicitly selected, private PHASE-69.7 CBD/CNCF source build.

Created and updated October 6, 2026. Semantic provenance describes the selected
inputs; it is neither acceptance evidence nor a content certificate.
"""

import argparse
import json
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys


class PreparationRefused(Exception):
    pass


def git(root: Path, *arguments: str) -> bytes:
    result = subprocess.run(
        ["git", "-C", str(root), *arguments],
        check=False, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if result.returncode:
        raise PreparationRefused("source-git-read-failed")
    return result.stdout


def output_path(value: str, boundary: Path) -> Path:
    selected = Path(value).absolute()
    if selected != selected.resolve():
        raise PreparationRefused("output-symlink-or-nonnormal-path")
    if not selected.is_relative_to(boundary) or selected == boundary:
        raise PreparationRefused("output-outside-private-boundary")
    if ".git" in selected.relative_to(boundary).parts:
        raise PreparationRefused("output-git-path-forbidden")
    return selected


def selected_files(root: Path) -> list[str]:
    gitlinks = set()
    for entry in git(root, "ls-files", "--stage", "-z").split(b"\0"):
        if entry:
            metadata, name = entry.split(b"\t", 1)
            if metadata.startswith(b"160000 "):
                gitlinks.add(name.decode("utf-8"))
    paths = set()
    for entry in git(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z").split(b"\0"):
        if not entry:
            continue
        name = entry.decode("utf-8")
        relative = PurePosixPath(name)
        if relative.is_absolute() or any(part in ("", ".", "..") for part in relative.parts):
            raise PreparationRefused("source-path-unsafe")
        if any(part in ("target", ".git", ".codex-workflow") for part in relative.parts) or name in gitlinks:
            continue
        source = root.joinpath(*relative.parts)
        if source.is_symlink():
            if relative.name in ("AGENT.md", "AGENTS.md", "RULE.md"):
                continue
            raise PreparationRefused("executable-source-symlink-forbidden")
        if any(parent.is_symlink() for parent in source.parents if parent != root and parent.is_relative_to(root)):
            raise PreparationRefused("source-symlink-parent-forbidden")
        if not source.exists():
            continue  # A current tracked deletion is part of the admitted tree.
        if not source.is_file():
            raise PreparationRefused("source-not-regular-file")
        paths.add(name)
    return sorted(paths)


def copy_files(root: Path, directory: Path, paths: list[str]) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    for name in paths:
        source = root / name
        destination = directory / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination, follow_symlinks=False)


def refuse_symlink_tree(directory: Path) -> None:
    if directory.exists():
        if not directory.is_dir():
            raise PreparationRefused("selected-output-not-directory")
        for child in directory.rglob("*"):
            if child.name == ".git":
                raise PreparationRefused("existing-output-git-path-forbidden")
            if child.is_symlink():
                raise PreparationRefused("existing-output-symlink-forbidden")


def prepare(arguments: argparse.Namespace) -> None:
    owner = Path(arguments.owner_repository).resolve(strict=True)
    source = Path(arguments.source_repository).resolve(strict=True)
    admitted = owner / ".codex-workflow/checkouts/textus-cbd-support"
    if source != admitted or admitted.is_symlink() or not (owner / "build.sbt").is_file() or not (source / "build.sbt").is_file():
        raise PreparationRefused("native-repository-selection-invalid")
    if Path(git(owner, "rev-parse", "--show-toplevel").decode().strip()).resolve() != owner or \
            Path(git(source, "rev-parse", "--show-toplevel").decode().strip()).resolve() != source:
        raise PreparationRefused("native-repository-root-invalid")
    boundary = owner / "target/phase-69.7-cbd2c"
    if boundary != boundary.resolve():
        raise PreparationRefused("private-boundary-symlink-forbidden")
    directory = output_path(arguments.directory, boundary)
    cncfdirectory = output_path(arguments.cncf_directory, boundary)
    provenance = output_path(arguments.provenance, boundary)
    if not cncfdirectory.is_relative_to(directory) or cncfdirectory == directory or provenance.is_relative_to(directory):
        raise PreparationRefused("selected-output-layout-invalid")
    refuse_symlink_tree(directory)
    if provenance.is_symlink():
        raise PreparationRefused("provenance-symlink-forbidden")
    binding = {
        "ownerRoot": str(owner), "sourceRoot": str(source), "sourceCopy": str(directory),
        "cncfSourceRoot": str(owner), "cncfSourceCopy": str(cncfdirectory),
    }
    nonempty = directory.exists() and any(directory.iterdir())
    if arguments.replace:
        if not provenance.is_file():
            raise PreparationRefused("replace-provenance-required")
        previous = json.loads(provenance.read_text(encoding="utf-8"))
        if any(previous.get(key) != value for key, value in binding.items()):
            raise PreparationRefused("replace-semantic-binding-mismatch")
    elif nonempty or provenance.exists():
        raise PreparationRefused("selected-output-already-present")
    # Select both actual current trees before changing the private output.
    cbdpaths = selected_files(source)
    cncfpaths = selected_files(owner)
    if any(PurePosixPath(name).is_relative_to(PurePosixPath(cncfdirectory.relative_to(directory).as_posix())) for name in cbdpaths):
        raise PreparationRefused("nested-source-copy-collision")
    cbdhead = git(source, "rev-parse", "HEAD").decode().strip()
    cncfhead = git(owner, "rev-parse", "HEAD").decode().strip()
    if arguments.replace and directory.exists():
        shutil.rmtree(directory)
    copy_files(source, directory, cbdpaths)
    copy_files(owner, cncfdirectory, cncfpaths)
    provenance.parent.mkdir(parents=True, exist_ok=True)
    provenance.write_text(json.dumps({
        **binding, "sourceHead": cbdhead, "copiedPaths": cbdpaths,
        "cncfSourceHead": cncfhead, "cncfCopiedPaths": cncfpaths,
    }, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "prepared", "sourceCopy": str(directory), "provenance": str(provenance)}))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("owner-repository", "source-repository", "directory", "cncf-directory", "provenance"):
        parser.add_argument("--" + name, required=True)
    parser.add_argument("--replace", action="store_true")
    try:
        prepare(parser.parse_args())
        return 0
    except PreparationRefused as error:
        print(json.dumps({"status": "refused", "reason": str(error)}), file=sys.stderr)
        return 2
    except (OSError, ValueError, UnicodeError):
        print(json.dumps({"status": "refused", "reason": "source-preparation-invalid"}), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
