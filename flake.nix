{
    description = "The official flake for ParalyaBot, the Discord bot of the Paralya server.";

    inputs = {
        nixpkgs.url = "github:NixOS/nixpkgs/release-25.11";
    };

    outputs = { self, nixpkgs }:
        let
            system = "x86_64-linux";
            pkgs = nixpkgs.legacyPackages.${system};


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

                    mkGradleBuild = { task, version, output, name, extension ? "jar", extraArgs ? "", outputHash }:
                        pkgs.stdenv.mkDerivation {
                            pname = name;
                            version = version;
                            src = ./.;
                            buildInputs = with pkgs; [ jdk21 cacert ];
                            dontConfigure = true;
                            outputHash = outputHash;
                            outputHashMode = "recursive";
                            outputHashAlgo = "sha256";

                            buildPhase = ''
                                export GRADLE_USER_HOME=$(mktemp -d)
                                export JAVA_HOME=${pkgs.jdk21}
                                export TZ=UTC
                                export LANG=C.UTF-8
                                export LC_ALL=C.UTF-8
                                export SOURCE_DATE_EPOCH=${toString self.lastModified}
                                export JAVA_TOOL_OPTIONS="-Djava.properties.date=${lastCommitAsTimestamp}"

                                ./gradlew ${task} \
                                    --no-daemon \
                                    --no-configuration-cache \
                                    --info \
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
                            task = "shadowJar";
                            version = version;
                            output = "build/libs/paralya-bot-${version}.jar";
                            name = "paralyabot";
                            outputHash = "sha256-dNN+n+AMIuCcNOm9VUK4aBO6/TpzM5W4pusaKhZNmvQ=";
                        };

                    lg-plugin = 
                        let
                            version = extractPluginVersion "lg";
                        in
                        mkGradleBuild {
                            task = "lg:distZip";
                            version = version;
                            output = "lg/build/distributions/lg-${version}.zip";
                            name = "lg-plugin-${version}";
                            extension = "zip";
                            outputHash = "sha256-ilVClqaW66m/fROj6MEOsUlVGYa177NnAMnCWPwWFWA=";
                        };

                    sta-plugin = 
                        let
                            version = extractPluginVersion "sta";
                        in
                        mkGradleBuild {
                            task = "sta:distZip";
                            version = extractPluginVersion "sta";
                            output = "sta/build/distributions/sta-${version}.zip";
                            name = "sta-plugin-${version}";
                            extension = "zip";
                            outputHash = "sha256-NrxEpEzwQvUUwtMtwKfLPyULVqNuniYfyajfyVQcOsE=";
                        };

                    paralyabot-image = 
                        let 
                            headlessJdk = pkgs.jdk21.override {
                                headless = true;
                                enableGtk = false;
                                enableJavaFX = false;
                            };
                            jre21 = pkgs.jre21_minimal.override {
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
                                jre21
                                pkgs.cacert
                            ];
                            
                            extraCommands = ''
                                mkdir -p app/config app/plugins
                                cp ${self.packages.${system}.paralyabot-jar}/paralyabot.jar app/paralyabot.jar
                            '';
                            
                            config = {
                                Entrypoint = [ "${jre21}/bin/java" "-jar" "/app/paralyabot.jar" ];
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
                        IMAGE_PATH=$(nix build .#paralyabot-image --print-out-paths)
                        if [ -n "$IMAGE_PATH" ]; then
                            "$IMAGE_PATH" | podman load
                        fi
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
                        ${build-bot}/bin/build-bot && ${run-bot}/bin/run-bot ''${1:-ParalyaBot}
                    '';

                    build-plugin = pkgs.writeShellScriptBin "build-plugin" ''
                        PLUGIN=$1
                        KEEP_RESULT=''${2:-false}
                        
                        if [ -z "$PLUGIN" ]; then
                            echo "Usage: build-plugin <plugin-name> [keep-result]"
                            exit 1
                        fi
                        
                        nix build .#$PLUGIN-plugin \
                            --print-out-paths \
                            ''${KEEP_RESULT:+--no-link} \
                    '';

                    deploy-plugin = pkgs.writeShellScriptBin "deploy-plugin" ''
                        PLUGIN=$1
                        if [ -z "$PLUGIN" ]; then
                            echo "Usage: deploy-plugin <plugin-name>"
                            exit 1
                        fi

                        echo "Building $PLUGIN-plugin..."
                        OUT_PATH=$(${build-plugin}/bin/build-plugin "$PLUGIN" true)
                        
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
                        PACKAGE="''${1:-paralyabot-jar}"
                        echo "Building .#$PACKAGE to check for hash mismatch..."
                        LOG=$(mktemp)
                        nix build ".#$PACKAGE" > "$LOG" 2>&1 || true
                        
                        if grep -q "hash mismatch" "$LOG"; then
                            echo "Hash mismatch detected."
                            
                            OLD_HASH=$(grep "specified:" "$LOG" | grep -oP 'sha256-\S+')
                            NEW_HASH=$(grep "got:" "$LOG" | grep -oP 'sha256-\S+')
                            
                            if [ -n "$OLD_HASH" ] && [ -n "$NEW_HASH" ]; then
                            echo "Updating flake.nix..."
                            echo "Replacing: $OLD_HASH"
                            echo "With:      $NEW_HASH"
                            
                            sed -i "s|$OLD_HASH|$NEW_HASH|" flake.nix
                            echo "Success. You can now build."
                            else
                            echo "Could not parse hashes from output. See log:"
                            cat "$LOG"
                            fi
                        else
                            echo "No hash mismatch found. Last build log:"
                            cat "$LOG"
                        fi
                        rm "$LOG"
                    '';
                in
                pkgs.mkShell {
                    buildInputs = with pkgs; [
                        jdk21
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