"""Basic repository checks; not a full secret scanner or application test suite."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
required = ["README.md", "AGENTS.md", "CONTRIBUTING.md", ".gitignore", ".env.example",
            "docs/team.md", "docs/contracts/README.md", "tests/integration/README.md"]
errors = [f"Missing required file: {name}" for name in required if not (root / name).is_file()]
tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
for filename in filter(None, tracked):
    path = Path(filename)
    name = path.name.lower()
    if ((name == ".env" or name.startswith(".env.")) and not name.endswith(".example")):
        errors.append(f"Local environment file tracked: {filename}")
    if name == "local.properties" or path.suffix.lower() in {".jks", ".keystore", ".p12", ".pem", ".apk", ".aab"}:
        errors.append(f"Local credential or build artifact tracked: {filename}")
for name in [".env", ".env.local", "apps/web/.env", "services/api/.env", "apps/vehicle/local.properties"]:
    result = subprocess.run(["git", "check-ignore", "--no-index", "-q", name], cwd=root)
    if result.returncode != 0:
        errors.append(f"Local configuration is not ignored: {name}")
if subprocess.run(["git", "check-ignore", "--no-index", "-q", ".env.example"], cwd=root).returncode != 1:
    errors.append(".env.example must remain trackable")
if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("Repository checks passed (application and SDK checks are separate).")
