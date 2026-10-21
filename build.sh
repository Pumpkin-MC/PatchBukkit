#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$DIR/java"
./gradlew jar --no-daemon

cd "$DIR/rust"
RUST_MIN_STACK=33554432 cargo build "$@"
if [ -f "$DIR/../Pumpkin/Cargo.lock" ]; then
    cp "$DIR/rust/Cargo.lock" "$DIR/../Pumpkin/Cargo.lock"
fi
