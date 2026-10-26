#!/usr/bin/env bash
# Builds the signed apt repository that serves the Linux package, from the .deb files of the
# releases. Run by the apt-repo job of the Installers workflow, and by hand to try it out.
#   build-apt-repo.sh <site-dir> <key-id> <suite>=<deb-dir>...
# Each <suite>=<deb-dir> puts the .deb files in that folder into that suite. The workflow uses
# `stable` for the newest final release and `testing` for the newest release of any kind, alphas
# included. A folder with no .deb files, or one that does not exist, leaves the suite empty.
# <site-dir> is the folder published as the web site. It is rebuilt from scratch each time, so there
# is no database to keep in step, and each suite serves only the .deb files given: one release.
# Older releases stay on the GitHub releases page. Every suite with packages must carry both
# amd64 and arm64, except the suites named (space separated) in APT_LENIENT_SUITES, for a release
# carried over from before arm64 existed. The signing key must already be in the gpg
# keyring (APT_GPG_KEY in CI).
set -euo pipefail

[ $# -ge 3 ] || { echo "usage: $0 <site-dir> <key-id> <suite>=<deb-dir>..." >&2; exit 2; }
SITE="$1"; KEY="$2"; shift 2
HERE="$(cd "$(dirname "$0")" && pwd)"

rm -rf "$SITE/pool" "$SITE/dists"
mkdir -p "$SITE"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/conf"
: > "$WORK/conf/distributions"
for spec in "$@"; do
  [[ "$spec" == *=* ]] || { echo "Expected <suite>=<deb-dir>, got '$spec'" >&2; exit 2; }
  suite="${spec%%=*}"
  cat >> "$WORK/conf/distributions" <<CONF
Origin: RC Timing Control
Label: RC Timing Control
Codename: $suite
Suite: $suite
Architectures: amd64 arm64
Components: main
Description: RC Timing Control, race timing for RC car clubs ($suite)
SignWith: $KEY

CONF
done

total=0
for spec in "$@"; do
  suite="${spec%%=*}"; dir="${spec#*=}"
  # Writes the suite's empty indexes even when there is nothing to add to it
  reprepro -b "$WORK" --outdir "$SITE" export "$suite"
  count=0
  for deb in "$dir"/*.deb; do
    [ -e "$deb" ] || continue
    reprepro -b "$WORK" --outdir "$SITE" -S misc -P optional includedeb "$suite" "$deb"
    count=$((count + 1))
  done
  total=$((total + count))
  if [ "$count" -gt 0 ] && [[ " ${APT_LENIENT_SUITES:-} " != *" $suite "* ]]; then
    # A release must carry every architecture the docs promise
    for arch in amd64 arm64; do
      grep -q '^Package: rctimingcontrol' "$SITE/dists/$suite/main/binary-$arch/Packages" \
        || { echo "No $arch package in the $suite suite" >&2; exit 1; }
    done
  fi
done
[ "$total" -gt 0 ] || { echo "No .deb files were found" >&2; exit 1; }

# The public key, as a binary keyring for /etc/apt/keyrings, and armoured for people to check
gpg --batch --yes --export "$KEY" > "$SITE/rctc-archive-keyring.gpg"
gpg --batch --yes --armor --export "$KEY" > "$SITE/rctc-archive-keyring.asc"

cp "$HERE"/apt-site/index.html "$HERE"/apt-site/*.sources "$SITE/"
touch "$SITE/.nojekyll"
echo "Built the apt repository in $SITE"
