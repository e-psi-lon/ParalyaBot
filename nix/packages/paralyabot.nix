{
  mkGradleBuild,
  build-logic,
  deps-compile,
  common-compile,
  common-runtime,
}:
rec {
  paralyabot-jar-deps = mkGradleBuild {
    pname = "paralyabot-jar-deps";
    module = "bot";
    artifactVersion = "static";
    versionProperty = "paralyabot.deps.version";
    srcRoots = [
      ../../build-logic
      ../../deps
      ../../common
      ../../bot/build.gradle.kts
      ./paralyabot.nix
    ];
    task = "bot:copyRuntimeClasspath";
    buildDependencies = [
      build-logic
      deps-compile
      common-compile
    ];
    installPhase = ''
      mkdir -p $out
      cp bot/build/deps/*.jar $out/
    '';
  };

  paralyabot-jar = mkGradleBuild {
    pname = "paralyabot-jar";
    module = "bot";
    srcRoots = [
      ../../build-logic
      ../../deps
      ../../common
      ../../bot
      ./paralyabot.nix
    ];
    task = "bot:jar";

    preBuild = ''
      CLASS_PATH="${common-runtime}/paralya-bot-common.jar"
      EXTRA_JARS=$(ls ${paralyabot-jar-deps}/*.jar | tr '\n' ' ')
      export RAW_CLASSPATH="$CLASS_PATH $EXTRA_JARS"
    '';
    buildDependencies = [
      build-logic
      deps-compile
      common-compile
    ];
    installPhase = ''
      mkdir -p $out META-INF
      cp bot/build/libs/paralya-bot.jar $out/
      mkdir -p $out/nix-support
      echo ${common-runtime} > $out/nix-support/runtime-depends
      echo ${paralyabot-jar-deps} >> $out/nix-support/runtime-depends
    '';
  };
}
