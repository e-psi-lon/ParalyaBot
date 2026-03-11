{
    description = "The official flake for ParalyaBot, the Discord bot of the Paralya server.";

    inputs = {
        nixpkgs.url = "github:NixOS/nixpkgs/release-25.11";
    };

    outputs = { self, nixpkgs }:
        let
            system = "x86_64-linux";
            pkgs = nixpkgs.legacyPackages.${system};
            lib = pkgs.lib;
            project-jdk = pkgs.jdk25;


            lastCommitAsTimestamp = let
                d = self.lastModifiedDate;
                year = builtins.substring 0 4 d;
                month = builtins.substring 4 2 d;
                day = builtins.substring 6 2 d;
                hour = builtins.substring 8 2 d;
                minute = builtins.substring 10 2 d;
                second = builtins.substring 12 2 d;
            in "${year}-${month}-${day}T${hour}:${minute}:${second}Z";
        in {
            packages.${system} =
                let
                    parseProperties = path: let
                        lines = pkgs.lib.splitString "\n" (builtins.readFile path);
                        toPair = line: let
                           match = builtins.match "([^=]+)=(.*)$" line;
                        in if match == null then null else {
                            name = builtins.elemAt match 0;
                            value = builtins.elemAt match 1;
                        };
                    in builtins.listToAttrs (builtins.filter (x: x != null) (map toPair lines));

                    gradleProperties = parseProperties ./gradle.properties;
                    extractVersion = propertyName:
                        if builtins.hasAttr propertyName gradleProperties
                        then gradleProperties.${propertyName}
                        else throw "Property ${propertyName} not found";

                    extractPluginVersion = pluginName: extractVersion "plugin.${pluginName}.version";

                    wrapperProperties = parseProperties ./gradle/wrapper/gradle-wrapper.properties;
                    gradleVersion = let
                        url = wrapperProperties.distributionUrl;
                        match = builtins.match ".*gradle-([0-9.]+)-bin\\.zip" url;
                    in builtins.elemAt match 0;
                    gradle-wrapper = (pkgs.gradle-packages.mkGradle {
                        version = gradleVersion;
                        hash = "sha256-oX3dhaJran9d23H/iwX8UQTAICxuZHgkKXkMkzaGyAY=";
                        defaultJava = project-jdk;
                    }).wrapped;

                    mkGradleBuild = { task, version, output, name, outputHash, extension ? "jar", extraArgs ? "" }:
                        pkgs.stdenv.mkDerivation {
                            pname = name;
                            inherit version outputHash;
                            src = ./.;
                            buildInputs = with pkgs; [ project-jdk cacert gradle-wrapper ];
                            dontConfigure = true;
                            outputHashMode = "recursive";
                            outputHashAlgo = "sha256";

                            buildPhase = ''
                                export GRADLE_USER_HOME=$(mktemp -d)
                                export JAVA_HOME=${project-jdk}
                                export TZ=UTC
                                export LANG=C.UTF-8
                                export LC_ALL=C.UTF-8
                                export SOURCE_DATE_EPOCH=1730143059
                                export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF8 -Djava.properties.date=$(date -u +"%Y-%m-%dT%H:%M:%SZ" -d "@$SOURCE_DATE_EPOCH")"


                                ${lib.getExe gradle-wrapper} ${task} \
                                    --no-daemon \
                                    --no-configuration-cache \
                                    --console=plain \
                                    --stacktrace \
                                    ${extraArgs}
                            '';

                            installPhase = ''
                                mkdir -p $out
                                cp ${output} $out/${name}.${extension}
                            '';
                        };
                in {
                    paralyabot-jar =
                        let
                            version = extractVersion "paralyabot.version";
                        in
                        mkGradleBuild {
                            inherit version;
                            task = "shadowJar";
                            output = "build/libs/paralya-bot-${version}.jar";
                            name = "paralyabot";
                            outputHash = "sha256-TTw0CoUtMkv5C+WUPCODZipSZKSQrq60FKKKVQIPbio=";
                        };

                    lg-plugin =
                        let
                            version = extractPluginVersion "lg";
                        in
                        mkGradleBuild {
                            inherit version;
                            task = "lg:distZip";
                            output = "lg/build/distributions/lg-${version}.zip";
                            name = "lg-plugin-${version}";
                            extension = "zip";
                            outputHash = "sha256-Mj7oEmfEFJpD3O9/x7HA+mC5sCrYdCdAa/ki2vkPT7s=";
                        };

                    sta-plugin =
                        let
                            version = extractPluginVersion "sta";
                        in
                        mkGradleBuild {
                            inherit version;
                            task = "sta:distZip";
                            output = "sta/build/distributions/sta-${version}.zip";
                            name = "sta-plugin-${version}";
                            extension = "zip";
                            outputHash = "sha256-N4jEuHX/zgY8pQ712WtgJNVnWsFCQkalS2ZG+6e+a4I=";
                        };

                    paralyabot-image =
                        let
                            headlessJdk = project-jdk.override {
                                headless = true;
                                enableGtk = false;
                                enableJavaFX = false;
                            };
                            project-jre = pkgs.jre25_minimal.override {
                                jdk = headlessJdk;
                                jdkOnBuild = headlessJdk;
                                modules = [ "java.base" "java.xml" "java.naming" "java.logging" "jdk.crypto.ec" ];
                            };
                        in
                        pkgs.dockerTools.streamLayeredImage {
                            name = "paralyabot";
                            tag = "latest";
                            created = lastCommitAsTimestamp;

                            contents = [
                                project-jre
                                pkgs.cacert
                            ];

                            extraCommands = ''
                                mkdir -p app/config app/plugins
                                cp ${self.packages.${system}.paralyabot-jar}/paralyabot.jar app/paralyabot.jar
                            '';

                            config = {
                                User = "1000:1000";
                                Entrypoint = [
                                    (lib.getExe project-jre)
                                    "-XX:+UseCompactObjectHeaders"
                                    "-XX:+UseContainerSupport"
                                    "-XX:+UseStringDeduplication"
                                    "-XX:+PerfDisableSharedMem"
                                    "-XX:SoftMaxHeapSize=32m"
                                    "-XX:+DisableExplicitGC"
                                    "-XX:+ExitOnOutOfMemoryError"
                                    "-XX:MaxGCPauseMillis=50"
                                    "-XX:G1HeapRegionSize=1m"
                                    "-XX:ReservedCodeCacheSize=32m"
                                    "-Xms12m"
                                    "-Xmx64m"
                                    "-jar"
                                    "/app/paralyabot.jar"
                                ];
                                WorkingDir = "/app";
                                Env = [
                                    "PARALYA_BOT_CONFIG_FILE=/app/external/config.conf"
                                    "PARALYA_BOT_PLUGINS_DIR=/app/external/plugins"
                                ];
                                Volumes = {
                                    "/app/external" = {};
                                };
                            };

                            maxLayers = 25;
                        };
                };

            devShells.${system}.default =
                let
                    build-bot = pkgs.writeShellScriptBin "build-bot" ''
                        set -e
                        IMAGE_PATH=$(nix build .#paralyabot-image --print-out-paths)
                        "$IMAGE_PATH" | podman load
                    '';
                    run-bot = pkgs.writeShellScriptBin "run-bot" ''
                        CONTAINER_NAME="''${1:-ParalyaBot}"

                        echo "Starting container: $CONTAINER_NAME..."
                        podman run \
                            --name "$CONTAINER_NAME" \
                            --replace \
                            --detach \
                            --volume "$PWD/container:/app/external:Z" \
                            localhost/paralyabot:latest
                        echo "Container $CONTAINER_NAME started successfully."
                    '';

                    build-and-run-bot = pkgs.writeShellScriptBin "build-and-run-bot" ''
                        ${lib.getExe build-bot} && ${lib.getExe run-bot} ''${1:-ParalyaBot}
                    '';

                    build-plugin = pkgs.writeShellScriptBin "build-plugin" ''
                        PLUGIN=$1
                        NO_SYMLINK=''${2:-false}

                        if [ -z "$PLUGIN" ]; then
                            echo "Usage: build-plugin <plugin-name> [keep-result]"
                            exit 1
                        fi

                        nix build .#$PLUGIN-plugin \
                            --print-out-paths \
                            ''${NO_SYMLINK:+--no-link} \
                    '';

                    deploy-plugin = pkgs.writeShellScriptBin "deploy-plugin" ''
                        PLUGIN=$1
                        if [ -z "$PLUGIN" ]; then
                            echo "Usage: deploy-plugin <plugin-name>"
                            exit 1
                        fi

                        echo "Building $PLUGIN-plugin..."
                        OUT_PATH=$(${lib.getExe build-plugin} "$PLUGIN" true)

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

                    update-hash = pkgs.writeShellScriptBin "update-hash" ''
                        if [ "''${1:-}" = "--all" ]; then
                            for pkg in paralyabot-jar lg-plugin sta-plugin; do
                                ${lib.getExe pkgs.nix-update} --flake --version=skip "$pkg"
                            done
                        else
                            PACKAGE="''${1:-paralyabot-jar}"
                            ${lib.getExe pkgs.nix-update} --flake --version=skip "$PACKAGE"
                        fi
                    '';
                in
                pkgs.mkShell {
                    shellHook = ''
                        ln -sfn ${project-jdk}/lib/openjdk .jdk
                        export JAVA_HOME=$PWD/.jdk
                    '';
                    buildInputs = with pkgs; [
                        project-jdk
                        nix-update
                        build-bot
                        run-bot
                        build-and-run-bot
                        build-plugin
                        deploy-plugin
                        update-hash
                    ];
                };
        };
}