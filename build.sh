cd java
./gradlew jar
cd ..
cd rust
cargo update -p pumpkin-core -p pumpkin-api-macros -p pumpkin-data -p pumpkin-protocol -p pumpkin-util
cargo build
