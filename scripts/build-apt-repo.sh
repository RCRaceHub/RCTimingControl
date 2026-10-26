#!/usr/bin/env bash
# Builds the signed apt repository that serves the Linux package, from the .deb files of
# every release. Run by the apt-repo job of the Installers workflow, and by hand to try it out.
#   build-apt-repo.sh <site-dir> <key-id> <new.deb>...
# <site-dir> is the folder published as the web site. It is rebuilt from scratch each time, so there
# is no database to keep in step, and it serves only the .deb files given: the newest release.
# Older releases stay on the GitHub releases page. The signing key must already be in the gpg
# keyring (APT_GPG_KEY in CI).
set -euo pipefail

[ $# -ge 3 ] || { echo "usage: $0 <site-dir> <key-id> <deb>..." >&2; exit 2; }
SITE="$1"; KEY="$2"; shift 2
SUITE=stable
HERE="$(cd "$(dirname "$0")" && pwd)"

rm -rf "$SITE/pool" "$SITE/dists"
mkdir -p "$SITE"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/conf"
cat > "$WORK/conf/distributions" <<CONF
Origin: RC Timing Control
Label: RC Timing Control
Codename: $SUITE
Suite: $SUITE
Architectures: amd64 arm64
Components: main
Description: RC Timing Control, race timing for RC car clubs
SignWith: $KEY
CONF

for deb in "$@"; do
  reprepro -b "$WORK" --outdir "$SITE" -S misc -P optional includedeb "$SUITE" "$deb"
done

# The public key, as a binary keyring for /etc/apt/keyrings, and armoured for people to check
gpg --batch --yes --export "$KEY" > "$SITE/rctc-archive-keyring.gpg"
gpg --batch --yes --armor --export "$KEY" > "$SITE/rctc-archive-keyring.asc"

cp "$HERE/apt-site/index.html" "$HERE/apt-site/rctimingcontrol.sources" "$SITE/"
touch "$SITE/.nojekyll"
# A release must carry every architecture the docs promise
for arch in amd64 arm64; do
  grep -q '^Package: rctimingcontrol' "$SITE/dists/$SUITE/main/binary-$arch/Packages" \
    || { echo "No $arch package in the repository" >&2; exit 1; }
done
echo "Built the apt repository in $SITE"
