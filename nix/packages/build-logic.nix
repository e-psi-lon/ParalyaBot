{ mkGradleBuild }:
mkGradleBuild {
  pname = "paralyabot-build-logic";
  srcRoots = [
    ../../build-logic
    ./build-logic.nix
  ];
  module = "build-logic";
  task = "build-logic:build";
  updateTask = "build-logic:build";
  installPhase = ''
    mkdir -p $out/build-logic/build $out/gradle-home
    cp -r build-logic/build/. $out/build-logic/build/
    cp -r $GRADLE_USER_HOME/caches $out/gradle-home/caches
  '';
}
