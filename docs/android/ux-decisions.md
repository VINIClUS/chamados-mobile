# App Android "Chamados TI": decisões de UX para revisão

> Status: **interfaces aprovadas em 2026-10-30; ampliadas em 2026-11-06 e em revisão**. O design acompanha a API da `main` e ganhou as jornadas J9 a J12. As respostas às perguntas estão em §10. O protótipo em Kotlin/Compose está em [`ANDROID.md`](../../ANDROID.md) e ainda segue o contrato antigo, ver §13.

## 1. Ponto de partida (o que o sistema já faz)

| Camada | Observação relevante para o mobile |
|---|---|
| Backend | Spring Boot 4, base `/api`. Os dados são isolados pela secretaria ativa, enviada no header `X-Active-Secretary-Id`. |
| Autenticação | JWT em cookies HttpOnly (`access_token` e `refresh_token`) mais CSRF (`GET /auth/csrf` → cookie `XSRF-TOKEN` → header `X-XSRF-TOKEN`). Não há bearer token. |
| Sessão | `POST /auth/login` e `POST /auth/refresh` retornam `{expiresAt, expiresInSeconds}`. O perfil vem de `GET /auth/me` (`UserResponseDTO` com `role` e `secretaries[]`). |
| Web | React com sidebar desktop e formulários longos de cinco seções. O app **não** vai reproduzir esse layout. |

### Papéis (enum `Role` do backend)

| Papel | Rótulo no Perfil (`ProfilePage.tsx:5`) |
|---|---|
| `ADMIN` | Administrador geral |
| `TECHNICAL_ADMIN` | Administrador técnico |
| `TECHNICAL` | Técnico de TI |
| `VIEWER` | Somente relatórios |
| `SYSTEM_EXTERNAL` | Sistema Externo |

## 2. Persona principal e jornadas essenciais

**Persona primária:** o técnico de campo (`TECHNICAL`). Ele circula entre prédios da prefeitura, recebe chamados por telefone e WhatsApp, atende com uma mão e precisa registrar o serviço e as peças usadas antes de ir para o próximo local.

**Personas secundárias:** a administração técnica (`TECHNICAL_ADMIN`/`ADMIN`), que organiza a fila, reabre chamados e dá entrada no estoque; e quem só consome relatórios (`VIEWER`), que no app vê apenas o Perfil.

| # | Jornada | Endpoints | Prioridade |
|---|---|---|---|
| J1 | Entrar e escolher a secretaria | `/auth/csrf`, `/auth/login`, `/auth/me` | v1 |
| J2 | Ver "meu turno": indicadores e chamados recentes | `GET /dashboard/calls` | v1 |
| J3 | Encontrar um chamado (busca e filtros) | `GET /calls/filter` (paginado) | v1 |
| J4 | Entender um chamado e contatar o solicitante | `GET /calls/{id}`, `GET /calls/{id}/attachments/{aid}` | v1 |
| J5 | Atender: relatar, mudar status, usar peças | `PATCH /calls/{id}/progress`, `GET /items/all?status=STOCK`, `GET /users/technicians` | v1 |
| J6 | Abrir chamado assistido (com cadastro rápido de servidor) | `POST /calls`, `POST /calls/{id}/attachments`, `/applicants/search`, `POST /applicants`, `/positions/active`, `POST /positions`, `/unit/secretary/{id}`, `/local/unit/{id}`, `/equipment/local/{id}`, `/category/all`, `/sub-category/category/{id}` | v1 |
| J7 | Consultar o estoque da secretaria | `GET /items/all` (`category`, `status`) | v1 |
| J8 | Perfil, troca de secretaria, tema e saída | `/auth/me`, `/auth/logout` | v1 |
| J9 | Ver chamado fechado e reabrir | `GET /calls/{id}`, `POST /calls/{id}/reopen` | Desenhada, integração pendente |
| J10 | Corrigir os dados de um chamado | `PUT /calls/{id}` | Desenhada, integração pendente |
| J11 | Registrar saída de materiais e consultar comprovantes | `GET /items/movements/locations`, `POST /items/movements/dispatch`, `GET /items/movements/receipts`, `GET /items/movements/receipts/{id}` | Desenhada, integração pendente |
| J12 | Dar entrada e baixa no estoque | `POST /items/entries`, `POST /items/{id}/discard`, `/item-type/all`, `/brand/all`, `/item-model/brand/{id}`, `/specification-Key/all` | Desenhada, integração pendente |

**Fora do app, mantidos no web:** configurações do sistema (cadastros estruturais), cadastro e composição de equipamentos, ranking de computadores, auditoria, relatórios, administração de cargos, CRUD completo de solicitantes (só o cadastro rápido entra na J6), edição de item (`PUT /items/{id}`), baixa de equipamento, reparo externo e a rota antiga `/items/movements/outflow`.

