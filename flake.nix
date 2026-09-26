{
  description = "The official flake for ParalyaBot, the Discord bot of the Paralya server.";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/release-26.05";

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
      lib = pkgs.lib;
      project-jdk = pkgs.jdk25;

      baseGradleFileset = lib.fileset.unions [
        ./build-logic
        ./settings.gradle.kts
        ./gradle.properties
        ./gradle
        ./flake.nix
        ./flake.lock
        ./config
        ./nix/parse-properties.nix
        ./build.gradle.kts
      ];

      modules = [
        "build-logic"
        "deps"
        "common"
        "bot"
        "lg"
        "sta"
        "ai"
      ];

      utils = import ./nix/parse-properties.nix {
        inherit lib;
        inherit (self) lastModifiedDate;
      };
      mkGradleBuild = import ./nix/gradle.nix {
        inherit
          lib
          baseGradleFileset
          modules
          project-jdk
          ;
        inherit (pkgs) stdenv gradle-packages;
        inherit (utils) parseProperties extractVersion;
      };

    in
    {
      packages.${system} =
        let
          build-logic = pkgs.callPackage ./nix/packages/build-logic.nix { inherit mkGradleBuild; };
          deps = pkgs.callPackage ./nix/packages/deps.nix {
            inherit mkGradleBuild build-logic;
          };
          common = pkgs.callPackage ./nix/packages/common.nix {
            inherit mkGradleBuild build-logic;
            inherit (utils) extractVersion;
            inherit (deps) deps-compile deps-runtime;
          };
          paralyabot = pkgs.callPackage ./nix/packages/paralyabot.nix {
            inherit mkGradleBuild build-logic;
            inherit (deps) deps-compile;
            inherit (common) common-compile common-runtime;
          };
          paralyabot-image = pkgs.callPackage ./nix/packages/image.nix {
            inherit lib project-jdk;
            inherit (paralyabot) paralyabot-jar;
            inherit (utils) lastCommitAsTimestamp;
          };
          lg-plugin = pkgs.callPackage ./nix/packages/plugins/lg.nix {
            inherit mkGradleBuild build-logic;
            inherit (deps) deps-compile;
            inherit (common) common-compile;
          };
          ai-plugin = pkgs.callPackage ./nix/packages/plugins/ai.nix {
            inherit mkGradleBuild build-logic;
            inherit (deps) deps-compile;
            inherit (common) common-compile;
          };
          cache = pkgs.callPackage ./nix/packages/cache.nix {
            inherit (utils) extractVersion;
          };
          paralyabot-cache-image = pkgs.callPackage ./nix/packages/cache-image.nix {
            paralyabot-cache = cache;
            inherit (utils) lastCommitAsTimestamp;
          };
        in
        {
          inherit build-logic paralyabot-image lg-plugin ai-plugin cache paralyabot-cache-image;
          inherit (deps) deps-compile deps-runtime;
          inherit (common) common-compile common-runtime-deps common-runtime common-update;
          inherit (paralyabot) paralyabot-jar paralyabot-jar-deps paralyabot-jar-update;

          build-logic-update = build-logic.mitmCache.updateScript;
          deps-compile-update = deps.deps-compile.mitmCache.updateScript;
          lg-plugin-update = lg-plugin.mitmCache.updateScript;
          ai-plugin-update = ai-plugin.mitmCache.updateScript;
        };

      devShells.${system}.default = pkgs.callPackage ./nix/shell.nix {
        inherit lib project-jdk;
        inherit (utils) extractVersion;
      };
    };
}
