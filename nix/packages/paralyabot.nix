{
  zip,
  unzip,
  perl,
  mkGradleBuild,
  build-logic,
  deps-compile,
  common-compile,
  common-runtime,
}:
rec {
  paralyabot-jar-deps = mkGradleBuild {
    pname = "paralyabot-jar-deps";
    module = ".";
    depsData = ../../deps.json;
    srcRoots = [
      ../../common
      ./paralyabot.nix
    ];
    task = ":copyRuntimeClasspath";
    buildDependencies = [
      build-logic
      deps-compile
      common-compile
    ];
    installPhase = ''
      mkdir -p $out
      cp build/deps/*.jar $out/
    '';
  };

  paralyabot-jar = mkGradleBuild {
    pname = "paralyabot-jar";
    module = ".";
    depsData = ../../deps.json;
    srcRoots = [
      ../../common
      ../../src
      ./paralyabot.nix
    ];
    task = ":jar";
    extraNativeInputs = [
      zip
      unzip
      perl
    ];
    buildDependencies = [
      build-logic
      deps-compile
      common-compile
    ];
    installPhase = ''
      mkdir -p $out META-INF
      cp build/libs/paralya-bot.jar $out/

      unzip -p "$out/paralya-bot.jar" META-INF/MANIFEST.MF > META-INF/MANIFEST.MF

      CLASS_PATH="${common-runtime}/paralya-bot-common.jar"
      EXTRA_JARS=$(ls ${paralyabot-jar-deps}/*.jar | tr '\n' ' ')
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
      echo ${common-runtime} > $out/nix-support/runtime-depends
      echo ${paralyabot-jar-deps} >> $out/nix-support/runtime-depends
    '';
  };
}
