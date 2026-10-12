usage() {
  cat <<'EOF'
Usage: pbot [target] <verb> [args...]

Without a target, the verb applies to all components (cache, then bot):
  pbot build | run | up

Targets:
  bot, cache      build | run [container-name] | up [container-name]
  plugin          build <name> [keep-result] | deploy <name>
  deps            update [package]

Other:
  snapshot-check
EOF
}

die() {
  echo "pbot: $*" >&2
  exit 1
}

has() {
  [ -n "${SCRIPTS[$1]:-}" ]
}

is_target() {
  local t
  for t in "${TARGETS[@]}"; do
    if [ "$t" = "$1" ]; then
      return 0
    fi
  done
  return 1
}

if [ $# -eq 0 ]; then
  usage
  exit 0
fi

case "$1" in
  -h | --help | help)
    usage
    exit 0
    ;;
esac

if has "$1"; then
  cmd=$1
  shift
  exec "${SCRIPTS[$cmd]}" "$@"
fi

if is_target "$1"; then
  target=$1
  shift
  [ $# -gt 0 ] || die "missing verb for '$target' (try --help)"
  verb=$1
  shift
  has "$target/$verb" || die "'$target' has no verb '$verb' (try --help)"
  exec "${SCRIPTS[$target/$verb]}" "$@"
fi

verb=$1
shift
[ $# -eq 0 ] || die "'$verb' without a target takes no arguments"

known=
for v in "${ALL_VERBS[@]}"; do
  if [ "$v" = "$verb" ]; then
    known=1
  fi
done
[ -n "$known" ] || die "unknown command '$verb' (try --help)"

for t in "${ALL[@]}"; do
  "${SCRIPTS[$t/$verb]}"
done
