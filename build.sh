#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$DIR/java"
./gradlew jar --no-daemon

cd "$DIR/rust"
RUST_MIN_STACK=33554432 cargo build "$@"

TARGET_DIR="debug"
for arg in "$@"; do
    if [ "$arg" = "--release" ]; then
        TARGET_DIR="release"
    fi
done

if [ -d "$DIR/../Pumpkin/plugins" ]; then
    cp -v "$DIR/rust/target/$TARGET_DIR/libpatchbukkit.so" "$DIR/../Pumpkin/plugins/libpatchbukkit.so"
fi
