{
    description = "The official flake for ParalyaBot, the Discord bot of the Paralya server.";

    inputs = {
        nixpkgs.url = "github:NixOS/nixpkgs/release-25.11";
    };

    outputs = { self, nixpkgs }:
        let
            system = "x86_64-linux";
            pkgs = nixpkgs.legacyPackages.${system};

            gradlePropsContent = builtins.readFile ./gradle.properties;
            versionMatch = builtins.match ".*paralyabot\.version=([^\n]+)" gradlePropsContent;
            globalVersion = builtins.elemAt versionMatch 0;
            jarName = "paralya-bot-${globalVersion}.jar";
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

            mkGradleBuild = { task, version, output, name, extension ? "jar" }:
                pkgs.stdenv.mkDerivation {
                    pname = name;
                    version = version;
                    src = ./.;
                    buildInputs = [ pkgs.jdk21 pkgs.cacert ];
                    dontConfigure = true;
                    buildPhase = ''
                        export GRADLE_USER_HOME=$(mktemp -d)
                        ./gradlew ${task} --no-daemon --no-configuration-cache
                    '';
                    installPhase = ''
                        mkdir -p $out
                        cp ${output} $out/${name}.${extension}
                    '';
                };
        in {
            packages.${system} = {
                paralyabot-jar = mkGradleBuild {
                    task = "shadowJar";
                    version = globalVersion;
                    output = "build/libs/${jarName}";
                    name = "paralyabot";
                };


                paralyabot-image = pkgs.dockerTools.streamLayeredImage {
                    name = "paralyabot";
                    tag = "latest";
                    created = builtins.readFile (pkgs.runCommand "created-timestamp" {} ''
                        printf '%s' "$(date -u -d @${toString self.lastModified} +'%Y-%m-%dT%H:%M:%SZ')" > $out
                    '');
                    
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

            devShells.${system}.default = pkgs.mkShell {
                buildInputs = with pkgs; [
                    jdk21
                ];
            };
        };
}