## 3. Matriz papel × navegação

| Papel | Início | Chamados | Novo | Atender e editar | Reabrir | Estoque | Saída e baixa | Entrada | Perfil |
|---|---|---|---|---|---|---|---|---|---|
| ADMIN | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| TECHNICAL_ADMIN | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| TECHNICAL | ✔ | ✔ | ✔ | ✔ | — | ✔ | ✔ | — | ✔ |
| SYSTEM_EXTERNAL | leitura | leitura | — | — | — | leitura | — | — | ✔ |
| VIEWER | — | — | — | — | — | — | — | — | ✔ com aviso |

`SYSTEM_EXTERNAL` segue a posição do CT-01: somente leitura, sem filtro por técnico, porque `/users/technicians` responde 403. `VIEWER` recebe 403 em tudo fora de `auth/*` e `/reports/**` (`SecurityConfig.java:94-105`), e os relatórios não estão no app.

A lógica de visibilidade fica em um único `RolePolicy` no domínio do app. As telas recebem flags prontas (`canAttend`, `canCreate`, `canEdit`, `canReopen`, `canDispatch`, `canDiscard`, `canReceive`) e não comparam papéis.

## 4. Navegação mobile-first

```
Splash ──► Login ──► Esqueci a senha
             │
             ▼
     Seleção de secretaria  (pulada se o usuário tem só uma)
             │
             ▼
┌──────────── App (NavigationSuiteScaffold) ─────────────┐
│ Início        Chamados          Estoque        Perfil  │
│   │              │                 │              │    │
│   ├─► Detalhe ◄──┤                 ├─► Item (sheet) ─► Baixa (sheet)
│   │     ├─► Atender (tela cheia, modal)               │
│   │     ├─► Editar (tela cheia, modal)                │
│   │     ├─► Reabrir (sheet, só em Fechado)            │
│   │     └─► Anexo (visualizador/compartilhar)         │
│   │                                 ├─► FAB "Nova saída" (wizard de 3 etapas)
│   │                                 ├─► Saídas ─► Comprovante
│   │                                 └─► Entrada (só admins)
│   └─► FAB "Novo chamado" (wizard de 4 etapas)         │
└────────────────────────────────────────────────────────┘
```

Decisões:

1. **Barra de navegação inferior** com até quatro destinos (Material 3 `NavigationBar`). Em telas largas, o mesmo `NavigationSuiteScaffold` vira `NavigationRail` sem reescrever telas.
2. **Destinos dependem do papel.** `VIEWER` não tem nenhum destino com dados, então a barra some e ele vê só o Perfil, com um aviso de que os relatórios estão no sistema web.
3. **"Novo chamado" é um FAB estendido**, presente em Início e Chamados. Ele recolhe para o ícone ao rolar. Não ocupa um destino da barra, porque é uma ação e não um lugar. "Nova saída" segue a mesma regra no Estoque.
4. **A secretaria ativa aparece como chip na top app bar de Início.** Um toque abre um bottom sheet para trocar. A troca limpa os caches e recarrega os dados, o que espelha o header `X-Active-Secretary-Id`.
5. **Detalhe, Atender e Editar são telas empilhadas.** Atender e Editar abrem como diálogo de tela cheia, com fechar no canto, pela quantidade de campos e pelo teclado. O voltar do sistema pede confirmação se houver rascunho.
6. **Deep link** `chamados://calls/{id}` já previsto, para notificações futuras.
7. **O histórico de saídas e a Entrada ficam na top app bar do Estoque.** São acessos menos frequentes que a consulta, e a Entrada só aparece para `ADMIN` e `TECHNICAL_ADMIN`.

## 5. Decisões por tela

### Login
- E-mail e senha com `KeyboardType` adequado, ação IME "Entrar" e mostrar/ocultar senha.
- A mensagem de erro do backend aparece em linha, sem toast.
- O link "Esqueci a senha" envia o e-mail. Como o backend sempre responde com sucesso, o feedback é neutro.

### Seleção de secretaria
- Lista de radio buttons em cards, com a última secretaria usada pré-selecionada (preferência local em DataStore).

### Início ("Meu turno")
- Saudação curta e chip da secretaria.
- **Carrossel horizontal (LazyRow) de indicadores** vindos de `CallDashboardResponseDTO`, com os alertas primeiro: Urgentes, Sem responsável, Ativos, Novos e Em andamento. O próximo card aparece cortado na borda, sinalizando a rolagem. Cada card é um atalho que abre Chamados com o filtro correspondente. Uma grade foi descartada porque ocuparia ~420 dp e empurraria os chamados recentes para fora da primeira dobra em 360×800.
- Lista "Recentes" com até 5 `CallCard` e o link "Ver todos".
- Pull-to-refresh.

