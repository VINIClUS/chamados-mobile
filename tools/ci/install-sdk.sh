#!/usr/bin/env bash
set -euo pipefail

# Prepara um runner de CI (imagem eclipse-temurin:21-jdk) para o build do app: a configuração do Gradle só da CI
# (tools/ci/gradle-user-home, copiada para o $GRADLE_USER_HOME), os pacotes do Android SDK que o build pede (README,
# "Requisitos") e, com apt-get, só as ferramentas que faltarem: unzip, se o SDK tiver de ser baixado, e as que o job
# pedir nos argumentos.
#
# Idempotente: com o $ANDROID_HOME restaurado do cache, só confere os pacotes e sai. O cmdline-tools (sdkmanager)
# fica fora do $ANDROID_HOME, para não ir para o cache: o build não precisa dele.
#
# Uso: tools/ci/install-sdk.sh [comando...]   (comando: o pacote do apt de mesmo nome, ex. python3)
# Variáveis: ANDROID_HOME e GRADLE_USER_HOME (obrigatórias).

: "${ANDROID_HOME:?Defina ANDROID_HOME}"
: "${GRADLE_USER_HOME:?Defina GRADLE_USER_HOME}"
android_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# cmdline-tools 23.0. O sha1 é o publicado no repository2-3.xml do Google.
cmdline_tools_zip="commandlinetools-linux-16111833_latest.zip"
cmdline_tools_sha1="e025545c62a8e64c7559119566a569fb1dec5f60"
cmdline_tools_dir="/opt/android-cmdline-tools"
packages=("platforms;android-37.0" "build-tools;37.0.0")

installed() {
    [[ -f "$ANDROID_HOME/platforms/android-37.0/android.jar" && -x "$ANDROID_HOME/build-tools/37.0.0/apksigner" ]]
}

mkdir -p "$GRADLE_USER_HOME"
cp -R "$android_dir/tools/ci/gradle-user-home/." "$GRADLE_USER_HOME/"

tools=("$@")
installed || tools+=(unzip)
missing_tools=()
for tool in "${tools[@]}"; do
    command -v "$tool" >/dev/null || missing_tools+=("$tool")
done
if ((${#missing_tools[@]})); then
    echo "==> Instalando ${missing_tools[*]}"
    apt-get update -qq
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends "${missing_tools[@]}" >/dev/null
fi

if installed; then
    echo "==> Android SDK já instalado em $ANDROID_HOME (cache)"
    exit 0
fi

if [[ ! -x "$cmdline_tools_dir/latest/bin/sdkmanager" ]]; then
    echo "==> Baixando $cmdline_tools_zip"
    tmp="$(mktemp -d)"
    curl -fsSL --retry 3 -o "$tmp/$cmdline_tools_zip" "https://dl.google.com/android/repository/$cmdline_tools_zip"
    echo "$cmdline_tools_sha1  $tmp/$cmdline_tools_zip" | sha1sum -c --quiet
    unzip -q "$tmp/$cmdline_tools_zip" -d "$tmp"
    mkdir -p "$cmdline_tools_dir"
    mv "$tmp/cmdline-tools" "$cmdline_tools_dir/latest"
    rm -rf "$tmp"
fi

sdkmanager=("$cmdline_tools_dir/latest/bin/sdkmanager" "--sdk_root=$ANDROID_HOME")
mkdir -p "$ANDROID_HOME"
echo "==> Aceitando as licenças e instalando ${packages[*]}"
# O yes termina com SIGPIPE quando o sdkmanager fecha a entrada; o pipefail não pode tratar isso como erro.
(yes || true) | "${sdkmanager[@]}" --licenses >/dev/null
"${sdkmanager[@]}" --install "${packages[@]}" >/dev/null

installed || {
    echo "ERRO: o sdkmanager terminou sem instalar ${packages[*]} em $ANDROID_HOME." >&2
    exit 1
}
echo "==> Android SDK pronto em $ANDROID_HOME"
