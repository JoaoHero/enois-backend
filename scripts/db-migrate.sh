#!/usr/bin/env bash
set -euo pipefail

# Copia um banco para outro: exporta da origem e importa no destino
# Uso: MARIADB_HOST=... MARIADB_USER=... MARIADB_PASSWORD=... MARIADB_DATABASE=... \
#      DEST_HOST=... DEST_USER=... DEST_PASSWORD=... ./scripts/db-migrate.sh

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  echo "Copia o banco de origem para o destino (dump direto, sem arquivo intermediario)."
  echo ""
  echo "Uso: $0"
  echo ""
  echo "Origem:"
  echo "  MARIADB_HOST       (obrigatoria)"
  echo "  MARIADB_PORT       (default: 3306)"
  echo "  MARIADB_USER       (obrigatoria)"
  echo "  MARIADB_PASSWORD   (obrigatoria)"
  echo "  MARIADB_DATABASE   (obrigatoria)"
  echo ""
  echo "Destino:"
  echo "  DEST_HOST          (obrigatoria)"
  echo "  DEST_PORT          (default: 3306)"
  echo "  DEST_USER          (obrigatoria)"
  echo "  DEST_PASSWORD      (opcional)"
  echo ""
  echo "Senhas nunca vao na linha de comando."
  echo "Requer mariadb-dump e mariadb (ou mysqldump e mysql) instalados."
  exit 0
fi

# Sem defaults de origem/destino: os dois alvos precisam ser escolhidos de proposito.
ORIGIN_HOST="${MARIADB_HOST:?defina MARIADB_HOST}"
ORIGIN_PORT="${MARIADB_PORT:-3306}"
ORIGIN_USER="${MARIADB_USER:?defina MARIADB_USER}"
ORIGIN_PASS="${MARIADB_PASSWORD:?defina MARIADB_PASSWORD}"
ORIGIN_DB="${MARIADB_DATABASE:?defina MARIADB_DATABASE}"

DEST_HOST="${DEST_HOST:?defina DEST_HOST}"
DEST_PORT="${DEST_PORT:-3306}"
DEST_USER="${DEST_USER:?defina DEST_USER}"
DEST_PASS="${DEST_PASSWORD:-}"

DUMPER=$(command -v mariadb-dump || command -v mysqldump || echo "")
CLIENT=$(command -v mariadb || command -v mysql || echo "")

if [ -z "$DUMPER" ] || [ -z "$CLIENT" ]; then
  echo "ERRO: instale mariadb-client" >&2
  echo "  brew install mariadb" >&2
  echo "  apt install mariadb-client" >&2
  exit 1
fi

# Senhas em arquivos de opcoes 0600 (nao aparecem em `ps`), apagados na saida.
ORIGIN_OPTS=$(mktemp)
DEST_OPTS=$(mktemp)
trap 'rm -f "$ORIGIN_OPTS" "$DEST_OPTS"' EXIT
chmod 600 "$ORIGIN_OPTS" "$DEST_OPTS"
write_opts() {
  local escaped=${2//\\/\\\\}
  printf '[client]\npassword="%s"\n' "${escaped//\"/\\\"}" > "$1"
}
write_opts "$ORIGIN_OPTS" "$ORIGIN_PASS"
write_opts "$DEST_OPTS" "$DEST_PASS"

echo "Exportando de $ORIGIN_HOST:$ORIGIN_PORT/$ORIGIN_DB"
echo "Importando em $DEST_HOST:$DEST_PORT como $DEST_USER"
echo ""

"$DUMPER" \
  --defaults-extra-file="$ORIGIN_OPTS" \
  --host="$ORIGIN_HOST" \
  --port="$ORIGIN_PORT" \
  --user="$ORIGIN_USER" \
  --single-transaction \
  --routines \
  --triggers \
  --skip-lock-tables \
  --skip-add-locks \
  --no-tablespaces \
  --complete-insert \
  "$ORIGIN_DB" | \
"$CLIENT" --defaults-extra-file="$DEST_OPTS" --host="$DEST_HOST" --port="$DEST_PORT" --user="$DEST_USER"

echo ""
echo "Migracao concluida!"
