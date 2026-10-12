{ mkGradleBuild }:
mkGradleBuild {
  pname = "paralyabot-build-logic";
  srcRoots = [
    ../../build-logic
  ];
  versionProperty = "module.build-logic.version";
  module = "build-logic";
  task = "build-logic:build";
  updateTask = "build-logic:dependencies --write-verification-metadata sha256";
  installPhase = ''
    mkdir -p $out/build-logic/build $out/gradle-home
    cp -r build-logic/build/. $out/build-logic/build/
    cp -r $GRADLE_USER_HOME/caches $out/gradle-home/caches
  '';
}
