{ lib, lastModifiedDate }:
rec {
  lastCommitAsTimestamp =
    let
      d = lastModifiedDate;
      year = builtins.substring 0 4 d;
      month = builtins.substring 4 2 d;
      day = builtins.substring 6 2 d;
      hour = builtins.substring 8 2 d;
      minute = builtins.substring 10 2 d;
      second = builtins.substring 12 2 d;
    in
    "${year}-${month}-${day}T${hour}:${minute}:${second}Z";

  parseProperties =
    path:
    let
      lines = lib.splitString "\n" (builtins.readFile path);
      toPair =
        line:
        let
          match = builtins.match "([^=]+)=(.*)$" line;
        in
        if match == null then
          null
        else
          {
            name = builtins.elemAt match 0;
            value = builtins.elemAt match 1;
          };
    in
    builtins.listToAttrs (builtins.filter (x: x != null) (map toPair lines));

  versionsProperties = parseProperties ../versions.properties;
  extractVersion =
    propertyName:
    if builtins.hasAttr propertyName versionsProperties then
      versionsProperties.${propertyName}
    else
      throw "Property ${propertyName} not found";
}
