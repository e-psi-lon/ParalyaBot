{
  mkGradleBuild,
  build-logic,
  deps-compile,
  common-compile,
}:
mkGradleBuild {
  pname = "lg-plugin";
  module = "lg";
  versionProperty = "plugin.lg.version";
  srcRoots = [
    ../../../build-logic
    ../../../deps
    ../../../common
    ../../../lg
    ./lg.nix
  ];
  task = ":lg:distZip";
  buildDependencies = [
    build-logic
    deps-compile
    common-compile
  ];
  installPhase = ''
    mkdir -p $out
    cp lg/build/distributions/lg-''$version.zip $out/
  '';
}
