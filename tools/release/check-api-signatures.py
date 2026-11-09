"""Confere no APK release se o R8 manteve os tipos de resposta da ChamadosApi.

O Retrofit descobre o tipo de resposta de uma função suspend pela assinatura genérica do parâmetro
Continuation. Se o R8 remove a classe de resposta, a assinatura vira Continuation<Object> e a chamada
falha só em runtime ("Unable to create converter for class java.lang.Object"). Este script lê o dex
com o dexdump e falha se algum método da ChamadosApi ficou assim.

Uso: python3 -I check-api-signatures.py <apk> <mapping.txt> <dexdump>
"""

import re
import subprocess
import sys
import tempfile
import zipfile

API_CLASS = "br.com.portosoftware.chamados.data.remote.ChamadosApi"


def obfuscated_names(mapping_path):
    """Nome da ChamadosApi no APK e o nome original de cada método ofuscado dela."""
    class_name, methods = None, {}
    with open(mapping_path, encoding="utf-8") as mapping:
        for line in mapping:
            if class_name is None:
                if line.startswith(f"{API_CLASS} -> "):
                    class_name = line.split(" -> ")[1].strip().rstrip(":")
            elif not line.startswith(" "):
                break
            elif "(" in line and " -> " in line:
                original, obfuscated = line.strip().split(" -> ")
                methods[obfuscated] = original.split("(")[0].split()[-1]
    if class_name is None:
        raise SystemExit(f"ERRO: {API_CLASS} não está no {mapping_path}")
    return class_name, methods


def api_signatures(apk_path, dexdump, class_name):
    """Assinaturas genéricas dos métodos da classe. O dexdump lista as anotações antes do descritor."""
    found, pending, method = [], [], None
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(apk_path) as apk:
        for dex in (name for name in apk.namelist() if re.fullmatch(r"classes\d*\.dex", name)):
            apk.extract(dex, tmp)
            dump = subprocess.run([dexdump, "-a", f"{tmp}/{dex}"], capture_output=True, text=True, check=True).stdout
            for line in dump.splitlines():
                if re.match(r"Class #\d+ annotations:", line):
                    pending, method = [], None
                elif line.startswith("Annotations on method"):
                    method = line.split("'")[1]
                elif "dalvik/annotation/Signature" in line and method:
                    pending.append((method, line.split("value=", 1)[1]))
                elif "Class descriptor" in line:
                    if f"'L{class_name};'" in line:
                        found = pending
                    pending = []
    return found


def main():
    apk_path, mapping_path, dexdump = sys.argv[1:4]
    class_name, methods = obfuscated_names(mapping_path)
    signatures = api_signatures(apk_path, dexdump, class_name)
    if not signatures:
        raise SystemExit(f"ERRO: nenhuma assinatura genérica em {class_name}; o R8 removeu todas?")
    erased = re.compile(r'"L\w+<" "Ljava/lang/Object;" ">;\)"')  # Continuation<Object> como último parâmetro
    broken = [methods.get(method, method) for method, signature in signatures if erased.search(signature)]
    if broken:
        raise SystemExit(f"ERRO: o R8 apagou o tipo de resposta de {', '.join(broken)} na ChamadosApi")
    print(f"OK: {len(signatures)} métodos da ChamadosApi com o tipo de resposta preservado")


if __name__ == "__main__":
    main()
