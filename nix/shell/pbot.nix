{
  lib,
  writeShellScriptBin,
  components,
  order,
  tools,
}:
let
  targets = components // {
    plugin = {
      build = tools.build-plugin;
      deploy = tools.deploy-plugin;
    };
    deps = {
      update = tools.update-deps;
    };
  };

  allVerbs = [
    "build"
    "run"
    "up"
  ];

  q = s: ''"${s}"'';

  scriptEntries =
    lib.concatLists (
      lib.mapAttrsToList (
        t: verbs: lib.mapAttrsToList (v: s: "[${q "${t}/${v}"}]=${q (lib.getExe s)}") verbs
      ) targets
    )
    ++ [ "[${q "snapshot-check"}]=${q (lib.getExe tools.snapshot-check)}" ];
in
writeShellScriptBin "pbot" ''
  set -euo pipefail

  declare -A SCRIPTS=(
    ${lib.concatStringsSep "\n  " scriptEntries}
  )
  TARGETS=(${lib.escapeShellArgs (lib.attrNames targets)})
  ALL=(${lib.escapeShellArgs order})
  ALL_VERBS=(${lib.escapeShellArgs allVerbs})

  ${builtins.readFile ./pbot.sh}
''
