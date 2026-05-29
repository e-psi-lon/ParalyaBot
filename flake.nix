{
  description = "The official flake for ParalyaBot, the Discord bot of the Paralya server.";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/release-25.11";

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
      lib = pkgs.lib;
      project-jdk = pkgs.jdk25;
      project-jre-base = pkgs.jre25_minimal;

      baseGradleFileset = lib.fileset.unions [
        ./build-logic
        ./settings.gradle.kts
        ./gradle.properties
        ./gradle
        ./flake.nix
        ./flake.lock
        ./config
        ./nix/parse-properties.nix
        (lib.fileset.fileFilter (file: file.name == "build.gradle.kts") ./.)
      ];

      utils = import ./nix/parse-properties.nix {
        inherit lib;
        inherit (self) lastModifiedDate;
      };
      mkGradleBuild = import ./nix/gradle.nix {
        inherit lib baseGradleFileset project-jdk;
        inherit (pkgs) stdenv gradle-packages;
        inherit (utils) parseProperties extractVersion;
      };

    in
    {
      packages.${system} =
        let
          build-logic = import ./nix/packages/build-logic.nix { inherit mkGradleBuild; };
          deps = import ./nix/packages/deps.nix {
            inherit (pkgs) runCommand;
            inherit mkGradleBuild build-logic;
          };
          common = import ./nix/packages/common.nix {
            inherit (pkgs) runCommand zip unzip perl;
            inherit mkGradleBuild build-logic;
            inherit (deps) deps-compile deps-runtime;
          };
          paralyabot = import ./nix/packages/paralyabot.nix {
            inherit (pkgs) zip unzip perl;
            inherit mkGradleBuild build-logic;
            inherit (deps) deps-compile;
            inherit (common) common-compile common-runtime;
          };
          paralyabot-image = import ./nix/packages/image.nix {
            inherit lib project-jdk project-jre-base;
            inherit (pkgs) dockerTools cacert;
            inherit (paralyabot) paralyabot-jar;
            inherit (utils) lastCommitAsTimestamp;
          };
        in
        {
          inherit build-logic paralyabot-image;
          inherit (deps) deps-compile deps-runtime;
          inherit (common) common-compile common-runtime;
          inherit (paralyabot) paralyabot-jar paralyabot-jar-deps;
        };

      devShells.${system}.default = import ./nix/shell.nix {
        inherit lib project-jdk;
        inherit (pkgs) writeShellScriptBin mkShell;
        inherit (utils) extractVersion;
      };
    };
}
