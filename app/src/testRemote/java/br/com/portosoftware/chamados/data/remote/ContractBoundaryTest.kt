package br.com.portosoftware.chamados.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Os modelos gerados do contrato só aparecem na camada remota. Telas, ViewModels e domínio (`src/main`) também
 * compilam no flavor fake, onde o pacote nem existe, então um import ali já quebra a compilação do fake. Este teste
 * cobre o que essa compilação não vê: o nome completo, código do `src/remote` fora de `data/remote` (em Kotlin ou
 * Java), e um DTO escrito à mão que duplique o contrato. Em `src/remote`, nenhum arquivo cita a anotação
 * `kotlinx.serialization.Serializable`: nem por import (com ou sem apelido), nem pelo nome completo, nem como
 * `@Serializable` vindo de um import com `*`.
 *
 * É uma varredura de texto. Um `typealias` declarado em `data/remote` e usado fora dela passa, porque o arquivo que
 * o usa não traz o nome do pacote.
 */
class ContractBoundaryTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `so a camada remota cita os modelos gerados, e nenhum DTO e escrito a mao`() {
        val src = File("src")
        assertTrue("rode a partir do módulo app: ${src.absoluteFile}", File(src, "main/java").isDirectory)

        assertEquals(emptyList<String>(), violations(src))
        assertTrue("a varredura não achou a camada remota", File(src, "remote/java/$PACKAGE/data/remote/ChamadosApi.kt").isFile)
    }

    @Test
    fun `a varredura acusa import, nome completo, Java e DTO escrito a mao, com ou sem apelido`() {
        val src = temp.root
        val remote = "remote/java/$PACKAGE"
        write(src, "main/java/$PACKAGE/ui/calls/CallsViewModel.kt", "import $GENERATED.CallStatus\n")
        write(src, "$remote/di/Module.kt", "val x = $GENERATED.Role.ADMIN\n")
        write(src, "$remote/di/LegacyModule.java", "import $GENERATED.Role;\n")
        write(src, "fake/java/$PACKAGE/data/fake/FakeData.kt", "import $GENERATED.Role\n")
        write(src, "$remote/data/remote/Dtos.kt", "@Serializable data class UserDto(val id: String)\n")
        write(src, "$remote/data/remote/QualifiedDto.kt", "@kotlinx.serialization.Serializable data class Qualified(val id: String)\n")
        val aliased = "import kotlinx.serialization.Serializable as S\n\n@S data class Aliased(val id: String)\n"
        write(src, "$remote/data/remote/AliasedDto.kt", aliased)
        // Permitidos: o pacote gerado em data/remote, e outras classes da kotlinx.serialization.
        write(src, "$remote/data/remote/Mappers.kt", "import $GENERATED.Role\n")
        write(src, "$remote/data/remote/ApiCall.kt", "import kotlinx.serialization.SerializationException\n")

        assertEquals(
            listOf(
                "fake/java/$PACKAGE/data/fake/FakeData.kt: cita contract.model",
                "main/java/$PACKAGE/ui/calls/CallsViewModel.kt: cita contract.model",
                "$remote/data/remote/AliasedDto.kt: @Serializable escrito à mão",
                "$remote/data/remote/Dtos.kt: @Serializable escrito à mão",
                "$remote/data/remote/QualifiedDto.kt: @Serializable escrito à mão",
                "$remote/di/LegacyModule.java: cita contract.model",
                "$remote/di/Module.kt: cita contract.model",
            ),
            violations(src),
        )
    }

    private fun write(
        root: File,
        path: String,
        text: String,
    ) = File(root, path).apply { parentFile?.mkdirs() }.writeText(text)

    private companion object {
        const val PACKAGE = "br/com/portosoftware/chamados"
        const val GENERATED = "br.com.portosoftware.chamados.contract.model"

        // A anotação por import (com ou sem apelido) ou pelo nome completo, e @Serializable solto (import com *).
        val SERIALIZABLE = Regex("""kotlinx\.serialization\.Serializable\b|@Serializable\b""")

        /** Fontes do app (fora dos testes) que citam os modelos gerados fora da camada remota, ou declaram DTO à mão. */
        fun violations(src: File): List<String> =
            listOf("main", "fake", "remote")
                .flatMap { set -> File(src, set).walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") } }
                .flatMap { file ->
                    val path = file.relativeTo(src).invariantSeparatorsPath
                    val text = file.readText()
                    val remote = path.startsWith("remote/")
                    listOfNotNull(
                        "$path: cita contract.model".takeIf { GENERATED in text && !path.startsWith("remote/java/$PACKAGE/data/remote/") },
                        "$path: @Serializable escrito à mão".takeIf { remote && SERIALIZABLE.containsMatchIn(text) },
                    )
                }.sorted()
    }
}
