{
  runCommand,
  zip,
  unzip,
  perl,
  mkGradleBuild,
  extractVersion,
  build-logic,
  deps-compile,
  deps-runtime,
}:
rec {
  common-compile = mkGradleBuild {
    pname = "paralyabot-common-compile";
    module = "common";
    versionProperty = "module.common.version";
    extraGradleFlags = [ "-Pmodule.common.min-compatible-version=${extractVersion "module.common.min-compatible-version"}" ];
    srcRoots = [
      ../../common
      ./common.nix
    ];
    task = "common:jar";
    updateTask = "common:nixDownloadDeps";
    buildDependencies = [
      build-logic
      deps-compile
    ];
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
    srcRoots = [
      ../../common/build.gradle.kts
      ./common.nix
    ];
    extraGradleFlags = [ "-Pmodule.common.min-compatible-version=${extractVersion "module.common.min-compatible-version"}" ];
    task = "common:copyRuntimeClasspath";
    buildDependencies = [
      build-logic
      deps-compile
      common-compile
    ];
    installPhase = ''
      mkdir -p $out
      cp common/build/deps/*.jar $out/
    '';
  };

  common-runtime =
    runCommand "paralyabot-common-runtime"
      {
        nativeBuildInputs = [
          zip
          unzip
          perl
        ];
      }
      ''
        mkdir -p $out META-INF
        cp --no-preserve=mode ${common-compile}/paralya-bot-common.jar $out/

        unzip -p "$out/paralya-bot-common.jar" META-INF/MANIFEST.MF > META-INF/MANIFEST.MF

        CLASS_PATH=${deps-runtime}/paralya-bot-deps.jar
        EXTRA_JARS=$(ls ${common-runtime-deps}/*.jar | tr '\n' ' ')
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
        echo ${deps-runtime} > $out/nix-support/runtime-depends
        echo ${common-runtime-deps} >> $out/nix-support/runtime-depends
      '';
}
