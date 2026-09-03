#!/usr/bin/env bash
# Postgres backup — infra plan §7 (Backups & retention).
#   Usage: scripts/ops/backup-db.sh
# pg_dump -Fc into backups/daily/ (keep 7); Sundays also copy into backups/weekly/ (keep 4).
# Called by release.sh before every deploy (release procedure step 3) and by the nightly cron:
#   0 2 * * * /opt/orbit/scripts/ops/backup-db.sh >> /var/log/orbit/backup.log 2>&1
# Off-VM copy: set BACKUP_REMOTE to an rsync target (e.g. user@host:/orbit-backups) — plan §7
# requires one; the script warns loudly when it is unset.
# Snapshot volume is deliberately NOT backed up (7-day TTL, expendable — plan §7).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
BACKUP_DIR="${ORBIT_BACKUP_DIR:-$ROOT/backups}"
CONTAINER="${ORBIT_PG_CONTAINER:-orbit-postgres}"
DB="${ORBIT_PG_DB:-orbit}"
DB_USER="${ORBIT_PG_USER:-orbit}"
KEEP_DAILY=7
KEEP_WEEKLY=4

DAILY="$BACKUP_DIR/daily"
WEEKLY="$BACKUP_DIR/weekly"
mkdir -p "$DAILY" "$WEEKLY"
DUMP="$DAILY/orbit_$(date +%F_%H%M%S).dump"

# A failed run must not leave a zero-byte file that later reads as a backup.
cleanup_on_fail() { [ -s "$DUMP" ] || rm -f "$DUMP"; }
trap cleanup_on_fail EXIT

echo "== Orbit DB backup → $DUMP"

PG_DUMP="${ORBIT_PG_DUMP:-pg_dump}"   # override when host pg_dump doesn't match server major
IN_CONTAINER=false
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${CONTAINER}\$"; then
  IN_CONTAINER=true
  docker exec "$CONTAINER" pg_dump -U "$DB_USER" -Fc "$DB" > "$DUMP"
elif command -v "$PG_DUMP" >/dev/null 2>&1; then
  "$PG_DUMP" -h "${PGHOST:-localhost}" -p "${PGPORT:-5432}" -U "$DB_USER" -Fc "$DB" > "$DUMP"
else
  echo "ERROR: container '$CONTAINER' not running and no local pg_dump" >&2
  exit 1
fi

[ -s "$DUMP" ] || { echo "ERROR: dump is empty: $DUMP" >&2; exit 1; }

# A custom-format dump must yield a table of contents — cheap integrity gate,
# not a substitute for the pre-go-live restore drill (plan §7).
if $IN_CONTAINER; then
  docker exec -i "$CONTAINER" pg_restore --list /dev/stdin < "$DUMP" > /dev/null
elif command -v pg_restore >/dev/null 2>&1; then
  pg_restore --list "$DUMP" > /dev/null
else
  echo "   (pg_restore unavailable — integrity check skipped)"
fi

# At-rest encryption (audit M5): dumps hold client PII, so encrypt before they
# rest on disk / are copied off-VM. Opt in with one of:
#   BACKUP_GPG_RECIPIENT  — public-key encrypt to this gpg key id/email
#   BACKUP_GPG_PASSPHRASE — symmetric AES256 encrypt with this passphrase
if [ -n "${BACKUP_GPG_RECIPIENT:-}" ]; then
  gpg --batch --yes --encrypt --recipient "$BACKUP_GPG_RECIPIENT" -o "$DUMP.gpg" "$DUMP"
  shred -u "$DUMP" 2>/dev/null || rm -f "$DUMP"
  DUMP="$DUMP.gpg"
  echo "-- Encrypted with gpg (recipient $BACKUP_GPG_RECIPIENT)"
elif [ -n "${BACKUP_GPG_PASSPHRASE:-}" ]; then
  gpg --batch --yes --symmetric --cipher-algo AES256 \
      --passphrase "$BACKUP_GPG_PASSPHRASE" -o "$DUMP.gpg" "$DUMP"
  shred -u "$DUMP" 2>/dev/null || rm -f "$DUMP"
  DUMP="$DUMP.gpg"
  echo "-- Encrypted with gpg (symmetric AES256)"
else
  echo "WARNING: no BACKUP_GPG_RECIPIENT/BACKUP_GPG_PASSPHRASE — dump stored UNENCRYPTED."
  echo "         It holds client PII; set one before go-live (plan §7)."
fi

if [ "$(date +%u)" = "7" ]; then
  cp "$DUMP" "$WEEKLY/"
  echo "-- Sunday: copied to $WEEKLY/"
fi

# Retention: filenames are timestamped and space-free, so ls -t is safe here.
# (|| true: an empty dir makes ls exit 1, which pipefail would turn into an abort.)
prune() {
  (ls -1t "$1"/orbit_*.dump* 2>/dev/null || true) | tail -n +"$(($2 + 1))" | while read -r old; do
    rm -f "$old"
  done
}
prune "$DAILY" "$KEEP_DAILY"
prune "$WEEKLY" "$KEEP_WEEKLY"

if [ -n "${BACKUP_REMOTE:-}" ]; then
  echo "-- Off-VM copy → $BACKUP_REMOTE"
  rsync -az "$DUMP" "$BACKUP_REMOTE"/
else
  echo "WARNING: BACKUP_REMOTE unset — no off-VM copy. A backup on the same disk"
  echo "         does not survive the VM (plan §7). Set it before go-live."
fi

count() { (ls -1 "$1"/orbit_*.dump* 2>/dev/null || true) | wc -l | tr -d ' '; }
echo "== Done: $(du -h "$DUMP" | cut -f1) $(basename "$DUMP")"
echo "   daily: $(count "$DAILY") kept · weekly: $(count "$WEEKLY") kept"
