# format: "toml-key|groupPath|artifactId|host"
declare -a TARGETS=(
  "kordex-library|dev/kordex|annotations|https://snapshots-repo.kordex.dev"
  "kordex-data-api|dev/kordex/data|api|https://snapshots-repo.kordex.dev"
  "kord-core|dev/kord|kord-core|https://snapshots.kord.dev"
  "kord-voice|dev/kord|kord-voice|https://snapshots.kord.dev"
  "kord-common|dev/kord|kord-common|https://snapshots.kord.dev"
  "kord-gateway|dev/kord|kord-gateway|https://snapshots.kord.dev"
  "kord-rest|dev/kord|kord-rest|https://snapshots.kord.dev"
)

: "${PROJECT_ROOT:?PROJECT_ROOT is not set; run inside the dev shell}"
TOML_FILE="$PROJECT_ROOT/gradle/libs.versions.toml"

if [[ ! -f "$TOML_FILE" ]]; then
  echo "Can't find $TOML_FILE" >&2
  exit 1
fi

status=0

for target in "${TARGETS[@]}"; do
  IFS='|' read -r key groupPath artifactId host <<< "$target"

  pinned=$(awk -v key="$key" '
    /^\[/ { in_versions = ($0 ~ /^\[versions\]/); next }
    in_versions && $0 ~ ("^" key "[ \t]*=") {
      if (match($0, /"[^"]+"/)) { print substr($0, RSTART + 1, RLENGTH - 2) }
      exit
    }
  ' "$TOML_FILE")

  if [[ -z "$pinned" ]]; then
    echo "== $key: not found in [versions] of $TOML_FILE (skipping) =="
    continue
  fi

  base_version=$(sed -E 's/-[0-9]{8}\.[0-9]{6}-[0-9]+$//' <<< "$pinned")
  metadata_url="${host}/${groupPath}/${artifactId}/${base_version}-SNAPSHOT/maven-metadata.xml"

  echo "== $key =="
  echo "  pinned:   $pinned"
  echo "  fetching: $metadata_url"

  if ! xml=$(curl -sSfL --max-time 15 "$metadata_url") || [[ -z "$xml" ]]; then
    echo "  -> fetch failed (bad URL, wrong artifactId, or host unreachable)"
    echo
    status=1
    continue
  fi

  latest_ts=$(grep -oE '<timestamp>[^<]+</timestamp>' <<< "$xml" | tail -1 | sed -E 's/<[^>]+>//g' || true)
  latest_build=$(grep -oE '<buildNumber>[^<]+</buildNumber>' <<< "$xml" | tail -1 | sed -E 's/<[^>]+>//g' || true)

  if [[ -z "$latest_ts" || -z "$latest_build" ]]; then
    echo "  -> couldn't parse timestamp/buildNumber from metadata. Raw response:"
    head -20 <<< "$xml"
    echo
    status=1
    continue
  fi

  latest_full="${base_version}-${latest_ts}-${latest_build}"

  if [[ "$latest_full" == "$pinned" ]]; then
    echo "  -> up to date"
  else
    echo "  -> NEW BUILD AVAILABLE: $latest_full"
    status=1
  fi
  echo
done

exit "$status"