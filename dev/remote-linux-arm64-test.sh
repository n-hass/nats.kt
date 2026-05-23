#!/usr/bin/env bash
set -euo pipefail

# Cross-compile linuxArm64 integration tests on the local machine, sync the artefacts to a
# remote linux box, and run them there. Required configuration:
#
#   REMOTE      SSH target (e.g. an entry in your ~/.ssh/config, or user@host)
#   REMOTE_DIR  Absolute path on the remote box used as the work directory
#
# Optional:
#   FILTER  First positional arg — Kotlin test filter passed via --ktest_gradle_filter
#
# Settings are commonly sourced from a private file (e.g. .envrc.private) that is gitignored.

: "${REMOTE:?Set REMOTE to the SSH target of the linux-arm64 builder (e.g. via .envrc.private)}"
: "${REMOTE_DIR:?Set REMOTE_DIR to an absolute path on \$REMOTE used as the work directory}"

FILTER=${1:-}

# Run gradle inside the project's nix dev shell so OPENSSL_INCLUDE_DIR (and the rest of
# the build env) is exported for cinterop, no matter what shell launched this script.
nix develop --command gradle \
  :integration-tests:linkDebugTestLinuxArm64 \
  :test-harness:nats-server-daemon:installDist \
  :test-harness:tls-test-server:installDist

ssh "$REMOTE" "mkdir -p $REMOTE_DIR/test-bin $REMOTE_DIR/harness $REMOTE_DIR/logs"

rsync -a integration-tests/build/bin/linuxArm64/debugTest/test.kexe \
  "$REMOTE:$REMOTE_DIR/test-bin/"
rsync -a --delete test-harness/nats-server-daemon/build/install/nats-server-daemon/ \
  "$REMOTE:$REMOTE_DIR/harness/nats-server-daemon/"
rsync -a --delete test-harness/tls-test-server/build/install/tls-test-server/ \
  "$REMOTE:$REMOTE_DIR/harness/tls-test-server/"

ssh "$REMOTE" REMOTE_DIR="$REMOTE_DIR" FILTER="$FILTER" bash -s <<'REMOTE_SCRIPT'
set -euo pipefail

nix-shell -p patchelf glibc zlib libxcrypt-legacy --run '
  set -e
  GLIBC=$(nix-build --no-out-link -E "(import <nixpkgs> {}).glibc")
  ZLIB=$(nix-build --no-out-link -E "(import <nixpkgs> {}).zlib")
  GCCLIB=$(nix-build --no-out-link -E "(import <nixpkgs> {}).stdenv.cc.cc.lib")
  CRYPT=$(nix-build --no-out-link -E "(import <nixpkgs> {}).libxcrypt-legacy")
  patchelf \
    --set-interpreter "$GLIBC/lib/ld-linux-aarch64.so.1" \
    --set-rpath "$GLIBC/lib:$ZLIB/lib:$GCCLIB/lib:$CRYPT/lib" \
    '"$REMOTE_DIR"'/test-bin/test.kexe
'

pkill -f nats-server-daemon 2>/dev/null || true
pkill -f "nats-server " 2>/dev/null || true
sleep 0.5

nix-shell -p jdk21 nats-server openssl --run "
  PATH=\$PATH nohup $REMOTE_DIR/harness/nats-server-daemon/bin/nats-server-daemon \
    > $REMOTE_DIR/logs/daemon.log 2>&1 &
"

for _ in $(seq 1 20); do
  ss -tln 2>/dev/null | grep -q :4500 && break
  sleep 0.2
done

if [ -n "$FILTER" ]; then
  "$REMOTE_DIR/test-bin/test.kexe" --ktest_gradle_filter="$FILTER"
else
  "$REMOTE_DIR/test-bin/test.kexe"
fi
REMOTE_SCRIPT
