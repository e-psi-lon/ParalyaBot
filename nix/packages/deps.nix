{
  runCommand,
  mkGradleBuild,
  build-logic,
}:
rec {
  deps-compile = mkGradleBuild {
    pname = "paralyabot-deps";
    module = "deps";
    versionProperty = "module.deps.version";
    srcRoots = [
      ../../deps
      ./deps.nix
    ];
    task = "deps:shadowJar";
    buildDependencies = [ build-logic ];
    installPhase = ''
      mkdir -p $out/deps/build $out/gradle-home
      cp -r deps/build/. $out/deps/build/
      cp -r $GRADLE_USER_HOME/caches $out/gradle-home/caches
      cp deps/build/libs/paralya-bot-deps.jar $out/
    '';
  };
  deps-runtime = runCommand "paralyabot-deps-runtime" { } ''
    mkdir -p $out
    cp --no-preserve=mode ${deps-compile}/paralya-bot-deps.jar $out/
  '';
}
