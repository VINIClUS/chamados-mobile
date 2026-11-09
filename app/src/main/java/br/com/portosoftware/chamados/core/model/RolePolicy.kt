package br.com.portosoftware.chamados.core.model

/** Destinos de primeiro nível do app. */
enum class TopLevel { HOME, CALLS, STOCK, PROFILE }

/**
 * Único lugar que decide o que cada papel vê e faz. As telas recebem flags prontas
 * e nunca comparam papéis. Segue o que o backend libera (`SecurityConfig`):
 * - ADMIN, TECHNICAL_ADMIN e TECHNICAL criam e atendem;
 * - SYSTEM_EXTERNAL só lê, e não lista técnicos;
 * - VIEWER só tem acesso a relatórios, que o app não tem: fica só com o Perfil, que explica isso;
 * - papel desconhecido não libera nada: também fica só com o Perfil, com um aviso próprio.
 */
data class RolePolicy(
    val role: Role,
) {
    val destinations: List<TopLevel> =
        when (role) {
            Role.ADMIN, Role.TECHNICAL_ADMIN, Role.TECHNICAL, Role.SYSTEM_EXTERNAL -> {
                listOf(TopLevel.HOME, TopLevel.CALLS, TopLevel.STOCK, TopLevel.PROFILE)
            }

            Role.VIEWER, Role.UNKNOWN -> {
                listOf(TopLevel.PROFILE)
            }
        }

    /** Destino inicial depois do login. */
    val start: TopLevel get() = destinations.first()

    /**
     * Com menos de três destinos a barra some e o Perfil abre pelo avatar
     * da top app bar (diretriz do Material 3).
     */
    val showsNavigationBar: Boolean get() = destinations.size >= 3

    /** O Perfil só volta quando abre por cima de outra tela; como destino inicial, voltar deixaria a tela vazia. */
    val profileHasBack: Boolean get() = !showsNavigationBar && start != TopLevel.PROFILE

    /** Papel sem nenhuma tela de dados no app: o Perfil avisa. */
    val hasNoAppContent: Boolean get() = destinations == listOf(TopLevel.PROFILE)

    private val worksOnCalls: Boolean = role in setOf(Role.ADMIN, Role.TECHNICAL_ADMIN, Role.TECHNICAL)

    val canCreateCall: Boolean = worksOnCalls

    val canAttend: Boolean = worksOnCalls

    val canUploadAttachments: Boolean = canAttend

    /** `GET /users/technicians` só responde 200 para quem atende (SecurityConfig). */
    val canListTechnicians: Boolean = worksOnCalls

    val stockReadOnly: Boolean = true // v1: estoque é só consulta para todos os papéis.
}
