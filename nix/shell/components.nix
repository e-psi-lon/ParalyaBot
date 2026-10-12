{ lib, writeShellScriptBin }:
let
  inherit (import ./common.nix) requireRoot;
  network = "paralya-bot-network";
  cacheContainer = "ParalyaBotCache";

  detectRuntime = ''
    if command -v docker &> /dev/null; then
        RUNTIME=docker
    elif command -v podman &> /dev/null; then
        RUNTIME=podman
    else
        echo "Neither docker nor podman found" >&2
        exit 1
    fi
  '';

  definitions = {
    cache = {
      name = "bot-cache";
      imageAttr = "paralyabot-cache-image";
      image = "paralyabot-cache";
      container = cacheContainer;
      runArgs = [ ];
    };
    bot = {
      name = "bot";
      imageAttr = "paralyabot-image";
      image = "paralyabot";
      container = "ParalyaBot";
      runArgs = [
        ''--env KORD_CACHE_URL="redis://${cacheContainer}:6379"''
        ''--volume "$PROJECT_ROOT/container:/app/external:Z"''
      ];
    };
  };

  mkComponent =
    c:
    let
      build = writeShellScriptBin "build-${c.name}" ''
        set -e
        ${requireRoot}
        ${detectRuntime}
        IMAGE_PATH=$(nix build "$PROJECT_ROOT#${c.imageAttr}" --print-out-paths --show-trace)
        echo "Loading ${c.name} image into $RUNTIME..."
        "$IMAGE_PATH" | "$RUNTIME" load
      '';

      run = writeShellScriptBin "run-${c.name}" ''
        set -e
        ${requireRoot}
        CONTAINER_NAME="''${1:-${c.container}}"
        echo "Starting container: $CONTAINER_NAME..."
        podman network exists ${network} || podman network create ${network}
        podman run \
            --name "$CONTAINER_NAME" \
            --replace \
            --detach \
            --network ${network} \
            --userns=keep-id \
        ${lib.concatMapStrings (a: "    ${a} \\\n") c.runArgs}    localhost/${c.image}:latest
        echo "Container $CONTAINER_NAME started successfully."
      '';

      up = writeShellScriptBin "up-${c.name}" ''
        ${lib.getExe build} && ${lib.getExe run} "$@"
      '';
    in
    {
      inherit build run up;
    };
in
{
  components = lib.mapAttrs (_: mkComponent) definitions;
  # Start order for "all": the cache must be up before the bot.
  order = [
    "cache"
    "bot"
  ];
}
