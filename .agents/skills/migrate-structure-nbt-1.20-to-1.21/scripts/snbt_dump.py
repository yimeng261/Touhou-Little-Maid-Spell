#!/usr/bin/env python3
"""Dump every .nbt under --source as a .snbt text file under --out, mirroring the directory tree.

Pair with `diff -r` (or your favourite diff tool) to compare two directory trees of NBT files
without binary noise. nbtlib's SNBT formatter is stable — the same NBT always serializes the
same way — so a no-op migration produces a no-op diff.
"""
from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

import nbtlib


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    parser.add_argument('--source', required=True, type=Path, help='Directory of .nbt files (recurses)')
    parser.add_argument('--out', required=True, type=Path, help='Output directory for .snbt files')
    parser.add_argument('--clean', action='store_true', help='Wipe --out before dumping')
    parser.add_argument('--indent', type=int, default=2, help='SNBT indent (default 2)')
    args = parser.parse_args(argv)

    if not args.source.is_dir():
        print(f"--source must be a directory: {args.source}", file=sys.stderr)
        return 1

    if args.clean and args.out.exists():
        shutil.rmtree(args.out)
    args.out.mkdir(parents=True, exist_ok=True)

    n = 0
    for p in sorted(args.source.rglob('*.nbt')):
        f = nbtlib.load(str(p))
        root = f.root if hasattr(f, 'root') else f
        rel = p.relative_to(args.source)
        out = args.out / rel.with_suffix('.snbt')
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(root.snbt(indent=args.indent))
        n += 1

    print(f"Dumped {n} files to {args.out}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
