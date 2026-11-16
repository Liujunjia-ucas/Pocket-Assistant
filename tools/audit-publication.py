"""Offline allowlist, redacted source checks, license hashes, and manifest audit.

Never reads ignored runtime/configuration directories. Not a replacement for a
dedicated secret scanner, legal review, or a penetration test.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = "PUBLICATION-MANIFEST.json"
IGNORED = {".git", ".gradle", ".idea", ".vscode", "build", "state", "workspace", "dist", "artifacts", "node_modules", "__pycache__"}
ROOT_FILES = {
    ".gitignore", ".gitattributes", ".env.example", "LICENSE", "README.md", "PRIVACY.md", "SECURITY.md", "RISK-NOTICE.md", "THIRD-PARTY-NOTICES.md", "CONTRIBUTING.md",
    "package.json", "build.gradle", "settings.gradle", "gradle.properties", "build-android.ps1", "start-server.cmd", "app/build.gradle", "app/src/main/AndroidManifest.xml", MANIFEST,
}
PREFIXES = {
    "app/src/main/java/": {".java"},
    "app/src/main/res/": {".xml"},
    "app/src/main/assets/renderer/": {".js", ".css", ".html", ".json", ".txt", ".woff2"},
    "server/": {".mjs"}, "test/": {".java", ".mjs", ".ps1"},
    "tools/": {".py", ".ps1", ".cs"}, "docs/": {".md", ".json"},
}
PATTERNS = {
    "credential_format": re.compile(r"\b(?:sk-(?:proj-)?[A-Za-z0-9_-]{24,}|gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[0-9A-Z]{16})\b"),
    "private_key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    "developer_home": re.compile(r"[A-Za-z]:[\\/]Users[\\/][A-Za-z0-9_. -]+[\\/]"),
    "production_drive": re.compile(r"[EF]:[\\/](?:codex|Clash|tailscale)", re.I),
    "personal_email": re.compile(r"[A-Za-z0-9_.+-]+@(?:mails\.ucas\.ac\.cn|qq\.com)\b", re.I),
    "signed_download": re.compile(r"https?://[^\s\"<>]+[?&](?:X-Amz-Signature|X-Tos-Signature|X-Goog-Signature)=[A-Za-z0-9]+", re.I),
}

def digest(data):
    return hashlib.sha256(data).hexdigest()

def files():
    def visit(directory):
        for path in sorted(directory.iterdir()):
            if path.is_symlink():
                yield path
            elif path.is_dir():
                if path.name not in IGNORED:
                    yield from visit(path)
            elif not path.name.endswith(".pyc"):
                yield path
    return list(visit(ROOT))

def allowed(path):
    rel = path.relative_to(ROOT).as_posix()
    if path.is_symlink():
        return False
    if rel in ROOT_FILES:
        return True
    return any(rel.startswith(prefix) and path.suffix.lower() in suffixes for prefix, suffixes in PREFIXES.items())

def audit(write=False):
    errors, entries = [], []
    for path in files():
        rel = path.relative_to(ROOT).as_posix()
        if not allowed(path):
            errors.append({"file": rel, "rule": "outside_publication_allowlist"})
            continue
        if rel == MANIFEST:
            continue
        data = path.read_bytes()
        if len(data) > 10 * 1024 * 1024:
            errors.append({"file": rel, "rule": "unexpected_large_file"})
        entries.append({"path": rel, "bytes": len(data), "sha256": digest(data)})
        if path.suffix == ".woff2":
            continue
        try:
            source = data.decode("utf-8")
        except UnicodeDecodeError:
            errors.append({"file": rel, "rule": "unexpected_binary"})
            continue
        for name, pattern in PATTERNS.items():
            for match in pattern.finditer(source):
                # Report location only, never the matched secret/private value.
                errors.append({"file": rel, "line": source.count("\n", 0, match.start()) + 1, "rule": name})
    renderer = ROOT / "app/src/main/assets/renderer"
    for package in json.loads((renderer / "vendor-manifest.json").read_text(encoding="utf-8")):
        for entry in package["files"]:
            path = renderer / entry["path"]
            if not path.is_file() or digest(path.read_bytes()) != entry["sha256"]:
                errors.append({"file": path.relative_to(ROOT).as_posix(), "rule": "vendored_license_or_asset_hash_changed"})
    required = {
        "app/build.gradle": ["applicationId 'org.pocketassistant.community'", "debuggable false", "jniDebuggable false"],
        "app/src/main/AndroidManifest.xml": ['android:allowBackup="false"', 'android:dataExtractionRules="@xml/data_extraction_rules"', '.CommunityApplication'],
        "app/src/main/java/com/codexpocket/app/CommunityApplication.java": ['setWebContentsDebuggingEnabled(false)'],
        'app/src/main/java/com/codexpocket/app/MainActivity.java': ["FLAG_SECURE", 'prefs.getString("base", "")', "BridgeEndpoint.configured(base,token)"],
        "app/src/main/java/com/codexpocket/app/RoundedDialogBuilder.java": ["FLAG_SECURE"],
        "app/src/main/java/com/codexpocket/app/RemoteFiles.java": ["FLAG_SECURE"],
        "app/src/main/java/com/codexpocket/app/AssistantSync.java": ['getBoolean("auto_sync",false)', 'getBoolean("ai_triage",false)'],
        "app/src/main/java/com/codexpocket/app/LocalCompanion.java": ['.put("enabled",false)', 'FileVault.read(file)'],
        "app/src/main/java/com/codexpocket/app/DirectChats.java": ['FileVault.read(file)', 'FileVault.encrypt(target,bytes)'],
        "app/src/main/java/com/codexpocket/app/AssistantStore.java": ['FileVault.read(file)', 'FileVault.encrypt(file,bytes)'],
        "server/main.mjs": ["|| '127.0.0.1'", "CODEX_POCKET_ALLOW_PROXY_CONTROL==='1'", "artifacts.allowedDirectory"],
        "app/src/main/assets/renderer/index.html": ["connect-src 'none'", "img-src 'none'"],
    }
    for rel, snippets in required.items():
        source=(ROOT/rel).read_text(encoding="utf-8")
        if any(snippet not in source for snippet in snippets):
            errors.append({"file":rel,"rule":"expected_security_default_changed"})
    for name in ["LICENSE", "README.md", "PRIVACY.md", "SECURITY.md", "RISK-NOTICE.md", "THIRD-PARTY-NOTICES.md", "docs/PUBLISHING.md", "docs/VERIFICATION.md"]:
        if not (ROOT/name).is_file():
            errors.append({"file":name,"rule":"required_document_missing"})
    inventory={"format":1,"edition":"Pocket Assistant Community 1.0.0","files":entries}
    manifest=ROOT/MANIFEST
    if write and not errors:
        manifest.write_text(json.dumps(inventory,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    elif manifest.is_file():
        if json.loads(manifest.read_text(encoding="utf-8")) != inventory:
            errors.append({"file":MANIFEST,"rule":"inventory_differs_review_then_update_manifest"})
    else:
        errors.append({"file":MANIFEST,"rule":"manifest_missing"})
    report={"checkedFiles":len(entries),"bytes":sum(e["bytes"] for e in entries),"errors":errors,
        "scope":"Allowlisted source only; ignored runtime/build directories not read. Regex scan is not a guarantee of absence of secrets."}
    print(json.dumps(report,ensure_ascii=False,indent=2))
    return report,inventory

if __name__ == "__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--write-manifest",action="store_true",help="After intentional review, record the current clean source tree")
    args=parser.parse_args()
    report,_=audit(args.write_manifest)
    sys.exit(1 if report["errors"] else 0)
