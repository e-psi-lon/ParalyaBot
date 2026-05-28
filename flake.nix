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

      lastCommitAsTimestamp =
        let
          d = self.lastModifiedDate;
          year = builtins.substring 0 4 d;
          month = builtins.substring 4 2 d;
          day = builtins.substring 6 2 d;
          hour = builtins.substring 8 2 d;
          minute = builtins.substring 10 2 d;
          second = builtins.substring 12 2 d;
        in
        "${year}-${month}-${day}T${hour}:${minute}:${second}Z";

      parseProperties =
        path:
        let
          lines = pkgs.lib.splitString "\n" (builtins.readFile path);
          toPair =
            line:
            let
              match = builtins.match "([^=]+)=(.*)$" line;
            in
            if match == null then
              null
            else
              {
                name = builtins.elemAt match 0;
                value = builtins.elemAt match 1;
              };
        in
        builtins.listToAttrs (builtins.filter (x: x != null) (map toPair lines));

      gradleProperties = parseProperties ./gradle.properties;
      extractVersion =
        propertyName:
        if builtins.hasAttr propertyName gradleProperties then
          gradleProperties.${propertyName}
        else
          throw "Property ${propertyName} not found";
    in
    {
      packages.${system} =
        let

          extractPluginVersion = pluginName: extractVersion "plugin.${pluginName}.version";

          baseGradleFileset = lib.fileset.unions [
            ./build-logic
            ./settings.gradle.kts
            ./gradle.properties
            ./gradle
            ./flake.nix
            ./flake.lock
            ./config
            (lib.fileset.fileFilter (file: file.name == "build.gradle.kts") ./.)
          ];

          wrapperProperties = parseProperties ./gradle/wrapper/gradle-wrapper.properties;
          gradleVersion =
            let
              url = wrapperProperties.distributionUrl;
              match = builtins.match ".*gradle-([0-9.]+)-bin\\.zip" url;
            in
            builtins.elemAt match 0;
          gradle-wrapper =
            (pkgs.gradle-packages.mkGradle {
              version = gradleVersion;
              hash = "sha256-KrKVjyoeURIMMmytbzhRU7sR7pOzwhbF/M6/37t+xss=";
              defaultJava = project-jdk;
            }).wrapped;

          mkGradleBuild =
            {
              pname,
              module ? pname,
              version ? extractVersion "paralyabot.version",
              task,
              updateTask ? if module == "." then ":nixDownloadDeps" else ":${module}:nixDownloadDeps",
              srcRoots ? [ ],
              depsData ? ./${module}/deps.json,
              buildDependencies ? [ ],
              installPhase,
              preBuild ? "",
              extraNativeInputs ? [ ],
            }:
            pkgs.stdenv.mkDerivation (finalAttrs: {
              inherit pname version;
              src = lib.fileset.toSource {
                root = ./.;
                fileset = lib.fileset.unions ([ baseGradleFileset ] ++ srcRoots);
              };
              nativeBuildInputs = [ gradle-wrapper ] ++ extraNativeInputs;

              strictDeps = true;
              mitmCache = gradle-wrapper.fetchDeps {
                pkg = finalAttrs.finalPackage;
                pname = pname;
                data = depsData;
              };

              gradleBuildTask = task;
              gradleUpdateTask = updateTask;

              preBuild = ''
                mkdir -p $GRADLE_USER_HOME

                ${lib.concatMapStrings (dep: ''
                  mkdir -p ${dep.passthru.buildDir}/build/
                  mkdir -p $GRADLE_USER_HOME/caches/
                  cp -rL --no-preserve=mode ${dep}/${dep.passthru.buildDir}/build/. ${dep.passthru.buildDir}/build/
                  cp -rL --no-preserve=mode ${dep}/gradle-home/caches/. $GRADLE_USER_HOME/caches/
                '') buildDependencies}

                chmod -R u+w .
                chmod -R u+w $GRADLE_USER_HOME

                ${preBuild}
              '';

              passthru = {
                buildDir = module;
              };

              env = {
                GRADLE_USER_HOME = "./gradle-home";
                JAVA_HOME = "${project-jdk}";
                LC_ALL = "C.UTF-8";
                SOURCE_DATE_EPOCH = "1730143059";
                JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF8 -Djava.properties.date=2024-10-28T18:24:19Z";
              };

              inherit installPhase;
            });
        in
        {
          build-logic = mkGradleBuild {
            pname = "paralyabot-build-logic";
            module = "build-logic";
            task = "build-logic:build";
            updateTask = "build-logic:build";
            installPhase = ''
              mkdir -p $out/build-logic/build $out/gradle-home
              cp -r build-logic/build/. $out/build-logic/build/
              cp -r $GRADLE_USER_HOME/caches $out/gradle-home/caches
            '';
          };
          deps-compile = mkGradleBuild {
            pname = "paralyabot-deps";
            module = "deps";
            srcRoots = [ ./deps ];
            task = "deps:shadowJar";
            buildDependencies = [ self.packages.${system}.build-logic ];
            installPhase = ''
                mkdir -p $out/deps/build $out/gradle-home
                cp -r deps/build/. $out/deps/build/
                cp -r $GRADLE_USER_HOME/caches $out/gradle-home/caches
                cp deps/build/libs/paralya-bot-deps.jar $out/
            '';
          };
          deps-runtime = pkgs.runCommand "paralyabot-deps-runtime" {} ''
            mkdir -p $out
            cp --no-preserve=mode ${self.packages.${system}.deps-compile}/paralya-bot-deps.jar $out/
          '';
          common-compile = mkGradleBuild {
            pname = "paralyabot-common-compile";
            module = "common";
            srcRoots = [ ./common ];
            task = "common:jar";
            updateTask = "common:nixDownloadDeps";
            buildDependencies = [ self.packages.${system}.build-logic self.packages.${system}.deps-compile ];
            installPhase = ''
                mkdir -p $out/common/build $out/gradle-home
                cp -r common/build/. $out/common/build/
                cp -r $GRADLE_USER_HOME/caches $out/gradle-home/caches
                cp common/build/libs/paralya-bot-common.jar $out/
            '';
          };
          common-runtime-deps = mkGradleBuild {
            pname = "paralyabot-common-deps";
            module = "common";
            srcRoots = [ ./common/build.gradle.kts ];
            task = "common:copyRuntimeClasspath";
            buildDependencies = [ self.packages.${system}.build-logic self.packages.${system}.deps-compile self.packages.${system}.common-compile ];
            installPhase = ''
              mkdir -p $out
              cp common/build/deps/*.jar $out/
            '';
          };

          common-runtime = pkgs.runCommand "paralyabot-common-runtime" {
            nativeBuildInputs = with pkgs; [ zip unzip perl ];
          } ''
            mkdir -p $out META-INF
            cp --no-preserve=mode ${self.packages.${system}.common-compile}/paralya-bot-common.jar $out/

            unzip -p "$out/paralya-bot-common.jar" META-INF/MANIFEST.MF > META-INF/MANIFEST.MF

            CLASS_PATH=${self.packages.${system}.deps-runtime}/paralya-bot-deps.jar
            EXTRA_JARS=$(ls ${self.packages.${system}.common-runtime-deps}/*.jar | tr '\n' ' ')
            export RAW_CLASSPATH="Class-Path: $CLASS_PATH $EXTRA_JARS"
            perl -i -0777 -pe '
              my $cp = $ENV{RAW_CLASSPATH};
              my $formatted = "";
              if ($cp =~ s/^(.{1,70})//) { $formatted .= $1 . "\r\n"; }
              while ($cp =~ s/^(.{1,69})//) { $formatted .= " " . $1 . "\r\n"; }
              
              s/^Class-Path:.*(?:\r?\n .*)*\r?\n//gm;

              s|(Manifest-Version:.*?\r?\n)|$1$formatted|g;
            ' META-INF/MANIFEST.MF

            zip "$out/paralya-bot-common.jar" META-INF/MANIFEST.MF

            mkdir -p $out/nix-support
            echo ${self.packages.${system}.deps-runtime} > $out/nix-support/runtime-depends
            echo ${self.packages.${system}.common-runtime-deps} >> $out/nix-support/runtime-depends
          '';

          paralyabot-jar-deps = mkGradleBuild {
            pname = "paralyabot-jar-deps";
            module = ".";
            depsData = ./deps.json;
            srcRoots = [ ./common ];
            task = ":copyRuntimeClasspath";
            buildDependencies = [ self.packages.${system}.build-logic self.packages.${system}.deps-compile self.packages.${system}.common-compile ];
            installPhase = ''
              mkdir -p $out
              cp build/deps/*.jar $out/
            '';
          };

          paralyabot-jar = mkGradleBuild {
            pname = "paralyabot-jar";
            module = ".";
            depsData = ./deps.json;
            srcRoots = [ ./common ./src ];
            task = ":jar";
            extraNativeInputs = with pkgs; [ zip unzip perl ];
            buildDependencies = [ self.packages.${system}.build-logic self.packages.${system}.deps-compile self.packages.${system}.common-compile ];
            installPhase = ''
                mkdir -p $out META-INF
                cp build/libs/paralya-bot.jar $out/
                
                unzip -p "$out/paralya-bot.jar" META-INF/MANIFEST.MF > META-INF/MANIFEST.MF

                CLASS_PATH="${self.packages.${system}.common-runtime}/paralya-bot-common.jar"
                EXTRA_JARS=$(ls ${self.packages.${system}.paralyabot-jar-deps}/*.jar | tr '\n' ' ')
                export RAW_CLASSPATH="Class-Path: $CLASS_PATH $EXTRA_JARS"

                perl -i -0777 -pe '
                  my $cp = $ENV{RAW_CLASSPATH};
                  my $formatted = "";
                  if ($cp =~ s/^(.{1,70})//) { $formatted .= $1 . "\r\n"; }
                  while ($cp =~ s/^(.{1,69})//) { $formatted .= " " . $1 . "\r\n"; }
                  
                  s/^Class-Path:.*(?:\r?\n .*)*\r?\n//gm;

                  s|(Manifest-Version:.*?\r?\n)|$1$formatted|g;
                ' META-INF/MANIFEST.MF

                zip "$out/paralya-bot.jar" META-INF/MANIFEST.MF

                mkdir -p $out/nix-support
                echo ${self.packages.${system}.common-runtime} > $out/nix-support/runtime-depends
                echo ${self.packages.${system}.paralyabot-jar-deps} >> $out/nix-support/runtime-depends
            '';
          };
          lg-plugin = mkGradleBuild {
            pname = "lg-plugin";
            module = "lg";
            version = extractPluginVersion "lg";
            srcRoots = [ ./common ./lg ];
            task = ":lg:distZip";
            buildDependencies = [ self.packages.${system}.build-logic self.packages.${system}.deps-compile self.packages.${system}.common-compile ];
            installPhase = ''
              mkdir -p $out
              cp lg/build/distributions/lg-''${version}.zip $out/
            '';
          };

          sta-plugin = mkGradleBuild {
            pname = "sta-plugin";
            module = "sta";
            version = extractPluginVersion "sta";
            srcRoots = [ ./common ./sta ];
            task = ":sta:distZip";
            buildDependencies = [ self.packages.${system}.build-logic self.packages.${system}.deps-compile self.packages.${system}.common-compile ];
            installPhase = ''
              mkdir -p $out
              cp sta/build/distributions/sta-''${version}.zip $out/
            '';
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
                modules = [
                  "java.base"
                  "java.xml"
                  "java.naming"
                  "java.logging"
                  "jdk.crypto.ec"
                ];
              };
            in
            pkgs.dockerTools.streamLayeredImage {
              name = "paralyabot";
              tag = "latest";
              created = lastCommitAsTimestamp;

              contents = [ pkgs.cacert ];

              extraCommands = ''
                mkdir -p app tmp
                chmod 1777 tmp
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
                  "-XX:ReservedCodeCacheSize=64m"
                  "-Xms12m"
                  "-Xmx256m"
                  "--enable-native-access=ALL-UNNAMED"
                  "-jar"
                  "${self.packages.${system}.paralyabot-jar}/paralya-bot.jar"
                ];
                WorkingDir = "/app";
                Env = [
                  "PARALYA_BOT_CONFIG_FILE=/app/external/config.conf"
                  "PARALYA_BOT_PLUGINS_DIR=/app/external/plugins"
                  "BOT_DEVELOPER_ID=708006478807695450"
                  # Required by data collection even if disabled
                  "DATA_COLLECTION_UUID=7f7bec74-d7f9-4320-9a7e-46eab2be31c8"
                ];
                Volumes = {
                  "/app/external" = { };
                };
              };

              maxLayers = 25;
            };
        };

      devShells.${system}.default =
        let
          build-bot = pkgs.writeShellScriptBin "build-bot" ''
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

          deploy-plugin = pkgs.writeShellScriptBin "deploy-plugin" ''
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

          update-deps = pkgs.writeShellScriptBin "update-deps" ''
            case "''${1:-}" in
                --help|-h)
                    echo "Usage: update-deps [PACKAGE]"
                    echo "If no package is specified, updates all dependency lockfiles."
                    ;;
                "")
                    for pkg in build-logic deps-compile common-compile paralyabot-jar lg-plugin sta-plugin; do
                        if ! $(nix build .#''${pkg}.mitmCache.updateScript --print-out-paths); then
                            echo "Error: Failed to update ''${pkg} dependencies. Check the output above for details." >&2
                        fi
                    done
                    ;;
                *)
                    $(nix build .#"''${1}".mitmCache.updateScript --print-out-paths)
                    ;;
            esac
          '';
        in
        pkgs.mkShell {
          shellHook = ''
            ln -sfn ${project-jdk}/lib/openjdk .jdk
            export JAVA_HOME=$PWD/.jdk
            export PARALYABOT_VERSION=${extractVersion "paralyabot.version"}
          '';
          buildInputs = [
            project-jdk
            build-bot
            run-bot
            build-and-run-bot
            build-plugin
            deploy-plugin
            update-deps
          ];
        };
    };
}