### Chamados
- `SearchBar` M3 no topo, com debounce de 400 ms e busca em `search`.
- **Linha de filtros rápidos** (chips roláveis): Status, Prioridade e Sem responsável. "Mais filtros" abre um bottom sheet com status em escolha única, período, unidade, local, categoria e técnico.
- A lista paginada usa Paging 3 (`page`/`size=20`), com rodapé de carregamento e de erro com "Tentar novamente".
- O `CallCard` mostra protocolo `#XXXXXXXX`, status, prioridade, descrição em duas linhas, local, solicitante, técnico e tempo relativo ("há 2 h").

### Detalhe do chamado
- O cabeçalho mostra protocolo, status, prioridade e a descrição completa.
- O bloco do **solicitante** mostra nome e cargo, com ações nativas: Ligar (`ACTION_DIAL`), WhatsApp (`wa.me`) e E-mail (`ACTION_SENDTO`). Esse é um ganho real sobre o web.
- Seções: Local, Classificação, Equipamento (patrimônio e modelo), Responsável, **linha do tempo** do histórico, com o mais recente primeiro, e Anexos (abre via `FileProvider` ou compartilhar).
- **Barra de ações inferior fixa:** "Atender" (primária). Com status `OPENED` e sem responsável, há também "Assumir" (tonal). Assumir envia `PATCH /progress` com `status=IN_PROGRESS`, `responsibleTechnicianId=eu` e a nota automática "Atendimento iniciado por {nome}.", já que a nota é obrigatória.
- O ícone de lápis na top app bar abre Editar, só enquanto o chamado não está fechado.

### Chamado fechado e reabertura
- Chamado `CLOSED` é só leitura: sem barra de ações, sem lápis e sem envio de anexos. O técnico vê o aviso "Chamado fechado: somente leitura. Só um administrador pode reabrir."
- `ADMIN` e `TECHNICAL_ADMIN` veem "Reabrir", que abre um sheet com justificativa de 10 a 1000 caracteres e contador.
- A justificativa vai para o histórico, e o chamado volta como Aberto. Se o envio falhar, o sheet mantém o texto.

### Editar chamado
- O resumo reaproveita a Revisão do wizard: Solicitante, Local, Problema e Responsável, cada um com lápis. Status e peças continuam em Atender.
- O formulário sempre envia o técnico, porque omitir o campo remove o responsável (`CallService.java:415-422`).
- Sair com alterações pede confirmação. Se outra pessoa fechou o chamado nesse meio-tempo, o 409 vira uma tela que explica o motivo e volta ao Detalhe.

### Atender (registrar progresso)
- **"O que foi feito"** é obrigatório, com no máximo 1000 caracteres e contador.
- **Novo status** em chips de escolha única, filtrados pela mesma tabela `allowedTransitions` do backend (o status atual fica marcado como "atual").
- O responsável começa preenchido com o usuário logado e pode ser trocado (`/users/technicians`).
- **Peças utilizadas**: "Adicionar peça" abre um bottom sheet com busca no estoque (`status=STOCK`). Cada peça vira uma linha com stepper de quantidade, limitada ao saldo e a 100 itens diferentes.
- **Peça de tipo único:** o stepper fica travado em 1 e um aviso diz "Componentes únicos substituirão o atual, que será encaminhado para manutenção."
- O botão fixo "Registrar atendimento" mostra progresso. Se der certo, volta ao detalhe com snackbar "Atendimento registrado".

### Novo chamado (wizard de 4 etapas)

O formulário web de cinco seções vira um **stepper com uma decisão por tela**:

1. **Solicitante:** busca com no mínimo 2 letras. A linha do resultado mostra o cargo. "Não encontrou? Cadastro rápido" abre um bottom sheet com nome, telefone mascarado `(99)99999-9999`, e-mail, **cargo obrigatório**, unidade e local. Se o cargo não existir, "Cargo não cadastrado" + "Criar" o cadastra ali mesmo.
2. **Local:** unidade e local preenchidos pela lotação do solicitante, editáveis. O equipamento é opcional e vem de `/equipment/local/{id}`.
3. **Problema:** categoria → sintoma em chips, descrição (10 a 1000 caracteres) e prioridade em um segmented button de 4 níveis.
4. **Revisão e envio:** resumo editável por etapa, status inicial (Aberto, Em andamento ou Fechado), técnico opcional e anexos por **câmera ou galeria** (Photo Picker). As fotos aceitas são JPG ou PNG, até 10 MB cada e 25 MB no total. Quem escolhe Fechado perde a opção de anexos e vê um aviso. O arquivo recusado aparece em um banner com o motivo, e os demais continuam.

