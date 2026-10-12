{
    lib,
    writeShellScriptBin,
    writeShellApplication,
    coreutils,
    curl,
    gawk,
    gnugrep,
    gnused,
}:
let
  inherit (import ./common.nix) requireRoot;
  build-plugin = writeShellScriptBin "build-plugin" ''
    set -e
    ${requireRoot}
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

    nix build "$PROJECT_ROOT#$PLUGIN-plugin" \
        --print-out-paths \
        $NO_LINK
  '';

  deploy-plugin = writeShellScriptBin "deploy-plugin" ''
    set -e
    ${requireRoot}
    PLUGIN=$1
    if [ -z "$PLUGIN" ]; then
        echo "Usage: deploy-plugin <plugin-name>"
        exit 1
    fi

    echo "Building $PLUGIN-plugin..."
    OUT_PATH=$(${lib.getExe build-plugin} "$PLUGIN" false)

    PLUGIN_DIR="$PROJECT_ROOT/container/plugins"
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

  # `nix build` on an *-update package prints the path of an updater script;
  # the $(...) runs it. Intentional "build the updater, then run it" idiom.
  update-deps = writeShellScriptBin "update-deps" ''
    set -e
    ${requireRoot}
    case "''${1:-}" in
        --help|-h)
            echo "Usage: update-deps [PACKAGE]"
            echo "If no package is specified, updates all dependency lockfiles."
            ;;
        "")
            for pkg in build-logic deps-compile common paralyabot-jar lg-plugin sta-plugin ai-plugin; do
                echo "Updating $pkg..."
                if ! "$(nix build "$PROJECT_ROOT#$pkg-update" --print-out-paths)"; then
                    echo "Error: Failed to update $pkg dependencies. Check the output above for details." >&2
                fi
            done
            ;;
        *)
            if ! "$(nix build "$PROJECT_ROOT#$1-update" --print-out-paths)"; then
                echo "Error: Failed to update $1 dependencies. Check the output above for details." >&2
            fi
            ;;
    esac
  '';

  snapshot-check = writeShellApplication {
    name = "snapshot-check";
    runtimeInputs = [ coreutils curl gawk gnugrep gnused ];
    text = builtins.readFile ./snapshot-check.sh;
  };
in
{
  inherit
    build-plugin
    deploy-plugin
    update-deps
    snapshot-check
    ;
}
