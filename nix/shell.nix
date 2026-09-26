{
  lib,
  go,
  golangci-lint,
  writeShellScriptBin,
  mkShell,
  project-jdk,
  extractVersion,
}:
let
  build-bot = writeShellScriptBin "build-bot" ''
    set -e
    IMAGE_PATH=$(nix build .#paralyabot-image --print-out-paths --show-trace)

    RUNTIME=""
    if command -v docker &> /dev/null; then
        RUNTIME="docker"
    elif command -v podman &> /dev/null; then
        RUNTIME="podman"
    else
        echo "Neither docker nor podman found"
        exit 1
    fi

    echo "Loading image into $RUNTIME..."
    "$IMAGE_PATH" | "$RUNTIME" load
  '';

  build-bot-cache = writeShellScriptBin "build-bot-cache" ''
    set -e
    IMAGE_PATH=$(nix build .#paralyabot-cache-image --print-out-paths --show-trace)

    RUNTIME=""
    if command -v docker &> /dev/null; then
        RUNTIME="docker"
    elif command -v podman &> /dev/null; then
        RUNTIME="podman"
    else
        echo "Neither docker nor podman found"
        exit 1
    fi

    echo "Loading cache image into $RUNTIME..."
    "$IMAGE_PATH" | "$RUNTIME" load
  '';

  run-bot = writeShellScriptBin "run-bot" ''
    CONTAINER_NAME="''${1:-ParalyaBot}"
    echo "Starting container: $CONTAINER_NAME..."
    podman network exists paralya-bot-network || podman network create paralya-bot-network
    podman run \
        --name "$CONTAINER_NAME" \
        --replace \
        --detach \
        --network paralya-bot-network \
        --env KORD_CACHE_URL="redis://ParalyaBotCache:6379" \
        --userns=keep-id \
        --volume "$PWD/container:/app/external:Z" \
        localhost/paralyabot:latest
    echo "Container $CONTAINER_NAME started successfully."
  '';

  run-bot-cache = writeShellScriptBin "run-bot-cache" ''
    CONTAINER_NAME="''${1:-ParalyaBotCache}"
    echo "Starting cache container: $CONTAINER_NAME..."
    podman network exists paralya-bot-network || podman network create paralya-bot-network
    podman run \
        --name "$CONTAINER_NAME" \
        --replace \
        --detach \
        --network paralya-bot-network \
        --userns=keep-id \
        localhost/paralyabot-cache:latest
    echo "Cache container $CONTAINER_NAME started successfully."
  '';

  build-and-run-bot = writeShellScriptBin "build-and-run-bot" ''
    ${lib.getExe build-bot} && ${lib.getExe run-bot} ''${1:-ParalyaBot}
  '';

  build-and-run-bot-cache = writeShellScriptBin "build-and-run-bot-cache" ''
    ${lib.getExe build-bot-cache} && ${lib.getExe run-bot-cache} ''${1:-ParalyaBotCache}
  '';

  run-all = writeShellScriptBin "run-all" ''
   ${lib.getExe run-bot-cache} && ${lib.getExe run-bot}
  '';

  build-and-run-all = writeShellScriptBin "build-and-run-all" ''
   ${lib.getExe build-and-run-bot-cache} && ${lib.getExe build-and-run-bot}
  '';

  build-plugin = writeShellScriptBin "build-plugin" ''
    PLUGIN=$1
    KEEP_RESULT=''${2:-false}

    if [ -z "$PLUGIN" ]; then
        echo "Usage: build-plugin <plugin-name> [keep-result]"
        exit 1
    fi

    NO_LINK=""
    if [ "$KEEP_RESULT" = "false" ]; then
        NO_LINK="--no-link"
    fi

    nix build .#$PLUGIN-plugin \
        --print-out-paths \
        $NO_LINK
  '';

  deploy-plugin = writeShellScriptBin "deploy-plugin" ''
    PLUGIN=$1
    if [ -z "$PLUGIN" ]; then
        echo "Usage: deploy-plugin <plugin-name>"
        exit 1
    fi

    echo "Building $PLUGIN-plugin..."
    OUT_PATH=$(${lib.getExe build-plugin} "$PLUGIN" false)

    PLUGIN_DIR="$PWD/container/plugins"
    mkdir -p "$PLUGIN_DIR"

    cp -f "$OUT_PATH"/*.zip "$PLUGIN_DIR/"

    for zip in "$OUT_PATH"/*.zip; do
        FILENAME=$(basename "$zip")
        DIRNAME="''${FILENAME%.zip}"
        if [ -d "$PLUGIN_DIR/$DIRNAME" ]; then
            rm -rf "$PLUGIN_DIR/$DIRNAME"
        fi
    done

    echo "$PLUGIN deployed successfully."
  '';

  update-deps = writeShellScriptBin "update-deps" ''
    set -e
    case "''${1:-}" in
        --help|-h)
            echo "Usage: update-deps [PACKAGE]"
            echo "If no package is specified, updates all dependency lockfiles."
            ;;
        "")
            for pkg_update in build-logic-update deps-compile-update common-update paralyabot-jar-update lg-plugin-update sta-plugin-update ai-plugin-update; do
                if ! $(nix build .#''${pkg_update} --print-out-paths); then
                    echo "Error: Failed to update ''${pkg_update} dependencies. Check the output above for details." >&2
                fi
            done
            ;;
        *)
            if ! $(nix build .#"''${1}-update" --print-out-paths); then
                 echo "Error: Failed to update ''${1} dependencies. Check the output above for details." >&2
            fi
            ;;
    esac
  '';

  snapshot-update = writeShellScriptBin "snapshot-update" ''
    ${builtins.readFile ./snapshot-update.sh}
  '';
in
mkShell {
  shellHook = ''
    ln -sfn ${project-jdk}/lib/openjdk .jdk
    ln -sfn ${go} .goroot
    export JAVA_HOME=$PWD/.jdk
    export PARALYABOT_VERSION=${extractVersion "paralyabot.version"}
    export PARALYABOT_CACHE_VERSION=${extractVersion "paralyabot.cache.version"}
  '';
  packages = [
    project-jdk
    go
    golangci-lint
    build-bot
    build-bot-cache
    run-bot
    run-bot-cache
    build-and-run-bot
    build-and-run-bot-cache
    run-all
    build-and-run-all
    build-plugin
    deploy-plugin
    update-deps
    snapshot-update
  ];
}
