{
  mkGradleBuild,
  build-logic,
  deps-compile,
  common-compile,
}:
mkGradleBuild {
  pname = "sta-plugin";
  module = "sta";
  versionProperty = "plugin.sta.version";
  srcRoots = [
    ../../../build-logic
    ../../../deps
    ../../../common
    ../../../sta
    ./sta.nix
  ];
  task = ":sta:distZip";
  buildDependencies = [
    build-logic
    deps-compile
    common-compile
  ];
  installPhase = ''
    mkdir -p $out
    cp sta/build/distributions/sta-''$version.zip $out/
  '';
}