Cada etapa valida antes do "Continuar", e o rascunho sobrevive à rotação e à morte do processo (`SavedStateHandle`).

### Estoque
- Busca, chips de categoria (Todos, Peças, Insumos), chips por tipo de item e lista com modelo, marca, tipo e saldo. Saldo baixo ganha destaque `error`. O detalhe abre em bottom sheet com as especificações, a categoria e "Dar baixa".
- Quando a API devolve menos itens do que o total, um banner avisa "Mostrando os primeiros 200 de 340 itens" e aponta a busca e os filtros, para a lista não parecer completa (DIV-10).

### Saída de materiais (wizard de 3 etapas)
1. **Destino:** locais com `receives=true` (`GET /items/movements/locations`), em seleção múltipla. Sem locais habilitados, um estado vazio explica o motivo.
2. **Materiais:** um chip por local, com a contagem. Em cada local: recebedor opcional (até 150), observações (até 2000) e materiais. Insumo usa stepper; item individualizado sai uma unidade por vez. Local sem material bloqueia o "Continuar", com o texto "Inclua ao menos um material em Farmácia ou retire esse local da saída."
3. **Conferir:** um card por local e o aviso de que o estoque será baixado e um PDF com uma página por local será gerado. O recebedor assina o comprovante impresso.

O `requestId` é gerado na primeira confirmação e reaproveitado nas repetições. Por isso, depois de uma falha sem resposta, a tela não deixa voltar nem editar: oferece só "Tentar confirmação novamente", com o texto "As quantidades estão protegidas contra baixa duplicada."

O PDF é gerado no aparelho, como na web. Se a geração falhar, a saída continua registrada, e a tela oferece "Gerar PDF novamente".

### Saídas e comprovante
- Lista paginada de comprovantes, com o número `2026/000123`, materiais, local, recebedor e tempo relativo. A busca usa `material`; o sheet de filtros tem Local, Data inicial e Data final.
- O comprovante mostra destino, recebedor, secretaria, equipe e materiais, com "Compartilhar PDF". Sem recebedor, aparece "Assinatura no comprovante impresso".

### Entrada no estoque (só admins)
- Tipo, marca e modelo em cascata; quantidade; série e patrimônio opcionais; e as especificações do tipo, que vêm da API.
- Uma peça com quantidade 5 gera 5 registros, até 1000 por entrada, e um insumo entra como um único registro com a quantidade informada. Com série ou patrimônio, a quantidade da peça precisa ser 1, e o erro é mostrado com o texto do backend.

### Baixa de item
- O sheet parte do detalhe do item e pede o "Motivo do descarte", até 1000 caracteres.
- **A baixa zera todo o saldo do item** (`ItemService.java:196`), então um aviso mostra a quantidade: "A baixa zera todo o saldo: 12 un. saem do estoque de uma só vez."
- O botão de confirmação usa a cor `error`, por ser irreversível no app.

### Perfil
- Nome, e-mail, papel com o rótulo do web e secretarias, com troca de secretaria, equipe de TI e as secretarias que ela atende, tema (Sistema, Claro ou Escuro) e "Sair", que pede confirmação.
- `VIEWER` vê só esta tela, sem barra de navegação, com o aviso "Seu perfil só acessa relatórios, disponíveis no sistema web."

## 6. Regras de negócio que moldam a interface

1. **Status iniciais permitidos na criação:** `OPENED`, `IN_PROGRESS` e `CLOSED`. Não há observação na criação, e chamado criado como `CLOSED` não recebe anexos.
2. **Transições de status** espelham `CallService.allowedTransitions`: `OPENED` vai para `IN_PROGRESS` ou `CLOSED`; `IN_PROGRESS` vai para `OPENED` ou `CLOSED`; `CLOSED` só sai pela reabertura. A UI só oferece transições válidas, e o backend continua sendo a autoridade.
3. **Prioridade:** hoje o backend grava a prioridade enviada (padrão `NORMAL`). O `CallPriorityCalculator` existe, mas não é chamado em `save`. O app segue o contrato atual, com seleção manual, e está pronto para trocar por uma prévia calculada quando o backend ativar a regra (ver §10).
4. **Anexos** sobem em uma segunda chamada multipart, depois que o chamado é criado. Se o upload falhar, o chamado **existe**: o estado "criado com pendências" oferece "Tentar enviar anexos de novo" e "Ver chamado". O backend aceita PDF, PNG, JPG, TXT, Word e Excel; o app oferece só câmera e galeria, então a tela cita JPG e PNG.
5. **Status desconhecido** aparece em chip neutro e não libera nenhuma ação (DIV-13). Assim, um valor novo da API não derruba a tela nem oferece uma transição inválida.
6. **Sessão:** um 401 dispara uma renovação única e compartilhada entre requisições concorrentes: `GET /auth/csrf` (cookie XSRF novo) e depois `POST /auth/refresh`. Se o refresh responder 403, repete o par `csrf` + `refresh` uma vez, como faz `src/web/src/api/axios.ts`. Se falhar, aparece o diálogo "Sessão encerrada" e o app volta ao Login. No protótipo, o rascunho do wizard sobrevive à rotação e à morte do processo, mas **não** a um novo login.
7. **Datas:** o backend envia `LocalDateTime` sem fuso. O app interpreta em `America/Sao_Paulo`.
8. **Peça única:** o tipo com `isUnique` só aceita quantidade 1, e o componente atual vai para manutenção (`CallService.java:206-211`).
9. **Saída:** a repetição com o mesmo `requestId` é segura. Só `receives=true` recebe materiais, e cada item aparece uma vez por local (`StockDispatchService.java:59-98`).
10. **Entrada e baixa:** entrada só para `ADMIN` e `TECHNICAL_ADMIN`. A baixa aceita também `TECHNICAL`, exige motivo e zera o saldo.

