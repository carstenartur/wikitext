from __future__ import annotations

import base64
import io
import shutil
import subprocess
import tarfile
from pathlib import Path

ROOT = Path.cwd()
UPDATE = ROOT / ".wikihelp-update"
encoded = "".join(path.read_text(encoding="ascii") for path in sorted(UPDATE.glob("payload-*")))
archive = base64.b64decode(encoded)
with tarfile.open(fileobj=io.BytesIO(archive), mode="r:gz") as bundle:
    bundle.extractall(ROOT)

legacy_parsers = [
    "wikitext-sample/wikitext-sample/lib/org.eclipse.mylyn.wikitext.confluence.core_1.7.0.I20120417-0032.jar",
    "wikitext-sample/wikitext-sample/lib/org.eclipse.mylyn.wikitext.textile.core_1.7.0.I20120417-0032.jar",
    "wikitext-sample/wikitext-sample/lib/org.eclipse.mylyn.wikitext.tracwiki.core_1.7.0.I20120417-0032.jar",
    "wikitext-sample/wikitext-sample/lib/org.eclipse.mylyn.wikitext.twiki.core_1.7.0.I20120417-0032.jar",
]
subprocess.run(["git", "checkout", "origin/master", "--", *legacy_parsers], check=True)
(ROOT / ".github/workflows/apply-wikihelp-update.yml").unlink()
shutil.rmtree(UPDATE)
