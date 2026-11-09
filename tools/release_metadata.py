#!/usr/bin/env python3
"""Create the public, credential-free update manifest for a signed release APK."""

import argparse
import hashlib
import json
import pathlib
import re
import subprocess


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=pathlib.Path)
    parser.add_argument("--aapt", default="aapt")
    parser.add_argument("--tag", required=True, help="Release tag, for example v0.6.0")
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    badging = subprocess.check_output([args.aapt, "dump", "badging", str(args.apk)], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging, re.MULTILINE)
    minimum = re.search(r"^sdkVersion:'([0-9]+)'", badging, re.MULTILINE)
    if not package or not minimum or package[1] != "br.edu.uniconnect":
        raise SystemExit("APK inválido ou packageName inesperado.")
    if args.tag != f"v{package[3]}":
        raise SystemExit("A tag deve corresponder exatamente ao versionName do APK.")
    payload = {
        "schemaVersion": 1,
        "versionCode": int(package[2]),
        "versionName": package[3],
        "minSdk": int(minimum[1]),
        "apkAsset": args.apk.name,
        "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    args.apk.with_suffix(args.apk.suffix + ".sha256").write_text(
        f"{payload['sha256']}  {args.apk.name}\n", encoding="utf-8"
    )


if __name__ == "__main__":
    main()
