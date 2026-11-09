# Regras do R8 para o build release, além das que as bibliotecas já trazem
# (kotlinx.serialization, Retrofit, OkHttp, Hilt e Navigation publicam as suas).
# Cada regra aqui corresponde a uma falha observada no APK release; veja README, "Release".

# O Retrofit lê o tipo de resposta de uma função suspend na assinatura genérica do Continuation
# (`Continuation<? super SessionInfoResponseDTO>`). Uma resposta que o app recebe mas nunca lê, como a de
# POST /auth/login, não tem outra referência no código: o R8 removia a classe, a assinatura virava
# `Continuation<Object>` e o login falhava com "Serializer for class 'Any' is not found". O mesmo vale dentro de
# Response<T> e List<T>.
#
# A regra cobre o pacote inteiro dos modelos gerados do contrato (contract.model), e não só os tipos que a
# ChamadosApi devolve hoje: um método novo cuja resposta o app não leia voltaria à falha acima, e ela só aparece no
# release. O tools/release/check-api-signatures.py confere as assinaturas ao gerar o release.
-keep,allowobfuscation,allowoptimization @kotlinx.serialization.Serializable class br.com.portosoftware.chamados.contract.model.**
