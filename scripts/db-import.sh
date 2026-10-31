#!/usr/bin/env bash
set -euo pipefail

# Importa um dump SQL para um banco
# Uso: DEST_HOST=... DEST_USER=... DEST_PASSWORD=... ./scripts/db-import.sh dumps/arquivo.sql
#       ./scripts/db-import.sh --help

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  echo "Importa um arquivo .sql para um banco de dados."
  echo ""
  echo "Uso: $0 arquivo.sql"
  echo ""
  echo "Variaveis de ambiente (banco destino):"
  echo "  DEST_HOST      (obrigatoria)"
  echo "  DEST_PORT      (default: 3306)"
  echo "  DEST_USER      (obrigatoria)"
  echo "  DEST_PASSWORD  (opcional; nunca vai na linha de comando)"
  echo ""
  echo "Exemplo:"
  echo "  DEST_HOST=localhost DEST_USER=root $0 dumps/backup.sql"
  echo ""
  echo "Requer mariadb ou mysql instalado."
  exit 0
fi

FILE="${1:-}"
# Sem default de destino: o alvo precisa ser escolhido de proposito a cada execucao.
DB_HOST="${DEST_HOST:?defina DEST_HOST}"
DB_PORT="${DEST_PORT:-3306}"
DB_USER="${DEST_USER:?defina DEST_USER}"
DB_PASS="${DEST_PASSWORD:-}"

if [ -z "$FILE" ] || [ ! -f "$FILE" ]; then
  echo "ERRO: arquivo nao encontrado: $FILE" >&2
  echo "  $0 --help" >&2
  exit 1
fi

CLIENT=$(command -v mariadb || command -v mysql || echo "")

if [ -z "$CLIENT" ]; then
  echo "ERRO: instale mariadb-client" >&2
  echo "  brew install mariadb" >&2
  echo "  apt install mariadb-client" >&2
  exit 1
fi

# A senha vai num arquivo de opcoes 0600 (nao aparece em `ps`), apagado na saida.
OPTS_FILE=$(mktemp)
trap 'rm -f "$OPTS_FILE"' EXIT
chmod 600 "$OPTS_FILE"
ESCAPED_PASS=${DB_PASS//\\/\\\\}
printf '[client]\npassword="%s"\n' "${ESCAPED_PASS//\"/\\\"}" > "$OPTS_FILE"

echo "Importando $FILE para $DB_HOST:$DB_PORT como $DB_USER ..."

"$CLIENT" --defaults-extra-file="$OPTS_FILE" --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" < "$FILE"

echo "Pronto!"
