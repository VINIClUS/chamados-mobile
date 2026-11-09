# shellcheck shell=bash
# Incluído (source) pelo GitHub Actions e pelos scripts locais que assinam: monta a chave de release a partir
# das variáveis de CI/CD num diretório temporário, apagado na saída, e exporta CHAMADOS_SIGNING_PROPERTIES.
# Variáveis: ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS (padrão chamados-release).
# Configuração: .github/workflows/android.yml, "Variáveis de ambiente".

if [[ -z "${ANDROID_KEYSTORE_BASE64:-}" || -z "${ANDROID_KEYSTORE_PASSWORD:-}" ]]; then
    cat >&2 <<'EOF'
ERRO: faltam as variáveis ANDROID_KEYSTORE_BASE64 e/ou ANDROID_KEYSTORE_PASSWORD, e sem elas não há APK assinado.
O administrador da organização precisa configurar os secrets do ambiente GitHub:
  1. as cadastra como Protected, com escopo nos ambientes staging e production;
  2. protege as tags android-v*, porque variáveis Protected só chegam a refs protegidas.
Veja ANDROID.md, "Release".
EOF
    exit 1
fi
# O build.gradle.kts lê o arquivo com java.util.Properties, e o build-release.sh, com sed: uma barra invertida
# seria lida diferente pelos dois.
if [[ "$ANDROID_KEYSTORE_PASSWORD" == *\\* ]]; then
    echo "ERRO: ANDROID_KEYSTORE_PASSWORD não pode ter barra invertida." >&2
    exit 1
fi

umask 077
signing_dir="$(mktemp -d)"
trap 'rm -rf "$signing_dir"' EXIT
base64 -d <<<"$ANDROID_KEYSTORE_BASE64" >"$signing_dir/release.jks"
# No PKCS12 a senha da chave é a do keystore (ANDROID.md, "Chave de assinatura").
printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=%s\nkeyPassword=%s\n' \
    "$signing_dir/release.jks" "$ANDROID_KEYSTORE_PASSWORD" "${ANDROID_KEY_ALIAS:-chamados-release}" \
    "$ANDROID_KEYSTORE_PASSWORD" >"$signing_dir/keystore.properties"
export CHAMADOS_SIGNING_PROPERTIES="$signing_dir/keystore.properties"
