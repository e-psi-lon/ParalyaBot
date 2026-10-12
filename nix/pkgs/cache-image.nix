{
  lib,
  dockerTools,
  lastCommitAsTimestamp,
  paralyabot-cache,
}:
dockerTools.streamLayeredImage {
  name = "paralyabot-cache";
  tag = "latest";
  created = lastCommitAsTimestamp;

  extraCommands = ''
    mkdir -p app
    ln -s ${lib.getExe paralyabot-cache} app/paralyabot-cache
  '';

  config = {
    User = "1000:1000";
    Entrypoint = [ (lib.getExe paralyabot-cache) ];
    WorkingDir = "/app";
  };

  maxLayers = 25;
}