## 7. Matriz de estados

Legenda: **✓ desenhado** tem frame no arquivo de design. **◇ especificado** está definido aqui, mas ainda não tem frame próprio. Hoje nenhum estado está nesse caso.

| Tela | Carregando | Vazio | Erro | Sucesso | Específicos |
|---|---|---|---|---|---|
| Login | ✓ botão com progresso | — | ✓ credencial inválida (inline) | ✓ seleção de secretaria | ✓ diálogo "Sessão encerrada"; ✓ confirmação de "Esqueci a senha" |
| Início | ✓ skeleton | ✓ "Nenhum chamado ativo" | ✓ erro de rede com "Tentar novamente" | ✓ dados | ✓ offline (banner) |
| Chamados | ✓ skeleton de lista | ✓ sem resultado para filtros (limpar filtros) | ✓ erro de tela cheia | ✓ lista | ✓ offline + falha de paginação no rodapé; ✓ sheet "Mais filtros" |
| Detalhe | ✓ skeleton | — | ✓ 403; ✓ 404 | ✓ dados | ✓ após atender (snackbar) |
| Atender | — | ✓ sem peças adicionadas | ✓ erro de envio; ✓ erro de validação por campo | ✓ snackbar no Detalhe | ✓ enviando; ✓ sheet "Adicionar peça"; ✓ peça única |
| Novo chamado | ✓ listas em cascata carregando | ✓ busca sem solicitante | ✓ validação por etapa | ✓ protocolo criado | ✓ criado com anexo pendente; ✓ sheet Cadastro rápido; ✓ validação do cargo; ✓ criando cargo; ✓ anexo recusado |
| Estoque | ✓ skeleton | ✓ busca sem resultado; ✓ estoque vazio | ✓ erro com retry | ✓ lista; ✓ visão do administrador | ✓ sheet de detalhe do item; ✓ lista limitada |
| Perfil | — | — | — | ✓ dados | ✓ somente relatórios (`VIEWER`); ✓ sessão expirada |
| Reabrir | — | ✓ Detalhe fechado, sem ações | ✓ validação; ✓ erro de envio | ✓ Detalhe reaberto | ✓ fechado com Reabrir; ✓ sheet |
| Editar | — | — | ✓ erro de envio; ✓ chamado já fechado (409) | ✓ Detalhe após editar | ✓ resumo e problema; ✓ descartar alterações |
| Saída de materiais | ✓ locais carregando; ✓ confirmando | ✓ sem locais | ✓ validação; ✓ erro de confirmação | ✓ confirmada | ✓ sheet "Adicionar material"; ✓ PDF não gerado |
| Histórico de saídas | ✓ skeleton | ✓ sem resultado para filtros | ✓ erro com retry | ✓ lista; ✓ comprovante | ✓ sheet de filtros |
| Entrada | ✓ listas carregando | — | ✓ validação; ✓ erro de envio | ✓ snackbar no Estoque | ✓ especificações (rolado) |
| Baixa | — | — | ✓ validação; ✓ erro de envio | ✓ snackbar no Estoque | ✓ sheet com aviso do saldo |

Os estados viram uma `sealed interface UiState { Loading; Empty; Error(message, retry); Success(data) }` compartilhada.

## 8. Design system (Material 3)

