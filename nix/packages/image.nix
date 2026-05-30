{
  lib,
  dockerTools,
  cacert,
  writeTextDir,
  project-jdk,
  project-jre-base,
  lastCommitAsTimestamp,
  paralyabot-jar,
}:
let
  headlessJdk = project-jdk.override {
    headless = true;
    enableGtk = false;
    enableJavaFX = false;
  };
  project-jre = project-jre-base.override {
    jdk = headlessJdk;
    jdkOnBuild = headlessJdk;
    modules = [
      "java.base"
      "java.xml"
      "java.naming"
      "java.logging"
      "jdk.crypto.ec"
    ];
  };
in
dockerTools.streamLayeredImage {
  name = "paralyabot";
  tag = "latest";
  created = lastCommitAsTimestamp;

  contents =
    let
      nixosVersion = lib.trivial.release;
      nixosCodeName = lib.trivial.codeName;

      nixosCodeNamePretty =
        let
          firstChar = lib.toUpper (builtins.substring 0 1 nixosCodeName);
          restChars = builtins.substring 1 (builtins.stringLength nixosCodeName) nixosCodeName;
        in
        "${firstChar}${restChars}";

      prettyName = "${nixosVersion} (${nixosCodeNamePretty})";
      versionSuffix = lib.trivial.versionSuffix;
    in
    [
      cacert
      (writeTextDir "etc/os-release" ''
      ANSI_COLOR="0;38;2;126;186;228"
      BUG_REPORT_URL="https://github.com/NixOS/nixpkgs/issues"
      BUILD_ID="${nixosVersion}.${versionSuffix}"
      CPE_NAME="cpe:/o:nixos:nixos:${nixosVersion}"
      DEFAULT_HOSTNAME=nixos
      DOCUMENTATION_URL="https://nixos.org/learn.html"
      HOME_URL="https://nixos.org/"
      ID=nixos
      ID_LIKE=""
      IMAGE_ID=""
      IMAGE_VERSION=""
      LOGO="nix-snowflake"
      NAME=NixOS
      PRETTY_NAME="NixOS ${prettyName}"
      SUPPORT_URL="https://nixos.org/community.html"
      VARIANT=""
      VARIANT_ID=""
      VENDOR_NAME=NixOS
      VENDOR_URL="https://nixos.org/"
      VERSION="${prettyName}"
      VERSION_CODENAME="${nixosCodeName}"
      VERSION_ID="${nixosVersion}"

    '')

    # Complete /etc/lsb-release Stub
    (writeTextDir "etc/lsb-release" ''
      DISTRIB_CODENAME="${nixosCodeName}"
      DISTRIB_DESCRIPTION="NixOS ${prettyName}"
      DISTRIB_ID=nixos
      DISTRIB_RELEASE="${nixosVersion}"
      LSB_VERSION="${prettyName}"
    '')
    ];

  extraCommands = ''
    mkdir -p app tmp
    chmod 1777 tmp
  '';

  config = {
    User = "1000:1000";
    Entrypoint = [
      (lib.getExe project-jre)
      "-XX:+UseCompactObjectHeaders"
      "-XX:+UseContainerSupport"
      "-XX:+UseStringDeduplication"
      "-XX:+PerfDisableSharedMem"
      "-XX:SoftMaxHeapSize=32m"
      "-XX:+DisableExplicitGC"
      "-XX:+ExitOnOutOfMemoryError"
      "-XX:MaxGCPauseMillis=50"
      "-XX:G1HeapRegionSize=1m"
      "-XX:ReservedCodeCacheSize=64m"
      "-Xms12m"
      "-Xmx256m"
      "--enable-native-access=ALL-UNNAMED"
      "-jar"
      "${paralyabot-jar}/paralya-bot.jar"
    ];
    WorkingDir = "/app";
    Env = [
      "PARALYA_BOT_CONFIG_FILE=/app/external/config.conf"
      "PARALYA_BOT_PLUGINS_DIR=/app/external/plugins"
      "BOT_DEVELOPER_ID=708006478807695450"
      # Required by data collection even if disabled
      "DATA_COLLECTION_UUID=7f7bec74-d7f9-4320-9a7e-46eab2be31c8"
    ];
    Volumes = {
      "/app/external" = { };
    };
  };

  maxLayers = 25;
}
