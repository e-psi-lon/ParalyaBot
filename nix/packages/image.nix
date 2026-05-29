{
  lib,
  cacert,
  dockerTools,
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

  contents = [ cacert ];

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