- **Cor:** esquema M3 gerado com `material-color-utilities` (variante *Fidelity*). O tema claro sai do cobre **#C36F3C**, a marca do tema marfim do web. O tema escuro sai do azul **#3B82F6**, o azul que o web escuro mantém (decisão da revisão, §10.6). O *dynamic color* (Material You) fica **desligado por padrão**, para não alterar a semântica de status, e pode virar uma opção no Perfil.
- **Cores estendidas:** a semântica de status e prioridade não existe no M3, então cada uma ganha `color`, `onColor`, `container` e `onContainer` em claro e escuro. Os chips **sempre** levam ícone e texto, e a cor nunca é o único sinal. A cor de sucesso, que antes pegava carona em `status/resolved`, agora é `state/success/*`.
- **Tipografia:** Roboto, a fonte do sistema Android, com a escala M3 completa (Display, Headline, Title, Body, Label). O web usa Inter, mas o app prioriza o aspecto nativo e dispensa empacotar fonte.
- **Forma:** M3 `extraSmall` 4, `small` 8, `medium` 12, `large` 16, `extraLarge` 28. Cards usam 12, sheets 28 e chips 8.
- **Espaçamento:** grade de 4 dp (4, 8, 12, 16, 20, 24, 32, 40). As margens laterais das telas são 16 dp.
- **Toque:** alvo mínimo de 48 dp, contraste AA validado nos pares `on*`, rótulos de conteúdo nos ícones e suporte a fonte ampliada até 200% (os cards quebram linha e não truncam informação crítica).
- **Tamanho de referência:** 360×800 dp, a classe *Compact*.

O arquivo de design usa variáveis com os mesmos nomes dos papéis do `ColorScheme` do Compose, para a tradução ser 1:1.

## 9. Preparação da camada de dados (para a fase de implementação)

```
ui (Composable + ViewModel + UiState)   ← não conhece a origem dos dados
  └─ domain: models + interfaces de repositório (CallRepository, AuthRepository,
             StockRepository, CatalogRepository, ApplicantRepository, SessionRepository)
       ├─ data/fake:   FakeCallRepository…  (protótipo, latência e erros simulados)
       └─ data/remote: RetrofitCallRepository… (Spring Boot)
            ├─ OkHttp CookieJar persistente (access, refresh e XSRF)
            ├─ Interceptor: X-XSRF-TOKEN + X-Active-Secretary-Id
            ├─ Authenticator: 401 → GET /auth/csrf + POST /auth/refresh (single-flight;
            │                 repete o par uma vez se o refresh der 403)
            └─ DTOs espelhando os records Java + mappers → domain
```

A troca de Fake para Remote acontece em **um módulo de injeção (Hilt)**, sem tocar nas telas. Os erros da API (`ApiErrorResponse` com `message` e `violations`) viram `DomainError.Validation(fieldErrors)`, e os formulários mostram os erros por campo.

## 10. Divergências encontradas e perguntas para revisão

Respostas da revisão (2026-10-30):

| # | Pergunta | Decisão |
|---|---|---|
| 1 | Prioridade manual × `CallPriorityCalculator` (o calculador existe, mas não é usado) | **Manual, como no web.** Segmented button de 4 níveis no wizard. |
| 2 | `USER` abrindo chamados (a API permite, a rota web bloqueia) | ~~Não, como no web.~~ **Substituída em 2026-11-06:** o papel saiu do backend. |
| 3 | `STOCK_MANAGER` sem acesso a chamados | ~~Intencional.~~ **Substituída em 2026-11-06:** o papel saiu do backend. |
| 4 | Atalho "Assumir" com entrada automática no histórico | **De acordo.** Nota "Atendimento iniciado por {nome}." |
| 5 | Saída de materiais | ~~Fica para a v2.~~ **Antecipada em 2026-11-06**, desenhada na J11. |
| 6 | Tema | **Cobre no claro, azul no escuro**, como o web. |

Escolhas da ampliação (2026-11-06), à espera de revisão visual:

| Pedido | Decisão |
|---|---|
| Fluxos novos | Reabrir chamado, Editar chamado, Saída de materiais, Entrada e baixa no estoque. |
| Ajustes | Peças e insumos no Estoque, aviso de peça única e Equipe de TI no Perfil. |
| Anexos | **Só câmera e galeria.** Os demais formatos que o backend aceita (PDF, TXT, Word e Excel) ficam no web. |

## 11. Artefatos

