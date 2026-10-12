{
  buildGoModule,
  extractVersion,
  iana-etc,
  removeReferencesTo,
  tzdata,
}:
buildGoModule {
  pname = "paralyabot-cache";
  version = extractVersion "paralyabot.cache.version";
  src = ../../cache;
  vendorHash = null; # We only use the standard library, so no vendor hash is needed
  env.CGO_ENABLED = 0;

  ldflags = [
    "-s"
    "-w"
  ];

  postFixup = ''
    find "$out/bin" -type f -exec ${removeReferencesTo}/bin/remove-references-to \
        -t ${iana-etc} \
        -t ${tzdata} \
        '{}' +
  '';

  meta.mainProgram = "paralyabot-cache";
}
