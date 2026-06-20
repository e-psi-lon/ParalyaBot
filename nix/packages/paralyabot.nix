{
  mkGradleBuild,
  build-logic,
  deps-compile,
  common-compile,
  common-runtime,
}:
let
  sharedArgs = {
    module = "bot";
    buildDependencies = [
      build-logic
      deps-compile
      common-compile
    ];
  };

  jarArgs = sharedArgs // {
    srcRoots = [
      ../../build-logic
      ../../deps
      ../../common
      ../../bot
      ./paralyabot.nix
    ];
    task = "bot:jar";
  };

  paralyabot-jar-deps = mkGradleBuild (sharedArgs // {
    pname = "paralyabot-jar-deps";
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
    installPhase = ''
      mkdir -p $out
      cp bot/build/deps/*.jar $out/
    '';
  });
in {
  inherit paralyabot-jar-deps;

  paralyabot-jar-update = (mkGradleBuild (jarArgs // {
    pname = "paralyabot-jar-update";
    preBuild = ''
      CLASS_PATH="${common-runtime}/paralya-bot-common.jar"
      export RAW_CLASSPATH="$CLASS_PATH $EXTRA_JARS"
    '';
    installPhase = "";
  })).mitmCache.updateScript;

  paralyabot-jar = mkGradleBuild (jarArgs // {
    pname = "paralyabot-jar";
    preBuild = ''
      CLASS_PATH="${common-runtime}/paralya-bot-common.jar"
      EXTRA_JARS=$(ls ${paralyabot-jar-deps}/*.jar | tr '\n' ' ')
      export RAW_CLASSPATH="$CLASS_PATH $EXTRA_JARS"
    '';
    installPhase = ''
      mkdir -p $out META-INF
      cp bot/build/libs/paralya-bot.jar $out/
      mkdir -p $out/nix-support
      echo ${common-runtime} > $out/nix-support/runtime-depends
      echo ${paralyabot-jar-deps} >> $out/nix-support/runtime-depends
    '';
  });
}