**Design:** [`design/android/chamados-ti-android.pen`](../../design/android/chamados-ti-android.pen), que abre no app [pen.dev](https://pen.dev). Edite pelo app ou pelo MCP dele, não à mão, e salve e feche o arquivo antes de trocar de ramo.

O formato não tem páginas, então o arquivo é um único canvas com três blocos de seções:

| Bloco | Conteúdo | Seções |
|---|---|---|
| Leia-me | Capa, mapa de navegação, 15 decisões, matriz papel × navegação, matriz de estados, respostas da revisão e notas técnicas | `Leia-me · Chamados TI Android` |
| Design System | Especimes de cor claro/escuro, tipografia/espaço/forma, 59 ícones, sistema Android e 33 componentes | `Fundamentos · Cor`, `Fundamentos · Tipografia, Espaço & Forma`, `Ícones · Material Symbols`, `Sistema Android & utilitários` e `Componentes ·` Ações & Seleção, Entrada & Conteúdo, Navegação, Feedback & Estados |
| Telas | 103 telas de 360×800 dp em 12 seções | J1 Acesso (5), J2 Início (5), J3 Chamados (6), J4 Detalhe (5), J5 Atender (7), J6 Novo chamado (18), J7/J8 Estoque, Perfil e sessão (10), tema escuro (7), J9 Chamado fechado e reabertura (6), J10 Editar chamado (6), J11 Saída de materiais (17) e J12 Entrada e baixa no estoque (11) |

**Variáveis**

| Grupo | Conteúdo |
|---|---|
| `scheme/*`, `status/*`, `priority/*`, `state/*` | 71 cores, cada uma com valor claro e escuro no eixo de tema `mode`: 36 papéis M3, 28 cores estendidas (12 de status e 16 de prioridade), 4 de `state/success` e 3 tokens de estado com alfa (disabledContainer, disabledContent, scrim) |
| `space/*` | 10 tokens |
| `shape/*` | 7 tokens |

Os frames escuros (`Scheme Dark` e as telas `· escuro`) usam `theme: { mode: dark }`.

**Componentes** (especimes nas seções `Componentes ·` e `Sistema Android & utilitários`): Button, FAB, Icon Button, Status Chip, Priority Badge, Filter Chip, Assist Chip, Segmented Button/Prioridade, Selection Card, Text Field, Text Area, List Item, Section Header, Wizard Stepper, Quantity Stepper, Timeline Item, KPI Card, Call Card, Stock Item Row, Top App Bar, Navigation Bar Item, Navigation Bar, Search Bar, Bottom Action Bar, State Message, Skeleton, Snackbar, Banner, Dialog, Avatar, System/Status Bar, System/Gesture Bar e Sheet/Drag Handle.

**Limitações conhecidas do arquivo**

- Os componentes não são reutilizáveis: as telas usam cópias soltas, sem variantes nem propriedades. Alterar um especime não propaga para as telas.
- Há 15 estilos de texto e 3 de elevação só como especimes, porque o formato não tem estilos reutilizáveis. Cada nó guarda os próprios valores. As variáveis também não têm code syntax Android.
- 40 cores seguem como valor fixo: o fundo e o contorno das 20 seções do canvas, que não são telas nem componentes.
- Os ícones acrescentados depois da importação usam o nó `icon` do formato, com Material Symbols Outlined. Os demais são caminhos importados, então os dois grupos têm peso visual um pouco diferente. `search_off` e `inventory_2` são usados nas telas sem especime próprio.
- O formato não corta texto com reticências. Em 49 textos o "…" está escrito no conteúdo, com o texto completo em `context`, e eles não se ajustam se o frame crescer. Outros 6 são rótulos de progresso, como "Carregando…".
- O Git mostra um diff de linhas do `.pen`, mas ele é ruidoso demais para revisão. O registro revisável das mudanças é a seção §12.
- O formato não tem quebra de linha em layout. Os frames que quebravam viraram linhas fixas (`Linha 1`, `Linha 2`…).
- O contorno não desconta do layout, então frames com contorno podem diferir até 2 dp do desenho original. O contorno do mapa de navegação, que era tracejado, ficou sólido.

**Auditoria automática** (2026-11-06, depois da ampliação, sobre o documento inteiro)

- **Termos antigos:** nenhum status, papel ou campo removido aparece em texto ou nome de nó, exceto as duas menções intencionais ao histórico das decisões no Leia-me (respostas 2 e 3).
- **Texto placeholder:** nenhum visível nas telas. Os 86 "Texto de apoio" estão em campos ocultos (`enabled: false`), e os 9 "Rótulo" são especimes de Button.
- **Fontes:** só Roboto.
- **Pinturas sem vínculo:** restam só as 40 das seções do canvas. As cores de sombra dos efeitos de elevação seguem literais, por serem pretas com alfa e iguais nos dois temas. Todas as referências a variáveis, em qualquer propriedade, existem.
- **Canvas:** nenhuma seção se sobrepõe a outra.

## 12. Contrato × tela

Fonte: `src/main/java` e `src/main/resources` na `HEAD` de 2026-11-06. Os caminhos partem de `br/com/portosoftware/chamados`.

| Contrato | Regra na interface | Telas | Evidência |
|---|---|---|---|
| 3 status | Aberto, Em andamento e Fechado; desconhecido em neutro | Status Chip, Início, Chamados, "Mais filtros", Atender, Novo · 4, tema escuro | `enums/CallStatus.java` |
| Transições | Só as válidas a partir do status atual | 7 telas de Atender | `service/CallService.java:470-476` |
| Filtro por um status | "Mais filtros" em escolha única; sem "Serviço crítico" | 6 telas de Chamados e o escuro | `dto/request/CallFilterDTO.java` |
| Painel | 5 KPIs, sem "Aguardando" | Início (sucesso, offline, escuro) | `CallDashboardResponseDTO` |
| Criação | Sem canal de contato nem observação | Novo · 3, Novo · 4, Revisão, Detalhe | `dto/request/CallRequestDTO.java` |
| Cargo | Obrigatório; pode ser criado no cadastro rápido | Cadastro rápido e seus estados; busca e Detalhe | `dto/request/ApplicantRequestDTO.java`, `controller/PositionController.java` |
| Anexos | 10 MB cada, 25 MB no total; nenhum em chamado fechado | Novo · 4, `anexo recusado`, `Fechado sem anexos` | `service/CallAttachmentService.java:39`, `:74`, `application.yml:52-53` |
| Reabertura | Justificativa de 10 a 1000; `ADMIN` e `TECHNICAL_ADMIN` | J9 | `dto/request/CallReopenRequestDTO.java`, `controller/CallController.java:87-88`, `service/CallService.java:436-452` |
| Edição | `PUT` completo, sempre com técnico; 409 se fechado | J10 | `service/CallService.java:374-434`, `service/CallLifecycle.java` |
| Peça única | Quantidade 1 e aviso de substituição | `Atender · peça única (rolado)` | `service/CallService.java:206-211` |
| Saída | `requestId`; até 100 locais e 200 itens por local; recebedor até 150; observações até 2000; só `receives=true`; individualizado uma vez | `Saída ·` (J11) | `dto/request/StockDispatchRequestDTO.java`, `service/StockDispatchService.java:59-98` |
| Histórico de saídas | Filtros por local, material e datas | `Saídas ·` (J11) | `controller/StockOutflowController.java` |
| Comprovante | Número, destino, recebedor, equipe e linhas de material | `Saídas · comprovante` | `dto/response/StockIssueReceiptResponseDTO.java` |
| Entrada | Só admins; peça até 1000; com série ou patrimônio, uma por vez | `Entrada ·` (J12) | `controller/ItemController.java:48-49`, `service/ItemService.java:55-71` |
| Baixa | Motivo obrigatório até 1000; zera todo o saldo | `Baixa ·` (J12), detalhe do item | `dto/request/DiscardRequestDTO.java`, `service/ItemService.java:196`, `controller/ItemController.java:71-72` |
| Categoria | Peças e insumos | Estoque, detalhe do item | `ItemController`, `enums/ItemCategory.java` |
| Equipe de TI | Nome da equipe no Perfil | Perfil | `dto/response/SecretaryResponseDTO.java` |
| Papéis | 5 papéis; `VIEWER` com 403 fora de `auth/*` e `/reports/**` | Perfil, `Perfil · somente relatórios`, matriz do §3 | `enums/Role.java`, `config/SecurityConfig.java:94-105` |
| Paginação | Aviso quando a lista vem cortada | `Estoque · lista limitada` | DIV-10 do CT-01 |

## 13. O que o código Android precisa seguir

Nada disso foi alterado no aplicativo Android nesta ampliação.

**Divergências, escopo do CT-02:**

- Trocar o rótulo `Em atendimento` de `IN_PROGRESS` por `Em andamento` e tratar status desconhecido como neutro, sem ação.
- Remover canal de contato, serviço crítico, observação na criação e os papéis `SECRETARY_ADMIN`, `STOCK_MANAGER` e `USER`.
- Tirar o KPI "Aguardando" e restringir "Mais filtros" a um status por vez.
- Exigir cargo no cadastro rápido e mostrar o cargo na busca e no Detalhe.

**Fluxos novos, ainda sem tarefa no backlog de contratos:** o CT-02 corrige divergências, e implementar J9 a J12 e os estados avulsos fica fora dele. Essa implementação usa as flags de §3.

**Dependência:** `Estoque · lista limitada` precisa de `totalElements`, que o app ainda não recebe. `items/all` devolve a página aninhada, e o cliente lê só `content` (DIV-10). Essa tela depende da correção de paginação do CT-02 e do CT-03.

**Fora desta ampliação:** os cards da Home e as listas ainda divergem (R1-01). A API não tem filtro de "ativos", então a lista filtrada mostra chamados fechados, e a solução depende do backend.
