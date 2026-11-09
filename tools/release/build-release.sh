#!/usr/bin/env bash
set -euo pipefail

# Pipeline de release do app (variante remoteRelease), com os mesmos dois caminhos do Android Studio:
#
#   signed-apk  Build → Generate Signed Bundle / APK → APK. O Studio injeta a chave pelas propriedades
#               android.injected.signing.* e grava em <destino>/remote/release/ (android.injected.apk.location);
#               aqui o destino é app/, o padrão do wizard. Não depende do signingConfig do build.gradle.kts.
#   apks        Build → Generate App Bundles or APKs → Generate APKs, com remoteRelease em Build Variants.
#               Roda assembleRemoteRelease, que assina com o signingConfig "release" do app/build.gradle.kts.
#
# Os dois conferem a assinatura (certificado igual ao do keystore, nunca o de debug) e se o R8 manteve os
# tipos de resposta da API (tools/release/check-api-signatures.py). Depois copiam APK, mapping.txt e SHA256SUMS
# para dist/<versionName>-<versionCode>/. Guarde o mapping.txt de cada versão publicada: sem ele, os stack
# traces do release (R8) não podem ser decodificados.
#
# Uso: tools/release/build-release.sh signed-apk|apks [argumentos extras do Gradle]
# Variáveis: CHAMADOS_SIGNING_PROPERTIES (padrão ~/.config/chamados-android/keystore.properties),
#            ANDROID_HOME (padrão sdk.dir do local.properties), BUILD_TOOLS (padrão 37.0.0).
# As senhas são lidas do arquivo e passadas pelo ambiente, nunca pela linha de comando.

android_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
signing_file="${CHAMADOS_SIGNING_PROPERTIES:-$HOME/.config/chamados-android/keystore.properties}"

usage() {
    sed -n '4,21p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

prop() {
    sed -n "s/^$1=//p" "$signing_file" | head -n 1
}

sdk_dir() {
    if [[ -n "${ANDROID_HOME:-}" ]]; then
        printf '%s\n' "$ANDROID_HOME"
    else
        sed -n 's/^sdk\.dir=//p' "$android_dir/local.properties"
    fi
}

mode="${1:-}"
case "$mode" in
    signed-apk | apks) shift ;;
    -h | --help)
        usage
        exit 0
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac

if [[ ! -r "$signing_file" ]]; then
    echo "ERRO: $signing_file não existe. Veja ANDROID.md, \"Release\"." >&2
    exit 1
fi

build_tools="$(sdk_dir)/build-tools/${BUILD_TOOLS:-37.0.0}"
store_file="$(prop storeFile)"
key_alias="$(prop keyAlias)"
export CHAMADOS_STOREPASS
CHAMADOS_STOREPASS="$(prop storePassword)"

expected_sha256="$(keytool -list -v -keystore "$store_file" -alias "$key_alias" -storepass:env CHAMADOS_STOREPASS |
    sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]')"
if [[ -z "$expected_sha256" ]]; then
    echo "ERRO: não consegui ler o certificado de $key_alias em $store_file." >&2
    exit 1
fi

cd "$android_dir"
if [[ "$mode" == signed-apk ]]; then
    apk="$android_dir/app/remote/release/app-remote-release.apk"
    metadata="$android_dir/app/remote/release/output-metadata.json"
    rm -f "$apk"
    # Os nomes têm ponto: o bash não sabe exportá-los, então o Python monta o ambiente. O gradlew roda pelo bash
    # porque o /bin/sh (dash) descarta essas variáveis antes de chegar ao Gradle.
    STORE_FILE="$store_file" KEY_ALIAS="$key_alias" KEY_PASSWORD_FROM="$signing_file" APK_LOCATION="$android_dir/app" \
        python3 -I - bash ./gradlew :app:assembleRemoteRelease "$@" <<'PY'
import os
import sys

env = dict(os.environ)
with open(env.pop("KEY_PASSWORD_FROM"), encoding="utf-8") as props:
    key_password = next(line.split("=", 1)[1].rstrip("\n") for line in props if line.startswith("keyPassword="))
injected = {
    "android.injected.signing.store.file": env.pop("STORE_FILE"),
    "android.injected.signing.store.password": env.pop("CHAMADOS_STOREPASS"),
    "android.injected.signing.key.alias": env.pop("KEY_ALIAS"),
    "android.injected.signing.key.password": key_password,
    "android.injected.apk.location": env.pop("APK_LOCATION"),
}
env.update({f"ORG_GRADLE_PROJECT_{name}": value for name, value in injected.items()})
os.execvpe(sys.argv[1], sys.argv[1:], env)
PY
else
    apk="$android_dir/app/build/outputs/apk/remote/release/app-remote-release.apk"
    metadata="$android_dir/app/build/outputs/apk/remote/release/output-metadata.json"
    rm -f "$apk"
    env -u CHAMADOS_STOREPASS ./gradlew :app:assembleRemoteRelease "$@"
fi

if [[ ! -f "$apk" ]]; then
    echo "ERRO: o Gradle terminou sem gerar $apk (assinatura ausente gera app-remote-release-unsigned.apk)." >&2
    exit 1
fi

echo "==> Conferindo a assinatura de $apk"
certs="$("$build_tools/apksigner" verify --print-certs "$apk")"
# Uma linha por esquema de assinatura ("V2 Signer: certificate SHA-256 digest: ..."); todas devem ser a mesma chave.
actual_sha256="$(sed -n 's/^.*Signer.*: certificate SHA-256 digest: //p' <<<"$certs" | sort -u)"
if grep -q 'CN=Android Debug' <<<"$certs"; then
    echo "ERRO: APK assinado com a chave de debug." >&2
    exit 1
fi
if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    echo "ERRO: certificado $actual_sha256 difere do keystore ($expected_sha256)." >&2
    exit 1
fi
if "$build_tools/aapt2" dump badging "$apk" | grep -q "application-debuggable"; then
    echo "ERRO: APK release marcado como debuggable." >&2
    exit 1
fi

mapping="$android_dir/app/build/outputs/mapping/remoteRelease/mapping.txt"
echo "==> Conferindo os tipos de resposta da API depois do R8"
python3 -I "$android_dir/tools/release/check-api-signatures.py" "$apk" "$mapping" "$build_tools/dexdump"

version="$(python3 -I -c 'import json, sys
element = json.load(open(sys.argv[1]))["elements"][0]
print(element["versionName"] + "-" + str(element["versionCode"]))' "$metadata")"
dist="$android_dir/dist/$version"
mkdir -p "$dist"
cp "$apk" "$dist/app-remote-release.apk"
cp "$mapping" "$dist/mapping.txt"
(cd "$dist" && sha256sum app-remote-release.apk mapping.txt >SHA256SUMS)

echo "OK: $mode assinado com $key_alias (SHA-256 $actual_sha256)"
echo "Saída: $dist"
