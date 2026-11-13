{
  description = "PatchBukkit development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      rust-overlay,
    }:
    let
      # The platforms the CI matrix in .github/workflows/rust.yml builds for
      # (ubuntu-latest, ubuntu-26.04-arm and macos-latest). Intel macOS is left
      # out: nixpkgs unstable no longer supports x86_64-darwin.
      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "aarch64-darwin"
      ];

      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f (pkgsFor system));

      pkgsFor =
        system:
        import nixpkgs {
          inherit system;
          overlays = [ (import rust-overlay) ];
        };
    in
    {
      devShells = forAllSystems (pkgs: {
        default = pkgs.mkShell {
          packages = [
            # Matches rust/rust-toolchain.toml (stable + rust-analyzer, rust-src,
            # rustfmt, clippy), so the shell lines up with what the CI runs.
            (pkgs.rust-bin.fromRustupToolchainFile ./rust/rust-toolchain.toml)

            # build.gradle.kts targets JavaLanguageVersion.of(25); the CI uses zulu.
            pkgs.zulu25

            # proto/ is compiled by prost-build (protoc-bin-vendored) and the
            # Gradle protobuf plugin (protoc 4.35.1 artifact); protoc is provided
            # here for convenience when working with the .proto definitions.
            pkgs.protobuf

            # Backs the `machete` job in the CI.
            pkgs.cargo-machete
          ];

          shellHook = ''
            echo "PatchBukkit development environment"
            echo "  rustc: $(rustc --version)"
            echo "  java:  $(java -version 2>&1 | head -n 1)"
            echo "  gradle: provided by ./java/gradlew (9.7.1)"
            echo "Run ./build.sh to build the Java jar and the Rust cdylib."
          '';
        };
      });
    };
}
