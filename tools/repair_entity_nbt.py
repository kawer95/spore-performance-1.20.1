#!/usr/bin/env python3
"""Audit and repair oversized entity NBT caused by tagged empty TaCZ stacks.

Dry-run is the default.  ``--apply`` requires ``--backup`` and rewrites region
files through a verified temporary file before atomically replacing the source.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import math
import os
from pathlib import Path
import shutil
import struct
import sys
import tempfile
import zlib

import nbtlib

SECTOR_BYTES = 4096
HEADER_BYTES = 8192
MAX_ENCHANTMENTS = 1024
TACZ_KEYS = frozenset({"GunDisplayId", "GunCurrentAmmoCount", "GunId"})


def serialized_size(tag: nbtlib.Compound) -> int:
    output = io.BytesIO()
    nbtlib.File(tag).write(output)
    return len(output.getvalue())


def canonical_empty_stack() -> nbtlib.Compound:
    return nbtlib.Compound({"id": nbtlib.String("minecraft:air"), "Count": nbtlib.Byte(0)})


def integer(tag, default: int = 0) -> int:
    try:
        return int(tag)
    except (TypeError, ValueError):
        return default


def corrupt_empty_stack(tag) -> bool:
    if not isinstance(tag, nbtlib.Compound):
        return False
    item_id = str(tag.get("id", "minecraft:air"))
    count = integer(tag.get("Count", 0))
    item_tag = tag.get("tag")
    if item_id != "minecraft:air" and count > 0:
        return False
    if not isinstance(item_tag, nbtlib.Compound) or not TACZ_KEYS.intersection(item_tag.keys()):
        return False
    enchantments = item_tag.get("Enchantments")
    return isinstance(enchantments, nbtlib.List) and len(enchantments) > MAX_ENCHANTMENTS


def entity_identity(entity: nbtlib.Compound) -> dict:
    return {
        "entity_type": str(entity.get("id", "unknown")),
        "uuid": [integer(value) for value in entity.get("UUID", [])],
        "position": [float(value) for value in entity.get("Pos", [])],
    }


def sanitize_tree(tag, path: str, changes: list[dict]) -> None:
    if isinstance(tag, nbtlib.Compound):
        for key in list(tag.keys()):
            child = tag[key]
            child_path = f"{path}/{key}"
            if corrupt_empty_stack(child):
                enchantments = child["tag"]["Enchantments"]
                changes.append({
                    "kind": "corrupt_empty_tacz_stack",
                    "path": child_path,
                    "enchantments_removed": len(enchantments),
                    "gun_display_id": str(child["tag"].get("GunDisplayId", "")),
                })
                tag[key] = canonical_empty_stack()
                continue
            sanitize_tree(child, child_path, changes)
    elif isinstance(tag, nbtlib.List):
        index = 0
        while index < len(tag):
            child = tag[index]
            child_path = f"{path}[{index}]"
            if corrupt_empty_stack(child):
                enchantments = child["tag"]["Enchantments"]
                changes.append({
                    "kind": "corrupt_empty_tacz_stack",
                    "path": child_path,
                    "enchantments_removed": len(enchantments),
                    "gun_display_id": str(child["tag"].get("GunDisplayId", "")),
                })
                tag[index] = canonical_empty_stack()
            else:
                sanitize_tree(child, child_path, changes)
            index += 1


def sanitize_entity(entity: nbtlib.Compound) -> list[dict]:
    changes: list[dict] = []
    effects = entity.get("ActiveEffects")
    if isinstance(effects, nbtlib.List):
        for index, effect in enumerate(effects):
            if not isinstance(effect, nbtlib.Compound):
                continue
            effect_id = str(effect.get("forge:id", ""))
            curatives = effect.get("CurativeItems")
            if effect_id == "spore:marker" and isinstance(curatives, nbtlib.List) and curatives:
                changes.append({
                    "kind": "marker_curative_items",
                    "path": f"root/ActiveEffects[{index}]/CurativeItems",
                    "items_removed": len(curatives),
                })
                effect["CurativeItems"] = nbtlib.List[nbtlib.Compound]()
    sanitize_tree(entity, "root", changes)
    return changes


def decode_chunk(raw: bytes, start: int) -> tuple[int, bytes, int]:
    if start + 5 > len(raw):
        raise ValueError("chunk header exceeds region file")
    length = int.from_bytes(raw[start:start + 4], "big")
    compression = raw[start + 4]
    if compression & 0x80:
        raise ValueError("external .mcc entity chunks are not supported")
    payload = raw[start + 5:start + 4 + length]
    if len(payload) != length - 1:
        raise ValueError("truncated chunk payload")
    if compression == 1:
        decoded = gzip.decompress(payload)
    elif compression == 2:
        decoded = zlib.decompress(payload)
    elif compression == 3:
        decoded = payload
    else:
        raise ValueError(f"unsupported compression type {compression}")
    return compression, decoded, length


def encode_chunk(compression: int, decoded: bytes) -> bytes:
    if compression == 1:
        payload = gzip.compress(decoded)
    elif compression == 2:
        payload = zlib.compress(decoded)
    elif compression == 3:
        payload = decoded
    else:
        raise ValueError(f"unsupported compression type {compression}")
    return struct.pack(">I", len(payload) + 1) + bytes((compression,)) + payload


def nbt_bytes(root: nbtlib.File) -> bytes:
    output = io.BytesIO()
    root.write(output)
    return output.getvalue()


def process_region(path: Path, apply: bool) -> tuple[list[dict], bytes | None]:
    raw = path.read_bytes()
    if len(raw) < HEADER_BYTES:
        return [], None
    timestamps = raw[SECTOR_BYTES:HEADER_BYTES]
    locations = raw[:SECTOR_BYTES]
    chunks: dict[int, bytes] = {}
    report: list[dict] = []
    changed = False
    for index in range(1024):
        location = int.from_bytes(locations[index * 4:index * 4 + 4], "big")
        sector = location >> 8
        sector_count = location & 0xFF
        if sector == 0 or sector_count == 0:
            continue
        start = sector * SECTOR_BYTES
        compression, decoded, _ = decode_chunk(raw, start)
        root = nbtlib.File.parse(io.BytesIO(decoded))
        chunk_changed = False
        entities = root.get("Entities", [])
        for entity in entities:
            if not isinstance(entity, nbtlib.Compound):
                continue
            before = serialized_size(entity)
            changes = sanitize_entity(entity)
            if not changes:
                continue
            after = serialized_size(entity)
            entry = entity_identity(entity)
            entry.update({
                "region": str(path),
                "chunk_index": index,
                "before_bytes": before,
                "after_bytes": after,
                "changes": changes,
            })
            report.append(entry)
            chunk_changed = True
        if chunk_changed:
            changed = True
            chunks[index] = encode_chunk(compression, nbt_bytes(root))
        else:
            length = int.from_bytes(raw[start:start + 4], "big")
            chunks[index] = raw[start:start + 4 + length]
    if not changed or not apply:
        return report, None
    rebuilt = bytearray(HEADER_BYTES)
    rebuilt[SECTOR_BYTES:HEADER_BYTES] = timestamps
    next_sector = 2
    for index in sorted(chunks):
        chunk = chunks[index]
        sectors = math.ceil(len(chunk) / SECTOR_BYTES)
        if sectors > 255:
            raise ValueError(f"chunk {index} still exceeds 255 sectors")
        rebuilt[index * 4:index * 4 + 4] = ((next_sector << 8) | sectors).to_bytes(4, "big")
        rebuilt.extend(chunk)
        rebuilt.extend(b"\0" * (sectors * SECTOR_BYTES - len(chunk)))
        next_sector += sectors
    return report, bytes(rebuilt)


def entity_region_files(world: Path) -> list[Path]:
    return sorted(path for path in world.rglob("*.mca") if path.parent.name == "entities")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def create_backup(world: Path, backup: Path) -> None:
    if backup.exists():
        if not backup.is_dir() or not any(backup.iterdir()):
            raise ValueError(f"existing backup is not a populated directory: {backup}")
        return
    shutil.copytree(world, backup)


def write_atomic(path: Path, data: bytes) -> None:
    descriptor, temporary_name = tempfile.mkstemp(prefix=path.name + ".", suffix=".repair", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        # Parse every stored chunk from the candidate before replacement.
        candidate = temporary.read_bytes()
        for index in range(1024):
            location = int.from_bytes(candidate[index * 4:index * 4 + 4], "big")
            sector = location >> 8
            if sector:
                _, decoded, _ = decode_chunk(candidate, sector * SECTOR_BYTES)
                nbtlib.File.parse(io.BytesIO(decoded))
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def audit(world: Path, apply: bool) -> tuple[list[dict], list[dict]]:
    changes: list[dict] = []
    files: list[dict] = []
    for region in entity_region_files(world):
        before_hash = sha256(region)
        found, rebuilt = process_region(region, apply)
        if rebuilt is not None:
            write_atomic(region, rebuilt)
        after_hash = sha256(region)
        if found:
            changes.extend(found)
            files.append({
                "path": str(region),
                "before_sha256": before_hash,
                "after_sha256": after_hash,
                "changed": before_hash != after_hash,
            })
    return changes, files


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(prog="repair_entity_nbt")
    parser.add_argument("--world", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--backup", type=Path)
    parser.add_argument("--apply", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    world = args.world.resolve()
    if not world.is_dir():
        raise SystemExit(f"world does not exist: {world}")
    if args.apply and args.backup is None:
        raise SystemExit("--apply requires --backup")
    if args.apply:
        backup = args.backup.resolve()
        if backup == world or world in backup.parents:
            raise SystemExit("backup must be outside the world directory")
        create_backup(world, backup)
    changes, files = audit(world, args.apply)
    report = {
        "world": str(world),
        "mode": "apply" if args.apply else "dry-run",
        "entity_regions_scanned": len(entity_region_files(world)),
        "entities_changed": len(changes),
        "files": files,
        "entities": changes,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: report[key] for key in ("mode", "entity_regions_scanned", "entities_changed")}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
