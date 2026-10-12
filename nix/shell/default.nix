{
  callPackage,
  mkShell,
  mkShellNoCC,
  go,
  golangci-lint,
  project-jdk,
  extractVersion,
  ...
}:
let
  tools = callPackage ./tools.nix { };
  inherit (callPackage ./components.nix { }) components order;
  pbot = callPackage ./pbot.nix { inherit components order tools; };

  rootHook = ''
     PROJECT_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
     export PROJECT_ROOT
   '';

  mkCiShell =
    {
      versionVar,
      version,
      build,
    }:
    mkShellNoCC {
      packages = [ build ];
      shellHook = rootHook + ''
        export ${versionVar}=${version}
        if [ -n "''${GITHUB_ENV:-}" ]; then
          echo "${versionVar}=${version}" >> "$GITHUB_ENV"
        fi
      '';
    };
in
{
  default = mkShell {
    packages = [
      pbot
      project-jdk
      go
      golangci-lint
    ];
    shellHook = rootHook + ''
      ln -sfn ${project-jdk}/lib/openjdk $PROJECT_ROOT/.jdk
      ln -sfn ${go} $PROJECT_ROOT/.goroot
      export JAVA_HOME=$PROJECT_ROOT/.jdk
      export PARALYABOT_VERSION=${extractVersion "paralyabot.version"}
      export PARALYABOT_CACHE_VERSION=${extractVersion "paralyabot.cache.version"}
    '';
  };

  ci-bot = mkCiShell {
    versionVar = "PARALYABOT_VERSION";
    version = extractVersion "paralyabot.version";
    build = components.bot.build;
  };

  ci-bot-cache = mkCiShell {
    versionVar = "PARALYABOT_CACHE_VERSION";
    version = extractVersion "paralyabot.cache.version";
    build = components.cache.build;
  };
  ci-plugin = mkShellNoCC {
    packages = [ tools.build-plugin ];
    shellHook = rootHook;
  };
}
