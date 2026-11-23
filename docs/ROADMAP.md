# Roadmap — Yabai OCR

Ordem de execução: **bugs primeiro**, depois o que for mais fácil. Cada item é implementado sozinho,
marcado aqui e só então o próximo começa.

Legenda: `[ ]` pendente · `[x]` pronto · `[~]` parcial · `[-]` descartado

---

## 1. Correções de bug

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| B1 | `[x]` | **Botão «Fechar» cortado** no painel de resultado | trivial |
| B2 | `[x]` | **«Analisar» parece não fazer nada** | trivial |
| B3 | `[x]` | **Painel vazio em paisagem** (só cabeçalho e o rótulo do texto) | média |

### B1 — Botão «Fechar» cortado  `[x]` (18/10/2026)

Duas causas, as duas estruturais:

1. **Largura.** A linha de ações do painel era um `LinearLayout` horizontal, e um `LinearLayout` nunca
   quebra: quando a soma dos filhos passa da largura da tela, o Android espreme os últimos até a
   largura mínima e o texto sai uma letra por linha — o `F e c h a r` do print. A linha cresceu de 3
   para 4 botões quando entrou o «◀ Trechos».
2. **Altura.** O painel reservava um número fixo (`FIXED_CHROME_DP = 260`) para as partes que não rolam
   e entregava o resto da tela ao trecho que rola. Se qualquer coisa fora do scroller crescer — um
   resultado de OCR longo, ou a própria linha de botões quebrando em duas — o cartão passa do fim da
   tela e os botões de copiar ficam inalcançáveis.

Correção:

- As linhas de botões passaram a ser um `FlowLayout` (arquivo novo `overlay/FlowLayout.kt`), que
  **quebra para a linha de baixo** em vez de espremer.
- O teto do scroller deixou de ser adivinhado: `freeHeightForScroller` **mede** os vizinhos que não
  rolam a cada passagem de medida e dá o que sobra ao scroller. Não fica desatualizado quando o
  editor cresce ou a linha de botões quebra.
- O texto cru do Lens entrou para dentro do scroller — era o último bloco de altura ilimitada fora
  dele.

**Testes de regressão:** `app/src/test/java/dev/yabai/ocr/overlay/ResultOverlayLayoutTest.kt`, com
Robolectric (harness novo no módulo `app`). Mede o painel de verdade em 320/240/200 px:

| teste | o que ele prende |
| --- | --- |
| `the panel never asks for more height than the screen has` | o painel cabe abaixo da barra de status |
| `no button is squeezed narrower than its own label` | nenhum botão fica menor do que ele pede |
| `a crowded button row wraps onto another row` | a linha que não cabe desce, não é cortada |
| `the panel offers the actions it is meant to` | a lista de ações não muda sem querer |

Conferido contra o código antigo: os três primeiros **falham**, com
`«Fechar» was laid out 19px wide but wants 34px` e `the panel wants 686px but only 568px fit`. Não são
testes vazios.

### B2 — «Analisar» parece não fazer nada  `[x]` (18/10/2026)

O botão **tinha** função: refazia dicionário e tradução com o texto corrigido à mão. Mas nada dizia
isso ao usuário, e sem ter editado nada ele refazia exatamente a mesma análise e apagava o resultado
por um instante — daí a impressão de botão quebrado. Ele também só mudava de cor quando o texto tinha
sido alterado, o que ninguém percebia.

Decisão do usuário: **reanalisar sozinho e remover o botão**.

- O painel observa o texto editado e, **0,8 s depois da última tecla**, pede a análise de novo
  (`ResultOverlay.analyseSoon`). Digitar uma frase continua sendo *uma* análise, não uma por caractere.
- Se o texto voltar a ser o que já está na tela, nada é pedido de novo (comparação com
  `lastAnalysedText`), o que também impede o próprio `showResult` de disparar uma análise idêntica.
- Análise pendente é cancelada quando o painel sai da janela (`onDetachedFromWindow`), para não
  consultar dicionário de um painel que o usuário já fechou.
- O retorno visual passa a ser o das próprias seções: elas mostram «Traduzindo…»/«Consultando…»
  enquanto o trabalho acontece, em vez de um botão que muda de cor.
- A linha de ações ficou com 3 botões em vez de 4, e o rótulo do texto reconhecido agora diz que a
  análise se atualiza.

**Testes de regressão:** `correcting the text re-runs the lookup once the typing stops`,
`a burst of typing asks for a single lookup` e `showing a result does not look it up again`. O último
foi conferido removendo a guarda: ele falha, ou seja, prende mesmo o `showResult` de disparar análise.

---

## 2. Fácil

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| F1 | `[x]` | Menu em **ícones** numa linha abaixo da bolha | fácil |
| F2 | `[x]` | **Fechar arrastando a bolha** até um ✕ no centro de baixo | fácil |
| F3 | `[x]` | **Minimizar/recolher** as seções do painel | fácil |

### F1 — Menu em ícones  `[x]` (18/10/2026)

O menu era um cartão de quatro linhas de texto encostado no rodapé, sobre um fundo escurecido. Virou
uma faixa de quatro ícones **logo abaixo da bolha**:

| ícone | ação | texto (só como `contentDescription`) |
| --- | --- | --- |
| cantos de tela | tela inteira | «Tela inteira» |
| marcas de corte | selecionar área | «Selecionar área» |
| controles deslizantes | ajustes | «Ajustes» |
| ✕ | encerrar | «Encerrar» |

- Ícones em `res/drawable/ic_menu_*.xml`, vetores desenhados à mão, sem dependência nova. O ✕ é
  desenhado branco e **tingido de vermelho** pelo menu, mantendo o vermelho que a linha «Encerrar»
  tinha.
- Os textos saíram da tela e sobrevivem como `contentDescription`, que é o que um leitor de tela lê.
- **Sem escurecimento:** a faixa é a única coisa na janela. Um toque em qualquer outro lugar continua
  fechando o menu, e um toque no vão da própria faixa não fecha — é o comportamento de menu que se
  espera.
- Qualquer ícone fecha o menu antes de agir, num só lugar (`buildMenuStrip`), em vez de repetir o
  `hideMenu()` em cada ação.
- A faixa é **medida**, não descrita por constante: `menuAnchor` mede a faixa de verdade, centraliza
  sob a bolha, encosta na borda quando a bolha está perto dela e **passa para cima da bolha** quando
  não há espaço abaixo. Um quinto ícone não faz a faixa sair da tela.
- Ficaram sem uso e foram removidos: `menuRow` e `hairLine` (os dois em `Widgets.kt`), as três frases
  de ajuda do menu antigo (`overlay_full_screen_hint`, `overlay_full_screen_hint_direct`,
  `overlay_select_area_hint`) e o `picksText` do `OverlayController`, que só existia para escolher
  entre duas dessas frases.

**Testes de regressão:** `app/src/test/java/dev/yabai/ocr/overlay/ActionMenuTest.kt` (Robolectric,
mesmo harness do B1):

| teste | o que ele prende |
| --- | --- |
| `every action is an icon with an accessible name` | os quatro textos sobrevivem como descrição |
| `the strip shows four icons in a row` | quatro ícones, todos com desenho (os vetores inflam) |
| `each icon runs its own action` | nenhum ícone trocado de lugar |
| `the strip stays on screen wherever the bubble is` | nos cantos e no rodapé, a faixa cabe na tela |
| `the strip is placed below the bubble, and above it when it does not fit` | embaixo quando cabe, em cima quando não |

Os dois últimos prendem a matemática da âncora: sem o recorte na borda o teste acusa a faixa saindo da
tela, e sem a virada para cima ele acusa a faixa por cima do rodapé.

### F2 — Arrastar a bolha para fechar  `[x]` (18/10/2026)

Arrastar a bolha faz aparecer um **✕ no centro de baixo**; soltar a bolha em cima dele encerra o
serviço, **depois de perguntar**. Em qualquer outro lugar a bolha volta para a borda mais próxima,
como antes.

- O ✕ é um alvo circular de 64dp, 40dp acima do fim da tela, desenhado com o mesmo vetor do ícone
  «Encerrar» do menu (`ic_menu_stop`) sobre um disco escuro translúcido — a cruz vermelha se lê sobre
  qualquer app.
- **Acende enquanto a bolha está em cima:** disco vermelho cheio e cruz branca. Acender e «vai
  perguntar» são a **mesma** condição (`isOverCloseTarget`), então o realce nunca mente sobre o que
  vai acontecer ao soltar.
- A regra é o **centro** da bolha dentro do círculo do ✕: metade da bolha pode ficar de fora e ainda
  contar, o que torna o alvo tolerante sem deixá-lo vago.
- A janela do ✕ é `FLAG_NOT_TOUCHABLE`: quem está com o dedo é a bolha, o ✕ nunca rouba um toque.
- Soltar em cima do ✕ **não encerra nada**: abre um cartão «Encerrar o OCR flutuante?», com
  «Cancelar» e «Encerrar», no lugar onde o ✕ estava, para a resposta ficar debaixo do dedo. A bolha
  fica onde está até a resposta; «Cancelar» — ou um toque fora — a devolve para a borda.
- O vermelho do «Encerrar» está no **texto**, não no botão. Encher o botão de vermelho destacaria
  justamente o «sim» que a pergunta existe para evitar.
- `ACTION_CANCEL` (gesto tomado por outra janela) só tira o ✕ e devolve a bolha: nunca pergunta nada,
  porque o usuário não soltou.
- O ✕ e a pergunta também são tirados em `hideAllForCapture` e `destroy`, para não entrarem numa
  captura nem sobreviverem ao serviço.

**Testes de regressão:** `app/src/test/java/dev/yabai/ocr/overlay/DragToCloseTest.kt`:

| teste | o que ele prende |
| --- | --- |
| `the cross sits centred near the bottom of the screen` | o ✕ no centro, dentro da tela, no rodapé e grande o bastante |
| `only a bubble centred on the cross counts as a drop` | o alvo é o círculo, não o retângulo; quem manda é o centro |
| `dropping the bubble on the cross asks before closing` | **soltar em cima do ✕ não encerra direto** |
| `dropping the bubble away from the cross asks nothing` | arrastar para longe não pergunta nada |
| `the question closes the service only when it is answered yes` | «Cancelar» não encerra; «Encerrar» encerra |
| `the cross is a red cross with a name` | o ✕ tem desenho, nome para leitor de tela e o vermelho do tema |
| `the warning red is on the text, not on the button` | a decisão de estilo não se perde sozinha |

Conferido contra o código quebrado: com o drop encerrando direto (sem perguntar), o terceiro falha com
`the service was closed without asking`; com o alvo virando «qualquer sobreposição de retângulo», o
segundo falha com `beside the cross`. Não são testes vazios.

### F3 — Minimizar o painel  `[x]` (18/10/2026)

Duas dobras independentes:

- **Cada seção dobra sozinha.** O título de «Tradução», «Kanji» e «Dicionário» é clicável e tem um
  chevron; a seção vira uma linha e o resto do painel não se mexe. O «Dicionário» é o caso que
  importa — são até 60 entradas — e é ele que costuma empurrar a tradução para fora da tela.
- **O painel inteiro vira uma tira.** O chevron do cabeçalho esconde tudo menos o cabeçalho, que
  passa a mostrar **a tradução** (ou o texto reconhecido, quando ainda não há tradução) numa linha
  só, com reticências. Uma linha de resposta é o que permite ler o mangá embaixo com a tradução à
  vista — é também a base do M2.

Decisões que valem registrar:

- A seção é escondida com `GONE`, não `INVISIBLE`: recolher tem de **devolver a tela**, não só tirar o
  texto de vista. O teste de altura existe exatamente para isso.
- O estado de cada seção é lembrado por uma **chave**, não pelo título: o título do dicionário carrega
  uma contagem de palavras que muda a cada re-análise.
- A dobra sobrevive à re-análise que a edição dispara (as seções são reconstruídas no lugar), mas
  **uma captura nova abre tudo aberto**.
- O título da seção **não** é um botão de ação (não tem `ACTION_BUTTON_TAG`), então o teste do B1 que
  prende a lista exata de botões continua valendo; o controle de dobra é uma imagem, não um botão.
- O chevron é um vetor só (`ic_chevron`), girado: para baixo = aberto, para a direita = seção
  recolhida, para cima = painel recolhido.
- As seções são **campos** do painel, não views novas, e o cartão que estava sendo substituído ainda
  as segurava: uma view que já tem pai não pode ser adicionada de novo, então uma segunda captura no
  mesmo painel estourava. `showResult` agora desprende as quatro antes de montar o cartão — foi o
  teste «a new capture opens unfolded» que apareceu com o erro.

**Testes de regressão:** `app/src/test/java/dev/yabai/ocr/overlay/PanelFoldTest.kt`:

| teste | o que ele prende |
| --- | --- |
| `folding a section shortens the card and unfolding brings it back` | recolher o dicionário **devolve altura**, e desrecolher devolve tudo |
| `a folded section stays folded when the text is looked up again` | a escolha sobrevive à re-análise da edição |
| `the whole card folds to a strip that still shows the translation` | o painel vira uma linha **e a linha é a tradução**; abrir traz o resto |
| `a new capture opens unfolded` | captura nova não abre recolhida (e o painel pode ser reusado) |

O primeiro roda numa **tela alta** (`h1600dp`) de propósito: numa tela de 640px o conteúdo já é maior
que o orçamento do painel, então o cartão fica saturado e a altura não muda mesmo recolhendo — o que
muda é o que aparece. Na tela alta o número se mexe e o teste mede.

Conferido contra o código quebrado: com a seção escondida com `INVISIBLE`, o primeiro falha com
`folding the dictionary saved no height: 1315 → 1315`; sem lembrar a dobra, o segundo falha com
`the fold was forgotten`; com a dobra do painel não escondendo nada, o terceiro falha com
`row 1 of the card is still on screen`; com a tira mostrando o título em vez da resposta, o terceiro
falha com `expected:<[Perguntei ao funcionário e ele disse que saiu por aí.]> but
was:<[Texto reconhecido]>`; mantendo a dobra entre capturas, o quarto falha com
`expected:<0> but was:<8>`. Não são testes vazios.

---

## 3. Médio

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| M1 | `[x]` | Escolher **qual dicionário** usar, direto no painel | médio |
| M2 | `[x]` | Comparar **lado a lado com a imagem** capturada | médio |

### M1 — Trocar de dicionário no painel  `[x]` (18/10/2026)

A pergunta «esta palavra está no outro dicionário?» não tinha resposta sem ir aos ajustes, desabilitar um
dicionário, voltar e capturar de novo — e o ajuste ficava trocado para sempre. Agora o cabeçalho da seção
«Dicionário» diz **qual** dicionário está respondendo e abre a lista com um toque.

Como funciona:

- O motor passou a aceitar uma **máscara por consulta** (`LookupEngine.lookup(texto, only = …)`) em vez de
  consultar sempre todos os habilitados. O motor continua com todos carregados na memória: trocar de
  dicionário não recarrega índice nenhum.
- A máscara é um conjunto de **títulos**, não de ids do repositório. O painel tem de **mostrar** a escolha
  e cada acerto já carrega o título; o id só existe na tela de dicionários. Conjunto vazio = todos, que é
  o mesmo que «não filtrar» — não há um segundo estado «todos» para manter em dia.
- «Todos» é a ausência de filtro (`null`), e a escolha é gravada como **ajuste** (`dictionaryFilter`), então
  a próxima captura já começa filtrada. Quem grava é o app, não o painel: o painel só avisa
  (`onDictionaryPicked`), e o app reanalisa. Uma escolha no painel e a edição do texto caem no mesmo
  caminho de «a resposta está velha».
- **Um dicionário só: sem seletor.** Com dois ou mais ele aparece (`titles.size > 1`). Com um, seria um
  rótulo fantasiado de interruptor.
- **A lista é dos habilitados**, não dos instalados. A diferença é a tela de dicionários: oferecer um
  dicionário desabilitado aqui daria uma consulta que não devolve nada, sem dizer por quê. Habilitar
  continua sendo trabalho daquela tela.
- **Filtro que aponta para o nada é descartado**, não obedecido (`DictionaryRepository.resolveFilter`): se
  o dicionário foi desabilitado ou removido depois, obedecer devolveria zero palavras em toda captura — o
  que se parece exatamente com um dicionário quebrado. Descartar é o que traz o painel de volta para
  «Todos».
- **O bloco de kanji segue a mesma máscara**, senão a seção de cima contradiria a de baixo.
- O seletor é um **cartão dentro do próprio painel** (uma linha que quebra, com as opções), não um popup:
  o painel já é uma janela flutuante, e um popup precisaria de token próprio, poderia ser cortado pelas
  bordas que o painel já respeita e não apareceria em teste de layout nenhum.
- **Abrir o seletor desrecolhe a seção** (a lista mora no corpo, e corpo recolhido não se vê), e
  **escolher fecha o seletor** — a pergunta foi respondida. A seção é redesenhada *antes* de avisar o app,
  porque o app responde com «Consultando…» e um redesenho velho por cima disso apagaria o aviso. Foi o
  teste que pegou as duas: `renderWords` passou a limpar a própria seção, já que agora ela se redesenha
  sozinha (antes quem limpava era só o `setWords`, e o seletor empilhava uma segunda seção em cima).
- O seletor é um botão de ação de verdade (`ACTION_BUTTON_TAG`), e o título da seção continua **não**
  sendo: a lista exata de botões do B1 segue valendo (com um dicionário só, o seletor nem existe).
- Vale registrar o que o teste do motor descobriu: entre dois dicionários, **quem responde primeiro é o
  primeiro da lista de prioridade** — a máscara muda *qual* dicionário responde, não só «se» responde. É
  o que torna a escolha visível: `東京` está no JMdict e no JMnedict, e sem máscara o JMnedict nunca
  apareceria. Por isso a palavra de amostra é escolhida em tempo de execução entre as que estão **nos
  dois** (`assumeTrue` se nenhuma estiver): os fixtures são arquivos baixados que mudam.

**Testes de regressão:** `app/src/test/java/dev/yabai/ocr/overlay/DictionaryPickerTest.kt` (7) e duas
adições ao `core/src/test/kotlin/dev/yabai/ocr/core/dict/DictionaryCompileTest.kt`:

| teste | o que ele prende |
| --- | --- |
| `the header says which dictionary is answering` | o cabeçalho mostra a escolha; «Todos» quando não há filtro |
| `one dictionary is nothing to choose between` | com um dicionário só não há seletor |
| `the chooser is closed until it is asked for` | o seletor não ocupa espaço até ser tocado |
| `the chooser offers all of them plus each installed dictionary` | «Todos» primeiro, depois cada um na ordem de prioridade |
| `choosing a dictionary tells the app which one, and folds the chooser away` | a escolha é **entregue** ao app, e o seletor fecha |
| `asking for all of them again is a choice too` | «Todos» também é uma escolha, não um cancelamento |
| `opening the chooser unfolds a folded dictionary section` | abrir o seletor numa seção recolhida desrecolhe a seção |
| `a lookup only consults the dictionaries it was told to` | a máscara troca **qual** dicionário responde; conjunto vazio = todos; título inexistente = nenhuma palavra, não erro |
| `the kanji breakdown follows the same choice` | o bloco de kanji não contradiz as palavras acima (淀 só no KANJIDIC inglês) |

Conferido contra o código quebrado, sete quebras, cada uma derrubando o teste a que pertence: seletor
sempre visível → `the selector should stay out of the way expected null, but was:<android.widget.TextView…>`;
rótulo ignorando a escolha → `expected:<[JMdict] ▾> but was:<[Todos] ▾>`; escolha não avisando o app →
`expected:<[dictionary JMdict]> but was:<[]>` (e o de «Todos» junto); seletor não fechando →
`the chooser stayed open after the answer was given`; abrir sem desrecolher → `expected:<0> but was:<8>`;
motor ignorando a máscara → `the named dictionary should have answered expected:<JM[ne]dict [2026-10-17]>
but was:<JM[]dict [2026-10-17]>`; kanji ignorando a máscara → `the Portuguese build has no 淀: 1`. Não
são testes vazios.

### M2 — Imagem lado a lado  `[x]` (18/10/2026)

O recorte que ia para o OCR era descartado assim que a análise começava. Agora ele fica guardado
enquanto o painel está aberto, e pode aparecer **ao lado do texto reconhecido** — que é onde a imagem
serve para alguma coisa: quando o OCR troca um kanji, é ela que diz qual era.

- **A imagem é o que o texto é.** Numa captura de área é o recorte; num trecho escolhido no «tela
  inteira» é **aquele trecho**, não a tela toda — uma página em miniatura ao lado de uma linha não
  serviria para conferir kanji nenhum. A caixa que o motor devolve já está nas coordenadas do
  recorte, então o corte sai direto dela, sem conta inversa (`BlockImage.from`).
- **Botão «Imagem» na linha de ações**, e só quando há imagem para mostrar. É um **toggle**, e a
  escolha é um ajuste (`showImage`), então quem quer o lado a lado todos os dias liga uma vez; a
  mesma caixa existe nas configurações e as duas escrevem a mesma flag.
- **Ligado por padrão: não.** O que perde largura com a imagem é o texto reconhecido — justamente o
  campo que se edita para consertar o erro do OCR. Sem imagem, o painel fica exatamente como estava.
- **Quem manda no tamanho é a própria imagem**, não uma divisão fixa: `adjustViewBounds` dentro de um
  teto de 120×200dp, e nunca ampliando. Coluna de mangá é alta e estreita, tela inteira é larga; com
  meia-largura fixa uma das duas ficaria com um buraco do lado. O texto fica com o que sobra.
- **Ligar e desligar reconstrói só a linha do texto**, não o cartão: a tradução, o dicionário e o que
  está dobrado continuam valendo, e a análise não recomeça.
- **O bitmap é solto, não reciclado**, ao fechar o painel (e em `onDestroy`). Reciclar um bitmap que
  uma view ainda aponta estoura no desenho seguinte; largar a referência é tudo o que o coletor
  precisa para devolver uma tela inteira de pixels.
- A imagem é mais uma linha do cartão, então **a dobra do painel (F3) a esconde junto** com o resto,
  e o orçamento de altura do B1 passa a contá-la — o `freeHeightForScroller` mede o que está lá, não
  uma constante.

**Testes de regressão:** `app/src/test/java/dev/yabai/ocr/overlay/ImagePanelTest.kt` (6) e
`app/src/test/java/dev/yabai/ocr/util/BlockImageTest.kt` (4):

| teste | o que ele prende |
| --- | --- |
| `the image sits beside the recognised text, not above it` | imagem e texto na **mesma linha**, imagem à esquerda, dentro do teto e sem distorção |
| `without the image the text keeps the whole width` | sem imagem (desligada ou inexistente) o texto fica com a largura inteira |
| `the image can be turned on and off while the panel is open` | ligar tira largura do texto, desligar devolve, e nem o texto nem a tradução se perdem |
| `the image is offered as a button only when there is one` | a lista exata de botões com imagem, e sem imagem nenhum botão «Imagem» |
| `the image button asks the app to remember the choice` | o botão avisa o app, que é quem grava o ajuste |
| `a very tall capture does not push the panel off the screen` | teto de altura: uma captura 300×3000 não empurra o painel para fora |
| `the box the text came from is what gets cut out` | o corte é a caixa, nas coordenadas do recorte |
| `a box that is not there is the whole frame` | caixa ausente ou vazia cai no quadro inteiro |
| `a box that hangs off the edge is clipped` | caixa que passa da borda é recortada, não recusada |
| `a box that is nowhere near the frame is the whole frame` | caixa de outro tamanho não deixa o usuário sem imagem |

Conferido contra o código quebrado: com a linha virando vertical, o primeiro falha com
`the picture is not to the left of the text`; com o `ImageView` criado mesmo sem imagem, o segundo
falha com `the panel is showing an image`; sem reconstruir a linha ao alternar, o terceiro falha com
`no image appeared`; com o botão não avisando ninguém, o quinto falha com `expected:<[image]> but
was:<[]>`. Sem o teto de altura, o sexto falha com `the image is taller than its cap` (e o primeiro,
com `the picture is taller than its cap: 340`); com o corte sempre devolvendo o quadro inteiro, os
testes de `BlockImage` falham com `expected:<100> but was:<400>` e `expected:<20> but was:<400>`. Não
são testes vazios.

---

## 4. Difícil (pesquisa)

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| D1 | `[x]` | **Remover furigana** do texto reconhecido | difícil |
| D2 | `[x]` | **Adicionar furigana** aos kanji que não têm (estilo Dokuen) | difícil |

### D1 — Remover furigana  `[x]` (18/10/2026)

A frase saía como `しょくいん職員さんに聞いた`: a leitura colada na palavra que ela explica. Não é texto
perdido — é texto *errado*, e errado de um jeito que estraga as duas coisas que o app faz com ele: o
`しょくいん職員` não está em dicionário nenhum, e a tradução recebe uma palavra inventada no meio da frase.

**O caminho que valeu foi a geometria**, e não por palpite: a primeira coisa feita aqui foi *medir* o que o
Lens devolve para uma página com furigana. Foi desenhada uma página (`core/src/test/resources/test_furigana.png`,
800×1000, texto vertical e horizontal, com e sem furigana), ela foi enviada **uma vez** ao endpoint de
verdade, e a resposta foi gravada (`core/src/test/resources/lens_furigana.bin`, 1,7 kB) como fixture
offline. O que a medição mostrou:

| | leitura | linha que ela explica | espessura | distância |
| --- | --- | --- | --- | --- |
| vertical | `16×92` | `45×375` | 36% | 8 px |
| horizontal | `89×17` | `185×41` | 41% | 16 px |

Ou seja: **o Lens reporta o furigana como uma linha própria**, e não como palavra dentro da linha. Isso
explica o bug inteiro — a linha da leitura é fina e encosta na coluna do kanji, então a regra de junção que
monta os balões (`MERGE_GAP_RATIO`) juntava as duas no mesmo bloco, e a frase saía com a leitura na frente
(em japonês vertical o rubi fica à direita, e lendo-se da direita para a esquerda ele vem antes).

O que foi feito:

- `core/.../lens/RubyGeometry.kt` decide, **só por geometria**, se uma linha é leitura de outra. As quatro
  regras saíram da tabela acima, com folga para o lado conservador: a leitura é **mais fina** que a linha
  (≤ 60% da espessura, medido 36–41%), está **encostada** nela (vão ≤ 50% da espessura, medido 8 px de 45 e
  16 px de 41), **cobre** os caracteres que lê (sobreposição ≥ 50% do comprimento da leitura, medido 95% e
  100%) e **não passa** da linha por mais de um glifo (medido 5 px de 45). Além disso a leitura tem de ser
  **só kana** e a linha explicada tem de ter **pelo menos um kanji** — que é o que impede as duas de
  trocarem de papel (kana puro nunca é base).
- **Onde a leitura entra é medido, não adivinhado.** A caixa da leitura, projetada no comprimento da linha,
  diz **quais caracteres** ela lê: `しょくいん` cobre os dois primeiros de oito em `職員さんに聞いた`, então
  a leitura guardada é `(início 0, 2 caracteres)` — e é isso que faz `職員（しょくいん）さん` em vez de
  `（しょくいん）職員さん`. O arredondamento é para fora (início para baixo, fim para cima): errar para
  menos grudaria a leitura no kanji errado.
- **Dois modos** (`FuriganaMode`): `REMOVE` (padrão) tira a leitura da frase; `PARENTHESES` a mantém onde
  ela foi impressa. É a escolha que o item pedia — «remover o furigana, **ou** manter entre parênteses» — e
  quem decide é um ajuste na tela principal («Manter o furigana que a página imprime», chamado então de
  «Manter o furigana entre parênteses»), que viaja até o
  `LensResultBuilder` pelo `FuriganaMode` do motor. O modo entrou na chave do cache do `LensOcrEngine`
  (`LensKey`) junto com idioma/dimensão/ampliação: ele não muda a *requisição*, mas mudar o ajuste e a
  próxima captura responder com o modo antigo seria pior do que recriar um cliente HTTP.
- **O texto cru continua cru.** As linhas que o painel mostra em «texto cru do Lens» (`LensBlock.lines` e
  `LensSegment`) continuam exatamente como o Lens reportou, leitura incluída. A limpeza é da *frase*: uma
  lista de linhas que escondesse a leitura estaria mentindo sobre o que o OCR viu — e é justamente ali que
  se confere se o OCR errou.
- **Um grupo que era só leitura não vira trecho.** Se o balão tiver só a coluna do rubi (sem base detectada),
  ele não é oferecido no seletor de trechos: um retângulo que ao ser tocado não devolve texto nenhum é um
  botão que parece quebrado. A regra também deixa intacto o invariante que o M1 já prendia
  (`segments == blocks.flatMap { lines }`).
- **Não foi para o ML Kit.** O detector é geometria pura e poderia valer para as linhas do ML Kit offline,
  mas não há como medir isso aqui (o ML Kit precisa do aparelho), e uma regra de corte aplicada sem medição
  come texto de verdade. Fica registrado como o próximo passo *se* aparecer uma amostra.
- **O segundo caminho do item (dicionário) não foi usado e continua de pé**: ele serviria para *confirmar* a
  leitura contra o KANJIDIC quando a geometria está em dúvida. Com a regra conservadora — na dúvida, não
  mexe — a confirmação por dicionário só acrescentaria recall, e é exatamente o que o **D2** precisa
  (inserir leitura onde não há rubi nenhum, ali o dicionário é a única fonte).

**Testes de regressão:** `core/src/test/kotlin/dev/yabai/ocr/core/lens/FuriganaTest.kt` (11) e uma adição ao
`core/src/test/kotlin/dev/yabai/ocr/core/jp/JapaneseTextTest.kt`:

| teste | o que ele prende |
| --- | --- |
| `a reading is not part of the sentence` | `しょくいん` sai da frase; os outros balões ficam iguais |
| `a reading can be kept where it was printed` | `職員（しょくいん）さん…`, e o horizontal também, com a leitura **depois** dos caracteres que ela lê |
| `a line without furigana is untouched in both modes` | detector não come linha sem rubi (nos dois modos) |
| `the raw lines still show the reading` | o texto cru do Lens não foi limpo junto |
| `the recording has the page it came from` | a gravação e a imagem são um par (800×1000) |
| `a kana column of its own is not a reading` | duas colunas de balão (`もう` 29 px × `死んでいる` 37 px, de uma resposta real) não são rubi |
| `a small kana line with nothing to explain keeps its kana` | kana fina longe de qualquer kanji continua no lugar |
| `a small kana line across a gutter is not a reading` | kana fina, alinhada e cobrindo, mas do outro lado de uma sarjeta, é balão — não rubi |
| `each reading lands after its own characters` | **duas** leituras na mesma linha, cada uma no lugar certo |
| `a reading after a kanji outside the basic plane is not written inside it` | a posição é contada em **caracteres**, não em `char` (um kanji fora do BMP não é partido ao meio) |
| `a line without a box is left alone` | linha sem caixa não é rubi nem destino de rubi |
| `tells kana from kanji` (`JapaneseTextTest`) | `isKanaOnly`/`hasKanji`: os dois papéis não se confundem |

Conferido contra o código quebrado, dez quebras, cada uma derrubando um teste de verdade: detecção desligada →
`expected:<[職員さんに聞いた, …]> but was:<[しょくいん 職員さんに聞いた, …]>`; leitura não saindo da frase → o
mesmo; texto cru perdendo a leitura → `expected:<[しょくいん, 職員さんに聞いた]> but was:<[職員さんに聞いた]>`;
leitura escrita no início da linha → `expected:<[（しょくいんしつ）（しょくいん）職員室の職員]> …`; fim do
intervalo arredondado para baixo → `expected:<[職員（しょくいん）さんに聞いた…]> but was:<[職（しょくいん）員さんに聞いた…]>`;
leituras escritas por ordem crescente → `expected:<[職員室（しょくいんしつ）の職員（しょくいん）]> but
was:<[職員室（しょ（しょくいん）くいんしつ）の職員]>`; espessura sem limite → `expected:<[もう 死んでいる]>
but was:<[死んでいる]>`; sarjeta ignorada → `expected:<[しょくいん, 職員さんに聞いた]> but was:<[職員さんに聞いた]>`;
modo `REMOVE` escrevendo a leitura → `expected:<[職員さんに聞いた…]> but was:<[職員（しょくいん）さんに聞いた…]>`;
posição contada em `char` → `expected:<[職員𠮟（しかる）]> but was:<[職員?（しかる）?]>`. Não são testes vazios.

### D2 — Adicionar furigana  `[x]` (18/10/2026)

O mangá imprime a leitura de *alguns* kanji. Quando não imprime — que é a maioria das vezes — ela continua
existindo, e o app já a tinha em casa: o motor de dicionário segmenta a frase e cada palavra que ele
encontra traz a própria leitura. Agora o painel escreve essa leitura ao lado dos caracteres que ela lê,
num bloco «Furigana» novo:

```
職員さんに聞いた   →   職員（しょくいん）さんに聞（き）いた
```

Numa frase de verdade, com o JMdict instalado e a segmentação real:

```
私は昨日、友達と一緒に寿司を食べました。
  →  私（わたし）は昨日（さくじつ）、友達（ともだち）と一緒（いっしょ）に寿司（すし）を食（た）べました。
```

O que foi feito:

- **A leitura não é procurada de novo.** O bloco é montado a partir dos acertos que o painel já tinha
  (`WordsState.Done.matches`) — a leitura de uma palavra faz parte do acerto. É uma passagem sobre o
  texto, não outra consulta, e é por isso que ligar o bloco não deixa o painel mais lento.
- **Onde a leitura entra é resolvido por palavra**, em `core/.../dict/Furigana.kt` (`split`): percorre o
  termo do dicionário, separa os trechos de kanji dos de kana e reparte a leitura pelo trecho seguinte
  de kana. É o que dá `食（た）べました` em vez de `食（たべる）べました` — o `べました` está impresso na
  frase, e a leitura que vai em cima do kanji é só o `た`. Palavras com dois trechos de kanji saem
  certas (`申し込み` → `申（もう）し込（こ）み`), e a leitura em katakana casa com o kana em hiragana
  (`食ベル` / `たべる`): são a mesma leitura.
- **Quando a leitura não encaixa, não se escreve nada.** Se a leitura não for só kana, ou se o termo do
  dicionário não tiver os mesmos kanji da frase (um acerto desinflectido que perdeu caracteres), o
  trecho é deixado em paz. Uma leitura em cima do kanji errado ensina o caractere errado — é pior do
  que não ter leitura nenhuma.
- **Kanji que uma palavra já leu não recebe palpite por cima.** O bloco marca os caracteres que cada
  acerto cobriu e não volta neles: `職（しょく）` não vira `職（しょく）（もと?）`.
- **Só um kanji sozinho cai no KANJIDIC**, e sempre marcado: dois kanji colados lidos como se fossem uma
  palavra seriam uma palavra que o dicionário não tem, exatamente o que não se inventa. As leituras
  vêm do `KanjiEntry`, que agora **carrega** o on'yomi e o kun'yomi — o blob compacto já os tinha e o
  leitor os jogava fora. Ordem: **kun primeiro** (um kanji sozinho num texto japonês se lê à japonesa
  primeiro), ordenados até três, o `た.べる` do KANJIDIC reduzido ao `た` (o `べる` está na frase) e
  separados por `・`. O que é palpite sai dentro dos parênteses com `?` e é **contado**
  (`Annotated.uncertain`), e o painel mostra a nota que explica o `?` só quando há algum.
- **O texto reconhecido não é tocado.** O bloco é uma leitura *do* texto, não o texto: escrever dentro
  do campo editável envenenaria o dicionário e a tradução (é esse campo que alimenta os dois) e brigaria
  com as correções do usuário. Se a leitura já está impressa na frase — o rubi do mangá ou o
  `職員（しょくいん）` que o D1 deixa — ela **não é escrita uma segunda vez**. *(O N5, mais tarde, mudou
  isto a pedido do usuário: a leitura passou a ser escrita no próprio texto — ver «N5» na seção 6, com a
  proteção que impede a leitura de voltar como consulta e de apagar uma correção.)*
- **O bloco é mais um item dobrável** do painel, acima da lista de palavras (quem não sabe ler um kanji
  quer a leitura ao lado dele, não três seções abaixo) e o seu texto é **selecionável**, como todo o
  resto do painel.
- **Saída em texto (`職員（しょくいん）`), não em rubi por cima do kanji.** O rubi de verdade precisa de
  `Spannable` num `TextView` próprio, não é copiável como texto e não é o que o painel faz em lugar
  nenhum; a convenção de parênteses é a que o D1 já tinha introduzido. Fica registrado como refinamento
  possível, não como pendência.
- **A escolha do dicionário vale também aqui**: o bloco recebe o mesmo `only` que a consulta e o quadro
  de kanji, então nunca é respondido por um dicionário que a seção de palavras foi mandada ignorar —
  a mesma regra que o M1 prende do outro lado.
- **Ajuste próprio** («Escrever no painel a leitura do dicionário», chamado então de «Mostrar a leitura
  dos kanji»), **ligado** por padrão: é uma ajuda de leitura e
  custa um bloco de uma linha, que dobra. O ajuste do D1 é o outro lado da mesma pergunta — o que fazer
  com a leitura que o Lens **viu**; este é o que fazer com a que **ninguém viu**.
- **O ML Kit não foi tocado**, pela mesma razão do D1: a leitura do bloco sai do dicionário, que o ML
  Kit não consulta.

**Testes de regressão:** `core/.../dict/FuriganaTest.kt` (21, sem dicionários — os acertos são
construídos à mão, porque o que está em causa é onde a leitura entra e não se a consulta acha a palavra),
três novos em `core/.../dict/DictionaryCompileTest.kt` (com os dicionários de verdade) e
`app/.../overlay/FuriganaPanelTest.kt` (5):

| teste | o que ele prende |
| --- | --- |
| `a verb keeps its okurigana out of the reading` | `食（た）べました`, não `食（たべる）べました` |
| `each kanji run of a mixed word takes its own part of the reading` | `申し込み`, `有り難い`, `お客様` por trecho |
| `a word without kanji has nothing to read` | palavra em kana não ganha leitura |
| `a reading that does not line up with the word is refused` | leitura que não encaixa não é escrita |
| `a katakana spelling lines up with a hiragana reading` | katakana e hiragana são a mesma leitura |
| `a lone kanji reads kun first, without the okurigana` | kun antes de on, `た.べる` → `た`, até três |
| `a reading that only repeats is written once` | leitura repetida não aparece duas vezes |
| `what is not a reading is thrown away` | lixo no campo das leituras não vira leitura |
| `every kanji run gets its reading after it` | a frase da regressão sai inteira, e `uncertain` é 0 |
| `a kanji standing alone falls back to the kanji data, marked as the guess it is` | `淀（よど・テン・デン?）`, contado como palpite |
| `two kanji in a row are never read as if they were a word` | dois kanji colados não recebem leitura inventada |
| `a kanji a word already read is not read again from the kanji data` | o palpite não se sobrepõe à palavra |
| `a word that covers part of a kanji run is written where it ends` | `職員（しょくいん）室` |
| `a word whose dictionary spelling does not match the sentence is left alone` | leitura não é fixada sobre caracteres que não lê |
| `a reading the sentence already prints is not written a second time` | nada de `職員（しょくいん）（しょくいん）` |
| `a kanji outside the basic plane is not written inside itself` | posição contada em caracteres, não em `char` |
| `a sentence with no kanji has nothing to annotate` | frase sem kanji não gera bloco |
| `the reading of a sentence comes from the words it was segmented into` (dicionários) | a frase real do JMdict, `食（た）べました` incluído |
| `a kanji no word covers falls back to the kanji entry, marked as a guess` (dicionários) | o palpite vem do KANJIDIC e sai marcado |
| `the reading fallback follows the same dictionary choice` (dicionários) | o `only` vale também para o palpite |
| `the reading of the sentence is written beside the kanji` (painel) | o bloco mostra a leitura, selecionável |
| `nothing to read means no block at all` (painel) | sem leitura não há bloco — e a frase não muda |
| `a guessed reading is called out under the sentence` (painel) | a nota do `?` só aparece quando há palpite |
| `the block folds, and stays folded when the text is looked up again` (painel) | o bloco dobra e não se desdobra sozinho |
| `the block sits above the dictionary` (painel) | a ordem dos blocos é a decidida |

*(Os cinco testes de painel acima foram reescritos no **N5** — o bloco virou texto — e hoje são sete em
`FuriganaPanelTest`; os nomes desta tabela são os do D2, como estavam.)*

Conferido contra o código quebrado, **dezesseis** quebras, cada uma derrubando um teste de verdade:
detecção desligada → `expected:<職員（しょくいん）さんに聞（き）いた> but was:<null>`; okurigana entrando na
leitura → `expected:<…聞（き[]）いた> but was:<…聞（き[く]）いた>`; leitura já impressa escrita de novo →
`expected:<職員（しょくいん）[]さん…> but was:<職員（しょくいん）[（しょくいん）]さん…>`; qualquer parêntese
engolindo a leitura → `expected:<…聞[（き）]いた> but was:<…聞[]いた>`; leitura fixada sobre caracteres que
não lê → `expected null, but was:<Annotated(text=職（しょくいん）員室…)>`; palpite por cima de palavra →
`expected:<職（しょく）[]> but was:<職（しょく）[（もと?）]>`; dois kanji colados lidos como palavra →
`expected null, but was:<Annotated(text=滉洋（よみ?）…)>`; palpite contado como certeza →
`expected:<1> but was:<0>`; on antes de kun → `expected:<[よど, テン]> but was:<[テン, よど]>`; katakana
sem normalização → `expected:<[Run(kanji=食, reading=た)]> but was:<null>`; separador das leituras
ignorado → `expected:<[た, く, ショク]> but was:<[た]>`; kanji fora do plano básico contado em `char` →
`expected:<𠮟（しか）る> but was:<null>`; bloco fora do painel → `NullPointerException` na seção; nota do
palpite a desaparecer → `expected:<[? — o dicionário não tem este kanji como palavra…]> but was:<[null]>`;
leitura não selecionável → `the reading has to be selectable to be copied`; dicionário antes do bloco →
`expected the reading before the words, got [dictionary, furigana, dictionary]`. Não são testes vazios.

---

### Registro — a carga que vai para o Lens  (18/10/2026)

A última pendência aberta do roadmap era o `DEFAULT_MAX_DIMENSION`, que estava em 1600 aguentando uma
captura de tela de 1080×2400 reduzida a 720×1600. Se a redução custava alguma coisa não se sabia; agora
se sabe, porque foi medido contra o endpoint real (a sonda usada foi o `LensLiveTest` com uma lista de
cargas; a sonda foi apagada a seguir, as cargas eram arquivos temporários):

| carga | bytes | tempo | HTTP | trechos | leitura pequena |
| --- | --- | --- | --- | --- | --- |
| tela 1080×2400 reduzida (limite 1600 → 720×1600) | 104 702 | 1,2 s | 200 | 4–5 | **não** |
| tela 1080×2400 nativa (limite 2400) | 177 897 | 0,9–1,3 s | 200 | 5 | **sim** (`よど` como trecho próprio) |
| página fotografada 1080×2400 | 2 393 184 | 5,1 s | 200 | 5 | — |
| recorte 1350×3000 (extremo do ajuste) | 3 718 338 | 6,9 s | 200 | 6 | — |

As duas primeiras linhas são **a mesma página**, desenhada nítida nas duas densidades (texto a 30 px
com leitura a 15 px, e texto a 45 px com leitura a 22 px). O resultado se repetiu nas duas corridas:
com a leitura a 15 px o Lens não a reconhece de todo; a 22 px, vem como trecho próprio. E é essa leitura
que o D1 tira da frase e que o D2 escreve — a redução não custava bytes, custava a funcionalidade.

- `DEFAULT_MAX_DIMENSION` passou de **1600** para **2400**: a tela de um telefone comum entra inteira,
  sem reamostragem; uma tela maior continua sendo reduzida, mas só até 2400. *Superado no mesmo dia —
  ver a nota no fim da seção.*
- O que se paga: numa captura de tela +73 KB e o mesmo tempo; numa página fotografada 2,4 MB e ~5 s —
  dentro do `callTimeout` de 20 s, e o endpoint aceita (3,7 MB, ~7 s, também aceite). O teto não é o
  endpoint.
- Testes: `app/.../ocr/ImagePreparerTest.kt` (7) — a tela de telefone desce a 720×1600 no limite
  padrão (era «passa intacta» quando o limite era 2400), uma tela maior é reduzida mantendo as
  proporções, o recorte é ampliado mas só até duas vezes e nunca além do limite, e a imagem vai em
  **PNG** com bytes dentro. Conferido contra o código quebrado: subir o limite para 2400 →
  `expected:<720> but was:<1080>`; ampliação sem limite → `expected:<600> but was:<800>`; envio em JPEG
  → `expected a PNG signature, got [-1, -40, -1, -32]`.

**19/10/2026 — o limite voltou a 1600, e esta medição ficou superada.** A conclusão acima foi tirada
com uma página **desenhada**: fundo limpo, letra de imprensa, sem arte por baixo e sem a letra grande
estilizada que abre um título de mangá. Numa página real (ver «N1 — A precisão do OCR caiu») o tamanho
maior faz o Lens perder exatamente esse kanji. Como a palavra perdida não se recupera e a leitura
impressa que o maior ganhava é redundante (o D2 a escreve do dicionário, e um recorte é ampliado),
ficou o kanji. O resto desta seção — o protocolo, os bytes, o tempo — continua válido; o que mudou é
só a escolha do número.

---

## 5. Pedidos novos

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| I1 | `[x]` | **Perguntar a uma IA** que o usuário já tem no aparelho | médio |

### I1 — Perguntar a uma IA do aparelho  `[x]` (19/10/2026)

O pedido: uma maneira de traduzir/perguntar por **IA**, sabendo de antemão que «chamar um modelo» é
complicado (chave, conta, cota, custo) — e com uma sugestão de caminho melhor, se houvesse.

Havia, e é o contrário de integrar: **não chamar modelo nenhum**. O Android já sabe mandar texto para
qualquer app instalada (`ACTION_SEND`), e qualquer app de IA aceita texto compartilhado. O botão novo,
**«Perguntar à IA»**, faz três coisas e nenhuma delas passa por este app:

1. copia o **texto reconhecido** (o que está no painel, com as correções do usuário) para a área de
   transferência;
2. monta a pergunta: um **prompt editável** nos ajustes, de fábrica «Traduza para português e
   explique: », seguido do texto — prompt vazio manda só o texto;
3. abre a **app escolhida** com a pergunta já escrita, ou o **seletor do Android** para quem preferir
   decidir de cada vez.

Por que assim e não com uma API: não há chave, conta nem custo; a app que responde é a que o usuário
já escolheu para si (`ChatGPT`, `Gemini`, `Copilot`, `Claude`, o que estiver instalado); e continua
funcionando se ele trocar de app, porque não há lista de apps «suportadas» — a lista é a do aparelho.
O que se paga é que a app do outro lado não sabe nada desta, e por isso vai uma pergunta escrita.

Abriu outra app a partir da bolha é o mesmo caminho que o botão **«Ajustes»** da bolha já usava: o app
tem uma janela visível, a que o usuário acabou de tocar.

O que mudou:

- `app/.../ai/AskAi.kt` (novo) — a pergunta montada, o intent de compartilhamento, a lista de apps que aceitam
  texto e a **ordem de tentativas** (a app escolhida primeiro, o seletor sempre atrás).
- `AndroidManifest.xml` — um `<queries>` de `ACTION_SEND` + `text/plain`: sem ele o Android 11+ não
  responde à lista de apps, e o seletor dos ajustes ficaria vazio em qualquer telefone.
- `AppSettings` — `showAiButton` (ligado), `aiPrompt` (com o texto de fábrica) e `aiPackage` (só o
  nome do pacote: o nome da app é lido do aparelho, para não guardar um nome que já não existe).
- `ResultOverlay` — o botão na fila das **cópias**, primeiro da fila: é mais uma maneira de tirar o
  texto do painel, e a pergunta é sobre a frase toda (as duas cópias são sobre um pedaço).
- `OverlayController`, `FloatingOcrService` — a ponte e o serviço: copiar, tentar a app escolhida,
  cair no seletor. Quando é o seletor que responde à frente de uma app escolhida, a app já não abre e
  o nome dela é **esquecido**, para não se pagar a mesma falha em cada captura.
- `MainActivity` + `activity_main.xml` — seção «6. Perguntar a uma IA»: o botão, o prompt (gravado a
  cada tecla: não é um formulário com «guardar») e a escolha da app, com a lista lida do aparelho a
  cada abertura da tela e a cada toque. «Uso do Google Lens» passou a 7.

| teste | o que ele prende |
| --- | --- |
| `the question is the prompt and then the text` | a pergunta é o prompt seguido do texto |
| `an empty prompt sends the text alone` | prompt apagado é uma escolha, não uma avaria |
| `whitespace around the prompt and the text is dropped` | espaços e mudanças de linha que vêm de fora não fazem parte da pergunta |
| `the prompt and the text are never glued together` | exatamente um espaço entre os dois, como o usuário escreveu («:» acabado de escrever) |
| `the text keeps its own line breaks` | as linhas do texto são do texto, não se achatam |
| `an empty text is nothing to send` | sem texto não há pergunta |
| `the question leaves as a share of plain text` | `ACTION_SEND`, `text/plain` e o texto no extra |
| `without a chosen app the text goes to the chooser` | sem escolha, o intent não tem destinatário e vai embrulhado no seletor |
| `a chosen app is tried first, and the chooser is still there behind it` | a ordem das tentativas, e o seletor sempre atrás |
| `with no choice there is still something to open` | o seletor sozinho é uma lista válida |
| `the list is the phone's, with our own app left out` | a lista vem do `PackageManager` (e não do próprio app) |
| `an app appears once however many activities it offers` | duas atividades da mesma app são uma escolha |
| `the list is sorted by name` | a lista não muda de ordem entre aberturas |
| `the chosen app is named the way the phone names it` | o nome vem do aparelho, não de uma cópia guardada |
| `a package that is gone reads as no choice` | app desinstalada não deixa um nome a fingir |
| `no installed app at all leaves nothing to choose` | sem apps que aceitem texto, a lista é vazia e assumida |
| `the panel offers the question when the setting allows it` | o botão segue o ajuste (e não aparece sozinho) |
| `the question is offered next to the copy buttons` | o lugar do botão: a fila das cópias, primeiro |
| `the question is asked about the text on screen, corrections included` | pergunta sobre o texto **como está**, não como foi reconhecido |
| `an error panel does not offer the question` | onde não há texto não há pergunta |
| `the question reaches the app through the controller` | a ponte painel → app (é daqui que sairia um botão que não faz nada) |

A lista de ações que o painel oferece foi atualizada nos dois testes que a fixam
(`ResultOverlayLayoutTest`, `ImagePanelTest`) — é a rede que prende «um botão a mais» sem querer.

Não-vacuidade: 20 mutações em `AskAi.kt`, `ResultOverlay.kt` e `OverlayController.kt`, **20/20
detectadas** (`C:\Users\Eric PC\AppData\Local\Temp\opencode\ai_nonvacuous.ps1`). Duas valem a pena
contar: tirar o seletor de trás da app escolhida falha `a chosen app is tried first…`; e trocar o
texto da pergunta pelo texto tal como foi reconhecido falha `the question is asked about the text on
screen…`. A prova também detectou um teste **vazio**: `the question leaves as a share of plain text`
comparava o tipo MIME com a própria constante do código, e por isso passava mesmo com a constante
trocada para `text/html`; o teste passou a escrever `text/plain` por extenso (é um contrato com a app
do outro lado, não um nome nosso).

O que fica **de fora**, e por quê:

- o serviço (copiar, tentar, esquecer o nome morto) não tem teste: o `FloatingOcrService` não tem
  nenhum, porque precisa de uma `MediaProjection` de verdade. O que dava para separar sem mentir — a
  ordem das tentativas — foi para o `AskAi`, e é isso que os testes prendem.
- o `<queries>` do manifesto não tem teste: é uma declaração, e nenhum teste a lê. O sintoma de ela
  faltar (lista vazia no Android 11+) aparece no telefone, não aqui.
- a ponte dos ajustes (`MainActivity`: gravar o prompt a cada tecla, escolher a app na lista) não tem
  teste pela mesma razão que o resto dos ajustes não tem: o projeto não tem testes de `MainActivity`.

---

## 6. Segunda ronda  (19/10/2026)

Seis pontos relatados depois do I1, na ordem em que vão ser feitos: **bugs primeiro**, depois o que for
mais fácil. O foco declarado do app é a **precisão** — o resto é secundário — e é por isso que o N1
não espera por mais nada que não seja o print.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| N1 | `[x]` | **A precisão caiu**: um kanji que era reconhecido (淀) já não é | bug (a investigar) |
| N2 | `[x]` | **«Perguntar à IA» não abre app nenhuma** | bug (trivial) |
| N3 | `[x]` | **VOLTAR / tocar fora** = «Trechos»; «Fechar» quando não há trechos | fácil |
| N4 | `[x]` | **Ordem do furigana entre parênteses** saiu errada | fácil |
| N5 | `[x]` | **Furigana na página toda**, sem clicar num trecho | fácil |
| N6 | `[x]` | A bolha é **puxada para o ✕** quando chega perto | fácil |

### N1 — A precisão do OCR caiu  `[x]` (19/10/2026)

Relato: um kanji que o app antes reconhecia — o **淀** — deixou de ser reconhecido. Os prints chegaram
(antes, depois e a ordem do furigana); o do antes/depois mostra o essencial: no **antes** a caixa do
trecho cobre `淀みなき拳で。` inteiro; no **depois** ela começa **abaixo** do 淀 e cobre só
`みなき拳で。`. O kanji não foi «lido mal»: saiu do trecho.

**Causa: a carga que vai para o Lens.** Era a única alteração recente que muda o que o motor *vê* —
`DEFAULT_MAX_DIMENSION` de 1600 para 2400. Medido no endpoint real, na própria página do print, mudando
só o tamanho:

| carga | o que o Lens devolveu para a coluna |
| --- | --- |
| 720×1600 (limite antigo) | `淀みなき拳で。` — `PixelBox(x=50, y=319, 95×494)` |
| 1080×2400 (limite novo) | `みなきまで。` — `PixelBox(x=91, y=637, 114×575)`, e o 淀 **não aparece em segmento nenhum** |

Uma ressalva honesta sobre a segunda linha: o upload do print é 720×1600 (a imagem é reduzida ao subir),
por isso o teste a 1080×2400 foi feito sobre uma **cópia ampliada** — não é exatamente o que o telefone
viu, e é provável que a leitura `まで` seja artefato dessa ampliação. O que o print **do aparelho** prova,
e que a medição reproduz, é o que interessa: a coluna volta **sem o 淀** e a caixa começa onde a do print
começa. Não é a segmentação a mudar — o texto é que perdeu o kanji.

As outras três hipóteses ficam refutadas por construção:

- **D1** só retira linhas cujo texto é kana puro; `淀` não é kana, não podia ser tomado por leitura.
- **`placeBlocks`** descarta caixas minúsculas ou sem geometria; a 1080 não veio segmento nenhum com o
  淀, não havia caixa para descartar.
- **`upscaleSmall`** só toca em recortes abaixo de 420 px; a tela inteira nunca passa por lá.

**A troca, e por que se escolheu o kanji.** O 2400 existia por uma medição anterior (ver «Registro — a
carga que vai para o Lens»): numa **página desenhada**, a leitura pequena impressa (15 px a 720×1600
contra 22 px a 1080×2400) só é reconhecida no tamanho maior. Numa página de mangá real o mesmo tamanho
maior perde um kanji grande estilizado. Não há um tamanho que dê os dois. Ficou o kanji: uma palavra
perdida não se recupera, e a leitura impressa que o maior ganhava é redundante — o **D2** escreve a
leitura a partir do dicionário, e um **recorte** (onde a leitura pequena é olhada de perto) é ampliado e
continua sendo lido. O usuário escolheu esta opção depois de ver a medição.

**Correção:** `DEFAULT_MAX_DIMENSION` volta a **1600** — uma tela de 1080×2400 viaja como 720×1600. Só o
número mudou; nenhuma outra decisão do caminho de OCR foi tocada.

**Teste de regressão:** `ocr/ImagePreparerTest`:

| teste | o que ele prende |
| --- | --- |
| `a phone screen is brought down at the default` | a tela de 1080×2400 chega ao Lens como 720×1600, e não nativa |

Conferido contra o código quebrado: **2/2 mutações detectadas** — voltar a constante para 2400 (a
regressão exata) e anular a conta da redução; cada uma falha exatamente o teste acima
(`n1_nonvacuous.ps1`).

**O que fica em aberto, e por quê:**

- O teste prende a **decisão** (o tamanho), não o comportamento do Lens — não há teste offline que prove
  o que o endpoint devolve. A prova é a medição acima, com dois pedidos reais e datada.
- Um bloco com texto mas **sem geometria** continua não sendo oferecido no «Tela inteira» (o
  `placeBlocks` o descarta). Não foi a causa aqui — o texto do 淀 nem chegou — mas é o mesmo sintoma e
  continua sem solução.

### N2 — «Perguntar à IA» não abre app nenhuma  `[x]` (19/10/2026)

Relato: com uma app escolhida (DeepSeek) ou sem app nenhuma, o botão mostra sempre «Texto copiado, mas
nenhuma app instalada aceita texto», não abre seletor nenhum, e a mensagem ainda por cima aparece
atrás da janela do app que está aberto.

Causa, que se vê no código sem precisar do aparelho: o compartilhamento é lançado **a partir do
serviço**, e um `Context` que não é uma `Activity` só pode lançar um intent com
`FLAG_ACTIVITY_NEW_TASK`. Sem a flag, o
`startActivity` lança uma exceção de runtime e o `runCatching` à volta a transforma em «não abriu».
Como nenhum dos dois candidatos (app escolhida, seletor) levava a flag, **os dois falhavam sempre** —
e é isso que explica a mensagem aparecer igual nos dois casos. O botão «Ajustes» da bolha, que sempre
funcionou, é o contra-exemplo: ele *tem* a flag.

Correção:

- `AskAi.NEW_TASK` e um único `launchable(intent)` aplicado a **tudo o que é lançado**, o seletor
  incluído — a flag no intent que o seletor embrulha não serve, porque ele viaja como extra.
- O aviso de falha passou a ser escrito **no painel** (`ResultOverlay.addNote`), logo abaixo do
  cabeçalho, em vez de um toast do serviço, que é desenhado atrás da janela do app em primeiro plano.
  O mesmo aviso pedido duas vezes é escrito uma vez.
- O toast de sucesso saiu: quem confirma que abriu é a própria app que abriu. Só a falha total — nenhum
  app do telefone aceita texto — fala.
- Texto: «nenhuma app instalada» → «nenhum app instalado» (o substantivo é masculino), e o aviso diz
  também que o texto já está na área de transferência. A nota dos ajustes acompanhou o género.

**Testes de regressão:** `ai/AskAiTest` e `overlay/AskAiPanelTest`:

| teste | o que ele prende |
| --- | --- |
| `every share can be started from the service` | a flag está no envio (com e sem app escolhida), no seletor e em cada candidato |
| `a note is written in the panel that is open` | o aviso chega ao painel pelo controlador, não a um toast |
| `the same note is written once` | pedir o mesmo aviso duas vezes não duplica a linha |

Conferido contra o código antigo: **4/4 mutações detectadas** —
tirar a flag de todos os intents, tirá-la só do seletor, remover a deduplicação do aviso e fazer o
`showResultNote` não fazer nada; cada uma falha exatamente o teste acima (`n2_nonvacuous.ps1`).
O teste do seletor é separado de propósito: a flag no intent embrulhado não chega.

Fica sem teste, e por quê: o `FloatingOcrService.askAi` em si — o `startActivity` verdadeiro
precisa de um aparelho. O que ele decide (que intents tentar) está em `AskAi`, testado; a ponte
painel→serviço (`onAskAiRequested`) é a mesma do I1.

---

### N3 — VOLTAR / tocar fora  `[x]` (19/10/2026)

Relato: «Clicar fora ou usar o botão de voltar do celular deve ser o equivalente a opção de "Trechos"
quando tiver selecionado um texto em tela cheia ou se selecionou apenas um trecho específico; ele deve
ser equivalente a opção de "Fechar"».

O que estava, e se vê no código:

- O **BACK** do painel chamava sempre `onDismiss()` — fechava. Num texto escolhido de uma captura de
  tela inteira, isso jogava fora a captura: para a ter de volta era preciso capturar outra vez, e
  outra consulta ao Lens.
- **Tocar fora não fazia nada.** A janela do painel tinha só a altura do cartão e ficava encostada ao
  topo: a área fora dele não era do painel, era da app de baixo. O gesto não existia em lugar nenhum.
- O **seletor de trechos** não era focável, portanto o BACK o atravessava e ia para a app de baixo
  (no navegador, navegava uma página).

Correção — «um passo atrás», um só lugar a decidir:

- `ResultOverlay.stepBack()`: se o painel tem os trechos atrás de si (`canRepick`, que só é verdade
  numa captura de tela inteira), BACK e o toque fora vão para «◀ Trechos»; sem nada atrás — um
  recorte — fecham o painel. `showLoading`/`showError` põem `canGoBack = false` (não há resultado).
- A janela do painel passou a cobrir a **tela inteira**; a área vazia é o gesto. O cartão engole os
  próprios toques (`isClickable`), senão um toque no cartão contava como «fora».
- O seletor de trechos passou a ser **focável** e ouve o BACK: fecha como «Cancelar», em vez de o
  BACK ir para a app de baixo.

**Testes de regressão:** `overlay/BackAndOutsideTest` (7):

| teste | o que prende |
| --- | --- |
| `back goes to the boxes when the panel came from a whole-screen capture` | BACK → «Trechos» quando há trechos atrás |
| `back closes the panel when there is nothing behind it` | BACK → «Fechar» num recorte |
| `a tap outside the card steps back` | o toque fora faz o mesmo que o BACK |
| `a tap outside the card closes the panel when there is nothing behind it` | o mesmo, sem trechos atrás |
| `a tap on the card is not a tap outside` | o cartão não se confunde com a área vazia |
| `back leaves the segment picker` | BACK no seletor = «Cancelar» |
| `the result window covers the screen` | a janela cobre a tela (sem isto, o toque fora não chega) |

Conferido contra o código quebrado: **6/6 mutações detectadas** (`n3_nonvacuous.ps1`) — BACK sempre a
fechar, BACK sempre a ir aos trechos, toque fora sem efeito, cartão sem `isClickable`, janela de volta
a `WRAP_CONTENT`, e o seletor sem o ouvinte de teclas. Cada uma falha exatamente o teste que a prende.

**O que muda para quem usa:** com o painel aberto a tela lhe pertence — a bolha deixa de ser
alcançável enquanto o resultado está à frente. O caminho passa a ser um passo atrás primeiro. É o
efeito direto do pedido: não há «fora» sem uma janela que ocupe a tela.

**Fica de fora, e por quê:** o teclado. A janela é do topo e o cartão também, por isso o teclado não
lhe passa por cima; mas isso é uma conta de janelas que só se vê num aparelho — não há teste para
isso aqui.

---

### N4 — Ordem do furigana entre parênteses  `[x]` (19/10/2026)

Relato: «Opção do furigana entre parênteses está com uma ordem estranha como pode ver no print.» No
print, o balão tem cinco colunas verticais — `ネオの職員さん` / `に聞いたら` / `非童帝くん` /
`外出したって` / `いうから…`, na ordem em que se lê, da direita para a esquerda — e cada leitura está
impressa na sarjeta à direita dos kanji que ela lê. Com o ajuste ligado, o painel escreveu:

```
ネオ（き）の職員さ（しょくいん）んに聞い（ヒドー）たら非童（がいしゅつ）帝くん外出したっていうから…
```

Três leituras foram parar na **coluna anterior da frase** (a que fica à direita do balão) e a quarta
ficou na coluna certa, mas um caractere depois. Não é texto perdido — é texto errado, e são dois
defeitos somados. O certo é o que o mangá escreve:

```
ネオの職員（しょくいん）さんに聞（き）いたら非童帝（ヒドー）くん外出（がいしゅつ）したっていうから…
```

**Defeito 1 — a coluna vizinha serve tanto quanto a certa.** O rubi é impresso na sarjeta entre a sua
coluna e a anterior, então uma leitura tem candidatas dos **dois lados**: as duas são finas o bastante,
as duas a encostam, as duas a cobrem por inteiro. O desempate era «a primeira que serve» — isto é, a
coluna anterior, que já veio antes na frase. Daí `き` (leitura de 聞, na coluna 2) sair depois de `ネオ`,
na coluna 1: `ネオ（き）の`.

**Defeito 2 — o fim do intervalo arredondava para cima.** O rubi é composto ajustado aos kanji que lê,
mas sobra sempre um fio além deles; com `ceil`, qualquer fio contava como o caractere seguinte. O
`しょくいん` (cinco kana ajustadas aos dois kanji de 職員) passava um fio do fim de 員 e a leitura saía
depois de さ: `職員さ（しょくいん）ん`.

Correção, toda em `core/lens/RubyGeometry.kt`:

- A escolha da coluna passou a ser **lado, depois cobertura, depois distância**. O lado é o lado em que
  o rubi é impresso — **à direita de uma coluna, acima de uma linha** —, e é a única regra que separa «a
  coluna que esta leitura lê» de «a coluna anterior da frase». Continua sendo **preferência, não
  filtro**: rubi impresso do outro lado segue sendo lido como antes, em vez de voltar colado na frase.
- O **fim** do intervalo passou a arredondar para o mais próximo, e o **início** continua arredondando
  para baixo: perder o caractere onde a leitura começa grudaria a leitura no kanji errado — o oposto do
  defeito 2.

**Testes de regressão:** `core/lens/FuriganaTest` (3 novos, 14 no total):

| teste | o que prende |
| --- | --- |
| `a reading in a gutter belongs to the column it touches` | o balão do print inteiro: as quatro leituras depois dos caracteres certos |
| `a reading a hair longer than its characters keeps them` | o fio de rubi não vira um caractere (`職員（しょくいん）さん`) |
| `a reading printed on the other side is still a reading` | o lado é preferência: rubi do outro lado continua sendo lido |

Conferido contra o código quebrado: **3/3 mutações detectadas** (`n4_nonvacuous.ps1`) — voltar a escolher
a primeira coluna que serve, voltar a arredondar o fim para cima, e transformar o lado do rubi em filtro.

**Fica de fora, e por quê:** as caixas daquela página. O app não guarda a resposta do Lens e o print
mostra só o painel, então os testes usam as **proporções daquele balão** (colunas de cinco a sete
caracteres, a um glifo uma da outra, leituras com um terço da espessura), na escala de uma captura. O
que dá para conferir — e foi conferido — é que, com a regra antiga, essas caixas reproduzem a saída do
print **caractere por caractere** (inclusive `ネオ（き）の職員さ（しょくいん）んに聞い（ヒドー）たら非童
（がいしゅつ）帝くん`); o que os testes prendem é a regra, não os pixels exatos daquela página.

---

### N5 — Furigana na página toda  `[x]` (19/10/2026)

Relato: «Se selecionar a página toda e a opção de mostrar o furigana estiver habilitada, ele deve mostrar
o furigana ao lado (ou acima, dependendo de como está a orientação do texto) sem ser necessário clicar em
uma parte do texto.»

O que estava, e se vê no código: a leitura que o dicionário sabe (D2) era escrita num **bloco «Furigana»
próprio**, acima da lista de palavras. A leitura chegava, mas longe dos caracteres que a explicam: quem
lia a página tinha de ir e voltar entre o texto reconhecido e o bloco.

Correção: a leitura passou a ser escrita **no próprio texto reconhecido**, ao lado de cada kanji, e o
bloco deixou de existir — é a opção escolhida pelo usuário entre as três apresentadas (abrir o painel com
a página toda; mudar só quando o furigana está ligado; escrever a leitura no texto). O **fluxo de captura
não muda**: com «Escolher o trecho da tela» ligado, a tela inteira continua indo primeiro ao seletor de
trechos.

- `ResultOverlay.applyReading`: escreve `WordsState.Done.furigana` no `originalEditor`; sem nada a
  escrever, repõe o texto como veio (`cleanText`).
- `writingText`: um `TextWatcher` não distingue a escrita do painel da do usuário. A escrita da leitura é
  marcada, então não conta como edição nem compra uma consulta de um texto que o painel acabou de derivar
  da resposta que já tem.
- `userEdited`: depois da primeira tecla o texto é do usuário — uma consulta que chega depois não
  sobrescreve a correção com a leitura da frase antiga.
- `lastShownText` (era `lastAnalysedText`): o `analyseSoon` passou a comparar contra o que o painel
  escreveu por último, que pode já ser o texto com a leitura.
- A **nota do `?`** (palpite do KANJIDIC) subiu para o topo da lista de palavras, onde o bloco estava.
- `WordsState.Hidden`/`Failed` repõem o texto: sem resposta não há leitura para fixar.
- A seção «Furigana» e a string `result_furigana_label` saíram.

O dicionário e a tradução continuam recebendo o **texto limpo**: a leitura entra *depois* da consulta, e o
serviço analisa o texto que a captura produziu. Só uma edição manual muda isso — e aí o texto é do
usuário. É a decisão do D2 («o texto reconhecido não é tocado») revista por este item.

**Testes de regressão:** `app/overlay/FuriganaPanelTest` (7, reescritos dos 5 do bloco):

| teste | o que prende |
| --- | --- |
| `the reading of the sentence is written into the recognised text` | a leitura vai para o texto, e não há seção «Furigana» |
| `the reading of the whole page is written into the text` | o N5: a página toda, coluna a coluna |
| `nothing to read puts the recognised text back` | uma consulta sem leitura não deixa leitura velha |
| `a guessed reading is called out under the sentence` | a nota do `?` aparece só quando há palpite |
| `an edit is not overwritten by a later lookup` | a correção do usuário manda |
| `a new reading replaces the previous one` | a escrita do painel não conta como edição |
| `writing the reading does not ask for another lookup` | escrever a leitura não dispara consulta |

Conferido contra o código quebrado: **5/5 mutações detectadas** (`n5_nonvacuous.ps1`) — tirar a marca da
escrita do painel, tirar a proteção da edição, não escrever a leitura, não repor o texto quando não há
leitura, e calar a nota do palpite.

**Fica de fora, e por quê:** o passo do seletor de trechos. Com «Escolher o trecho da tela» ligado, a
leitura só aparece depois de escolher um trecho ou «Texto todo» — quem não quer esse passo desliga o
ajuste. A opção escolhida foi escrever a leitura no texto, não mudar o fluxo.

---

### N6 — A bolha é puxada para o ✕  `[x]` (19/10/2026)

Relato: «Ao arrastar para o botão "x" para fechar, quando estiver próximo do botão, ele deve ser
"puxado" para o "x".»

O que estava, e se vê no código: a bolha seguia o dedo **exatamente** até o ✕; só no fim, se o centro
caísse dentro do alvo, o ✕ ficava vermelho e o soltar perguntava. Ou seja: o aviso de que soltar ali
encerra chegava *depois* de o dedo ter de acertar o alvo por conta própria.

Correção: enquanto o dedo está a menos de um `closePullRadius` (o dobro do ✕) do centro do alvo, a
bolha é desenhada **puxada para o ✕**, por mais quanto mais perto — nada na borda do alcance, tudo em
cima do alvo. E como o soltar é julgado sobre a posição *desenhada*, o que se vê chegar é o que acontece
ao largar: o ✕ fica vermelho quando a bolha puxada está sobre ele, e soltar pergunta.

- `OverlayController.pullTowardsCloseTarget`: a posição desenhada para um dedo em (x, y). A duas vezes o
  tamanho do alvo não faz nada; em cima do alvo, centra a bolha nele. O puxão é quadrático
  (`closeness²`), para continuar sendo uma dica até perto do alvo em vez de arrancar a bolha do dedo
  cedo.
- O `ACTION_MOVE` desenha o resultado do puxão; o `endDrag` continua julgando a posição que recebe — que
  agora é a desenhada.
- O vermelho do ✕ acende um pouco antes: é o mesmo teste, sobre a posição puxada.

**Testes de regressão:** `app/overlay/DragToCloseTest` (5 novos, 12 no total):

| teste | o que prende |
| --- | --- |
| `far from the cross the bubble follows the finger exactly` | fora do alcance nada muda — nem na borda dele |
| `near the cross the bubble is drawn pulled towards it` | no meio do alcance, um quarto de aproximação (puxão quadrático) |
| `right on the cross the bubble is drawn centred on it` | em cima do alvo, a bolha fica centrada nele |
| `the pull never pushes the bubble away from the cross` | o puxão aproxima sempre, do mais longe para o mais perto |
| `dragging near the cross draws the bubble onto it and the release asks` | o gesto inteiro, pelo listener de toque: dedo perto, bolha puxada, soltar pergunta |

O último é o único que passa pelo `onBubbleTouch` de verdade (o `MotionEvent` vai para o `View` da
bolha), e é ele que prende o puxão **chegar à tela** — não só existir.

Conferido contra o código quebrado: **5/5 mutações detectadas** (`n6_nonvacuous.ps1`) — desligar o
puxão, o handler voltar a usar a posição do dedo, o puxão empurrar para o lado contrário, puxão linear
no lugar do quadrático, e puxão que passa do alvo.

**Fica de fora, e por quê:** animação. O puxão só existe enquanto o dedo está em baixo; solto longe do
✕, a bolha volta à borda como sempre (`snapToEdge`), e solto no ✕, quem aparece é a pergunta. Nada voa.

---

### Português do Brasil  `[x]` (19/10/2026)

Pedido do usuário: os textos do projeto estavam em português de Portugal e deviam passar a português do
Brasil. Nada de comportamento mudou — é só linguagem —, mas o que mudou fica listado para não se perder
o rastro:

- **Vocabulário:** ecrã → tela, ficheiro → arquivo, miúda → pequena, sítio → lugar, registo → registro,
  por omissão → por padrão, tecto → teto, apanhadas → detectadas, partilhar → compartilhar,
  actividades → atividades, artefacto → artefato.
- **Ortografia:** exactamente → exatamente, secção → seção, acções → ações, correcções → correções,
  directo → direto, projecto → projeto, actualização → atualização, excepção → exceção.
- **Sintaxe:** a ênclise saiu («vê-se» → «se vê», «sabe-se» → «se sabe», «descarta-o» → «o descarta») e
  as perífrases passaram a gerúndio («continua a ser lido» → «continua sendo lido»).
- **Ficaram como estavam:** as aspas angulares «», que são tipografia e não vocabulário, e as palavras
  que valem nos dois países («aparelho», «encostada», «Fechar», «Cancelar»). O `strings.xml` já era
  português do Brasil — só uma frase precisou de ajuste («letras miúdas» → «letras pequenas»).
- **Sem teste:** conversão de texto não tem comportamento para prender. A rede de segurança é o
  `app:testDebugUnitTest` continuar verde e o APK continuar a compilar.

---

## 7. Terceira ronda — o que o aparelho mostrou  (20/10/2026)

Dez pontos: os sete relatados depois de a ronda 6 ser testada no aparelho, o R8 que apareceu ao
testar esta ronda e os R9/R10 pedidos enquanto ela corria. Mesma ordem de sempre: **bugs primeiro**,
do que estraga mais para o que estraga menos, e só depois as melhorias.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R1 | `[x]` | **Ordem do texto vertical sai trocada** (`肉塊に潰されてる` → `てる潰され内塊に`) | bug (core) |
| R2 | `[x]` | **A leitura não aparece sobre a página** (na tela de trechos, sem tocar em nada) | função (picker) |
| R3 | `[x]` | **«Perguntar sempre» não abre** o app escolhido: a lista agora é do app | bug |
| R4 | `[x]` | O **puxão para o ✕** mal se sente; tem de ser como no Messenger | bug (fácil) |
| R5 | `[x]` | O exemplo dos ajustes mostra só `聞（き）` e não `職員（しょくいん）` | detalhe visual |
| R6 | `[x]` | **Botões do painel desorganizados**, com linhas de larguras diferentes | melhoria |
| R7 | `[x]` | **Captura em guia anônima**: verificar se é possível e, se for, deixar como opção | investigação |
| R8 | `[x]` | **Texto de uma coluna só não recebe caixa** (`やばい!!`, `わかった`, `なにぃ!!`) | bug (fácil) |
| R9 | `[x]` | **HOME e apps recentes** devem fechar o painel da tradução | função (janela) |
| R10 | `[x]` | **«Perguntar à IA» deve fechar** o painel antes de abrir o app | bug (fácil) |
| R11 | `[x]` | **A lista de apps mostra Gmail, Bluetooth, «Converter para JPEG»**: esconder o que não é IA nem tradutor | melhoria |

### R1 — A ordem do texto vertical  `[x]` (20/10/2026)

Relato, com dois exemplos do aparelho:

| o que está na página | o que o app reconheceu |
| --- | --- |
| `ネオの職員さんに聞いたら非童帝くん外出たっていうから` | `ネオの職員さんに聞いたら非童帝くんいうから外出たって` |
| `肉塊に潰されてる!!` | `てる潰され内塊に` |

Não é o Lens a ler mal as letras: as duas frases têm as mesmas letras, na mesma quantidade — o que está
trocado é a **ordem dos pedaços**. No segundo exemplo até o 肉 virou 内 (leitura errada mesmo), mas o
`てる!` que abre a frase é o fim de `潰されてる`, e `内塊に` é a coluna da direita.

**Causa, no código.** `LensResultBuilder.groupIntoBlocks` agrupa as linhas de um balão pela geometria
(`merge`), mas depois as junta na **ordem em que o Lens as devolveu**:

```kotlin
val sentence = group.filterNot { ... }.joinToString(" ") { withReadings(...) }
```

O Lens devolve as colunas de um texto vertical na ordem que lhe apetece: no primeiro balão chegam
`ネオの職員さん` (direita), `非童帝くん` (meio) e só depois `に聞いたら` — é a ordem 1, 3, 2 que o print
mostra. E no segundo, a coluna `潰されてる` foi partida em `潰され` e `てる!`, que chegam ao contrário.
Quem sabe a ordem de leitura são os **retângulos** das linhas: o texto, não.

**Correção.** Ordenar cada grupo pela geometria antes de juntar:

- **texto vertical** (linhas mais altas do que largas): colunas da direita para a esquerda; dentro de
  uma coluna, de cima para baixo.
- **texto horizontal**: linhas de cima para baixo; dentro de uma linha, da esquerda para a direita.

Duas linhas contam como a mesma coluna quando as faixas de x se sobrepõem — é isso que mantém
`潰され` e `てる!` juntas e na ordem certa, mesmo com o Lens a dar-lhes x (ou y) diferente por um pixel.
Um grupo com alguma linha sem geometria fica como veio: não há como ordenar metade dele.

O que **não** muda: que linhas formam um balão, a caixa do trecho, o rubi (D1/D2) e a lista de linhas
cruas. Só a ordem de leitura dentro do balão.

**Testes de regressão:** `core/lens/LensProtoParsingTest` (3 novos, 13 no total):

| teste | o que prende |
| --- | --- |
| `puts the columns of a vertical balloon in reading order` | colunas entregues fora de ordem (meio, direita, esquerda) voltam na ordem de leitura |
| `puts the two halves of a split column back in order` | a coluna partida em `潰され` / `てる!` volta de cima para baixo, mesmo com 1 px de diferença no x e a metade de baixo chegando primeiro |
| `puts the rows of a horizontal paragraph in reading order` | linhas horizontais entregues de baixo para cima voltam de cima para baixo |

Conferido contra o código quebrado: **5/5 mutações detectadas** (`r1_nonvacuous.ps1`) — não ordenar
nada, ler as colunas da esquerda para a direita, ler a coluna de baixo para cima, tratar tudo como
horizontal, e ordenar por x/y sem juntar as metades da coluna. Todas derrubam pelo menos um dos testes
novos; a de «da esquerda para a direita» e a de «tudo horizontal» derrubam também o teste antigo do
balão de três colunas, que passou a depender da ordem geométrica.

**O que fica de fora, e por quê:** a ordem **entre** balões. Numa página de mangá os balões não estão
numa grade — a ordem deles é a que o desenhista quis —, e o texto da tela toda continua juntando os
balões na ordem em que o Lens os devolve. O que se corrigiu é a frase dentro de cada balão, que é onde a
ordem é objetiva: colunas da direita para a esquerda, de cima para baixo.

### R2 — A leitura sobre a página  `[x]` (20/10/2026)

Relato: «não aparece furigana ao lado dos kanji identificados, mesmo com a função do furigana e de
mostrar o modo de leitura ativado», com dois prints **da tela de trechos** — a tela em que as caixas
aparecem sobre a página, antes de tocar em qualquer coisa. Pedido, desde o N5: ver a leitura **ao lado
do kanji** — à direita quando o texto é vertical, acima quando é horizontal —, sem ter de tocar num
trecho.

**O que estava errado.** O N5 escreveu as leituras **dentro do texto reconhecido**, e o painel faz
isso até hoje. Mas a tela de trechos desenha a página por cima com caixas e nada mais: nenhuma leitura
era desenhada ali. Não era o dicionário a falhar — era uma função que não existia naquela tela. (Os
prints antigos com uma seção «Furigana» são de antes do N5, quando a seção existia.)

**Correção.** Um ajuste novo, «Leitura sobre a página, na tela de trechos», com três escolhas — as
duas formas ficam disponíveis para o usuário avaliar no aparelho e dizer qual presta:

| modo | o que faz | o que se ganha e o que se perde |
| --- | --- | --- |
| **Rubi** | desenha a leitura ao lado do kanji: coluna de kana à direita de uma coluna vertical, linha pequena acima de uma horizontal, com contorno escuro | é o que se pediu e mantém o desenho visível; fica do tamanho do rubi impresso, minúsculo numa página inteira |
| **Tarja** | uma etiqueta ao lado do balão com a frase e as leituras entre parênteses (`職員（しょくいん）さんに…`) | legível de verdade, mas cobre parte do desenho |
| **Não mostrar** | como antes | é o padrão; a função é uma escolha, não uma surpresa |

**De onde vêm as posições.** O rubi precisa de saber **onde** cada leitura vai, e o `Furigana` só sabia
escrever a frase anotada. Duas peças novas:

1. `Furigana.spans` (exposto como `LookupEngine.readings`) devolve a mesma leitura com os caracteres
   que ela cobre, em vez de uma frase montada. `sentence` — o que o painel escreve — passou a ser
   montado a partir dela: uma regra só, dois usos, sem risco de dizerem coisas diferentes.
2. `LensBlock.pieces`: as colunas/linhas do balão **com as suas caixas**, em ordem de leitura e sem as
   leituras que a página imprime (essas não são texto). São elas que dizem em que faixa da coluna cada
   kanji está. `OcrBlock` leva as mesmas peças para o app, tanto pelo Lens como pelo ML Kit (que já
   dava a caixa de cada linha).

O `RubyLayout` converte as frações em pixels: à direita da coluna, empilhado, com o tamanho tirado da
espessura do glifo e nunca maior do que a coluna aguenta — cinco kana ao lado de dois kanji são o caso
real, e a leitura encolhe para caber em vez de descer pela coluna inteira; perto do fim, sobe para não
vazar. O `LabelLayout` escolhe o lado da tarja: direita, esquerda, abaixo, acima — o primeiro que
couber na tela — e nunca a deixa sair da tela.

Custo: uma consulta ao dicionário por coluna, em segundo plano, **depois** do OCR e antes de mostrar
as caixas. Se o dicionário não abrir, as caixas aparecem do mesmo jeito, só sem leitura: perder o
seletor de trechos por causa de uma leitura seria a troca errada.

**Testes de regressão:**

| teste | o que prende |
| --- | --- |
| `app/overlay/PickerReadingLayoutTest` (10) | rubi vertical à direita e empilhado; rubi horizontal acima e centrado; leitura de cinco kana cabe na coluna; leitura no fim sobe; coluna fina demais não ganha rubi; tarja à direita, à esquerda, abaixo e dentro da tela |
| `core/dict/FuriganaTest` (+4, 25 no total) | cada leitura vem com os caracteres que cobre; leitura adivinhada marcada; leitura que a página já imprime não volta; frase sem kanji não tem leitura |
| `core/lens/LensProtoParsingTest` (+1, 14 no total) | a leitura impressa fica nas linhas cruas mas **não** é peça do balão |

Conferido contra o código quebrado: **6/6 mutações detectadas** (`r2_nonvacuous.ps1`) — voltar à
regra antiga de tamanho mínimo, não empilhar o rubi, desenhar o rubi do lado errado, ignorar o espaço à
direita da tarja, redesenhar a leitura que a página já imprime, e tratar a leitura impressa como peça.

**O que fica de fora, e por quê:** o rubi é tão pequeno quanto o rubi impresso, porque a página é
mostrada inteira; quem quiser ler de perto usa a tarja, o painel ou o zoom do sistema. E o rubi só
aparece para o que o dicionário conhece — um kanji sem palavra e sem entrada de kanji fica sem
leitura, como no painel.

### R3 — «Perguntar sempre» não abre o app  `[x]` (20/10/2026, refeito)

Relato: com «App que responde» = **Perguntar sempre**, escolher uma IA no seletor do sistema não abre
nada; com um app fixo, abre normalmente.

**Primeira tentativa (não resolveu).** A suspeita foi a bandeira `FLAG_ACTIVITY_NEW_TASK` no
`ACTION_SEND` que o seletor embrulha: com ela, o app escolhido nasceria numa tarefa própria, atrás de
tudo. A bandeira saiu do embrulho e ficou só no seletor. Testado no aparelho: **mesmo sintoma**, agora
descrito passo a passo — o seletor abre, o app é escolhido, o seletor fecha, o painel reaparece, nada
acontece. Ou seja: a bandeira não era a causa (as duas variantes falham igual).

**Causa real.** Não é a bandeira, é **quem** inicia o app. Com um app fixo, quem chama o
`startActivity` é este app; com o seletor, quem chama é o seletor, de dentro da tarefa dele. No
aparelho em que isto foi testado esse segundo caminho não traz o app escolhido para a frente — o
sistema volta para a tarefa anterior, que é o leitor de mangá com o painel flutuante por cima. Daí a
tela «não mudar».

**Correção.** O app deixou de depender do seletor do sistema: ele **desenha a própria lista** de apps
que aceitam texto e, no toque, abre o app pelo nome do pacote — o mesmo `startActivity` do app fixo,
que sempre funcionou. A lista sai de `AskAi.targets`, a mesma que os ajustes já usam. `AskAi.plan`
decide o que um toque faz (`Direct`, `Choose`, `Nothing`), incluindo o caso do app lembrado que foi
desinstalado: ele não é aberto, o nome é esquecido e a lista toma o lugar. `AskAi.attempt` percorre os
candidatos e só avisa que abriu quando abriu.

**Segunda correção, depois do teste no aparelho.** A lista nasceu como uma fila de chips no rodapé do
painel e o resultado foi o previsto para uma fila longa: cortada pelo fim da tela, com os apps de baixo
inalcançáveis. Passou a ser uma **janela própria** (`AppPickerOverlay`): cartão no rodapé sobre um véu
escuro, cada app com **ícone e nome**, lista que rola acima de 60 % da tela, e três saídas — ✕, toque
fora do cartão e BACK — que fecham **só** a lista. O painel da tradução fica intacto atrás.

**Testes de regressão:** `AskAiTest` — app fixo instalado abre direto; sem escolha a lista é
oferecida; app desinstalado cai na lista; telefone sem nenhum app diz isso; o painel só é fechado
depois de um app abrir; a linha de tentativas para no primeiro que abre; tudo o que o serviço inicia
carrega `NEW_TASK`; o compartilhamento cru não carrega. `AppPickerTest` — um app por linha com nome e
ícone, ordem preservada, o toque numa linha devolve o pacote, ✕/fora/BACK fecham, e uma lista de 30
apps rola dentro de um cartão que não passa de 3/4 da tela. `AskAiPanelTest` — a escolha chega ao
serviço pelo controlador com o texto que estava na tela, e a lista sai do ar quando a escolha é feita.
Non-vacuidade **5/5** no seletor (`%TEMP%\opencode\r3c_nonvacuous.ps1`: sem ícone, sem limite de
altura, linha sem toque, toque fora sem efeito, BACK sem efeito) e **5/5** na decisão
(`%TEMP%\opencode\r3b_nonvacuous.ps1`).

`sendIntent` (o compartilhamento cru) continua sem bandeira e `directIntent` continua com ela; o
`chooserIntent` e o `candidates` do seletor foram removidos — não há mais caminho que passe pelo
seletor do sistema.

### R4 — O puxão para o ✕  `[x]` (20/10/2026)

Relato: «arrastar para perto do ✕ não está puxando; o correto é como no Messenger».

O puxão existia (N6) e a geometria estava certa, mas o formato era tímido: alcance de 2× o ✕ (128 dp)
e curva quadrática, ou seja, a 100 dp do ✕ a bolha andava **5 %** do que o dedo andou e a 60 dp, 28 %.
Na prática ele só se sentia quando a bolha já estava quase em cima do ✕ — o contrário do Messenger,
que **suga** a bolha assim que ela entra no raio.

**Correção.** Três coisas, todas no desenho do arrasto (`OverlayController`):

- o alcance passou a **3× o ✕** (192 dp): o puxão começa onde o dedo ainda está viajando, e não só no
  último trecho (a 130 dp do ✕, onde o arrasto costumava passar sem nada acontecer, já há puxão);
- a curva passou de `c²` para **smoothstep** (`3c² − 2c³`): a 70 % do alcance o puxão é **22 %** do
  caminho (era 9 %), e na metade do alcance é metade do caminho (era um quarto) — o puxão começa a
  responder enquanto o dedo se move;
- com o dedo **no** ✕ a bolha é desenhada exatamente no centro dele, não a 97 % do caminho: o
  soltar é julgado pelo que está desenhado, e ficar dois pixels curto deixaria o ✕ apagado depois do
  gesto que claramente queria acendê-lo.

E, o que mais se vê: a bolha **encolhe** enquanto é sugada (até 70 % do tamanho no ✕) e volta ao
tamanho normal ao soltar ou ao cancelar o gesto — sem isso, o puxão de poucos dp no fim lê como um
tremor, não como a bolha sendo engolida.

**Testes de regressão:** `DragToCloseTest` — a metade do alcance o puxão é metade (era um quarto); a
70 % do alcance a bolha já foi puxada mais de 15 %; a 130 dp do ✕ ainda há puxão (o alcance largo); o
dedo no ✕ desenha a bolha no centro exato; o puxão é 0 na borda, 1 no ✕ e nunca anda para trás; e,
pelo listener de toque de verdade, a bolha encolhe durante o arrasto e volta a 1,0 ao soltar.
Non-vacuidade **5/5** (`%TEMP%\opencode\r4_nonvacuous.ps1`: curva quadrática de volta, sem o encaixe
no ✕, alcance de volta a 2×, sem encolhimento, encolhimento não desfeito ao soltar).

### R5 — O exemplo dos ajustes  `[x]` (20/10/2026)

O texto do ajuste era `Mostrar a leitura dos kanji — 職員さんに聞（き）いた`: anotava só o 聞 e deixava o
職員 sem leitura, ao contrário do que o app faz — e ao contrário do ajuste logo acima, que mostra
`職員（しょくいん）`. Num celular o exemplo **é** a explicação do ajuste; um exemplo que contradiz o
comportamento ensina o contrário.

**Correção.** `Mostrar a leitura dos kanji — 職員（しょくいん）さんに聞（き）いた`.

**Rótulos, depois do teste no aparelho.** A pergunta «qual a diferença entre os dois ajustes?» é a
resposta: os rótulos não diziam de **onde vinha** cada leitura, e os dois exibiam `職員（しょくいん）`.
São coisas diferentes — `furiganaInParentheses` decide o que o app faz com a leitura que a **página
imprime** (ela entra no texto reconhecido, e por isso muda busca e tradução; desligado por padrão),
enquanto `showFurigana` escreve no painel a leitura que **o dicionário** conhece (não muda a frase
usada na busca; ligado por padrão) — mas quem lê não tem como saber. Passaram a dizer:

- **«Manter o furigana que a página imprime — 職員（しょくいん）»**;
- **«Escrever no painel a leitura do dicionário — 職員（しょくいん）さんに聞（き）いた»**.

**Testes de regressão:** `SettingExampleTest` (3) lê o recurso de verdade e trata o exemplo como uma
regra, não como uma cópia: **toda** palavra escrita em kanji na frase do exemplo tem de estar seguida
da leitura entre parênteses, e a leitura tem de ser kana — o teste falha se o exemplo voltar ao antigo
(`職員` sem leitura), se o 聞 também perder a dele, se a frase for trocada por uma sem kanji (onde «toda
palavra tem leitura» seria verdade de nada) e se a leitura for escrita em romaji. O segundo teste
amarra os dois ajustes de furigana à **mesma** frase: `職員（しょくいん）` tem de continuar sendo um
pedaço de `職員（しょくいん）さんに聞（き）いた`, para os dois exemplos não divergirem. O terceiro cobra
a origem: o rótulo do impresso diz «página», o do painel diz «dicionário» e **só** o primeiro fala em
página, para os dois não voltarem a ler como sinônimos. Non-vacuidade **5/5**
(`%TEMP%\opencode\r5_nonvacuous.ps1`: exemplo antigo, sem nenhuma anotação, frase sem kanji, leitura em
romaji, exemplos divergentes) e **3/3** nos rótulos (`%TEMP%\opencode\r5b_nonvacuous.ps1`: primeiro sem
«página», segundo sem «dicionário», segundo falando em página).

### R6 — Os botões do painel  `[x]` (20/10/2026)

No print, as sete ações do painel caíam em quatro linhas de larguras diferentes (3 / 1 / 2 / 1),
alinhadas à direita, e «Copiar tradução» ficava sozinha embaixo. Não se sobrepunham de verdade — o
`FlowLayout` media e posicionava bem —, mas o bloco lia como monte.

Agora é uma grade de duas colunas de largura igual (`ActionGrid`), com 6 dp entre colunas e entre
linhas, preenchida da esquerda para a direita e de cima para baixo. A ordem foi refeita pela
importância: primeiro o que sai do painel — «Copiar original», «Copiar tradução», «Perguntar à IA»,
«Fechar» —, depois o que muda a captura (imagem, escolher trecho, tentar offline). Um botão que fica
sozinho na última linha ocupa a linha inteira: meio botão com um buraco ao lado parece botão que não
carregou, e o vão à direita é o resto do bloco, não um erro.

A grade expôs um limite que já existia. Com a imagem ligada, o painel estourava a tela: o
`freeHeightForScroller` já media os vizinhos para dar à seção rolável o que sobrava, mas a imagem tinha
teto fixo de 200 dp e ficava fora dessa conta — numa tela de 640 dp, com a imagem no teto e a linha a
mais de botões, o chrome passava do orçamento e a última linha caía abaixo da borda. A imagem passa a
pedir o próprio teto pela mesma regra (`freeHeightForImage`, por um `CappedImageView`): os 200 dp viram
teto máximo e ela encolhe quando o resto não cabe. É a única parte do cartão que pode ceder sem deixar
nada inalcançável.

Testes novos em `ActionGridTest` (7): duas por linha na ordem em que foram adicionadas, colunas de
largura igual, rótulo longo não empurra a coluna vizinha, último ímpar ocupa a linha, ação sozinha
ocupa a linha, menos ações refluem e as colunas têm vão entre si. A ordem e o encaixe no painel ficam
em `ResultOverlayLayoutTest` — a lista de ações na ordem em que o usuário as vê, e nenhum par
sobreposto a 160 px (o teste antigo cobrava que o `FlowLayout` quebrasse linha, o que deixou de ser
verdade) — e em `ImagePanelTest` (a captura alta não empurra o painel para fora, a imagem não some no
aperto e numa tela alta ela mantém os 200 dp). Non-vacuidade **6/6**
(`%TEMP%\opencode\r6_nonvacuous.ps1`: três colunas, sem vão, primeira coluna com o dobro da largura,
cópias fora do começo da grade, teto fixo da imagem, imagem sem espaço nenhum).

### R7 — Guia anônima  `[x]` (20/10/2026)

Pedido: «verificar se é possível dar permissão de visualização em guia anônima do Google; se for,
adicionar uma função, deixando claro que não é necessário».

Investigação, sem código novo. No Android não existe permissão para «ver» uma guia anônima. O que
existe é o `FLAG_SECURE`: quem esconde o conteúdo das capturas é **o app exibido**, e a
`MediaProjection` devolve aquela janela em branco. Nenhuma permissão de usuário contorna isso — o
sinalizador é decisão de quem é filmado, não de quem filma. A outra via do sistema é um **serviço de
acessibilidade**, que lê a árvore de textos da janela em vez de fotografá-la: é outra arquitetura,
exige uma permissão bem mais invasiva e não resolve o caso deste app — texto desenhado dentro de uma
imagem (mangá, scan, print) não existe na árvore de textos.

Fechado como «não é possível», sem botão e sem ajuste: a explicação foi para o README («Guia anônima e
telas que se escondem», no fim de **Precisão**), para o sintoma — `Não foi possível reconhecer o
texto.` com a página certa na frente — não parecer defeito. Sem teste novo: não houve código.

### R8 — O texto de uma coluna só não recebe caixa  `[x]` (20/10/2026)

Relato, com a página do Dandadan: faltam as caixas sobre `やばい!!`, `わかった`, `嫌じゃ!!` e
`なにぃ!!` — e sobre o `てる` de `潰されてる`. São sempre textos de **uma coluna**.

**Causa.** `FloatingOcrService.placeBlocks` jogava fora qualquer caixa com **menos de 12 dp nos dois
sentidos**, com o comentário «uma linha de texto tem uns 16 dp de altura». Numa coluna vertical a
espessura é a de **um glifo** — 10 a 11 dp num telefone 1080p —, por mais comprida que a frase seja.
Um balão de três colunas passava (44 px de largura na captura); o mesmo texto numa coluna só, não. É
exatamente a lista do relato.

Reproduzido com a página do print: o Lens devolveu `やばい!!` como 20×92 px numa página de 700 px de
largura, que no aparelho são 30×138 px. A caixa existia; quem a apagava era o filtro.

**Correção.** `PickerTargets.offered`: a caixa é oferecida quando o **lado maior** é um alvo
confortável (12 dp) e o **menor** chega à metade disso (6 dp) — e o seletor já infla toda caixa em
4 dp de cada lado ao testar o toque, o que leva uma coluna de 11 dp a 19 dp debaixo do dedo. Um risco
de 10 px continua de fora.

**O que não se corrige aqui:** o `てる` que o Lens **não devolveu** naquela captura. Na reprodução com
a mesma página vieram 17 segmentos e nenhum deles era o `てる`; noutra captura o `てる` veio junto
com `潰され`, num bloco só. Quando o reconhecedor não devolve a coluna, não há caixa a desenhar — isso
é precisão do OCR (N1), não este filtro. O que este item garante é que, **quando o Lens devolve uma
coluna, ela sempre vira caixa**.

**Testes de regressão:** `app/overlay/PickerTargetsTest` (6): coluna de 30×138, coluna de um caractere
(30×34), linha horizontal (240×44), pontinho (10×10), risco comprido e fino (240×10) e lado zero.
Voltar à regra dos dois lados derruba 2 testes (`r2_nonvacuous.ps1`).

### R9 — HOME e o botão de apps recentes  `[x]` (20/10/2026)

Pedido: apertar **HOME** ou o botão que mostra os apps abertos (o «quadrado» do Android, ou o gesto
equivalente) deve **fechar o painel da tradução**.

Por que hoje não fechava: o painel não é uma tela do app, é uma janela flutuante
(`TYPE_APPLICATION_OVERLAY`) desenhada por cima de tudo e criada pelo serviço. O app de baixo (o
leitor de mangá) vai para segundo plano quando o HOME é apertado, mas a janela flutuante **continua
lá** — ela não pertence a tarefa nenhuma.

Quem fecha é o **foco da janela** (`ResultOverlay.onWindowLost`, chamado de `onWindowFocusChanged`): o
launcher e a tela de recentes são janelas do sistema que tomam o foco, e o painel é focável (tem o
campo de texto). Sem permissão nova e sem `AccessibilityService`.

Três cuidados, cada um com o seu teste em `FocusLossTest`:

- **Ganhar** foco não fecha nada — só a perda interessa (`gaining the window focus leaves the panel
  alone`). Fechar nos dois sentidos tornaria o painel impossível de usar.
- Perder o foco para a **lista de apps** do «Perguntar à IA» não conta: aquela janela é deste app,
  desenhada por cima do painel de propósito, e fechá-la tem de voltar ao painel. O controlador só
  fecha quando não há lista aberta (`the app list taking the focus does not take the panel with it`).
- Tirar a janela do painel **também tira o foco**: o `hideResult` desarma o ouvinte antes de remover, e
  a perda de foco que vem depois disso não pode pedir uma segunda dispensa ao serviço (`taking the
  panel down does not dismiss it a second time`). Sem isso a análise seria cancelada duas vezes e o
  aviso de «painel fechado» chegaria repetido.

O fechamento passa todo pelo mesmo ponto (`dismissResult`: some o painel, depois avisa o serviço), para
o botão «Fechar», o ✕ do cabeçalho e a perda de foco não divergirem. A captura não precisa de guarda:
`hideAllForCapture` já tira o painel antes de fotografar a tela, e o painel do resultado seguinte é
outra janela.

Non-vacuidade **5/5** (`%TEMP%\opencode\r9_nonvacuous.ps1`: ignorar a perda de foco, fechar mesmo com
a lista aberta, deixar o ouvinte vivo depois da remoção, fechar também ao ganhar foco, não avisar o
serviço).

### R10 — «Perguntar à IA» deve fechar o painel  `[x]` (20/10/2026)

Pedido: ao tocar em **«Perguntar à IA»**, o painel da tradução deve fechar.

Hoje o texto é copiado, o app escolhido é aberto — e o painel fica por cima, porque é uma janela
flutuante. Fechar também tira o obstáculo do caminho do app que vai responder, e é o mesmo gesto que o
R9 vai usar para o HOME.

**Feito junto com o R3**, porque é o mesmo trecho de código: os dois caminhos (`openAi`, que serve o
app fixo e o escolhido na lista) abrem o app e fecham o painel na mesma chamada.

A ordem importa: tentar abrir; **só fechar se algum app abriu**; se nenhum abriu, o painel fica e
mostra «nenhum app aceita texto» (ou a lista de novo) — fechar ali deixaria o usuário sem a mensagem e
sem o texto. O texto já está na área de transferência antes da tentativa, então fechar não perde nada.

Quem garante a ordem é `AskAi.attempt`: `onOpened` (que é `overlays.hideResult()`) corre no instante
do primeiro sucesso, e nunca depois de todos falharem. Teste:
`the panel is taken down only after an app opened` em `AskAiTest`; a mutação que move o fechamento
para antes da tentativa derruba esse teste (`r3b_nonvacuous.ps1`, item C).

### R11 — O que não é IA na lista de apps  `[x]` (20/10/2026)

Relato: «pode esconder app que não são de IA ou de tradução».

A lista vem do `queryIntentActivities` de `text/plain`, que é *todo* app que aceita um texto
compartilhado: Gmail, Bluetooth, «Converter para JPEG», o gerenciador de downloads. Nenhum deles
responde a uma pergunta, e juntos empurram o ChatGPT para baixo da dobra.

**Correção.** `AskAi.relevant` filtra a lista por dois caminhos:

- **pacote conhecido** (`com.openai.chatgpt`, `com.google.android.apps.bard`, `com.anthropic.claude`,
  tradutores do Google/Microsoft/DeepL/Yandex e os de fábrica da Xiaomi/OPPO): o nome do app muda com o
  idioma do launcher, o pacote não; um pacote dentro do mesmo espaço (`…chatgpt.helper`) conta como o
  app a que pertence;
- **palavra inteira** no nome (`gpt`, `chatgpt`, `gemini`, `copilot`, `claude`, `assistente`, `tradutor`,
  `tradução`, `translate`, `deepl`, …): palavra inteira, porque `ia` dentro de «Media» ou «Galeria»
  traria a galeria e o player de música de volta.

Duas travas para o filtro não virar uma porta fechada: uma lista que ficaria **vazia** volta inteira
(um telefone com uma IA que ninguém previu é melhor servido pela lista completa do que por «nenhum app
aceita texto»), e a janela de escolha ganha um rodapé **«Mostrar todos (N)»** que troca para a lista
completa e volta com **«Só os de IA»**. A palpite é sobre nomes; o acesso à lista toda nunca é tirado.

**Testes de regressão:** `AskAiRelevantTest` (8) — apps que respondem ficam; Gmail/Bluetooth/JPEG/
download saem; palavra inteira (`Media`, `Galeria`, `Fotografia` ficam de fora do filtro); telefone
desconhecido mantém a lista toda; a ordem não muda; pacote conhecido vale mesmo com nome mudo; ajudante
do pacote conta como o app; acentos e formas sem acento são reconhecidos. `AppPickerTest` — o rodapé
leva à lista completa e volta, e não aparece quando não há mais nada a mostrar. `AskAiPanelTest` — a
janela abre na lista curta, com o rodapé da lista completa presente. Non-vacuidade **7/7**
(`%TEMP%\opencode\r11_nonvacuous.ps1`: nada filtrado, substring em vez de palavra, lista curta vazia
deixada vazia, pacotes conhecidos ignorados, a janela recebendo a lista inteira, rodapé nunca desenhado,
rodapé sempre desenhado).

---

## 8. Quarta ronda — o que o aparelho mostrou  (20/10/2026)

Cinco pedidos da primeira leva, mais dois que chegaram enquanto o R12 era feito. Um deles traz dois
problemas diferentes: o primeiro relato trazia um kanji que o reconhecedor não viu e as tarjas que se
sobrepunham. Valem as regras de sempre: **bugs primeiro**, do que estraga mais para o que estraga menos,
um item por vez, e um teste de regressão provado não vazio antes do `[x]`.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R12 | `[x]` | **Tarjas se sobrepõem** na opção tarja: duas etiquetas caem no mesmo lugar | bug (visual) |
| R13 | `[x]` | **O kanji 腹 não foi reconhecido** num balão de três colunas | bug (OCR) |
| R14 | `[x]` | **A ordem do texto na tradução offline** sai como saía a do Lens antes do R1 | bug (core) |
| R15 | `[x]` | **Dar zoom na página com as caixas na tela**, sem perder as marcações | função (picker) |
| R16 | `[x]` | **Voltar da lista de dicionários** para a aba em que se estava | função (painel) |
| R17 | `[x]` | **Tocar num kanji para ver a leitura dele** — uma opção a mais no ícone flutuante | função (nova) |
| R18 | `[x]` | **A lista de apps de IA nos ajustes** deve ser a mesma da aba de tradução | melhoria |
| R19 | `[x]` | **Textos do dicionário grudados** (`1-danintransitive1. to be called outNote…`) | melhoria (painel) |

### R12 — Tarjas que se sobrepõem  `[x]` (20/10/2026)

**Relato:** com a leitura sobre a página na forma **tarja**, algumas etiquetas aparecem uma em cima da
outra, e a frase das duas fica ilegível.

**Causa:** a etiqueta era colocada sem saber das outras. `LabelLayout.chip` olhava só para a própria
caixa e para as bordas da tela: escolhia a direita do balão quando cabia, depois a esquerda, depois
abaixo, depois acima, e pronto. Dois balões próximos — um acima do outro ou lado a lado — escolhiam a
mesma faixa da tela, e a segunda etiqueta era desenhada por cima da primeira. Não havia lista nenhuma
de «já ocupado».

**Correção:** `chip` passa a receber as etiquetas **já colocadas** naquela passagem de desenho
(`taken: List<Strip>`). A posição preferida continua sendo ao lado do balão; quando ela está ocupada, a
etiqueta anda **para baixo, em passos de uma etiqueta inteira**, e depois para cima (0, +1, −1, +2, −2,
até 24 passos), sempre colada ao próprio balão; só quando aquele lado não tem mais espaço é que ela
troca de lado, na mesma ordem de antes (direita, esquerda, abaixo, acima). Se tudo estiver ocupado,
vale a regra antiga, presa às bordas da tela: uma etiqueta sobre outra é ruim, uma etiqueta fora da
tela é pior. `BlockCanvasView` limpa a lista a cada passagem e acrescenta cada etiqueta que desenha.

O que a correção **não** faz: evitar a caixa do balão. A etiqueta continua podendo cobrir o desenho —
ela é a leitura, e é isso que a tarja é. O que ela não faz mais é cobrir **outra etiqueta**.

**Testes de regressão:** `PickerReadingLayoutTest` — `a strip does not land on the strip already placed`
(a segunda etiqueta desce uma posição e continua na faixa do próprio balão) e `a crowded page still gets
every strip on screen` (seis balões a 30 px um do outro, etiquetas de 40 px: todas dentro da tela e
nenhum par encostado). Os testes antigos da regra — direita, esquerda, abaixo, borda — continuam
valendo sem mudança. Non-vacuidade **3/3** (`%TEMP%\opencode\r12_nonvacuous.ps1`: lista de etiquetas
ignorada, etiqueta que nunca anda, bordas da tela não verificadas). `app:testDebugUnitTest` verde com
**150** testes.

### R13 — O kanji 腹  `[x]` (21/10/2026)

**Relato:** num balão de três colunas — `腹が減っては` / `なんとやら…と` / `いうことか？` — a caixa que
aparece na tela de trechos cobre o balão inteiro, mas o texto dela começa em `なんとやら…と`: a coluna
da direita, a que começa a frase, ficou de fora. O kanji 腹 não aparece em lugar nenhum.

**O que já se sabe:** não é o app que perde a coluna. `LensResultBuilder` não descarta linha nenhuma:
monta os `segments` com todas as linhas que o Lens devolveu e `groupIntoBlocks` reparte essas mesmas
linhas em blocos; a única coisa que sai da frase é o que foi reconhecido como rubi, e rubi é kana puro
(`RubyGeometry` exige `isKanaOnly`), o que `腹が減っては` não é. Se o Lens tivesse devolvido aquela
coluna, ela estaria em algum bloco — com caixa própria ou junto do resto do balão. Como não está em
bloco nenhum, a conclusão é que a coluna não voltou do reconhecedor; a caixa que se vê é só a união das
outras duas colunas, que por acaso cobre o balão inteiro.

**Fechado:** confirmado corrigido pelo usuário no aparelho, sem mudança de código. A leitura que
fechou o caso é a que já estava registrada — o reconhecedor é que não devolveu a coluna naquela
captura, e o app não tinha como inventá-la. Fica valendo o caminho do recorte, que manda a região ao
Lens com muito mais detalhe do que a tela inteira (1600 px no lado maior): quando um balão sair
incompleto, **Selecionar área** naquele balão é o que resolve, e o README já diz isso. Se voltar a
acontecer de forma repetível, a evidência a recolher é a mesma que estava pedida aqui: o texto do
painel daquele balão e a existência (ou não) de uma caixa fina sobre a coluna que falta.

### R14 — A ordem do texto na tradução offline  `[x]` (20/10/2026)

**Relato:** na tradução feita **offline**, os textos aparecem fora de ordem — o mesmo defeito que o R1
tinha consertado no Lens — e a pergunta era se dá para corrigir do mesmo jeito.

**Causa:** o R1 consertou a ordem **dentro do caminho do Lens**. O `LensResultBuilder` passou a ordenar
as linhas de um bloco pelos retângulos; o motor offline é outro arquivo, `MlKitOcrEngine`, e ali os
blocos saíam na ordem em que o ML Kit os devolve — a página de cima para baixo e da esquerda para a
direita — e as linhas de dentro de cada bloco na mesma ordem. Num balão vertical isso põe a coluna da
**esquerda**, que é a última a ser lida, em primeiro lugar.

**Correção:** a regra saiu de dentro do `LensResultBuilder` e virou
`core/layout/ReadingOrder`, que recebe só retângulos (`List<PixelBox?>`) e devolve os índices na ordem
de leitura: **colunas da direita para a esquerda** num texto vertical, **linhas de cima para baixo e da
esquerda para a direita** num horizontal, e as duas metades de uma coluna que o reconhecedor partiu
continuam juntas. Os dois motores passaram a chamar a mesma função: o Lens para as linhas de um bloco
(sem mudança de comportamento, os testes do R1 continuam os mesmos) e o ML Kit para os **blocos da
página** e para as **linhas de cada bloco**. A lista «linha por linha» do painel também segue a ordem
nova, porque `OcrOutcome.lines` passou a ser a lista já ordenada.

Quando um retângulo falta (`boundingBox` nulo), a ordem que veio é mantida: ordenar pela metade seria
pior do que não ordenar.

**Testes de regressão:** `ReadingOrderTest` (6) — colunas de um balão vertical da direita para a
esquerda; linhas de um parágrafo horizontal de cima para baixo e da esquerda para a direita; as duas
metades de uma coluna partida juntas, a de cima primeiro; retângulo sem geometria mantém a ordem
recebida; uma caixa e uma lista vazia não se mexem; uma página de balões verticais é lida a partir da
borda direita. Os testes do R1 (`LensProtoParsingTest`) continuam passando sem alteração — é a prova de
que a mudança de lugar não mudou o comportamento do Lens. Non-vacuidade **5/5**
(`%TEMP%\opencode\r14_nonvacuous.ps1`: página sempre lida como linhas, colunas da esquerda para a
direita, cada caixa na sua própria pilha, caixas de uma pilha sem ordenação, caixa sem geometria
tratada como se estivesse na origem). `core:test` **83 / 0 / 2 pulados**, `app:testDebugUnitTest`
**150 / 0**.

**Lacuna aceita:** o `MlKitOcrEngine` em si não roda em teste de unidade — depende do modelo do ML Kit,
instalado no aparelho. O que está testado é a função que decide a ordem; a ligação no motor são três
linhas e é verificada no aparelho.

### R15 — Zoom na página  `[x]` (20/10/2026)

**Relato:** na tela de trechos as caixas aparecem sobre a página, mas não há como ampliar o que está
embaixo. Num mangá denso a leitura impressa é pequena demais para conferir se a caixa pegou a coluna
certa, e o rubi desenhado fica do tamanho do rubi impresso.

**Por que não era só «escalar a tela»:** o seletor era uma janela transparente por cima do app. A
página pertence a outro aplicativo; ampliar o desenho do seletor ampliaria as caixas, não o mangá. A
captura já existe — é ela que foi para o OCR — então o seletor passou a desenhá-la como fundo, como o
Lens faz: a tela **congela** e é essa imagem que a pinça amplia.

**Como funciona:** página, caixas, rubi e tarjas são desenhados no mesmo sistema de coordenadas (o da
captura, um pixel por pixel de tela) e um único `Canvas.scale`/`translate` move todos juntos — uma
caixa não tem como sair de cima do texto, porque não há duas transformações. `PickerZoom` guarda a
escala (1× a 5×) e o deslocamento com duas regras: **o ponto sob os dedos fica sob os dedos** e **a
página não descobre a borda da tela** (nada de faixa vazia). Um dedo arrasta a página ampliada; a
pinça amplia; o toque continua escolhendo o trecho, agora convertido para coordenadas da página
(`toPageX`/`toPageY`) antes de ser testado contra as caixas e as tarjas. Um arrasto ou uma pinça
**não** escolhem nada e **não** fecham o seletor — antes, um arrasto caía no «fora» e fechava a tela.
Em 1× um dedo que treme continua sendo toque: só se começa a arrastar quando há o que arrastar. Cada
captura nova começa em 1×.

**Testes de regressão:** `PickerZoomTest` (7) — identidade em 1×; a pinça segura o ponto sob os dedos;
limites 1×/5×; em 1× não há o que arrastar; o arrasto move e o toque é lido na página; a página não
passa da própria borda; página nova começa em 1×. `TextPickerOverlayTest` (5, Robolectric) — toque
sobre a caixa depois do zoom acerta a caixa; toque fora fecha; arrasto não escolhe nem fecha;
tremidinha em 1× ainda é toque; captura nova esquece o zoom. `PickerPageDrawTest` (2, Robolectric em
modo gráfico nativo) — a página é realmente pintada sob as caixas e amplia junto com elas (um
quadrado vermelho conhecido é procurado no bitmap desenhado; sem o `Canvas.scale`, ele continua do
tamanho antigo). Non-vacuidade **7/7** (`%TEMP%\opencode\r15_nonvacuous.py`: sem a transformação, sem
o desenho da página, toque testado nas coordenadas da janela, arrasto virando toque, zoom antigo
sobrevivendo à captura nova, pinça sem âncora, página arrastável para fora da borda).
`app:testDebugUnitTest` **173 / 0**, `core:test` **95 / 0 / 2 pulados**.

**Lacuna aceita:** o gesto em si (`ScaleGestureDetector`) não é simulado — o que está testado é o
transformador que ele alimenta e o toque que o seletor entrega. O `FloatingOcrService` continua sem
teste (liga a captura ao seletor), como já era.

### R16 — Voltar da lista de dicionários  `[x]` (21/10/2026)

**Relato:** com a lista de dicionários aberta na seção «Dicionário», o **VOLTAR** fechava o painel
inteiro — e com ele a captura — em vez de fechar só a lista e deixar o painel onde estava.

**Causa:** a lista de dicionários não é uma tela: é uma fileira de chips desenhada **dentro** da seção
(`pickerOpen`). O `stepBack()` só conhecia dois degraus — «◀ Trechos» e «Fechar» — e não sabia que a
lista estava aberta; qualquer BACK ou toque fora do cartão pulava direto para o degrau seguinte.

**Correção:** a lista passou a ser um degrau. `stepBack()` fecha a lista, redesenha a seção com as
mesmas palavras (`wordsState`, guardado no `renderWords`) e volta — o painel, o texto e a tradução
ficam exatamente onde estavam. Só o BACK seguinte é que sai do painel, como antes. O `wordsState` é
esquecido quando chega uma consulta nova (`Working`/`Failed`/`Hidden`) ou um resultado novo: uma lista
aberta sobre palavras que já não estão na tela seria uma armadilha.

**Testável:** o BACK passou de `setOnKeyListener` para `dispatchKeyEvent` sobrescrito. O motivo não é
estilo: um key listener num `ViewGroup` não é entregue pelo Robolectric (o `dispatchKeyEvent` volta
`false` sem nunca chamar o listener), e o botão voltar é exatamente o gesto que este item conserta.
Com o `dispatchKeyEvent`, o teste aperta o BACK de verdade.

**Testes de regressão:** `ResultOverlayLayoutTest.back closes the dictionary list before leaving the
panel` — abre a lista pelo controle do cabeçalho, aperta BACK e exige a lista fechada, o painel aberto
e nenhum «◀ Trechos»/«Fechar»; abre de novo, toca fora do cartão (o mesmo degrau) e exige o mesmo; o
BACK seguinte, esse sim, pede «◀ Trechos». Non-vacuidade **2/2**
(`%TEMP%\opencode\r16_nonvacuous.py`: sem o degrau da lista, e sem guardar as palavras para redesenhar,
o teste falha). `app:testDebugUnitTest` **174 / 0**, `core:test` **95 / 0 / 2 pulados**.

### R17 — Tocar num kanji para ver a leitura  `[x]` (21/10/2026)

**Relato/pedido:** tocar num kanji para ver a leitura dele, como **uma opção a mais** no ícone
flutuante. Decidido com o usuário: a ação nova na bolha (漢) congela a tela, e o toque num kanji mostra
**as duas leituras** — a da frase e a do dicionário de kanji (音/訓) — mais as palavras daquela tela que
usam o kanji.

**O que já existia e foi reaproveitado:** o reconhecedor devolve, além do parágrafo, as **colunas/linhas**
que o compõem (`OcrPiece`), e o serviço já as posiciona na tela para desenhar rubi (`placeBlocks`). É
essa geometria que diz onde cada trecho está impresso.

**O que faltava:** o **caractere dentro do trecho**. O reconhecedor não devolve a caixa de cada letra.
Japonês é composto numa grade de células de largura fixa, então dividir a caixa pelo número de
caracteres devolve as células: `KanjiGrid` (core) faz isso, e a célula n é `total * n / count` em
inteiros — as células ladrilham a caixa exatamente, sem buraco nem sobreposição, que é o que faz o
destaque cair sobre **um** kanji. Coluna é lida de cima para baixo, linha da esquerda para a direita;
o ponto é preso à ponta do trecho, porque um dedo que passa um pouco do fim ainda está apontando
aquele trecho.

**Como ficou:**

1. **Menu (漢).** Um quinto item na faixa da bolha. O ícone é o próprio carácter, e não um desenho: um
   símbolo desenhado não é lido como «um kanji» do jeito que 漢 é. A faixa mede-se antes de se colocar
   ([`menuAnchor`]), então o quinto ícone não a empurra para fora da tela.
2. **A captura.** O mesmo caminho de «Tela inteira» (Lens, uma consulta), mas com um destino diferente:
   em vez de caixas de parágrafo para copiar, os **trechos** viram alvos — sem o mínimo de toque que o
   seletor de texto aplica, porque aqui o alvo pode ser uma coluna de um kanji só. A página congela e
   pode ser ampliada com a pinça, como na tela de trechos (R15).
3. **O toque.** O overlay acha o menor trecho sob o dedo, converte a posição (já sem o zoom) em índice
   de carácter e avisa o serviço. Se o carácter não for kanji, ele diz «Isso não é um kanji» — calar
   pareceria um toque quebrado.
4. **O cartão.** Montado fora da linha principal (abrir dicionário é arquivo, e a consulta é uma
   passada no índice): a leitura **da frase** vem da palavra que cobre o carácter — a **mais estreita**,
   porque em 職員 o toque no 職 pergunta por 職員 (しょくいん), não por um pedaço maior que o contenha;
   as **leituras do dicionário** vêm da entrada de kanji (`kanjiBreakdown`, uma entrada por carácter); e
   as **palavras da tela** são as que contêm o carácter, sem repetição. O cartão tem ✕, e o ✕ da tela
   está lá desde o início — uma janela de tela cheia cuja única saída é o VOLTAR é uma armadilha.

**Sobre «compostos mais comuns»:** o dicionário compilado é indexado por **entrada**, não por kanji
contido; listar os compostos mais comuns de um kanji exigiria um índice novo. O que o cartão mostra é a
versão honesta da mesma ideia — as palavras que usam aquele kanji **no texto que está na tela**, que
saem da mesma consulta que já está sendo feita. Se o índice novo fizer falta, fica como pedido separado.

**Testes de regressão:** `core/src/test/kotlin/dev/yabai/ocr/core/layout/KanjiGridTest.kt` (8: coluna,
linha, primeira e última célula, dedo um pouco fora, ladrilhamento exato, texto vazio);
`app/src/test/java/dev/yabai/ocr/overlay/KanjiCardTextTest.kt` (7: palavra mais estreita, palavra que não
cobre, acerto sem leitura, compostos, repetição, limite, leituras aparadas);
`app/src/test/java/dev/yabai/ocr/overlay/KanjiOverlayTest.kt` (10, Robolectric: toque em coluna,
destaque na célula certa, toque depois do zoom, toque fora não fecha, arrasto não escolhe, VOLTAR, ✕
antes de qualquer kanji, cartão completo, cartão sem dicionário, página nova sem cartão);
`ActionMenuTest` (6: o quinto item, o glifo 漢, cada ícone com a sua ação, a faixa na tela). Non-vacuidade
**5/5** (`%TEMP%\opencode\r17_nonvacuous.py`: coluna lida em x, palavra mais larga, filtro de compostos,
item do menu removido, toque em coordenadas da janela em vez da página — todos derrubam um teste).
`app:testDebugUnitTest` **201 / 0**, `core:test` **103 / 0 / 2 pulados**.

### R18 — A lista de apps de IA nos ajustes  `[x]` (21/10/2026)

**Relato:** a lista de aplicativos que aparece **nos ajustes** para escolher quem recebe o texto mostra
todo app que responde a um `SEND` — Gmail, Bluetooth, «Converter para JPEG» — enquanto a janela da aba
de tradução já mostra só IA e tradutor, com uma opção de ver a lista completa (R11). Duas listas, dois
critérios, no mesmo app.

**O que foi feito:** os ajustes passaram a desenhar a **mesma lista** da janela de escolha. A forma da
lista saiu da tela e foi para o `AskAi`, em `AskAi.rows(all, showingAll)`: a primeira linha é sempre
**«Automático (só apps de IA)»** (o padrão, que na hora de perguntar oferece só a lista curta), depois
os apps — os de IA e tradutor, ou todos — e, quando o telefone reporta mais apps do que os que estão à
vista, a linha final **«Mostrar todos (N)»** / **«Só os de IA»**. Escolher um app continua guardando o
**pacote**, não o nome, e o app fixado aparece na lista mesmo que o filtro não o tivesse escolhido.

Compartilhar a *forma* da lista, e não só o filtro, é o que impede as duas telas de divergirem outra
vez: o `MainActivity` só traduz cada `Row` para um texto e trata o toque, sem decidir nada.

**Testes de regressão:** `app/src/test/java/dev/yabai/ocr/ai/AskAiSettingsListTest.kt` (6) fixa a lista
— automático primeiro, curta por omissão, completa com o rodapé, telefone sem app de IA mostrando tudo
sem rodapé, e o pacote que cada linha entrega. `app/src/test/java/dev/yabai/ocr/AiAppSettingsTest.kt`
(3 de Robolectric) abre a **tela de ajustes de verdade**, com um telefone de mentira (ChatGPT e Gmail),
toca no botão e lê os itens do diálogo: «Automático (só apps de IA)», «ChatGPT», «Mostrar todos (2)»;
toca no rodapé e vê a lista completa com «Só os de IA»; toca em Gmail e confere o pacote guardado.
Non-vacuidade **3/3** (`%TEMP%\opencode\r18_nonvacuous.py`: sem o filtro, sem o rodapé, e com o
`MainActivity` voltando à lista crua, os testes falham).

### R19 — O texto do dicionário  `[x]` (20/10/2026)

**Relato:** os sentidos saem grudados — `1. 1-danintransitive1. to be called outNote passive form of
呼ぶ` — e o exemplo em japonês encosta na definição (`to be referred to asバラは花の女王と…`). Fica
difícil achar o sentido que interessa.

**Causa:** não é o dicionário, é o `GlossaryRenderer`. Ele achata a árvore `structured-content` do
Yomitan em texto puro e junta irmãos com `""` (`"li" -> renderChildren(separator = "", …)`), então
etiqueta gramatical, definição e nota viram uma frase só. Pior: o Jitendex guarda o **sentido inteiro**
dentro de **um item de lista** (`ul[sense-groups] > li[sense-group] > ol > li[sense]`), e o
renderizador antigo tratava esse item como se fosse uma definição só — daí o `1.` do painel numerar as
etiquetas e o `1.` do dicionário numerar os sentidos outra vez.

**O que foi feito:**

1. **Separar.** O renderizador passou a entender a estrutura real: cada `li[sense]` vira **um bloco
   numerado** (o painel numera uma vez só), e as definições de um mesmo sentido ficam na mesma linha,
   separadas por `;` — «to live on (e.g. a salary); to live off; to subsist on». Nota
   (`li[sense-note]`), exemplo (`li[example-sentence]`) e tradução saem em blocos próprios, **depois**
   do sentido a que pertencem. Num `li` comum, um filho de bloco (`div`, `ul`, `ol`) começa linha
   nova; um pedaço de frase (`span`) continua na mesma linha, que é o que mantém o japonês correto.
2. **Marcar o tipo de cada bloco.** O texto compilado carrega uma gramática de linha: sentido começa
   com `1. `, nota com `ℹ`, exemplo com `例` e a tradução do exemplo com `→`. O painel lê essa
   gramática em **`DefinitionStyle`**, num lugar só, e desenha o número em negrito e nota/exemplo
   menores, em cinza e recuados (`LeadingMarginSpan`) — a segunda linha de um sentido já alinha sob a
   primeira, pelo recuo pendente do `renderDefinition`.
3. **Etiquetas no cabeçalho.** A classe gramatical saiu de dentro da definição: o renderizador devolve
   a lista em separado (`Rendered.tags`), o compilador junta com a etiqueta do registro
   (`mergeTags`, sem repetir) e o painel a mostra na linha de apoio, ao lado do dicionário
   («1-dan・transitive · Jitendex»).
4. **Ruído fora.** O rótulo «Note» e o número da nota de rodapé do Tatoeba (`[1]`) não entram; a
   atribuição (`JMdict | Tatoeba`) continua de fora. Um `structured-content` com mais de uma parte
   (entrada + atribuição, como o Jitendex faz) é lido parte por parte — antes o formato antigo caía
   inteiro no caminho de texto puro.

**Reimportar é necessário.** A definição é gravada como texto no momento da importação; um dicionário
que já está no aparelho continua com o texto antigo. O selo `compiledWith` subiu para
`FORMAT_VERSION = 2`, é lido de volta (`DictionaryInfo.outdated`) e a lista de dicionários avisa
«Compilado com uma versão antiga do app: importe este dicionário de novo…». Recompilar sozinho não dá:
o `.zip` de origem não é guardado depois da conversão.

**Testes de regressão:** `GlossaryRendererTest` (9) — a entrada real do Jitendex (呼ばれる) com dois
sentidos, nota, exemplo e tradução, comparada linha a linha; etiquetas fora da definição; nota de
rodapé fora; glossário de uma string; duas definições do mesmo sentido na mesma linha; nota dentro de
uma definição em linha própria; lista de definições sem grudar; atribuição ao lado da entrada não
derruba o formato; limite de sentidos. `DefinitionStyleTest` (6) — onde começa e termina cada marca,
inclusive depois do japonês (caracteres, não bytes), e texto sem marcas sem estilo.
`DefinitionPanelTest` (3) — o número em negrito, a nota menor e recuada, a etiqueta na linha de apoio,
definição sem marcas sem estilo nenhum. `DictionaryMetaTest` (3) e o teste do JMdict passaram a exigir
o selo de versão. Non-vacuidade **5/5** (`%TEMP%\opencode\r19_nonvacuous.py`: sem a recursão no
`structured-content` de várias partes, sem a separação de `li`, sem o caso do glossário na mesma
linha, sem o estilo no painel e sem a linha das etiquetas, cada teste correspondente falha).
`core:test` **95 / 0 / 2 pulados**, `app:testDebugUnitTest` **159 / 0**.

O que **não** vale a pena: procurar dentro da definição (a busca do app é por palavra, não por texto de
definição) e ordenar sentidos por frequência (o dicionário já vem na ordem que o autor escolheu).

---

## 9. Quinta ronda — o que o aparelho mostrou  (21/10/2026)

Sete pedidos de uma vez: dois bugs que atrapalham o uso, um que só aparece em página com tarja preta,
três melhorias de dicionário e uma pergunta sobre traduzir um dicionário inteiro. As regras de sempre
valem: **bugs primeiro**, um item por vez, cada um com teste de regressão provado não vazio antes do
`[x]`.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R20 | `[x]` | **«Copiar original» e «Perguntar à IA» mandam o texto com o furigana** escrito nele | bug (texto) |
| R21 | `[x]` | **Cancelar um download** em andamento, sem esperar o arquivo inteiro | função (dicionário) |
| R22 | `[x]` | **Qual dicionário é qual**: dois títulos iguais na lista, sem nada que os distinga | melhoria |
| R23 | `[x]` | **Caixas fora do texto** em páginas com barra preta em cima e embaixo | bug (geometria) |
| R24 | `[x]` | **Fechar a notificação do OCR** deixa a captura num estado que só dá erro | bug (serviço) |
| R25 | `[x]` | **Pouco furigana vindo do Lens** (página 9, capítulo 13) | não se repetiu |
| R26 | `[x]` | **Aviso de formato antigo** que o usuário não viu antes de reimportar | melhoria |
| R27 | `[-]` | **Traduzir um dicionário inglês para português** com IA: vale a pena? (análise) | estudo |

### R20 — O texto que sai do painel  `[x]` (21/10/2026)

**Relato:** «Copiar original» e «Perguntar à IA» copiam o texto **como ele aparece na tela**, com as
leituras dentro — `職員（しょくいん）さんに聞（き）いた`. No bloco de notas e na conversa com a IA, os
parênteses não fazem parte da frase.

**Causa:** as duas ações liam `originalEditor.text`, e é o editor que recebe o texto anotado quando o
dicionário responde (`applyReading`). O texto **reconhecido** estava guardado desde a abertura do
painel (`cleanText`), mas ninguém o usava para sair.

**Correção:** `textToSend()` — a **frase reconhecida**, menos quando o usuário editou: aí o editor é a
única cópia que tem a correção dele, e mandar o original desfaria o trabalho. As leituras continuam
sendo uma **vista** do texto, não o texto.

**Testes de regressão:** `CopyCleanTextTest` (4) — o painel realmente escreve as leituras na frase
(premissa), «Copiar original» e «Perguntar à IA» mandam a frase sem elas, e uma frase corrigida à mão
sai como o usuário a escreveu. Non-vacuidade **1/1**.

### R21 — Cancelar o download  `[x]` (21/10/2026)

**Relato:** um arquivo de 37 MB em dados móveis começa sem querer e não há como voltar atrás.

**Correção:** enquanto um download roda, o próprio botão da linha vira **Cancelar**; ele cancela a
`Job` daquele download e volta a ser «Baixar» ao terminar (inclusive quando falha). O downloader passou
a ser cooperativo em dois pontos: `copyWithProgress` verifica `ensureActive()` **entre dois blocos** —
sem isso a cópia terminaria depois do cancelamento — e `download` registra o cancelamento da corrotina
para fechar a **conexão** (`call.cancel()`), porque uma leitura pode ficar presa até o tempo limite de
60 s e «Cancelar» não pode significar «espere um minuto». O `.zip` parcial é apagado no `finally`, como
antes, e o usuário recebe «Download cancelado.».

**Testes de regressão:** `DictionaryDownloaderTest` (2) — um `InputStream` lento de 3,75 MB: o job é
cancelado no meio e a cópia **para** (menos bytes do que o total e mais do que zero, para o teste não
passar por não copiar nada); e a cópia não cancelada entrega todos os bytes. Non-vacuidade **1/1**.

**O que não está coberto:** a ligação do botão com o download não tem teste — o downloader precisa de
rede. É a mesma lacuna declarada de `FloatingOcrService`.

### R22 — Qual dicionário é qual  `[x]` (21/10/2026)

**Relato:** na lista de instalados os nomes se repetem e não há como saber qual é qual.

**Causa:** o título vem de **dentro do arquivo** («JMdict», «Wikcionário») e é o mesmo para todas as
variantes do mesmo dicionário — o JMdict em inglês e o português, os três Wikcionários. O que o usuário
escolheu (a entrada da lista do app, ou o nome do arquivo importado) é que diz o idioma, e isso ficava
só no `meta.json`.

**Correção:** `InstalledDictionary.displayName` = título + origem quando diferem, e a linha de apoio da
lista mostra «Origem: JMdict (inglês)». A mesma função nomeia o dicionário no aviso de formato antigo,
na lista e no painel, para os dois lugares falarem do mesmo cartão.

**Testes de regressão:** `InstalledDictionaryTest` (3) — origem diferente vira parte do nome, origem
igual não se repete, origem vazia não inventa nada. `DictionaryScreenTest` confere a linha na tela.
Non-vacuidade **1/1**.

### R23 — As caixas fora do texto  `[x]` (21/10/2026)

**Relato:** em páginas com **barra preta em cima e embaixo** (mangá e jogos), as caixas de tradução
aparecem deslocadas em relação ao texto original.

**Causa:** a página e as caixas viviam em dois sistemas de coordenadas diferentes. A **imagem** era
desenhada esticada para a janela (`drawBitmap(bitmap, null, RectF(0, 0, width, height), …)`), mas as
caixas vinham em **pixels da captura** e eram desenhadas — e tocadas — sem essa escala. Enquanto a
janela e a captura coincidiam, os dois números eram iguais e o erro era zero; quando o sistema encolhe
a janela (a barra de status voltando durante a captura, o que acontece justamente quando o app já está
em cima de um conteúdo de tela cheia), a imagem encolhia e as caixas não. Valia para o seletor de
trechos **e** para o leitor de kanji, que herdou o mesmo desenho.

**Correção:** a escala **página → janela** passou a ser explícita nos dois canvas
(`pageScaleX`/`pageScaleY`) e aplicada em três lugares que têm de concordar: o **desenho** (um
`canvas.scale` depois do zoom), a **conversão do toque** (`pageX`/`pageY` desfazem o zoom e a escala) e
o **layout das etiquetas**, que passa a ser calculado em pixels de página e esticado junto. Com isso o
sistema de coordenadas é um só — o da captura —, e a janela é só a lente por onde ele é visto.

**Testes de regressão (quatro, dois por canvas):** `TextPickerOverlayTest` — um toque onde a caixa é
**desenhada** numa janela de 600 px de altura acerta a caixa (antes errava por 10 px); `PickerPageDrawTest`
— em `@GraphicsMode(NATIVE)`, a caixa é **pintada** por cima do pedaço de página que ela representa, e o
que está fora dela continua sendo página; `KanjiOverlayTest` — o toque aponta o caractere em que o dedo
está e, no desenho, a **marca** cai sobre esse caractere, não dois caracteres abaixo.
Non-vacuidade **4/4**.

### R24 — Fechar a notificação do OCR  `[x]` (21/10/2026)

**Relato:** fechar a notificação «OCR ativo» faz a próxima captura falhar; ou a notificação não devia
poder ser fechada sem «Parar», ou fechá-la devia encerrar o OCR.

**Causa:** o serviço é um *foreground service* de projeção e a notificação é o que o anuncia. A partir
do **Android 13** o usuário pode descartar a notificação de um serviço em primeiro plano, e o serviço
continua rodando com a projeção já sem utilidade: o toque seguinte não tem quadro nenhum para ler, e o
painel responde «Falha ao capturar a tela». A notificação não pode ser tornada in-fechável — o sistema
permite o descarte desde o 13, por decisão dele, não do app.

**Correção:** o caminho escolhido foi o segundo dos dois que o pedido oferecia — **fechar encerra**. A
notificação ganhou `deleteIntent` com a mesma ação «Parar» do botão (nos aparelhos que reportam o
descarte), e a checagem que decide de verdade: antes de cada captura e de abrir o seletor de área, o
serviço pergunta ao sistema se a notificação ainda está lá
(`NotificationManager.getActiveNotifications`). Se não está, ele avisa («A notificação da captura foi
fechada, então o OCR foi encerrado…») e faz `stopSelf()` — a bolha sai junto, em vez de ficar na tela
respondendo erro. **Notificações desligadas para o app não contam como descarte**: é uma permissão que o
usuário recusou, a captura continua valendo e encerrar ali quebraria o app para quem só não quer a
gaveta cheia.

**Testes de regressão:** `CaptureNotificationTest` (3) — `Robolectric.buildService` de verdade: a
notificação pede a parada ao ser descartada, oferece o próprio «Parar», e a ação «Parar» encerra o
serviço (`ShadowService.isStoppedBySelf`). Non-vacuidade **1/1**.

**O que não está coberto:** o descarte em si — o Robolectric não simula barrer uma notificação de
serviço nem o encolhimento da janela pela barra de status. O teste fixa o contrato (descarte ⇒ parada);
o caminho real depende do aparelho.

### R25 — Pouco furigana vindo do Lens  `[x]` (21/10/2026)

**Relato:** na página 9, capítulo 13, o Lens quase não devolveu leitura enquanto o offline achou mais.

**Fechado sem mudança de código.** O usuário retestou na versão nova, com a mesma página, e a leitura
apareceu normalmente. Não se reproduz mais; a leitura do Lens depende do que ele decide reportar como
texto anotado naquela imagem, e nada no app mudou desde o relato. Fica registrado para reabrir se
voltar.

### R26 — O aviso de formato antigo  `[x]` (21/10/2026)

**Relato:** depois de apagar e reimportar o dicionário, o usuário não viu aviso nenhum de que o formato
antigo estava em uso — só soube porque foi avisado na conversa.

**Causa:** o aviso existia desde o R19 (`DictionaryInfo.outdated`), mas **num lugar só**: a última linha
de um cartão da tela de dicionários, abaixo do tamanho, da revisão e da atribuição, em letra secundária
de 12 sp. É a linha que só encontra quem já sabe que deve procurar por ela — e a tela onde a diferença
de definição é lida é o **painel**, que não dizia nada.

**Correção:** o mesmo fato passou a ser dito em três lugares, do mais visível para o mais específico:
um **aviso no topo da lista de instalados**, nomeando cada dicionário antigo; a linha do cartão, como
antes; e uma **nota no painel**, logo acima das definições, com o nome que o usuário vê na outra tela
(`WordsState.Done.stale`), para o texto velho não parecer defeito do app. O nome usado no aviso é o
`displayName` do R22, então os dois lugares falam do mesmo cartão.

**Testes de regressão:** `DictionaryScreenTest` — o aviso existe, nomeia o dicionário antigo e **não**
culpa o que está atual; `DefinitionPanelTest` — a nota aparece com o nome do dicionário em formato
antigo e **não** aparece quando todos estão atuais. Non-vacuidade **2/2**.

### R27 — Traduzir um dicionário para português  `[-]` (21/10/2026)

Pergunta: dá para pegar um dicionário em inglês (JMdict/Jitendex) e traduzir para português com IA, de
graça, com qualidade que valha a pena? Análise entregue na conversa — o item fica aberto esperando
decisão, porque não custa uma tarde de código e custa **muito** de máquina.

**Fechado em 27/10/2026 — descartado por decisão do usuário:** o dicionário que ele já tem atende.
Nenhum trabalho foi feito nem será.

**Fechamento da ronda:** `app:testDebugUnitTest` **220 / 0**, `core:test` **103 / 0 / 2 pulados** (com
`-Pdicts`). Non-vacuidade **10/10** (`%TEMP%\opencode\r20_nonvacuous.py`).

---

## 10. Sexta ronda — o cartão de kanji  (21/10/2026)

O cartão do R17 voltou com dois pontos: um bug que apaga a seção de dicionário, e uma apresentação que
lê a leitura da palavra como se fosse de um kanji só. Bugs primeiro, um item por vez.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R28 | `[x]` | **A ficha do kanji diz «nenhum dicionário respondeu»** para todo kanji | bug (consulta) |
| R29 | `[x]` | **Um retângulo por kanji** quando o toque cai num composto, e a leitura de cada um | melhoria (ficha) |

### R28 — «Nenhum dicionário de kanji respondeu»  `[x]` (21/10/2026)

**Relato:** no cartão do kanji, «Nenhum dicionário de kanji respondeu a este caractere» para todo kanji
tocado — enquanto a mesma tela mostrava «Nesta tela: 同時 どうじに», ou seja, os dicionários estavam
respondendo às palavras.

**Causa (aparelho):** nenhum dicionário de kanji estava instalado. A frase não era mentira, mas também não
dizia nada: era a mesma para «nunca instalei», «desativei» e «escolhi um dicionário de palavras no painel»,
e nenhum dos três se resolve sozinho. Pior: a ficha chamava de «caractere» o que era uma falta de
dicionário.

**Bug latente que a mesma frase escondia:** `kanjiCard` passava ao motor a **mesma seleção** do painel
(`settings.dictionaryFilter`) — `engine.kanjiBreakdown(symbol, only = only)`. A seleção existe para as duas
seções falarem a mesma língua, mas só um dicionário de **kanji** tem verbetes de kanji; com um dicionário
de palavras escolhido, a lista filtrada não tinha de onde ler o kanji e o cartão responderia «nada» mesmo
com o KANJIDIC instalado. A leitura de um kanji solto (`withFurigana`) caía na mesma armadilha.

**Correção:** `LookupEngine.kanjiSources(only)` — a seleção é mantida quando ela **tem** um dicionário de
kanji (aí a escolha é real: «só o KANJIDIC em português» continua calado sobre o 淀, que essa versão não
traz); quando não tem nenhum, é **alargada** para os dicionários que têm. `kanjiBreakdown`, `withFurigana`
e `readings` passam por ela.

**A ficha agora se explica:** `KanjiEntry` carrega `dictionaryTitle` e o cartão mostra «Origem: …»; quando
ainda assim não há verbete, a nota diz **qual dos três motivos** foi — nada instalado, instalado mas
desativado, ou instalado e ativo sem esse caractere — em vez da frase única que culpava o caractere.

**Testes de regressão:** `DictionaryCompileTest` — com JMdict e KANJIDIC instalados, a seleção no JMdict
ainda responde 同 (pelo KANJIDIC, que o verbete nomeia) e a leitura do 淀 solto sai; a seleção no KANJIDIC
continua respondendo só ele. `KanjiCardTextTest` — os três motivos. `KanjiOverlayTest` — a linha da origem
no cartão. Non-vacuidade **5/5** (`%TEMP%\opencode\r28_nonvacuous.py`).

**O que não está coberto:** a ligação do serviço com a ficha (`kanjiCard`) não tem teste — a mesma lacuna
declarada de `FloatingOcrService`; o que o serviço escolhe (filtro, motivo, origem) é testado nas peças
que ele usa.

### R29 — A leitura de cada kanji do composto  `[x]` (21/10/2026)

**Relato:** ao tocar no 同 de 同時に, o retângulo marca só o 同 e a leitura ao lado dele é `どうじに` — a
leitura da **palavra**. Parece que 同 se lê `どうじに`. O pedido: o retângulo sobre os kanji do composto e
a leitura de cada um.

**O que o app sabia:** `Furigana.split` separa a leitura de uma palavra entre os **trechos de kanji**
(申し込み → 申 もう, 込 こ), mas não dentro de um trecho: 同時 → 同時 どうじ, porque o dicionário não diz
onde a leitura se parte. Partir `どうじ` em どう + じ seria palpite, e a regra do app é não escrever
palpite.

**Correção (a divisão):** `Furigana.perKanji` usa os **verbetes de kanji** como vocabulário e só aceita a
divisão que **fecha**: cada kanji do trecho recebe uma das suas leituras (音 ou 訓, em kana) e a soma tem
de ser exatamente a leitura do trecho. 同 ドウ + 時 ジ = どうじ ✓. 今日 きょう ✗ e 大人 おとな ✗ (nenhuma
divisão fecha) — nesses a ficha continua com a leitura da palavra inteira, e **nada** é escrito por
chute. O vocabulário vem de `Furigana.allReadings` (sem o limite de três que serve para um kanji sozinho) e
o motor o monta em `LookupEngine.wordReadings`, escolhendo dicionários como o `kanjiBreakdown`.

**Correção (a ficha e a marca):** a leitura sai de perto do kanji e passa a ficar **colada à palavra**, em
linha própria (`同時に どうじに`), com a divisão logo abaixo (`Leitura de cada kanji: 同 どう ・ 時 じ`); a
divisão só vira linha quando há mais de um kanji e cada parte é um kanji só. O retângulo da página passa a
cobrir os **kanji do composto** (`KanjiGrid.boxOf(from, to)`, `KanjiCard.span`): o toque marca o caractere
na hora e o cartão, quando chega, estende a marca — desde que o dedo ainda esteja no mesmo caractere, para
um cartão atrasado não pintar a marca de outro toque.

**Dependência declarada:** sem um dicionário de kanji instalado o R29 **não tem** de onde tirar as
leituras — é o mesmo KANJIDIC que o R28 manda instalar.

**Testes de regressão:** `FuriganaTest` (6) — a divisão que fecha, a que não fecha (今日/大人), o kanji
sem verbete, um kanji só, o vocabulário completo. `DictionaryCompileTest` — com o KANJIDIC de verdade,
同時に どうじに → 同 どう / 時 じ e 今日 きょう → nada. `KanjiGridTest` — a caixa da faixa cobre exatamente as
células. `KanjiCardTextTest` (3) — a marca sem o に, o kanji que tem palavra própria, a linha só com dois
kanji. `KanjiOverlayTest` (3) — a palavra antes da leitura na ficha, a marca que cresce para o composto e o
cartão atrasado que não mexe na marca. Non-vacuidade **6/6** (`%TEMP%\opencode\r29_nonvacuous.py`).

**O que não está coberto:** a ligação do serviço (`kanjiCard` chama `wordReadings` e `kanjiSpan`) não tem
teste, como as demais do `FloatingOcrService`. A linha da palavra usa a leitura com peso 1 e reticências
quando falta espaço — mas a largura real em tela estreita não foi medida num aparelho.

**Fechamento da ronda:** `app:testDebugUnitTest` **228 / 0**, `core:test` **112 / 0 / 2 pulados** (com
`-Pdicts`). Non-vacuidade **5/5** (R28) + **6/6** (R29).

## 11. Sétima ronda — dicionário de verdade e o padrão do kanji  (21/10/2026)

Dois pedidos novos, um de cada vez: um dicionário em português oferecido pelo próprio app, e uma ação
da bolha que abre o painel como um dicionário — com campo de consulta e sugestões.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R30 | `[x]` | **Dicionário em português (ggjz.org)** na lista do app, e a escolha do dicionário padrão do cartão de kanji | média |
| R31 | `[x]` | **Ação «Dicionário» na bolha**: consulta digitada, com sugestões dos dicionários instalados | grande (motor + painel) |

### R30 — O Jitendex em português e o padrão do kanji  `[x]` (21/10/2026)

**Pedido:** acrescentar à lista curada o dicionário português do ggjz.org (uma adaptação do Jitendex) e,
se ele fosse bem feito, deixar o usuário escolher qual dicionário responde ao **cartão de kanji**.

**O dicionário.** O endereço publicado em `ggjz.org` é um **descritor** (`yomitan-ptbr.json`), não o
`.zip`: o arquivo direto muda de nome a cada versão, o descritor não. Ele devolve
`https://www.ggjz.org/data/yomitan-ptbr.zip`, então o download em dois passos que o app já tinha
(descritor → `downloadUrl`) passou a servir sem código novo de rede. O arquivo conferido tinha
**39.532.631 bytes** e `sha256 22B5CE93…E3137874`; por dentro, `Jitendex PT-BR [2026-09-29]`, formato 3,
cerca de **211 mil verbetes**. 日本 → «Japão», 日本語 → «japonês» — é uma tradução de verdade, com
definições em português, e não uma lista de equivalentes soltos.

**A entrada na lista:** `DictionaryCatalog`, id `ggjz-pt`, tipo `TERMS`, idioma `pt`, 39 MB, nome
«Jitendex em português (ggjz.org)», atribuição nomeando o Jitendex/Stephen Kraus **e** o ggjz.org. A
descrição do Wikcionário deixou de dizer que era o único português. O motor de consulta não mudou: um
dicionário a mais é um arquivo a mais.

**O padrão do kanji (a parte que exigiu cuidado).** O título de um dicionário instalado carrega a data
da versão (`Jitendex PT-BR [2026-09-29]`): guardá-lo como escolha quebraria a cada atualização. A escolha
é guardada como **id instalado** (`AppSettings.kanjiDictionary`, chave `kanji_dictionary`) e resolvida
para o título na hora de consultar (`KanjiDefault.titleOf`). Só entram na lista os dicionários **ativos e
com dados de kanji**; um id que não existe mais (removido, desativado, ou um dicionário de palavras)
**cai em «todos os dicionários de kanji»** em vez de deixar o cartão mudo, e a tela limpa o valor velho
quando percebe. A tela «Dicionários» ganhou o cartão **«Dicionário de kanji»**, com um diálogo de opções
(«Todos os dicionários de kanji» + cada um pelo `displayName`, que separa as variantes pelo `sourceName`).

O filtro do **painel de palavras** continua sendo `settings.dictionaryFilter`: são duas perguntas
diferentes («qual dicionário explica esta frase» e «qual dicionário lê este kanji») e agora têm duas
respostas independentes. No serviço, `kanjiCard` passou a usar `wordOnly` no contexto de frase/palavra e
`kanjiOnly` na ficha e nas leituras por kanji.

**Testes de regressão:** `DictionaryCatalogTest` (3) — a entrada `ggjz-pt` é de termos, em português, e o
descritor é o endereço estável. `KanjiDefaultTest` (6) — id → título, «todos», dicionário desativado,
dicionário de palavras, id sumido, e a lista de candidatos. `DictionaryScreenTest` (+2) — o cartão e o
diálogo do padrão. `DictionaryCompileTest` — `imports the portuguese jitendex from ggjz`: compila o ZIP
real e responde 日本 e 日本語.

**O que não está coberto:** o download pela rede do ggjz (como o de todos os outros itens da lista) não é
exercitado num teste; e a ligação do serviço com o cartão (`kanjiCard`) continua sem teste, a mesma lacuna
declarada das demais ações do `FloatingOcrService`.

### R31 — A ação «Dicionário»  `[x]` (21/10/2026)

**Pedido:** uma ação nova na bolha que abre o menu de tradução, mas com um campo onde o usuário **digita
a consulta**; ela funciona como um dicionário de verdade, sugere outros usos do kanji a partir dos
dicionários instalados (digitar 日本 sugere 日本語, 日本人, …) e, como na tradução, consulta todos os
dicionários ou um só.

**A consulta inteira primeiro.** O motor segmenta uma frase por maior correspondência; aplicado a uma
consulta digitada, `にほん` viraria a palavra 日本 *ou* um pedaço dela, e `食べました` só chegaria em
食べる por deinflexão. `LookupEngine.search` tenta o texto **inteiro** como cabeçalho e como leitura
antes de cair na segmentação: `にほん → 日本`, `食べました → 食べる`. É a diferença entre um dicionário e
um OCR.

**As sugestões.** `CompactDictionary.keysStartingWith` acha o bloco do índice que começa com o prefixo
(limite inferior + max-heap das chaves mais curtas, para não varrer o dicionário inteiro), e
`LookupEngine.suggest` junta cabeçalhos e leituras, tira repetidos, tira a própria consulta (ela já é a
resposta acima) e respeita o mesmo filtro de dicionários da resposta. A **ordem** foi o problema desta
ronda: a ordem de bytes do índice colocava 日本鼬, 日本鶉, 日本鰻 na frente só porque os kanji deles têm
codepoint maior. A ordem por leitura ainda deixava 日本人 em décimo. A solução usa o que os dicionários já
sabem: o campo **score** (JMdict: 1.999.800 para palavra news1/ichi1, 999.800 para a segunda faixa,
negativo para o resto; Jitendex PT-BR: 200 / 0 / −1). Ordena por score decrescente, depois tamanho, depois
leitura — e o painel mostra **10** sugestões. Para 日本: 日本一, 日本海, 日本画, 日本語, 日本史, 日本紙,
日本酒, 日本中, 日本人, 日本製.

**O painel.** Nada de uma segunda janela: `ResultOverlay.showSearch` reconstrói o mesmo cartão em modo de
busca — título «Dicionário», campo de **uma linha** com a dica, as sugestões como chips, e as seções de
palavras, kanji e o seletor de dicionário que já existiam. O que **não** existe nesse modo: tradução,
«Copiar original», «Perguntar à IA», imagem, «Trechos» e «Tentar offline»; e o `applyReading` é pulado,
porque uma resposta do dicionário não pode escrever a leitura por cima da consulta digitada. O campo
espera **250 ms** de pausa (a análise de uma captura espera 800 ms), e a janela pede o teclado depois de
anexada — o painel normal o esconde de propósito. Tocar num chip preenche o campo e passa pelo mesmo
caminho de uma tecla, então há **um** lugar que decide quando consultar. Back/AÇÃO VOLTAR fecham a busca:
não há captura atrás dela.

No serviço, `searchDictionary` cancela a consulta anterior a cada tecla e só mostra a resposta se ela
ainda for da consulta atual; trocar de dicionário no seletor refaz a **consulta**, não uma captura; e
começar uma captura desliga o modo de busca (o painel volta a analisar texto reconhecido).

**Testes de regressão — motor** (`DictionaryCompileTest`, com JMdict e o Jitendex PT-BR reais):
`the search answers a whole word before segmenting it` (にほん → 日本, 食べました → 食べる);
`suggestions follow a typed prefix in headwords and readings` (日本 e にほん → os compostos, sem repetir
a consulta); `suggestions consult only the chosen dictionaries` (o filtro vale para as sugestões).

**Testes de regressão — painel** (`DictionarySearchPanelTest`, 7): o cartão é uma busca e não um
resultado (campo vazio, dica certa, sem cópia/IA/tradução, sem a grade de ações da captura); a pausa na
digitação chega ao app como consulta e não antes dela; as sugestões são chips e um chip preenche o campo e
pede a resposta dele; a caixa vazia pede para digitar em vez de dizer «nada encontrado»; o seletor de
dicionário funciona na busca; a consulta não é sobrescrita pela resposta; e VOLTAR fecha.
`ActionMenuTest` passou a exigir as **seis** ações, com o «Dicionário» entre 漢 e Ajustes.

**Non-vacuidade (5/5, mutações aplicadas e desfeitas):**

| mutação | teste que falha |
| --- | --- |
| id do catálogo `ggjz-pt` renomeado | `the portuguese jitendex is offered as a terms dictionary` |
| `KanjiDefault.titleOf` sempre nulo | `the chosen dictionary names the title the engine knows` |
| ordenação das sugestões invertida (score crescente) | `suggestions follow a typed prefix…` → `[日本國, 日本戰, …]` |
| `searching = false` em `showSearch` | 3 testes do painel (pausa, chips, caixa vazia) |
| item «Dicionário» removido da faixa | 3 testes do menu (nomes, seis ações, cada ícone age) |

**O que não está coberto:** a ligação do serviço (`onDictionarySearchRequested`, `searchDictionary`,
troca de filtro) não tem teste — a mesma lacuna declarada das demais ações do `FloatingOcrService`; o foco
do teclado depois de a janela ser anexada não foi visto num aparelho; e a ordem das sugestões depende do
campo `score`, então um dicionário que o deixe todo em zero cai no desempate por tamanho/leitura (defensável,
mas não conferido em aparelho).

**Fechamento da ronda:** `app:testDebugUnitTest` **246 / 0**, `core:test` **116 / 0 / 2 pulados** (com
`-Pdicts`). Non-vacuidade **5/5**.

## 12. Ajustes pedidos depois da sétima ronda  (22/10/2026)

Quatro itens novos, todos originados do uso do aparelho. São melhorias de usabilidade, não bugs:
cada um tem teste de regressão.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R32a | `[x]` | Espaço no teclado fecha o dicionário | trivial |
| R32b | `[x]` | Botão «×» ao lado do campo para limpar a consulta | trivial |
| R32c | `[x]` | Opção «Manter a última busca aberta no dicionário» nos ajustes | baixa |
| R32d | `[x]` | Dicionário identifica romaji dentro do possível | média |

### R32a — Espaço fecha o dicionário  `[x]` (26/10/2026)

**Relato:** apertar **espaço** no teclado para trocar de língua fechava o painel — tanto o do
dicionário como o da tradução. O painel fechava **antes de a lista de línguas aparecer**.

**Primeira tentativa, e por que não bastou:** o painel era `isClickable` com um
`setOnClickListener { stepBack() }` para que um toque fora do card fechasse a busca, e o Android
transforma **espaço** num clique em cima de um `ViewGroup` focável. O `dispatchKeyEvent` passou a
engolir o espaço, mas o painel continuou fechando: o espaço do teclado não vira key event para o
app — o teclado abre uma **janela própria** (a lista de línguas) e essa janela **rouba o foco** da
janela do painel. O `onWindowLost` (R9) lia essa perda como «o usuário saiu do app» e fechava tudo.

**Correção (duas frentes):**

1. O painel não é mais clicável. O toque fora do card passou a ser um `setOnTouchListener` no
   painel, que olha se o dedo caiu fora dos limites do card e chama o mesmo `stepBack()`. Um key
   event não aciona um touch listener, então **espaço não pode mais fechar o painel** — nem espaço
   de teclado físico, nem de teclado virtual.
2. A perda de foco passou a ser **confirmada** antes de fechar: ela só conta como «o usuário saiu»
   quando o foco não volta. Enquanto o teclado está na tela (`WindowInsets.Type.ime()`), a perda é
   tratada como uma janela do próprio teclado e o painel espera a lista de línguas fechar; se o
   usuário realmente sair, o foco não volta, o teclado vai junto e o painel fecha. Um teto de 5 s
   evita que o painel fique preso sobre outro app.

**Testes:**

- `BackAndOutsideTest.space does not step out of the panel` — painel com o foco, espaço
  `ACTION_DOWN`/`ACTION_UP`: nada de «repick»/«fechar».
- `FocusLossTest.a keyboard window taking the focus does not take the panel with it` — perda de foco
  com teclado na tela: o painel fica; quando o teclado sai (perda de foco real), ele fecha.
- `DictionarySearchPanelTest.space in the search box does not close it` — espaço no modo busca.
- Os testes de R9 continuam valendo: a perda de foco sem teclado fecha o painel; ganhar o foco não
  fecha; a lista de apps do «Perguntar à IA» não o leva junto.

**Non-vacuidade (2/2, mutações aplicadas e desfeitas):**

| mutação | teste que falha |
| --- | --- |
| painel volta a ser `isClickable` com o `setOnClickListener` | `space does not step out of the panel` → `[repick]` |
| a confirmação ignora o teclado (fecha na primeira perda) | `a keyboard window taking the focus…` |

**O que não está coberto:** a ligação entre `onApplyWindowInsets` e o teclado real não é vista no
Robolectric (não há janela de IME); o teste injeta `keyboardVisible`. E o teto de 5 s é um
compromisso: uma lista de línguas aberta por mais tempo que isso fecha o painel.

### R32b — Botão «×» de limpar  `[x]` (22/10/2026)

**Pedido:** um «×» ao lado do campo de texto para limpar toda a consulta de uma vez, em vez de apagar
caractere por caractere.

**Correção:** o campo de busca agora vem dentro de um `LinearLayout` horizontal com o botão «×» à
direita. Tocar nele chama `clearSearch()`, que limpa o texto e posiciona o cursor no início; o
`TextWatcher` dispara a busca vazia, que mostra «digite para consultar» em vez de «nada encontrado».

**Teste:** `DictionarySearchPanelTest.the clear button empties the field and asks for an empty answer`
— digita «日本», confirma que a busca foi feita, toca no «×» (pelo content description), confirma que
o campo ficou vazio e que a busca vazia foi pedida.

### R32c — Manter a última busca  `[x]` (22/10/2026)

**Pedido:** uma opção nos ajustes para manter a última busca aberta quando o dicionário é reaberto,
em vez de começar sempre vazio.

**Correção:** `AppSettings` ganhou `dictionaryKeepLastSearch` (booleano, desligado por padrão) e
`dictionaryLastQuery` (a última consulta digitada). Quando a opção está ligada, `onDictionarySearchRequested`
pré-preenche o campo com `dictionaryLastQuery` e `searchDictionary` grava cada consulta não vazia de
volta. Desligar a opção também apaga a query guardada.

**UI:** novo checkbox «Manter a última busca aberta no dicionário» nos ajustes, abaixo de
«Consultar os dicionários instalados».

**Teste:** `DictionarySearchPanelTest.the last query is pre-filled when passed to the search card` —
abre o panel com `initialQuery = "日本"` e confirma que o campo começa com «日本» e que a busca é
disparada imediatamente.

### R32d — Romaji  `[x]` (22/10/2026)

**Pedido:** o dicionário deve identificar romaji dentro do possível.

**Correção:** novo arquivo `core/dict/Romaji.kt` com um conversor Hepburn romaji → hiragana (melhor
esforço): sílabas básicas, dígrafos (kya, shu...), consoante dupla (kk → っ), vogais longas com acento
(ō → ou, ā → aa) e ん. `LookupEngine.search()` converte a consulta para hiragana quando ela é romaji,
tenta correspondência exata como headword e, se não achar, procura verbetes cuja leitura corresponda
via `suggest(..., includeReadingMatch = true)`. Assim `nihon` encontra 日本 (leitura `にほん`) e
`toukyou` encontra 東京 (leitura `とうきょう`).

**Teste:** `RomajiTest` (9 casos: vogais, sílabas, dakuten, dígrafos, sokuon, vogais longas, ん, texto
não-romaji inalterado, consulta mista, detecção de romaji) e `DictionaryCompileTest.search finds
Japanese words by romaji reading` (nihon → 日本, toukyou → 東京, tōkyō → 東京).

**O que não está coberto:** romaji sem acento de vogal longa é ambíguo (`tokyo` pode ser `ときょお`
ou `とうきょう`); o conversor trata `o` como `お`, então `tokyo` não encontra 東京. Use `toukyou` ou
`tōkyō`. A ligação do serviço para as novas configurações não tem teste dedicado — mesma lacuna
declarada das demais ações do `FloatingOcrService`.

**Fechamento:** `app:testDebugUnitTest` **251 / 0**, `core:test` **130 / 0 / 2 pulados** (com
`-Pdicts`).

---

## 13. Ronda 33 — pedidos de 26/10/2026

Cinco pedidos novos, vindos do uso do aparelho (Poco X5 5G). Dois são bugs de tela; os outros três
são melhorias. A ordem de trabalho foi a pedida: bugs primeiro, depois do mais fácil ao mais difícil.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R33a | `[x]` | Texto de «OCR flutuante ativo» cortado embaixo no botão | trivial |
| R33b | `[x]` | Popup padrão de seleção nos campos de texto (dicionário e tradução) | média |
| R33c | `[x]` | Pausar e retomar o OCR pela notificação, além de «Parar» | baixa |
| R33d | `[x]` | Remover as permissões já concedidas, com confirmação | baixa |
| R33e | `[x]` | Dicionários de onomatopeia e de katakana (português/inglês) | média |

### R33a — Texto cortado no botão  `[x]` (26/10/2026)

**Relato:** com o OCR ativo, o botão diz «OCR flutuante ativo — toque para reabrir os ajustes» e a
última linha aparece cortada embaixo — lia-se «…reabrir os» e o «ajustes» ficava fora.

**Causa:** o `btnStart` do `activity_main.xml` tinha `layout_height="56dp"` fixo. O rótulo normal
cabe numa linha; o rótulo de «ativo» quebra em duas ou três, e a altura não acompanhava.

**Correção:** `layout_height="wrap_content"` com `minHeight="56dp"`. O botão parado continua com
56 dp (o mínimo), e o de «ativo» cresce o quanto o texto precisar — em qualquer escala de fonte.

**Teste:** `StartButtonTest.the running label is not clipped` mede o layout do próprio texto contra
a altura útil do botão (`getLineBottom` da última linha × `height - paddings`). Antes da correção:
`the «OCR flutuante ativo — toque para reabrir os ajustes» label needs 41 px but the button only has
36`.

**Non-vacuidade:** voltar o `56dp` faz o teste falhar com a mensagem acima; com o `wrap_content`
ele passa.

**O que não está coberto:** o teste roda em Robolectric (mdpi, 320 dp); uma fonte do aparelho muito
maior muda a altura, não a conclusão — o `wrap_content` acompanha qualquer medida.

### R33b — Menu de seleção no painel  `[x]` (26/10/2026)

**Relato:** selecionar um trecho no campo do dicionário ou no texto da tradução não abria o popup
padrão do celular (Copiar, Recortar, Selecionar tudo).

**Causa:** o popup de seleção do Android pertence à janela de uma *activity*. O painel é uma janela
`TYPE_APPLICATION_OVERLAY` do serviço; nela o `ActionMode` flutuante normalmente não aparece — e em
muitos aparelhos nem as alças de seleção. Não é um bug de configuração do `EditText`: é uma limitação
da janela.

**Correção:** o painel desenha o próprio menu, dentro da própria janela, em
`overlay/SelectionToolbar.kt`:

- long press seleciona a palavra sob o dedo (`wordRange`: letras, dígitos, 「ー」 e 「々») e abre a
  barra com **Copiar · Recortar · Colar · Selecionar tudo**;
- a barra fica acima da linha selecionada, abaixo quando não há espaço, e nunca sai do painel;
- um véu transparente fecha a barra no toque seguinte; BACK fecha a barra **antes** de sair do
  painel; qualquer reconstrução do card (resultado novo, busca, erro) tira a barra da tela;
- «Recortar» e «Colar» só aparecem em campo editável; «Colar» só quando há algo na área de
  transferência; «Copiar»/«Selecionar tudo» ficam desabilitados quando não há o que copiar.

Está ligado ao texto reconhecido (editável), à tradução, às definições do dicionário e ao corpo da
ficha de kanji.

**Testes:** `TextSelectionTest`, 7 casos — long press seleciona e abre; Copiar põe a palavra na área
de transferência e fecha; Recortar tira do campo de busca; Colar escreve por cima da seleção;
Selecionar tudo mantém o menu; a barra fica dentro do painel; BACK fecha só a barra.

**Non-vacuidade:** trocar o `setOnLongClickListener` por `{ false }` derruba os 7.

**O que não está coberto:** as alças de arrastar do sistema continuam fora do painel — o menu é o do
painel, com as quatro ações que o popup padrão oferece. Num `TextView` não editável a seleção é feita
pelo long press, não por alças.

### R33c — Pausar e retomar pela notificação  `[x]` (26/10/2026)

**Pedido:** uma ação que pause o OCR sem encerrar, além do «Parar» que já existia.

**Correção:**

- `FloatingOcrService` ganhou `ACTION_PAUSE` e `ACTION_RESUME`; a notificação passou a ter dois
  botões — **Pausar/Retomar** e **Parar**. Pausado, o título vira «Yabai OCR pausado» e o texto
  explica que «Retomar» traz a bolha de volta.
- Pausar **não** encerra o serviço nem a sessão de captura: `isPaused` sobe, a análise e a busca em
  curso são canceladas (`busy = false`), e `OverlayController.setPaused(true)` tira da tela a bolha,
  o painel, os seletores, o alvo de arrastar e a confirmação de fechar.
- A bolha guarda a posição: retomar a devolve onde estava, sem pedir a captura de novo.
- Uma captura que termina depois da pausa não ressuscita a bolha (`restoreAfterCapture` respeita a
  pausa), e um toque em voo não dispara captura (`requestCapture` sai cedo quando pausado). O painel,
  o seletor de trechos e o leitor de kanji também recusam abrir enquanto pausados
  (`ensureResultPanel`, `showTextPicker`, `showKanji`), e `showResultFor` descarta a captura inteira —
  sem copiar para a área de transferência nem disparar análise.
- «Parar» continua encerrando de verdade; `onDestroy` limpa `isPaused`.
- Nos ajustes, o status mostra «Pausado» e o botão principal, «OCR flutuante pausado — toque em
  «Retomar» na notificação»; tocá-lo manda `ACTION_RESUME`.

**Testes:** `CaptureNotificationTest`, 7 casos (4 novos: a notificação ativa oferece «Pausar»;
pausar mantém o serviço vivo e troca a notificação para «Retomar»; retomar volta a «Pausar»;
`onDestroy` limpa a pausa). `PauseTest`, 4 casos (a bolha some e volta ao mesmo lugar; o painel vai
junto; o fim de uma captura não desfaz a pausa; um resultado que chega depois da pausa não abre o
painel).

**Non-vacuidade (3/3, mutações aplicadas e desfeitas):**

| mutação | testes que falham |
| --- | --- |
| `setPaused` deixa de esconder a bolha | `pausing hides the bubble…`, `a capture ending after the pause…` |
| `pause()` deixa de marcar `isPaused` | `pausing keeps the service…`, `destroying the service clears the paused state` |
| `ensureResultPanel` deixa de olhar a pausa | `a result arriving after the pause…` |

**O que não está coberto:** o `PendingIntent` da notificação não é disparado pelo sistema no teste (a
ação é chamada direto em `onStartCommand`); a ligação do serviço com o `OverlayController` real não é
vista no Robolectric — `PauseTest` usa o controlador isolado.

### R33d — Remover permissões  `[x]` (26/10/2026)

**Pedido:** poder tirar as permissões já concedidas, com confirmação.

**Correção:** os dois botões de permissão passaram a ter dois papéis. Sem a permissão dizem
«Conceder» e pedem como antes; com ela dizem **«Remover»** e abrem um diálogo que explica a
consequência (a bolha não aparece sobre os outros apps / o aviso de captura some) com **Cancelar** e
**Remover**. Confirmar abre a tela do sistema já no ponto certo: sobreposição em
`ACTION_MANAGE_OVERLAY_PERMISSION` com o pacote deste app, notificações em
`ACTION_APP_NOTIFICATION_SETTINGS`. O Android não deixa um app revogar as próprias permissões — quem
desliga o interruptor é o usuário, e é isso que o diálogo deixa claro.

**Testes:** `PermissionRemovalTest`, 6 casos — permissão concedida vira «Remover» (sobreposição e
notificações); o clique pede confirmação antes de abrir a tela do sistema (a mensagem do diálogo é
conferida, e nada foi aberto antes do «Remover»); confirmar abre a tela certa; cancelar não abre
nada; sem a permissão o botão continua concedendo.

**Non-vacuidade (2/2):**

| mutação | testes que falham |
| --- | --- |
| o rótulo volta a ser sempre «Conceder» | os dois `turns the button into «Remover»` |
| o clique abre a tela do sistema sem passar pelo diálogo | os dois `asks before opening…` e `cancelling the question…` |

**O que não está coberto:** a revogação em si (só o sistema faz) e a volta da tela do sistema; o
teste confere o `Intent` que sai e o estado do diálogo. Como o botão lê o estado real ao voltar
(`refreshPermissions` no resultado), o rótulo se corrige sozinho.

### R33e — Onomatopeia e katakana  `[x]` (27/10/2026)

**Pedido:** verificar se existem dicionários Yomitan de onomatopeia e de palavras em katakana, em
português e inglês, e adicioná-los se existirem.

**O que a verificação achou:**

| procurado | resultado |
| --- | --- |
| katakana / empréstimos (gairaigo) | **já coberto.** JMdict, Jitendex e o Jitendex PT-BR do ggjz listam os gairaigo como entrada (コンビニ, アルバイト, サラリーマン) com a marca de «geralmente escrito em katakana». Não existe dicionário Yomitan dedicado a katakana nos catálogos públicos; a única fonte específica, `gairaigo.jepang.org`, é site, não arquivo Yomitan. Nada a acrescentar. |
| onomatopeia em inglês | **existe, mas não pode entrar na lista.** O «Onomatoproject» (12,2 MB, JA→EN) só é distribuído dentro de uma pasta do Google Drive: não tem endereço estável por arquivo nem licença declarada. Uma entrada da lista curada que morre no próximo arrastar de pasta deixa o usuário com um botão que falha. Continua importável à mão pelo botão de importar. |
| onomatopeia em japonês | **existe e entrou.** `surasura` (0,1 MB, 1.421 entradas) é um arquivo único no repositório do MarvNC, com endereço estável (verificado: HTTP 200, 104.920 bytes). Definições em japonês, com exemplos, para as miméticas que o JMdict não tem. |

**Correção:** entrada `surasura-ja` («Onomatopeias (surasura)») na lista curada, como dicionário de
palavras (não de kanji), e `surasura.zip` incluído na lista de «compila todo dicionário oferecido».

**Testes:**

- `DictionaryCatalogTest.the onomatopoeia dictionary is offered as a word dictionary` — id, tipo,
  idioma e endereço exato do arquivo.
- `DictionaryCompileTest.the onomatopoeia dictionary answers words jmdict does not have` — compila
  (1.421 entradas), ぴくりぴくり sai como texto («…ふるえ動くさま。», sem marcação crua), a palavra
  **não** está no JMdict, e a frase «猫がぴくりぴくりと動いた。」 segmenta com ela.
- `DictionaryCompileTest.compiles every dictionary offered in the catalogue` — agora inclui o
  `surasura.zip`.

**Non-vacuidade:** trocar o id `surasura-ja` faz o teste do catálogo falhar; a compilação e a busca
já falhariam se o arquivo não fosse um Yomitan válido.

**O que não está coberto:** o download de verdade (a URL foi verificada à mão, não no teste) e a
licença do surasura, que não é declarada pelo autor — está dito na atribuição da entrada. O
Onomatoproject continua fora da lista; quem quiser importa o zip pelo botão de importar.

---

## 14. Ronda 34 — pedidos de 27/10/2026

Dois ajustes no cartão de kanji, os dois vindos da mesma captura: tocar no 技 de **剣技 けんぎ** mostrava
o significado de 技 («skill; art; craft…») e nunca o de 剣技, e a última linha, «Nesta tela:», não serve
para nada. São melhorias de apresentação, não bugs de consulta.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R34a | `[x]` | **A ficha do kanji não mostra o significado do composto** | baixa |
| R34b | `[x]` | **Tirar «Nesta tela:» da ficha** | trivial |

### R34a — O significado do composto  `[x]` (27/10/2026)

**Relato:** na ficha do 技, com a palavra **剣技 けんぎ** no cabeçalho, o texto do dicionário era
«skill; art; craft; ability; feat; performance; vocation; arts» — o significado de **技 sozinho**. O que
o composto significa (esgrima, arte da espada) não aparecia em lugar nenhum.

**Causa:** a ficha faz duas perguntas a dois dicionários diferentes. A leitura e a palavra vêm do
dicionário de palavras (`wordAt` guardava só o `surface` e a `reading` do acerto); o texto de baixo vem
do dicionário de **kanji**, que só tem caracteres isolados. A definição do acerto de 剣技 chegava junto
com o `TokenMatch` e era **descartada** antes de virar cartão.

**Correção:** `KanjiCardText.wordMeaning` devolve a definição do **mesmo** acerto mais estreito que dá
a leitura (a mesma regra de `wordAt`, não uma segunda escolha), e `KanjiCard.wordMeaning` é desenhado
logo abaixo da linha da palavra, **antes** das leituras 音/訓 — junto do composto a que pertence, para
não ser lido como mais um sentido do kanji isolado. A linha só aparece quando a ficha nomeia um
composto (`word != character`): para um kanji sozinho o verbete do KANJIDIC já diz o mesmo, e repetir
seria ruído.

O texto sai como o dicionário o renderiza, com os sentidos numerados e a remissão, quando há
(`1. sword skills; swordsmanship → see: 剣術 …`, conferido compilando o JMdict de verdade).

**Testes:**

- `KanjiCardTextTest.the meaning is the one of the word that reads the character` — a definição é a do
  acerto mais estreito (技 dentro de 剣技 responde 技; o 剣 da mesma palavra responde 剣技), e vem
  aparada.
- `KanjiCardTextTest.a character no word covers has no word meaning` — toque fora de qualquer palavra
  não inventa significado.
- `KanjiOverlayTest.the card shows the meaning of the compound beside the compound` — o texto do
  composto aparece **entre** a palavra e o 音: do kanji.
- `KanjiOverlayTest.the card shows the reading, the dictionary readings and the meanings` — o caso
  completo (palavra, significado da palavra, significado do kanji).

**Non-vacuidade (2/2, mutações aplicadas e desfeitas):**

| mutação | testes que falham |
| --- | --- |
| `wordMeaning` devolve sempre `""` | `the meaning is the one of the word that reads the character` |
| o cartão deixa de desenhar a linha | `…the meanings`, `…the meaning of the compound beside the compound` |

**O que não está coberto:** a ligação do serviço com a ficha (`kanjiCard`) continua sem teste, a mesma
lacuna declarada de `FloatingOcrService`; o que o cartão recebe é testado nas peças que ele usa. O
significado vem do dicionário de **palavras** escolhido no painel, então com um dicionário só em
japonês a linha sai em japonês — é a mesma língua do resto do painel, de propósito.

### R34b — «Nesta tela:» fora da ficha  `[x]` (27/10/2026)

**Pedido:** a última linha da ficha («Nesta tela: 剣技 けんぎ») não é usada; tirar.

**Correção:** saíram a linha, o campo `compounds` de `KanjiCard`, a função `KanjiCardText.compounds`, a
string `kanji_compounds` e a consulta da página inteira que só existia para montá-la
(`pendingKanjiText`/`engine.lookup(page)`). O efeito colateral é bom: tocar num kanji deixou de fazer
uma passada extra pelo dicionário sobre toda a tela, então a ficha chega mais rápida.

**Teste:** o caso `the card shows the reading, the dictionary readings and the compounds` foi
substituído pelo dos significados (R34a), e as asserções de «Nesta tela» saíram. **Não há teste de
regressão para uma remoção**: um teste que confere a ausência de um texto que já não existe passa por
construção, e seria vazio.

---

## 15. Ronda 35 — pedidos de 28/10/2026

Seis pedidos, um deles bug: as ações da barra de seleção não funcionam nos dois campos onde o usuário
mais seleciona texto (a busca do dicionário e o texto da tradução). Os outros cinco são melhorias, e
dois deles são antes de tudo **verificações de possibilidade** (ReadEra e busca pela tradução) — a
resposta honesta pode ser «não dá» ou «dá com um custo que não vale a pena», e nesse caso o item fecha
com a justificativa em vez de código.

Ordem de trabalho: **bug primeiro**, depois do mais fácil ao mais difícil, como nas rondas anteriores.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R35a | `[x]` | **Copiar/colar/selecionar tudo não funcionam** na busca do dicionário e na tradução | bug |
| R35b | `[x]` | O «×» da busca vira **colar** quando o campo está vazio | trivial |
| R35c | `[x]` | **Bolha com posição livre**, não presa às bordas | baixa |
| R35d | `[x]` | Opção para **ignorar o texto entre parênteses** na consulta (双(そう)眸(ぼう) → 双眸) | média |
| R35e | `[x]` | **Integração com o ReadEra**: abrir a palavra direto no dicionário do app | estudo + média |
| R35f | `[x]` | **Pesquisar pela tradução** no dicionário — índice invertido sob demanda, no Android e no desktop | estudo + alta |

### R35a — Copiar, colar e selecionar tudo não funcionam  `[x]` (28/10/2026)

**Relato:** no **campo de busca do dicionário** e no **texto da tradução**, as opções da barra
(Copiar, Recortar, Colar, Selecionar tudo) não fazem nada.

**Causa — e não era coisa do aparelho:** o cartão do painel tem `elevation = 12dp`; a barra de seleção
tinha `10dp`. No Android, a ordem de desenho **e a ordem de entrega do toque** seguem o Z: a barra
ficava **debaixo do cartão**. Nenhum toque chegava aos botões — eles nem apareciam, cobertos pelo
cartão — e o toque ia para o texto do cartão atrás deles. Os testes do R33b não viam isso porque
chamavam `performClick()` direto no botão, sem passar pela entrega de toque da janela. Um teste com
toque de verdade (abaixo) reproduz a falha na hora.

**Correção:** a barra passou a `elevation = 20dp`, acima do cartão, com o motivo escrito ao lado do
valor para ninguém baixar de novo.

**Endurecimento junto (segunda suspeita):** as ações deixaram de depender da seleção **viva** da view.
Ao abrir, a barra guarda o texto e o intervalo escolhidos (`rememberSelection`) e, se o IME ou a janela
reescreverem a seleção entre o toque longo e o toque no botão, usa o intervalo lembrado — desde que o
texto seja ainda o mesmo. «Colar» cai para o cursor vivo, depois para o intervalo lembrado, e por fim
acrescenta ao texto. «Selecionar tudo» atualiza a lembrança, para que um «Copiar» seguinte não volte à
palavra antiga.

**Testes (`TextSelectionTest`, 4 novos):**

- `enquanto o menu está aberto, o toque vai para o botão e não para o véu` — liga o painel a uma
  janela de activity de verdade (o clique é um runnable postado, que só roda com looper) e entrega
  `ACTION_DOWN`/`ACTION_UP` **nas coordenadas do botão Copiar**: o texto tem de ir para a área de
  transferência e a barra sair. Falha com a barra a 10dp.
- `Copiar copia o que foi selecionado mesmo se a seleção se perder`, `Recortar tira a palavra lembrada…`
  e `Colar escreve sobre a palavra lembrada…` — depois do toque longo a seleção é apagada
  (`Selection.removeSelection`, como o IME faria) e a ação ainda tem de valer sobre a palavra escolhida.

**Non-vacuidade (2/2, mutações aplicadas e desfeitas):**

| mutação | testes que falham |
| --- | --- |
| a barra volta a `elevation = 10dp` | `enquanto o menu está aberto, o toque vai para o botão…` |
| a ação só olha a seleção viva (sem o intervalo lembrado) | os três «mesmo se a seleção se perder» |

**O que não está coberto:** o comportamento do GBoard no aparelho em si. A causa do relato está
fechada e provada; o intervalo lembrado cobre a seleção reescrita pelo IME, que é a outra suspeita
registrada, mas essa não foi reproduzida — se o usuário ainda vir falha, o próximo passo é o log do
intervalo no toque longo e no toque do botão.

### R35b — O «×» da busca vira «Colar»  `[x]` (28/10/2026)

**Pedido:** quando o campo do dicionário está vazio, o botão ao lado deixa de ser «×» e passa a colar o
que está na área de transferência (o mesmo texto que um Ctrl+C copiou).

**Correção:** o botão passou a ter dois estados, decididos pelo texto do campo:

- **com texto:** «✕» limpa a busca, como antes;
- **sem texto:** «Colar» põe a área de transferência no campo e deixa o `TextWatcher` agendar a
  consulta — o mesmo caminho da digitação, sem um segundo jeito de pedir ao dicionário. Sem nada para
  colar, um aviso curto («Não há texto copiado para colar.») em vez de silêncio.

O `contentDescription` acompanha cada estado («Limpar a consulta» / «Colar o texto copiado na
consulta»), e a troca acontece no `afterTextChanged`, então vale também para o primeiro caractere
digitado e para o campo que abre pré-preenchido. Texto colado é aparado e tem as quebras de linha
transformadas em um espaço: o campo é de uma linha, e um trecho copiado de um livro vem quebrado.

**Um bug achado no caminho:** `configureSearchEditor()`/`configureResultEditor()` trocam o `inputType`
do editor compartilhado, e o Android **re-aplica o texto** ao fazer isso — o que chega ao
`TextWatcher` como uma edição. Resultado: abrir a busca agendava uma consulta vazia (e a busca
pré-preenchida era pedida **duas vezes**, uma delas pela pausa). As duas configurações passaram a
rodar dentro de `asPanelEdit`, o mesmo sinal que o painel já usa quando escreve sozinho, e `showSearch`
descarta uma análise agendada pelo cartão anterior.

**Testes (`DictionarySearchPanelTest`, 3 novos + 1 asserção nova):**

- `the button beside an empty box pastes the clipboard and searches` — com «  剣技\n» na área de
  transferência, o clique escreve `剣技`, o botão volta a ser «✕» e a pausa pede a consulta;
- `the button turns back into Colar when the field is emptied` — rótulo e descrição mudam nos dois
  sentidos (vazio → «Colar», com texto → «✕»);
- `an empty clipboard says so instead of emptying the box` — aviso na tela, campo intacto e nenhuma
  consulta;
- `the last query is pre-filled when passed to the search card` ganhou a conferência de que a pausa
  **não repete** a consulta já pedida ao abrir.

**Non-vacuidade (2/2, mutações aplicadas e desfeitas):**

| mutação | testes que falham |
| --- | --- |
| o botão nunca vira «Colar» (`empty = false`) | os três testes do R35b |
| `configureSearchEditor` deixa de rodar como edição do painel | `an empty clipboard…` e `the last query is pre-filled…` (a consulta sai duas vezes) |

**O que não está coberto:** a área de transferência de verdade do aparelho (o Robolectric simula) e o
toque com o IME aberto, que é o mesmo terreno do R35a.

### R35c — Bolha livre  `[x]` (28/10/2026)

**Pedido:** a bolha não fica no meio da tela — só nas bordas. Poder arrastá-la para onde quiser.

**Causa:** `putBubbleOnEdge()` chamava `snapToEdge()` no fim de **todo** arrasto — e também nas duas
respostas «não» da pergunta de encerrar. O `snapToEdge` joga o X para a margem esquerda ou direita (o Y
já era livre). Era de propósito — bolha flutuante no meio de um quadrinho atrapalha a leitura —, mas o
usuário quer poder escolher.

**Correção:**

- **«Prender a bolha nas bordas»** nos ajustes (`AppSettings.bubbleOnEdge`, chave `bubble_on_edge`),
  **desligada** de fábrica: o padrão é a posição livre.
- `OverlayController` recebe `pinnedToEdge: () -> Boolean` — uma lambda, não um valor: a tela de ajustes
  pode mudar a escolha com o serviço rodando, e é o **próximo** arrasto que tem de obedecer. O construtor
  padrão lê `AppSettings`.
- `settleBubble(x, y)` decide o repouso: **presa** → `snapToEdge` (o comportamento de antes); **livre** →
  `clampBubble`, que só impede a bolha de sair da tela (4 dp de folga, para a borda continuar alcançável).
- `restingX`/`restingY` guardam o último repouso. `endDrag(bubbleX, bubbleY, landX, landY)` separa **onde
  a bolha foi desenhada** — é isso que julga o ✕, porque o que se vê chegando é o que acontece — de
  **onde o dedo soltou**, que é onde ela fica no modo livre. Antes, uma bolha puxada para o ✕ e respondida
  com «não» era deixada em cima dele.
- `restBubble()` devolve a bolha ao último repouso no arrasto cancelado (`ACTION_CANCEL`) e nas duas
  respostas «não»; antes ela ia sempre para a borda, mesmo em modo livre.
- `hideBubble()` esquece o repouso: a próxima bolha nasce no canto padrão, não onde a anterior morreu.

**Testes (`BubblePlacementTest`, 7 novos):**

- `em modo livre a bolha fica onde o dedo soltou` — arrasto até o meio da tela, pelo `OnTouchListener` de
  verdade (`ACTION_DOWN`/`MOVE`/`UP`), e a janela fica exatamente ali;
- `prender nas bordas continua grudando na lateral` — a opção não removeu o comportamento antigo: mesmo
  arrasto, X na margem direita e Y preservado;
- `cancelar o arrasto devolve a bolha ao lugar de antes` — depois de um repouso no meio, um `ACTION_CANCEL`
  em outro ponto não move nada;
- `pausar e retomar no modo livre nao move a bolha` — `setPaused(true)`/`false` mantém o repouso;
- `a escolha comeca livre e e guardada nos ajustes` — padrão desligado e persistência;
- `o controlador obedece a escolha guardada` — com a opção gravada, o construtor padrão (o que o serviço
  usa) encaixa na lateral;
- `a opcao dos ajustes grava a escolha` — a caixa de `MainActivity` mostra o padrão e grava o toque.

**Non-vacuidade (3/3, mutações aplicadas e desfeitas):**

| mutação | testes que falham |
| --- | --- |
| `settleBubble` volta a chamar `snapToEdge` sempre | `em modo livre a bolha fica onde o dedo soltou` |
| `settleBubble` chama `clampBubble` nos dois modos | `prender nas bordas continua grudando na lateral` e `o controlador obedece a escolha guardada` |
| `restBubble` encaixa incondicionalmente (`if (true \|\| restingX == Int.MIN_VALUE)`) | `cancelar o arrasto devolve a bolha ao lugar de antes` |

**O que não está coberto:** só Robolectric. Densidade, barras do sistema e o recorte real da tela do Poco
X5 não foram vistos; o teste usa 320×640 mdpi, onde `dp` e pixel coincidem. O arrasto a partir do repouso
livre é o caso testado; arrastar a bolha para **fora** da tela e soltar não tem teste próprio — quem
segura é o `clampBubble`, que já existia.

### R35d — Ignorar o que está entre parênteses  `[x]` (28/10/2026)

**Pedido:** uma opção para o dicionário consultar **sem as leituras impressas entre parênteses**:
`双(そう)眸(ぼう)` deve ser procurado como `双眸`.

**Nuance que decide o desenho:** não dá para apagar tudo que está entre parênteses — `(笑)` e `(2024)`
fazem parte do texto. A regra é remover só o grupo cujo conteúdo é **somente kana**, com o par completo,
aceitando `（）` e `()` e a forma intercalada, um grupo por kanji. `（）` vazio, `（ねこ1）`, `(cat)` e
parêntese sem par ficam onde estão: um parêntese só é leitura quando os caracteres dentro dele dizem
isso — o mesmo critério que `Furigana` usa para a leitura já impressa na página. A consulta muda; o texto
exibido e o texto copiado **não**.

**Correção:**

- `DictionaryQuery.stripReadings(text)` (core): uma passada pelos code points, sem regex e sem
  normalizar nada além de remover os grupos de leitura.
- `AppSettings.stripSearchReadings` (chave `strip_search_readings`), desligado por padrão, e
  `AppSettings.dictionaryQuery(query)` — **uma** função decide a política, para a busca não obedecer pela
  metade.
- `FloatingOcrService.runSearch` passa `settings.dictionaryQuery(query.trim())` ao motor e continua
  exibindo `query`: a caixa mostra o que foi digitado, e o que o dicionário recebeu é o texto limpo.
- Interruptor **«Ignorar as leituras entre parênteses na busca»** com uma linha de explicação em
  **Gerenciar dicionários**, ao lado dos dicionários porque é a eles que a consulta é feita.

**Onde vale — e onde não:** só na **busca digitada/colada**, que é «a consulta» do pedido. A análise de
um texto reconhecido continua entregando a frase ao motor **como ela está**: as posições dos matches são
o que o toque no kanji e a escrita do furigana usam sobre o texto original, e limpar a frase antes
moveria todos os intervalos. Quem lê rubi impresso no mangá continua com o caminho de sempre (o painel
mostra a leitura como texto, e o `Furigana` já trata a leitura separada); para consultar a palavra
isolada, copiar/colar é o caminho que esta opção atende.

**Testes (core, `DictionaryQueryTest`, 9):** leitura em `（）` e em `()` sai, inclusive uma por kanji
(`職員（しょくいん）さんに聞（き）いた` → `職員さんに聞いた`); `（職員）`, `(cat)`, `（ねこ1）` e `（ねこ ま）`
ficam; `（）` e `()` vazios ficam; parêntese sem o par (aberto, fechado, ou de estilos trocados) fica e
não come o resto da frase; katakana e `ー` contam como leitura (`コーヒー（こーひー）`); uma consulta que
é só a leitura vira vazia; texto sem parênteses passa intacto.

**Testes (app, `DictionaryStripReadingsTest`, 4):** desligada, a consulta é exatamente a digitada;
ligada, perde as leituras nos dois estilos de parêntese; parêntese que não é leitura continua na
consulta; a caixa de **Gerenciar dicionários** mostra o padrão e grava o toque.

**Non-vacuidade (4/4, mutações aplicadas e desfeitas):**

| mutação | testes que falham |
| --- | --- |
| `dictionaryQuery` ignora a política e devolve a consulta crua | `on, the query loses its readings in both bracket styles` |
| o ramo do parêntese ASCII some (`(` deixa de abrir grupo) | `a kana reading in ascii brackets is dropped too` e `katakana and the prolonged sound mark count as a reading` |
| a guarda de grupo vazio some (`kana > 0` deixa de ser exigida) | `an empty bracket stays` |
| o `setOnCheckedChangeListener` da caixa some | `the choice on the dictionaries screen is saved` |

**O que não está coberto:** o caminho completo «texto colado → dicionário de verdade responde» não tem
teste: o `runSearch` que liga a política ao motor está no serviço, cuja fiação já é uma lacuna conhecida
(as duas pontas — `dictionaryQuery` e `stripReadings` — são testadas). O comportamento da caixa com o
teclado aberto é o mesmo terreno do R35a, não reproduzido em Robolectric.

### R35e — Integração com o ReadEra  `[x]` (28/10/2026)

**Pedido:** o ReadEra tem a opção de abrir a palavra selecionada num dicionário/tradutor instalado;
querer que o Yabai OCR apareça nessa lista e abra a palavra já consultada.

**O que os prints responderam:** a lista «Aplicativos disponíveis» do ReadEra mostra Google Play
Services, Google Tradutor, Inter, **Japanese Dictionary Takoboto**, Leitor QR, Ligar ao Windows,
LinkedIn, Mensagens, Mercado Pago e Messenger — e a lista «Aplicativo de dicionário» oferece ainda o
Chrome com a URL `https://www.google.com/search?q=define:{text}` e o Google Tradutor com o texto
`波及`/Hakyū, que abriu com «efeito cascata». Ou seja: o ReadEra **manda texto para apps** e monta a
lista pelo que eles declaram aceitar. Não há API proprietária nem lista fechada; faltava o app declarar
a porta certa.

**O que ficou construído:** `LookupActivity`, uma tela leve que **não depende do serviço flutuante**
(nem da permissão de captura). Ela responde às duas portas padrão:

| porta | filtro no manifesto | de onde vem o texto |
| --- | --- | --- |
| `ACTION_PROCESS_TEXT` | `text/plain` | `EXTRA_PROCESS_TEXT` — o «Processar texto» do menu de seleção |
| `ACTION_SEND` | `text/plain` | `EXTRA_TEXT` — a folha de compartilhamento |

Em vez de uma segunda tela de resultado, a activity põe o **próprio painel** em modo busca
(`ResultOverlay.showSearch`): mesma caixa, mesmos cartões de palavra, mesma quebra de kanji, mesmo
seletor de dicionários. A consulta é a `runSearch` do serviço, copiada para a activity sem o caminho de
captura — `LookupEngine.search`/`kanjiBreakdown`/`suggest`, o filtro `dictionaryFilter`, e o R35d
aplicado por `settings.dictionaryQuery`. Como o painel agenda a busca a partir do campo (a pausa da
digitação), o texto do intent entra pelo mesmo caminho de uma tecla: não há um segundo jeito de pedir
ao dicionário. `onNewIntent` reabre a mesma tela com a nova palavra, e a tela é `singleTop`,
`excludeFromRecents`, para uma segunda seleção não empilhar activities.

**Testes (`LookupIntentTest`, 6):**

- `ACTION_PROCESS_TEXT opens the search with the word` e `ACTION_SEND opens the search with the shared word`
  — o intent vira o texto do campo de busca;
- `the word is trimmed and a foreign action carries nothing` — espaços e quebra de linha saem, e uma
  ação estranha (ou intent nulo) não inventa consulta;
- `both text doors reach the lookup screen` — pergunta ao `PackageManager`, como o ReadEra faz, quais
  activities respondem a cada ação com `text/plain`: `LookupActivity` tem de estar nas duas listas;
- `an intent without text still opens a usable search box` — sem texto a tela abre como busca vazia,
  não como activity em branco;
- `the screen keeps its content off the system bars` — a tela mira o Android 16 e desenha de borda a
  borda; as barras viram padding do host (63dp em cima e 48dp embaixo no teste), senão o cabeçalho
  nasce debaixo do relógio.

**Non-vacuidade (3/3, mutações aplicadas e desfeitas):**

| mutação | testes que falham |
| --- | --- |
| tira o filtro `PROCESS_TEXT` do manifesto | `both text doors reach the lookup screen` |
| `incomingText` devolve sempre `""` | os dois «opens the search with…» e o do `trim` |
| o listener de insets deixa de aplicar o padding | `the screen keeps its content off the system bars` |

**O que falta no aparelho:** confirmar que «Yabai OCR» passa a aparecer na lista do ReadEra e que tocar
nele abre a palavra. O teste prova que o Android resolve o intent para a tela; o ReadEra pode filtrar a
lista por rótulo ou por algum outro sinal, e só o Poco X5 responde a isso. Se ele não aparecer, o
próximo passo é registrar o intent que o ReadEra manda (um filtro de teste que anota ação, tipo e
extras), não construir outra tela às cegas.

**Confirmado no aparelho (29/10/2026):** «Yabai OCR» aparece na lista e abre a palavra; a tela cheia
foi a única coisa que o usuário pediu para trocar — o painel por cima do leitor está no **R37b**.

### R35f — Pesquisar pela tradução  `[x]` (07/11/2026)

**Pedido:** no dicionário, poder procurar pela **tradução** — digitar «espada» e achar 剣 / 刀 — com um
botão que escolha entre procurar «o original em japonês» e «a tradução». O pedido voltou depois de a
primeira medição (28/10) ter fechado o item: o usuário quis a função de qualquer forma, nas duas
versões (Android e desktop), desde que o custo só fosse pago por quem a liga.

**O que a primeira medição tinha medido:** procurar «espada» nas definições **a cada consulta**
significava ler as ~430 mil entradas do Jitendex PT-BR (3,3 s de varredura no PC) — inviável por
tecla. O piso de um índice invertido «ingênuo» (4 bytes por ocorrência, chave de 40 bytes, sem
stopwords além de 1–2 letras) foi estimado em **≈40 MB por dicionário**, e por isso o item foi
fechado. A medição estava certa para aquele desenho; o que mudou foi o desenho.

- **Causa raiz (do custo):** um índice invertido guardado como lista de ids brutos por palavra paga
  uma posição por ocorrência. Como o mesmo verbete repete «espada» em vários sentidos, as listas
  ficam longas e esparsas — daí os 40 MB.
- **Correção.**
  - `TranslationIndex` (core) grava um **sidecar** ao lado do dicionário, na primeira consulta por
    sentido, com postings comprimidos em **runs** (`palavra → runs de ids consecutivos`), chaves
    ordenadas com offsets e um `terms.invmeta` com o fingerprint de `terms.dat` (tamanho, mtime e
    nº de entradas). Nada disso entra no `terms.dat` nem no formato do dicionário: um sidecar
    apagado é reconstruído, e um dicionário atualizado invalida o índice sozinho.
  - A construção lê as definições em **chunks de 150 mil pares** e faz merge externo, para não
    estourar a memória do celular; o progresso é reportado ao painel.
  - **Tokenização normalizada:** minúsculas, acentos removidos (NFD), tokens de 1 letra e stopwords
    PT/EN/ES fora. `agua` e «água» acham o mesmo verbete.
  - **AND:** todos os tokens da consulta precisam estar na definição — senão «espada longa» traria
    meio dicionário. Os postings são intersectados do menor para o maior, e no máximo 20 mil ids
    são decodificados por consulta, com a ordem final pelo `score` do dicionário.
  - `LookupEngine.searchTranslation` mantém um índice aberto por dicionário e fecha tudo em
    `close()`.
  - **Android:** botão «🔤 Palavra / 🔄 Tradução» no cartão de busca (`ResultOverlay`), preferência
    `AppSettings.searchByTranslation`, aviso «Pesquisando pelo sentido… o primeiro uso de cada
    dicionário prepara um índice» e mensagem de vazio própria (uma consulta por sentido não é «uma
    frase sem palavras conhecidas»).
  - **Desktop:** o mesmo botão na «Busca Rápida» e no «Consultar Dicionários», preferência
    `DesktopSettings.searchByTranslation`, busca em `Dispatchers.Default` e status
    «Preparando o índice de tradução… %».
- **Arquivos.** `core/…/dict/TranslationIndex.kt` (novo) · `core/…/dict/LookupEngine.kt` ·
  `core/…/dict/CompactDictionary.kt` · `desktop/…/QuickDictWindow.kt` ·
  `desktop/…/DictionaryManagerWindow.kt` · `desktop/…/DesktopSettings.kt` ·
  `app/…/overlay/ResultOverlay.kt` · `app/…/dict/DictionarySearch.kt` · `app/…/AppSettings.kt`

Testes: **153 no core** (26 pulados, os que pedem dicionário/Lens de verdade), **96 no desktop** e
**397 no app**, sem falhas. Os novos são `TranslationIndexTest` (9), `TranslationIndexRealTest` (1,
mede um `.yabai` de verdade e é pulado sem `-PcompiledDicts`), `DesktopSettingsTest` (1) e
`DictionarySearchPanelTest` (3). As regras que mais doem foram conferidas por mutação:

| mutação | teste que acusa |
| --- | --- |
| intersecção trocada por «qualquer token» (união) | `every query token must be in the definition` |
| `normalize` sem remover acentos | `an accented query finds the unaccented definition and vice versa`, `tokens are lowercased and lose their accents` |
| fingerprint do dicionário ignorado ao abrir o sidecar | `an index whose dictionary changed is rebuilt instead of trusted` |
| nenhum progresso `building` antes do primeiro chunk | `the sidecar is written beside the dictionary and reports a one-time build` |
| mensagem de vazio sempre «nenhuma palavra deste texto» | `a translation search opens with its own hint and empty message` |
| botão do modo sem inverter o estado | `the mode button starts on Palavra and flips to Traducao` |
| `searchByTranslation` do desktop sempre `false` | `searchByTranslation defaults to false and persists the switch` |

**Medição real do índice** (teste `-PcompiledDicts`, Jitendex inglês: 436.555 entradas, 51 MB):
índice de **310.452 palavras em 7,0 s**, 8.120 KiB de postings + 4.422 KiB de chaves (≈12,5 MB no
disco), reabertura em **11 ms**; `sword` devolve **568 verbetes**, entre eles 剣. No Jitendex PT-BR
(ggjz, 68 MB) o sidecar ficou em **14,8 MB**.

Ponta a ponta, com o app real (`--minimized`) e o Jitendex PT-BR: «Busca Rápida» pela bandeja →
botão «🔤 Palavra» virou «🔄 Tradução» e o campo passou a pedir «Pesquisar pelo sentido (ex.: espada)»
→ «espada» mostrou «Preparando o índice de tradução… 0%», 27% e 55% nos quadros seguintes, e
terminou em **2 min 18 s** (23:54:56 → 23:57:14), com o sidecar de 14,8 MB gravado; a consulta
seguinte respondeu na hora. Depois de **fechar e reabrir a janela e reiniciar o app**, o modo voltou
como «🔄 Tradução» (preferência lembrada) e «espada» trouxe 刀【かたな】e 剣【けん】do Jitendex PT-BR;
`agua`, sem acento, trouxe 泉【いずみ】e 管【かん】em menos de 1,5 s, **sem** o aviso de preparação —
prova de que o índice foi reutilizado. `settings.properties` foi restaurado com o SHA-256 conferido.

Limitação conhecida: o índice existe **por dicionário** e ocupa espaço em disco (≈12–17 MB por
dicionário grande) até o dicionário ser removido — ele não é apagado quando o modo é desligado; a
busca por sentido é por **palavra inteira** (não há stemming: «espadas» não acha «espada»), e a
ordem dentro de cada dicionário é o `score` dele, não a proximidade do sentido.

---

## 16. Ronda 36 — os dois bugs de 28/10/2026

Os dois vieram do mesmo lugar — o menu de seleção do R33b — e foram relatados juntos com os prints do
ReadEra. O primeiro é corrupção de dado (o texto sumia sem ir para lugar nenhum); o segundo é uma
função que o Android dá de graça em qualquer campo e que no painel não existia.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R36a | `[x]` | **«Recortar» apaga o texto e não o põe na área de transferência** | bug |
| R36b | `[x]` | **Arrastar para estender a seleção**, com alças nas pontas | média |

### R36a — «Recortar» não copiava  `[x]` (28/10/2026)

**Relato:** «Recortar só está retirando o texto, o texto recortado não está ficando no ctrl + v».

**Causa:** `cutSelection()` fazia metade do trabalho — `editable.delete(...)` — e não a outra metade.
Recortar é copiar **e** apagar; sem o `setPrimaryClip`, a palavra saía do campo e a área de
transferência continuava com o que havia antes. O «Colar» seguinte, então, colava outra coisa, o que
fazia parecer que o recorte tinha funcionado pela metade.

**Correção:** a linha que faltava, antes do `delete`, com `CLIP_LABEL` igual ao do «Copiar» — os dois
caminhos deixam o mesmo rótulo na área de transferência:

```kotlin
clipboard?.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
editable.delete(range.first, range.last + 1)
```

**Teste (`TextSelectionTest`):** `Recortar toma a palavra do campo e a deixa na área de transferência` —
a área de transferência começa com «antigo», o campo com «gato preto», toque longo em «preto» e
«Recortar»: o campo tem de ficar «gato » **e** a área de transferência tem de ficar «preto». As duas
pontas, porque só uma delas já passava.

**Non-vacuidade:** tirada a linha do `setPrimaryClip`, o teste falha; reposta, passa.

### R36b — Arrastar para estender a seleção  `[x]` (28/10/2026)

**Relato:** «selecionar uma palavra e arrastar para o lado não vai mudando a seleção conforme vai
arrastando e também não tem uma opção para arrastar para o lado como funciona no celular normalmente».

**Causa:** as alças do Android pertencem à janela da activity. O painel é uma janela
`TYPE_APPLICATION_OVERLAY` do serviço; ali o sistema não desenha as alças nem entrega o arrasto, e o
toque longo deixava a seleção presa na palavra que ele achou. O R33b tinha resolvido o menu; faltava o
gesto.

**Correção:** duas alças desenhadas pelo próprio painel — `startHandle`/`endHandle`, marcadas por
`START_HANDLE_TAG`/`END_HANDLE_TAG`. Decisões que valem registro:

| decisão | por quê |
| --- | --- |
| área de toque de 32dp, círculo de 16dp | o círculo visível é pequeno de propósito; quem recebe o dedo é a view maior em volta |
| `ACTION_DOWN` só esconde a barra; a borda anda no `ACTION_MOVE` | mover no `DOWN` colapsava a seleção quando as alças eram desenhadas para um layout que já tinha mudado; o dedo que só encosta não deve perder a palavra |
| a barra fica **transparente** durante o arrasto e volta no `UP` | ela ficava exatamente sobre o texto que o usuário estava tentando pegar. `alpha = 0` e não `GONE`: `GONE` esconderia a própria alça e, com a barra focada fora da hierarquia, a janela pode perder o foco e o IME reescrever a seleção |
| `applyEdge` mantém a outra ponta | estender para a direita não pode mover o começo, e vice-versa |
| a seleção lembrada é atualizada a cada movimento | se o IME reescrever a seleção, «Copiar» usa a frase que o usuário construiu, não a palavra original |
| `getLineForVertical` + `getOffsetForHorizontal` | `Layout.getOffsetForPosition` é oculto no SDK; o par escolhe a mesma linha que o toque longo escolhe |
| alças adicionadas por último + `bringToFront()` | ficam acima do véu e da barra, que têm `elevation` |
| `hide()` remove as alças | o menu sair não pode deixar dois círculos flutuando sobre o painel |

`showFor` e `selectEverything` chamam `showHandles`, então a alça existe tanto para a palavra do toque
longo quanto para «Selecionar tudo».

**Testes (`TextSelectionTest`, 5 novos; a classe fica com 17):**

- `arrastar a alça do fim estende a seleção até o resto da frase` — toque longo em «gato» de «gato
  preto», arrasto da alça do fim até o último caractere: a seleção tem de virar «gato preto» e o menu
  continuar aberto;
- `arrastar a alça do início pega o que vinha antes da palavra` — o mesmo pelo outro lado, de «preto»
  até o começo;
- `as alças vão embora com o menu` — «Copiar» fecha a barra e não pode deixar alça para trás;
- `a barra sai da frente enquanto a alça anda e volta ao soltar` — no meio do arrasto a barra está em
  `alpha = 0` e a **alça continua visível**; no `UP` a barra volta a `alpha = 1`;
- `o menu volta visível depois de fechar no meio do arrasto` — o VOLTAR fecha o menu com a barra
  transparente; o próximo toque longo tem de abrir uma barra visível.

O arrasto é entregue pelo `OnTouchListener` que o aparelho usa (`DOWN`/`MOVE`/`UP` nas coordenadas do
painel), não por um método interno: o teste percorre o mesmo caminho do dedo.

**Non-vacuidade (4/4, mutações aplicadas e desfeitas):**

| mutação | testes que falham |
| --- | --- |
| `showFor` deixa de chamar `showHandles(view)` | os dois de arrasto e o das alças indo embora |
| o `ACTION_MOVE` da alça deixa de chamar `applyEdge` | os dois de arrasto |
| o `ACTION_DOWN` deixa de pôr a barra em `alpha = 0` | `a barra sai da frente enquanto a alça anda…` |
| `hide()` deixa de devolver `alpha = 1` | `o menu volta visível depois de fechar no meio do arrasto` |

**O que não está coberto:** o dedo no Poco X5 e o GBoard. As métricas de texto do Robolectric são
degeneradas (~1 px por caractere), então as coordenadas do teste são aproximadas de propósito — o que
ele prova é que o movimento vira seleção, não o pixel exato da alça. O tamanho de 32dp e a posição nos
fins de linha são escolhas de desenho a confirmar no aparelho.

---

## 17. Ronda 37 — o que o aparelho mostrou em 29/10/2026

Dois pedidos, um de cada assunto que a R35/R36 abriu: o menu de seleção do painel ainda não se
comporta como o do sistema, e a integração com o ReadEra funciona mas abre uma página em vez de
aparecer por cima.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R37a | `[x]` | **Tocar fora da seleção não fecha o menu nem desmarca** — precisa do ✕/VOLTAR | bug |
| R37b | `[x]` | **ReadEra abre o painel flutuante**, como o dicionário da bolha, em vez de uma tela | média |

### R37a — Tocar fora não desmarcava  `[x]` (29/10/2026)

**Relato:** «ao selecionar alguma parte do texto, clicar fora não deseleciona e nem some as opções de
copiar, colar, etc. é necessário apertar o botão de fechar ou de voltar. O correto é ter o funcionamento
igual o do padrão do celular».

**Causa — duas, empilhadas.** O menu já tinha um véu transparente (`addScrim`) cujo toque fechava a
barra, mas ele estava com `elevation = 0` e o cartão do painel com `12dp`. Como no R35a, o Android
ordena **desenho e entrega do toque** por Z: o véu ficava debaixo do cartão. O toque que o usuário dá
«fora» cai quase sempre **no cartão** (noutro trecho de texto, na margem do cartão, no cabeçalho), e
ali quem recebia era o texto — o véu nunca via o dedo. E mesmo quando o véu fechava a barra, ele
chamava `hide()`, que só tira a barra e as alças: o realce da palavra continuava, então o painel
parecia não ter desmarcado nada.

**Correção:**

- o véu passou a `elevation = 19dp` — acima do cartão (12), abaixo da barra (20) e das alças (22).
  Qualquer toque que não seja na barra ou numa alça chega a ele;
- o toque do véu chama `dismissSelection()`, que **desmarca** e depois fecha: num rótulo selecionável,
  `Selection.removeSelection` tira o realce; num campo editável, o cursor colapsa no fim do intervalo
  (o equivalente a tocar fora com o teclado aberto).

**Testes (`TextSelectionTest`, 2 novos; a classe fica com 19):**

- `tocar fora da barra fecha o menu e tira a seleção` — toque longo em «gato» e um toque de verdade em
  `y = 12`, a margem do cartão, longe da barra e das alças: o menu, as alças e o realce têm de sumir;
- `tocar fora fecha o menu na busca e deixa o cursor` — o mesmo no campo editável, conferindo que o
  cursor ficou colapsado.

O toque é entregue ao painel ligado a uma janela de activity de verdade (o clique é um runnable
postado), **não** um `performClick()` no véu: é justamente a ordem de entrega por Z que o teste
precisa exercitar.

**Non-vacuidade (2/2, mutações aplicadas e desfeitas):**

| mutação | testes que falham |
| --- | --- |
| o véu volta a `elevation = 0` | os dois testes de toque fora |
| `dismissSelection` só chama `hide()` | os dois: o menu fecha, mas a seleção fica |

### R37b — O ReadEra abre o painel por cima  `[x]` (29/10/2026)

**Relato:** «a integração funcionou, mas o aplicativo está abrindo em uma página separada ao invés de
manter na mesma página como acontece com o google tradutor. O correto é aparecer de uma forma como no
próprio dicionário do Yabai OCR… onde ele se mantém na mesma página, mas só aparece em cima».

**Causa:** a primeira versão do R35e era uma activity de tela cheia, escolhida por não depender da
permissão de sobreposição nem do serviço da bolha. Funcionava, mas trocava a página do leitor pela
nossa — o oposto do que o Google Tradutor faz.

**Correção — o painel da bolha, com um dono próprio:**

- `LookupActivity` virou uma **porta**: com a permissão de sobreposição, ela entrega a palavra ao
  `LookupOverlayService` e chama `finish()`. Sem a permissão, continua abrindo a tela de busca como
  antes — a integração não deixa de existir por falta de uma permissão;
- `LookupOverlayService` é um serviço **mínimo**, sem captura, sem bolha e sem notificação: ele cria
  **uma** janela `TYPE_APPLICATION_OVERLAY` com o mesmo `ResultOverlay` do dicionário da bolha
  (`OverlayController.showDictionarySearch`). Cartões de palavra, kanji, seletor de dicionários, menu
  de seleção, toque fora do cartão, VOLTAR e o fechamento por perda de foco vêm todos de graça;
- o painel fecha (✕, VOLTAR ou toque fora) → `onResultDismissed` → `stopSelf()`. Sem painel não há
  serviço; se a janela não puder ser criada, o serviço também se encerra;
- o tema da porta é **transparente** (`Theme.YabaiOcr.Doorway`), para não haver um piscar escuro entre
  o ReadEra e o painel. No caminho sem permissão a tela pinta o próprio fundo no código;
- a consulta saiu para `dict/DictionarySearch.kt` (`searchWords`), usada pelos três caminhos — busca
  da bolha, porta sem permissão e painel do ReadEra. Antes eram duas cópias da mesma consulta, e a
  terceira ia nascer agora.

**Testes:**

- `LookupIntentTest` (7) — o novo `with the overlay permission the word goes to the floating panel`
  confere que a porta **termina** e que o serviço foi iniciado com a palavra no extra; os 6 anteriores
  continuam cobrindo o caminho sem permissão, os dois filtros do manifesto e os insets;
- `LookupOverlayTest` (2) — `a palavra abre o painel flutuante com a consulta` confere o campo do
  painel e que a janela cobre a tela (senão o toque fora do cartão se perde, como no R9);
  `fechar o painel encerra o servico` manda VOLTAR e confere que o serviço parou e a janela saiu.

**Non-vacuidade (3/3, mutações aplicadas e desfeitas):**

| mutação | testes que falham |
| --- | --- |
| a porta ignora a permissão e sempre abre a tela | `with the overlay permission the word goes to the floating panel` |
| o serviço abre o painel com `initialQuery = ""` | `a palavra abre o painel flutuante com a consulta` |
| `onResultDismissed` não chama `stopSelf()` | `fechar o painel encerra o servico` |

**O que não está coberto:** o aparelho. O teste prova que há uma janela de sobreposição com a consulta
e que ela fecha o serviço; não prova como o ReadEra reage a perder o foco da janela (ele pode fechar o
próprio menu de seleção, o que é desejável), nem o tempo de vida de um serviço iniciado em segundo
plano. O serviço não é `foreground`: ele vive o tempo do painel, e se o Android o matar o painel some
junto — para uma consulta de segundos é o comportamento certo; se virar sessão longa, a solução é o
mesmo serviço em foreground com notificação, não outro tipo de janela.

**Corrigido na R38:** no aparelho, o painel aparecia sobre a tela do próprio Yabai — a porta estava
entrando na tarefa do app. Ver §18.

---

## 18. Ronda 38 — o painel do ReadEra abria sobre o app errado (29/10/2026)

O R37a foi confirmado no aparelho («o bug da seleção foi corrigido»). O R37b abriu o painel flutuante,
mas o app saía do ReadEra e o painel aparecia **sobre a tela do próprio Yabai OCR**.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R38 | `[x]` | **O ReadEra abria a porta dentro da tarefa do app** — painel sobre o Yabai, leitor atrás | bug |

### R38 — A porta entrava na tarefa do app  `[x]` (29/10/2026)

**Relato:** «está abrindo corretamente como o dicionário, mas ao invés de abrir no próprio ReadEra, ele
está saindo e indo para o aplicativo do Yabai OCR e abrindo o dicionário acima do aplicativo do Yabai
OCR. O correto seria abrir o dicionário acima do ReadEra».

**Causa:** a porta (`LookupActivity`) não declarava afinidade de tarefa, então herdava a do app — o
`applicationId`. O ReadEra abre o intent com `FLAG_ACTIVITY_NEW_TASK`; com essa afinidade, o Android não
criou tarefa nenhuma: encontrou a tarefa que o app **já tinha** (a do `MainActivity`) e a trouxe inteira
para a frente, com a porta em cima. Quando a porta terminou, quem estava embaixo era o `MainActivity` —
e uma janela `TYPE_APPLICATION_OVERLAY` aparece sobre o que estiver na frente, que era o app. O painel
estava certo; o leitor é que tinha ficado atrás.

**Correção:**

- `android:taskAffinity=".lookup"` na porta: com uma afinidade só dela, `NEW_TASK` cria **uma tarefa
  própria**. Ao terminar, a tarefa fica vazia e é removida, e o sistema retoma a tarefa anterior — o
  ReadEra. (Sem `NEW_TASK`, o Android já colocava a porta na tarefa do ReadEra, e terminar voltava para
  ele; por isso o caminho só quebrava com o `NEW_TASK` que o leitor manda.)
- o `LookupOverlayService` passou a desenhar o **primeiro** painel depois de
  `DOORWAY_DELAY_MS = 200 ms`, quando a porta já saiu de cena. Uma janela criada no meio da troca pega
  o foco da porta e o perde quando o leitor volta — e perda de foco que não volta é o sinal de «o
  usuário saiu» que fecha o painel (R9). Com a espera, o painel nasce sobre o leitor, do mesmo jeito
  que o dicionário da bolha nasce sobre o app que está na tela. Uma segunda palavra, com o painel já
  aberto, é respondida na hora.

**Testes:**

- `LookupIntentTest` (8) — `a porta nao divide a tarefa com a tela do app` compara a afinidade da porta
  com a do `MainActivity`: iguais, a porta volta a entrar na tarefa do app;
- `LookupOverlayTest` (3) — `o painel espera a porta sair de cena` avança meio atraso e confere que
  ainda não há janela, avança o resto e confere que há exatamente uma.

**Non-vacuidade (2/2, mutações aplicadas e desfeitas):**

| mutação | teste que falha |
| --- | --- |
| tira `android:taskAffinity` da porta | `a porta nao divide a tarefa com a tela do app` (as duas viram `dev.yabai.ocr.debug`) |
| desenha o painel na hora, sem o `postDelayed` | `o painel espera a porta sair de cena` |

**O que não está coberto:** o aparelho, de novo — e agora com dois desfechos possíveis de diagnóstico
fácil. Se o leitor **voltar** e o painel desaparecer sozinho, o problema é a perda de foco da troca de
tarefa e a correção é uma carência curta no `ResultOverlay` (não fechar por perda de foco nos primeiros
~500 ms de vida da janela). Se o leitor **não voltar**, o próximo passo é registrar as flags que o
ReadEra manda no `ACTION_PROCESS_TEXT` — não adivinhar outra tela.

---

## 19. Ronda 39 — copiar e perguntar no painel do dicionário (31/10/2026)

Pedido: «Adicionar no dicionário/painel do readera as opções que já existem no painel de tradução de
perguntar para ia, copiar original e copiar tradução.» Ainda na mesma conversa, o «Copiar tradução» foi
descartado («pode ignorar a opção de copiar tradução e deixar apenas o de copiar original e perguntar a
ia») — e com razão: o cartão de busca não mostra tradução nenhuma, e o botão prometeria o que não existe.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R39 | `[x]` | **O cartão de busca não levava a consulta embora** — sem «Copiar original» nem «Perguntar à IA» | pedido |

### R39 — As ações do cartão de captura no cartão de busca  `[x]` (31/10/2026)

**Causa:** duas coisas, e a segunda só apareceria no caminho do ReadEra.

1. `showSearch` não montava grade de ações nenhuma: o cartão de busca terminava no seletor de
   dicionários. O «Perguntar à IA» existia como botão anônimo criado dentro de `showResult`, então não
   havia o que reaproveitar.
2. `textToSend()` escolhia entre o texto limpo e o campo pelo `userEdited`. No cartão de busca
   `cleanText` é `""` e `userEdited` continua `false` — a consulta foi escrita pelo painel, não pelo
   usuário —, então «Copiar original» copiaria nada e a pergunta sairia vazia. Digitar no campo
   inverte o `userEdited` e esconde o defeito: o teste que digitasse passaria enquanto o ReadEra, que
   entrega a palavra já escrita, falharia.

**Correção:**

- `btAskAi` virou campo, ao lado dos dois «Copiar», e serve aos dois cartões.
- `showSearch(..., canAskAi)` monta uma `ActionGrid` com **Copiar original** e, quando o ajuste está
  ligado, **Perguntar à IA**. A linha fica escondida com o campo vazio ou o painel dobrado
  (`updateSearchActions()`, chamado pelo `TextWatcher` e pelo `applyFold()`): dois botões mortos leem
  como um painel que se esqueceu do que estava fazendo.
- `textToSend()` ganhou o ramo da busca: é o campo, com `trim()`. Não há leituras para tirar nem cópia
  limpa atrás dele.
- A ligação até os botões: `OverlayController.showDictionarySearch(canAskAi)` →
  `FloatingOcrService.onDictionarySearchRequested` e `LookupOverlayService.showPanelNow` passam
  `settings.showAiButton`; o serviço do ReadEra implementa `onAskAiRequested = askAi(text)` e
  `onAskAiToRequested = openAi(packageName, text)`, com o mesmo `copyToClipboard`/`AskAi.plan` do
  painel da bolha. A tela de busca sem permissão de sobreposição (`LookupActivity`) copia e, sem app
  de IA fixo, abre o seletor do sistema — a rota degradada da tela que já é a degradada.
- Sem «Copiar tradução»: o dicionário responde com definições, e o cartão não tem texto traduzido.

**Testes:**

- `DictionarySearchPanelTest` (18 no total):
  - `the card is a search box, not the capture result` — reescrito: com o campo vazio não há grade de
    ações visível nem «Copiar tradução»;
  - `a typed query offers Copiar original and Perguntar à IA`;
  - `Copiar original copies the word in the box` — digita ` 波及 ` e recebe `波及` (o `trim()`);
  - `Perguntar a IA asks about the word in the box` — a consulta chega **pela porta da frente**
    (`initialQuery`), com `userEdited` falso: é o caminho do ReadEra;
  - `the setting hides the question and keeps the copy`;
  - `the actions go away when the box is emptied`.
- `LookupOverlayTest` (4) — `o painel do readera copia a palavra e pergunta a ia`: sobe o serviço com
  `showAiButton = true`, toca nos dois botões, confere a área de transferência e, sem app de IA
  instalado no Robolectric, a nota `result_ai_no_app` no painel.

**Non-vacuidade (4/4, mutações aplicadas e desfeitas):**

| mutação | teste que falha |
| --- | --- |
| `textToSend()` volta ao corpo anterior (sem o ramo da busca) | `Copiar original copies the word in the box`, `Perguntar a IA asks about the word in the box`, `o painel do readera copia a palavra e pergunta a ia` |
| `updateSearchActions()` sempre `VISIBLE` | `the card is a search box, not the capture result`, `the actions go away when the box is emptied` |
| `onAskAiRequested = Unit` no `LookupOverlayService` | `o painel do readera copia a palavra e pergunta a ia` |
| `showSearch` ignora `canAskAi` e sempre acrescenta o botão | `the setting hides the question and keeps the copy` |

**O que não está coberto:**

- o aparelho: nenhuma das duas ações foi tocada no Poco X5 com um leitor de verdade;
- o caminho da bolha (`FloatingOcrService.onDictionarySearchRequested`) só é coberto até a chamada de
  `showDictionarySearch` — quem o exercita no Robolectric é o serviço gêmeo do ReadEra;
- a tela sem permissão de sobreposição (`LookupActivity.askAi` com `Intent.createChooser`) não tem
  teste: o Robolectric não tem apps de IA para resolver o chooser;
- a lista de apps desenhada sobre o painel do ReadEra (`showAskAiApps` a partir do
  `LookupOverlayService`) não é exercitada — o teste só chega à nota de «nenhum app».

---

## 20. Ronda 40 — Início e recentes fecham os painéis (31/10/2026)

Pedido: «Botão Início/Home e Botão de Aplicativos Recentes/Multitarefa precisam fechar a aba de
tradução/dicionário/dicionário do readera. Já tinha solicitado antes, acredito que não foi aplicado
por supostamente não dá certo, mas tem certeza que realmente não é possível? Você pesquisou?»

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R40 | `[x]` | **HOME e recentes fecham o painel** — os três painéis, sem permissão nova | bug |
| R41 | `[x]` | **HOME e recentes fecham também o leitor de kanji** | bug |

### R40 — O que o R9 assumiu e o aparelho desmentiu  `[x]` (31/10/2026)

**Por que o R9 não fechava no aparelho.** O R9 fechava pela perda de foco da janela
(`ResultOverlay.onWindowLost`, de `onWindowFocusChanged`). A suposição era que o launcher e a tela de
recentes tomam o foco de qualquer janela. Não tomam de uma `TYPE_APPLICATION_OVERLAY`: ela é desenhada
**acima** do launcher e da tela de recentes e pode continuar sendo a janela com foco de entrada. Sem
perda, o `onWindowLost` nunca dispara — e o painel fica flutuando sobre o launcher. A conclusão
anterior («o foco resolve») estava errada; não era impossível, era a rota errada.

**Pesquisa (AOSP `android16-release`).** O sistema anuncia a saída por **duas** vias, e nenhuma pede
permissão:

1. **A todas as janelas, pelo binder.** Quando HOME ou recentes é apertado,
   `RootWindowContainer.closeSystemDialogs(reason)` percorre todas as janelas
   (`forAllWindows(mCloseSystemDialogsConsumer)`) e chama `w.mClient.closeSystemDialogs(reason)` em
   cada uma que tem superfície. Do outro lado, `ViewRootImpl.W.closeSystemDialogs` →
   `dispatchCloseSystemDialogs` → `mView.onCloseSystemDialogs(reason)` na **view raiz** da janela.
   `View.onCloseSystemDialogs(String)` é `public` mas `@hide`/`@UnsupportedAppUsage`; declarar o
   mesmo método na subclasse, sem `override`, é o suficiente — a chamada do framework é virtual. É a
   rota que não depende de broadcast nenhum.
2. **O broadcast `ACTION_CLOSE_SYSTEM_DIALOGS`**, com `reason` = `homekey` ou `recentapps` (o gesto
   de swipe para home reporta `recentapps`, como no Launcher3). O broadcast está deprecado para
   **enviar** a partir do alvo 31, mas o sistema continua **enviando** — é o que o STK do AOSP usa,
   registrado com `RECEIVER_EXPORTED`.

O menu de energia manda `globalactions` e outras telas do sistema mandam outros motivos; fechar neles
tiraria o painel enquanto o usuário ainda está lendo. O filtro é o mesmo para as duas vias.

**Correção.**

- `OverlayController`: receptor dinâmico de `ACTION_CLOSE_SYSTEM_DIALOGS`, registrado com
  `ContextCompat.RECEIVER_EXPORTED` **só enquanto um painel existe** (`ensureResultPanel`), desligado
  quando o último painel sai — e o campo zerado, senão o painel seguinte não volta a escutar.
- `ResultOverlay`: `onCloseSystemDialogs(reason)` chama um `onSystemDialog` que o controlador liga ao
  mesmo `dismissForSystemDialog(reason)` do broadcast. O `hideResult` desarma as duas rotas antes de
  tirar a janela, para a queda de foco da remoção não pedir uma segunda dispensa.
- As duas rotas convergem num ponto só, que confere o motivo e chama `dismissResult()` uma vez. O
  segundo aviso — seja outra rota ou o `onWindowLost` do R9 — encontra `resultView == null` e não faz
  nada.
- Vale para os **três** painéis: o de tradução e o cartão de busca são o mesmo `ResultOverlay` criado
  por `ensureResultPanel`, e o painel do ReadEra usa o mesmo controlador pelo `LookupOverlayService`.
  A lista de apps do «Perguntar à IA» vai junto (`hideResult` já chamava `hideAppPicker`).
- O R9 continua no lugar: quando o foco **é** perdido de verdade (outro app vem para a frente), ele
  fecha. Só não é mais a rota do HOME.

**Testes.**

- `SystemDialogTest` (10 na ronda): HOME pelo broadcast, recentes, o cartão de busca, o menu de energia
  como controle negativo, a lista de apps indo junto, uma dispensa só (broadcast + janela + perda de
  foco), o painel seguinte voltando a escutar o broadcast, HOME pela janela, o menu de energia pela
  janela e o painel seguinte voltando a ouvir a janela.
- `LookupOverlayTest` (6): `o home fecha o painel do readera` (broadcast) e
  `o sistema fecha o painel do readera pela janela`; os dois conferem que o serviço para
  (`isStoppedBySelf`), não só que a janela sumiu.
- Suíte do app depois do R40: **329 testes / 45 classes, 0 falhas**. Núcleo: **140 testes, 0 falhas,
  2 pulados**.

**Non-vacuidade (6/6, mutações aplicadas e desfeitas):**

| mutação | teste que falha |
| --- | --- |
| o receptor de broadcast não faz nada | `o home fecha o painel e avisa o servico`, `o botao de apps recentes fecha o painel`, `o painel da busca fecha do mesmo jeito`, `a lista de apps vai junto`, `um painel aberto depois volta a escutar`, `o home fecha o painel do readera` |
| fecha com qualquer motivo | `um motivo que nao e sair nao fecha o painel`, `pela janela o menu de energia tambem nao fecha` |
| aceita só `homekey` | `o botao de apps recentes fecha o painel` |
| o campo do receptor não é zerado ao parar | `um painel aberto depois volta a escutar` |
| `ResultOverlay.onCloseSystemDialogs` não faz nada | `o sistema fecha o painel pela janela`, `um painel aberto depois volta a ouvir a janela`, `o sistema fecha o painel do readera pela janela` |

**O que não está coberto:**

- o aparelho: as duas rotas foram exercitadas no Robolectric, não no Poco X5 5G. É o teste que falta,
  com os botões e, se for o caso, com os gestos;
- que o sistema **entregue** o broadcast ou a chamada de janela — isso é do framework; os testes
  acionam as duas entradas como o framework aciona;
- o gesto de swipe para home: o motivo `recentapps` vem do código do Launcher3, não de uma observação
  no aparelho;
- ROMs que trocam o launcher (MIUI/HyperOS, no aparelho do usuário) podem acrescentar caminhos
  próprios; as duas rotas são as do AOSP, e é por isso que as duas ficaram;
- `onCloseSystemDialogs` é API escondida: é **declarada**, não refletida, e pode deixar de ser chamada
  num Android futuro. O broadcast continua como a outra rota, e o R9 como a terceira.

### R41 — O leitor de kanji também  `[x]` (01/11/2026)

Pedido: «o painel de kanji de fato não está fechando. Aplique para ele também». O R40 deixou essa
janela de fora de propósito: ela é aberta por «Ler kanji», não é um dos três painéis, e com ela aberta
o `resultView` é nulo — então nem o receptor era registrado. Faltavam duas coisas: registrar o ouvinte
quando só ela existe, e dar a ela a mesma rota de janela do `ResultOverlay`.

**Correção.**

- `KanjiOverlay` ganhou o mesmo `onSystemDialog`/`onCloseSystemDialogs(reason)` do `ResultOverlay`
  (API escondida, **declarada**, sem `override` — a chamada do framework é virtual).
- `showKanji` liga o callback ao `dismissForSystemDialog` e registra o receptor; `hideKanji` desarma o
  callback e só desliga o receptor se **nenhum** painel sobrou
  (`stopListeningForSystemDialogsIfIdle`). Sem essa condição, fechar o leitor deixaria o painel de
  tradução surdo ao HOME — é o teste `fechar o leitor de kanji nao deixa o painel surdo`.
- `hideResult` passou a usar a mesma condição de ociosidade, no lugar do desligamento direto: o
  receptor é um só para as duas janelas.
- `dismissForSystemDialog` fecha os dois quando os dois estão abertos e só chama o callback do serviço
  quando havia um painel de resultado — o leitor de kanji não tem callback de serviço, e um leitor
  fechado não pode ser contado como um resultado dispensado.

**Testes.** `SystemDialogTest`, 7 novos (17 no total): HOME e recentes pelo broadcast, HOME pela
janela, o menu de energia como controle negativo, os dois abertos fechando juntos com uma dispensa só,
fechar o leitor sem deixar o painel surdo, e um leitor novo voltando a escutar. Suíte do app depois do
R41: **336 testes / 45 classes, 0 falhas**.

**Non-vacuidade (4/4, mutações aplicadas e desfeitas):**

| mutação | teste que falha |
| --- | --- |
| `dismissForSystemDialog` ignora o leitor de kanji (só fecha o resultado) | `o home fecha o leitor de kanji`, `o botao de recentes fecha o leitor de kanji`, `o sistema fecha o leitor de kanji pela janela`, `o home fecha o painel e o leitor de kanji juntos`, `um leitor de kanji aberto depois volta a escutar` |
| `hideKanji` desliga o receptor direto, sem checar a ociosidade | `fechar o leitor de kanji nao deixa o painel surdo` |
| `KanjiOverlay.onCloseSystemDialogs` não faz nada | `o sistema fecha o leitor de kanji pela janela` |
| `showKanji` não registra o receptor | `o home fecha o leitor de kanji`, `o botao de recentes fecha o leitor de kanji`, `um leitor de kanji aberto depois volta a escutar` |

**O que não está coberto:**

- o aparelho: o leitor de kanji foi exercitado no Robolectric, não no Poco X5 5G;
- o gesto de swipe para home e as ROMs que trocam o launcher continuam valendo como no R40 — as duas
  rotas são as do AOSP;
- o leitor de kanji não avisa o `FloatingOcrService` ao fechar (não há callback de dispensa): um
  `showKanjiCard` que chegue atrasado vira no-op, porque `kanjiView` já é nulo;
- o `onCloseSystemDialogs` escondido continua sendo o mesmo risco de API do R40; o broadcast é a outra
  rota.

---

## 21. Ronda 42 — Jitendex: etiquetas separadas e «Ver também» fora (01/11/2026)

Pedido: «Em dicionários como o Jitendex PT-BR, algumas palavras têm opção de "ver mais" ou "ver mais" e
acho que provavelmente um link para uma página, mas no aplicativo isso fica estranho e não faz sentido
ter, já que não tem esse link, então isso deve ser retirado. Outro detalhe também é que pelo menos no
Jitendex PT-BR (não sei se aplica também a outros dicionários), tem coisas que se misturam com a
tradução e fica difícil identificar o que é tradução e o que não é. […] pode aparecer opções como
substantivo, suru, transitivo, experiência, tudo com o mesmo tamanho, fonte, etc. só estão separados em
linhas diferentes, então fica difícil saber o que realmente faz parte da tradução ou não nesse caso.»

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R42 | `[x]` | **Etiquetas de classe/campo fora da definição e «Ver também» removido** | bug |

### R42 — O que o renderer lia como definição  `[x]` (01/11/2026)

**Causa.** O Jitendex mudou o formato do `structured-content`: o grupo de sentidos deixou de ser
`sense-groups > li > ol` e passou a ter as etiquetas e os sentidos como **filhos diretos** de
`div[data-content=sense-group]`, cada sentido em `div[data-content=sense]`. O `GlossaryRenderer` só
conhecia a forma antiga: não achava a lista, devolvia `null` e o grupo inteiro caía no caminho de texto
puro. Aí a classe gramatical era numerada como se fosse o sentido (`1. substantivo`, `suru`,
`transitivo`) e o bloco de referência cruzada era escrito no meio da definição. O texto é gravado na
importação, então o defeito ficou congelado nos dicionários já instalados.

**Correção.**

- `GlossaryRenderer.senseItems` aceita as duas formas: primeiro os `div[sense]` filhos diretos;
  se não houver, a lista `ul`/`ol` antiga. As etiquetas do grupo continuam indo para
  `Rendered.tags` — que o painel já desenha na linha de apoio, menor e cinza, junto do nome do
  dicionário.
- `xref`, `xref-content`, `xref-glossary` e `reference-label` entraram em `SKIPPED_DATA`; `xref` saiu
  de `MARKERS`. O bloco inteiro — rótulo «Ver também», palavra ligada e glosa — desaparece, porque o
  painel não tem como seguir link nenhum.
- `DictionaryCompiler.FORMAT_VERSION` foi de **2 para 3**: o texto é renderizado no momento da
  importação, então uma cópia antiga não pode mostrar o render novo. `DictionaryInfo.outdated` passa a
  marcar as cópias antigas e o usuário precisa **baixar ou importar de novo** o dicionário.

**Testes.**

- `GlossaryRendererTest` (11, dois novos): `a sense group without a list still numbers the meaning and
  not the tags` usa a estrutura real do PT-BR e exige `1. experiência` com
  `tags = substantivo・suru・transitivo`; `a cross-reference is not written into the definition` exige
  `1. onomatopeia`, `tags = linguística` e nenhum «Ver também»/「擬音語」/glosa ligada no texto.
- `DictionaryCompileTest` (24): o teste do ggjz passou a conferir o **dicionário real** — 経験 com
  `substantivo` e `transitivo` nas etiquetas e `1. experiência` na definição, e 擬声語 com
  `linguística` nas etiquetas, `1. onomatopeia` na definição e sem «Ver também».
- `DictionaryScreenTest` deixou de fixar `compiledWith` em números soltos e usa
  `DictionaryCompiler.FORMAT_VERSION` (e `- 1` para a cópia antiga), senão cada aumento de formato
  quebrava o teste de aviso.
- Suítes depois do R42: **app 336 testes / 45 classes, 0 falhas**; **núcleo 142 testes, 0 falhas,
  2 pulados**.

**Non-vacuidade (2/2, mutações aplicadas e desfeitas):**

| mutação | teste que falha |
| --- | --- |
| `senseItems` volta a exigir a lista `ul`/`ol` (sem os `div[sense]` diretos) | `a sense group without a list still numbers the meaning and not the tags`, `a cross-reference is not written into the definition` |
| as quatro entradas de `xref` saem de `SKIPPED_DATA` | `a cross-reference is not written into the definition` |

**O que não está coberto:**

- o aparelho: o render foi conferido no núcleo com o `.zip` real do ggjz, não no Poco X5 5G;
- um dicionário já instalado: o R42 não reescreve o texto antigo — o aviso de formato é o máximo que o
  app pode fazer sem o `.zip` de origem;
- links que não sejam `xref` (por exemplo `href` solto dentro de um exemplo): continuam saindo como
  texto, porque o painel só remove o que sabe ser furniture do Jitendex;
- etiquetas que o Yomitan não marca como `tag`/`part-of-speech-info`/`field-info` continuam no corpo
  da definição — o renderer não adivinha quais spans são classe gramatical.

---

## 22. Ronda 43 — dicionários que se atualizam sem reinstalar (02/11/2026)

**O pedido.** Dicionário muda com o tempo; o usuário quer (a) verificar se há versão nova, (b)
atualizar, (c) escolher de quanto em quanto tempo verificar, e pergunta se isso é possível **sem
reinstalar**.

**A resposta.** É possível como *atualização*, não como *patch*. As definições são renderizadas e
gravadas nos arquivos na importação (`DictionaryCompiler`), então não existe diff para aplicar: o app
baixa o `.zip` novo, compila e troca a cópia **sobre o mesmo id**. O que faz parecer reinstalação é a
identidade — id, posição na lista e estado ativo/desativado — e isso tudo é preservado. O que **não**
dá para atualizar é um dicionário vindo de **Importar arquivo**: não há editor para consultar.

**Como a versão é descoberta.** Dois casos, porque os editores publicam de dois jeitos:

- **Com descritor** (`indexUrl`: Jitendex PT-BR do ggjz e os três Wikcionários): o app lê ~1 KB de JSON
  e compara o campo `revision` com o que foi gravado no `meta.json` na importação. A comparação é
  **numérica, componente a componente** (`2026.03.05` > `2025.08.12`; `2026-03-05` == `2026.03.05`),
  porque o descritor é publicado à mão e pode ficar para trás do arquivo que ele aponta. Rótulo sem
  número nenhum cai na igualdade de texto: igual é «em dia», diferente é oferecido.
- **Download direto** (`url`: JMdict, Jitendex, JMnedict, KANJIDIC, surasura): o app faz `HEAD` na URL
  «latest release» e compara `ETag`, depois `Last-Modified`, depois `Content-Length` com a assinatura
  HTTP guardada na instalação. `ETag` é o sinal mais forte; tamanho **diferente** prova versão
  diferente, tamanho **igual não prova nada** — nesse caso a resposta é «não deu para comparar», nunca
  «está em dia».

**A tela.** Uma seção **«Atualizações»** no topo de *Gerenciar dicionários*, com o resultado da última
verificação, **Verificar atualizações**, **Atualizar tudo** (só quando há algo) e o seletor de
periodicidade. Cada linha marcada ganha **Atualizar** ao lado das setas e de «Apagar». Verificar não
baixa nada: são um descritor ou um `HEAD` por dicionário. A conclusão fica em `UpdateStateStore`
(SharedPreferences), então continua visível depois que o processo morre; atualizar ou apagar um
dicionário esquece a conclusão dele, para não sobrar selo velho nem «Atualizar tudo» que reinstalaria
um dicionário removido.

**A periodicidade.** `UpdateInterval` = `OFF` (padrão), `DAILY`, `WEEKLY`, `MONTHLY`. É
`enqueueUniquePeriodicWork` com `NetworkType.CONNECTED`, política `KEEP` no início do app e
`CANCEL_AND_REENQUEUE` quando o usuário muda a escolha; `OFF` cancela. O worker **só verifica e
notifica** — baixar 39 MB sozinho em dados móveis não é uma decisão que o app deva tomar. Uma
notificação por conjunto de versões (a chave é `id:versão`), cancelada quando tudo está em dia; sem a
permissão de notificação a verificação continua e o resultado fica na tela.

**Dois bugs encontrados no caminho, e corrigidos:**

- `CompactDictionary.readMeta` chamava `asString`/`asInt` em campos opcionais gravados como `null`
  (`author`, `url`, `description`, `attribution`). O `JsonNull` lança, o repositório trata `meta.json`
  ilegível como «não é dicionário», e um `.zip` sem autor **desaparecia da lista depois de
  reiniciar**. Agora campo nulo vira ausente; o teste novo fixa isso.
- `DictionaryRepository.install` trocava os arquivos **antes** de soltar o `LookupEngine` antigo. Em
  qualquer sistema isso arrisca uma consulta lendo arquivo já removido; no Windows os arquivos
  abertos nem deixam o `rename` acontecer. `releaseLocked()` passou para antes do swap, e a cópia de
  emergência apaga o destino antes de `copyRecursively`.

**Os cinco bugs do aparelho (02/11/2026).** O primeiro APK do R43 foi para o Poco X5 5G e voltou com
cinco queixas; as três primeiras têm a mesma raiz.

1. **«Atualizar tudo» não mostrava progresso e nada era atualizado.** O downloader chama `onProgress`
   dentro de `withContext(Dispatchers.IO)` e o compilador chama o seu de uma worker thread. Os dois
   escreviam num `TextView`; no aparelho isso lança `CalledFromWrongThreadException`, a exceção aborta
   o download no primeiro relatório, o `catch` por dicionário conta como falha e o toast final diz
   «0 atualizados; N falharam». Daí também o «os dados não mudaram». O Robolectric não impõe afinidade
   de thread, por isso os 38 testes passaram. Agora existe `onUi {}`: se já está na main thread executa
   na hora, senão `runOnUiThread`, sempre ignorando a tela já destruída, e **todo** relatório de
   download/conversão passa por ele.
2. **«Verificar» e «Atualizar tudo» congelavam depois de uma atualização.** `refresh()` era chamado
   dentro do `try`, antes do `finally` zerar `busy`; o cartão era reconstruído com
   `isEnabled = busy == null` e nascia desabilitado. O `finally` seguinte mexia no botão **antigo**, já
   fora da tela. `startDownload`, `startUpdateAll` e `importZip` agora zeram `busy` **antes** de
   `refresh()`, e o `refresh()` mora no `finally` (com guarda `isFinishing`/`isDestroyed`) para também
   consertar cancelamento e falha.
3. **A «atualização» oferecida era mais antiga.** O descritor do `kty-ja-pt` anuncia `2025.08.12`, mas
   o `.zip` que ele aponta é compilado com `2026.03.05`; a comparação de texto chamava isso de
   atualização. `compareRevisions` compara os números em ordem, e remoto **menor ou igual** ao
   instalado é `CURRENT` — o app nunca oferece downgrade. Os três Wikcionários caem no mesmo caso.
4. **Verificar de novo continuava acusando os mesmos dicionários.** Consequência do item 3 para os
   dicionários com descritor, e do item 1 para os de download direto: como o download abortava antes
   do swap, a assinatura nova nunca era gravada. Com o `onUi` e a comparação numérica, a próxima
   verificação responde `CURRENT`; há teste de ponta a ponta para os dois tipos.
5. **A lista de instalados desaparecia depois do «Atualizar tudo».** Não reproduziu no Robolectric:
   o teste de update-all mantém as duas linhas. A causa provável é a mesma combinação acima (refresh
   com `busy` setado e o aborto no meio do swap) somada a uma varredura vazia memorizada. Duas
   defesas ficaram: `refresh()` só depois de `busy` limpo, e `installed()` **nunca guarda uma resposta
   vazia** — uma varredura que viu a pasta num instante ruim é refeita na próxima, em vez de esconder
   todos os dicionários até o processo morrer.

**Arquivos.** `dict/DictionaryUpdates.kt` (assinatura, checker, store, notifier, worker, scheduler),
`dict/DictionaryDownloader.kt` (`RemoteSource`, `download` devolve assinatura, `head`, `readText`),
`dict/DictionaryRepository.kt` (`install(..., source)`, `annotateSource`, `sourceId`/`sourceSignature`),
`AppSettings.kt` (`UpdateInterval` + `dictionaryUpdateInterval`), `DictionaryActivity.kt` (cartão,
selos, botões, diálogo, permissão), `MainActivity.kt` (`ensure`), strings e `libs.versions.toml`
(`androidx.work 2.10.1`).

**Testes (50 novos; app 386/51, núcleo 143 + 2 pulados, 0 falhas):**

- `DictionaryUpdateCheckerTest` (18): revisão igual/diferente/ausente, **remoto mais antigo = em dia**,
  **remoto mais novo = atualização**, separadores diferentes com os mesmos números, rótulos sem
  número (igual/diferente), ordenação numérica de `compareRevisions`, ETag, data, tamanho, instalação
  antiga sem assinatura, dicionário importado (`MANUAL`), falha de rede (`FAILED`), `checkAll` com um
  resultado por id, e a precedência de `RemoteSignature.sameAs` (incluindo «tamanho igual = não sei»).
- `DictionaryRepositoryUpdateTest` (6): reinstalar sobre o id mantém ordem e estado desativado e grava
  a origem; a definição nova já responde; importação manual não grava `sourceId`; **uma verificação
  logo depois da atualização responde `CURRENT`**; **um arquivo compilado com revisão mais nova não é
  oferecido com o rótulo velho do descritor**; **uma varredura vazia não fica memorizada**.
- `UpdateStateStoreTest` (5): ida e volta, nunca verificado, JSON corrompido, `forget`, chave de
  notificação.
- `DictionaryUpdateSchedulerTest` (5): 7/30 dias, troca substitui em vez de empilhar, `ensure` não
  reseta, `OFF` cancela, `OFF` no início não cria.
- `DictionaryUpdateNotifierTest` (4): notifica e nomeia, não repete o mesmo conjunto, cancela e
  esquece a chave quando tudo está em dia, sem permissão não posta nem queima a chave.
- `DictionaryUpdatesScreenTest` (12): selo e botão a partir do resultado guardado, verificação com
  checker injetado, «tudo em dia» sem botão, importado explica, `Atualizar` baixa/instala/limpa o selo
  e a definição nova responde, periodicidade `WEEKLY` guardada e agendada, origem sobrevive na linha,
  resultado de dicionário removido não oferece «Atualizar tudo»; **uma atualização manual não deixa
  «Verificar»/«Atualizar tudo» desabilitados**; **«Atualizar tudo» mantém as linhas e reabilita o
  cartão**; **o cartão mostra o percentual durante o download**; **um bloco enviado de uma thread de
  fundo roda na main thread**.
- `DictionaryMetaTest` (núcleo, +1): `meta.json` com campos opcionais nulos continua legível.

**Non-vacuidade (11/11, mutações aplicadas e desfeitas):**

| mutação | teste que falha |
| --- | --- |
| `checkRevision` volta a comparar texto (ou sempre diz «current») | 3 do `DictionaryUpdateCheckerTest` (revisão diferente/antiga, mesma versão com outro separador) e `an index dictionary updated with a newer archive…` |
| `annotateSource` não é chamado | `installing over the same id keeps the priority and the enable flag`, `a check right after an update says the dictionary is current` |
| `busy` é zerado **depois** de `refresh()` no download de uma linha | `updating one dictionary does not leave the update card frozen` |
| `busy` é zerado **depois** de `refresh()` no «Atualizar tudo» | `updating all keeps every dictionary on the list and re-enables the card` |
| `onUi` executa o bloco direto na thread que chamou | `progress reported from a background thread is marshalled to the main thread` |
| o relatório de progresso do download não chama `onPhase` | `the update card shows the download percentage while it runs` |
| `installed()` volta a memorizar uma lista vazia | `an empty answer is not cached as the list` |
| a periodicidade usa 1 dia fixo | 4 de scheduler/UI (7 e 30 dias, `ensure`, diálogo) |
| o notifier não grava a chave | `the same versions are announced only once` |
| `readMeta` volta a chamar `asString` em `JsonNull` | `optional fields written as null do not make the metadata unreadable` |
| `releaseLocked()` volta para depois do swap | `the new definitions are live right after the install` |

**O que não está coberto / limites:**

- o aparelho: o fluxo foi exercido no Robolectric com `.zip` gerado, não no Poco X5 5G; o bug da
  janela de download só aparece lá (afinidade de thread) e o da lista sumida não reproduziu no teste;
- servidores que não mandam `ETag` nem `Last-Modified` e mantêm o mesmo tamanho ficam
  permanentemente «não deu para comparar»; o botão «Atualizar» continua disponível;
- a granularidade do ggjz/Wikcionário é a `revision` publicada; se o editor esquecer de incrementá-la,
  a verificação não vê a mudança — e, com a comparação numérica, um rótulo esquecido para trás conta
  como «em dia» em vez de downgrade (que é o comportamento desejado);
- o worker não é pontual: o Android pode atrasar por bateria/rede, e «toda semana» é referência;
- não há push do editor, download delta, nem verificação de dicionário importado de arquivo;
- a atualização é tudo-ou-nada por dicionário: uma falha de rede no meio deixa a cópia antiga intacta
  (o staging é apagado), que é o comportamento desejado, mas não há retomada de download.

---





## 23. Ronda 44 — o balão do motor offline volta a ser um bloco (11/11/2026)

**Relato.** «Unlimited OCR quando captura a tela toda ele está identificando blocos de textos que
deveriam ser juntos como separados E não identificou alguns textos», com um print de um balão de duas
colunas (「戦う術が」 / 「無い」) chegando como duas caixas, contra o exemplo do Google Lens.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R43 | `[x]` | **Colunas do mesmo balão voltam a ser um bloco no motor offline** | bug |

### R43 — O balão que chegava partido  `[x]` (11/11/2026)

**Causa raiz.** No Android a opção «Unlimited-OCR» **não é outro motor**:
`OcrDispatcher.recognizeWithUnlimitedOcr` chama o `MlKitOcrEngine` e só troca o `engineLabel`. O ML Kit
reporta japonês vertical **uma coluna por `textBlock`**, então um balão de duas colunas chega como dois
parágrafos e a tela de trechos oferece duas caixas para uma frase. O Lens agrupa as colunas sozinho; o
motor offline não tinha nenhuma regra de agrupamento — só a ordenação (R14) dos blocos que ele já
trazia.

**Correção.**

- `core/.../layout/BlockColumns.kt` (novo): junta as caixas que são colunas do mesmo parágrafo — ambas
  mais altas que largas, faixa vertical em comum de pelo menos 50% da menor e vão horizontal de no
  máximo 0,6 da largura da coluna — e devolve os grupos já em ordem de leitura (direita para a
  esquerda dentro do grupo, grupos pela ordem de `ReadingOrder`). Texto horizontal não é tocado: o
  ML Kit já entrega linhas quebradas como um bloco, e duas linhas de parágrafos diferentes ficam
  perto demais para uma regra de vão.
- `MlKitOcrEngine.kt`: agrupa antes de ordenar; o `rect` do bloco passa a ser a união das colunas e os
  `pieces` continuam sendo as colunas, cada uma com sua caixa (é o que desenha a leitura onde o texto
  está). Uma caixa sem geometria continua parágrafo próprio.

**Testes.** `BlockColumnsTest` (6 novos, no `:core`). Suítes depois do R43: **núcleo 159 testes,
0 falhas**; **app 397 testes, 0 falhas** (a contagem do app não muda: nenhuma regra de tela foi
tocada). As regras que mais doem foram conferidas por mutação:

| mutação | teste que acusa |
| --- | --- |
| o vão deixa de ser comparado com a largura da coluna (`return true`) | `columns of two different balloons stay apart`, `a gap wider than a glyph is a different paragraph` |
| `isColumn` sempre falso | `the two columns of a vertical balloon are one paragraph` |
| `groups` devolve cada índice sozinho | 5 dos 6 (todos os que têm geometria) |

**Evidência.** Não houve captura no aparelho: a prova é de unidade, com a geometria do print (coluna de
40 px de largura, vão de 10 px dentro do balão contra 40 px entre balões) e a prova de mutação acima.

**Limitações.**

- **Texto que o ML Kit não lê continua não lido.** O agrupamento só junta o que veio; para mangá
  estilizado o Lens continua sendo o motor que lê mais.
- A opção «Unlimited-OCR» do Android **continua sendo o ML Kit com outro rótulo**. O modelo da Baidu
  (github.com/baidu/Unlimited-OCR, MIT) é um VLM de 3,34 B parâmetros / 6,78 GB servido em GPU
  (Transformers, vLLM, SGLang), não cabe num celular; rodá-lo de verdade exige escolher outro motor
  local com detecção e reconhecimento próprios, com pesos baixados e licença conferida.
- Balões empilhados no mesmo painel não são agrupados (a regra é só de colunas lado a lado), e uma
  coluna de um único caractere é quadrada — não dá para distingui-la de uma linha horizontal.

---

## 24. Ronda 45 — o Manga-OCR que não lia nada e o painel que não deixava escolher (12/11/2026)

**Relato (Android, sem teste no Desktop).** «O manga-ocr não está reconhecendo nenhum texto, nem
horizontal e nem vertical» e «após reconhecer o texto em tela cheia, ele abre automaticamente a aba de
reconhecimento ao invés de primeiro selecionar na imagem a parte que tem texto identificado», como Lens
e ML Kit fazem.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R44 | `[x]` | **Manga-OCR não reconhece texto nenhum** (detector e decodificador) | bug |
| R45 | `[x]` | **Tela inteira abria o painel sem passar pela tela de trechos** | bug |

### R44 — O motor que não lia nada  `[x]` (12/11/2026)

O motor vive no `:core` e é o mesmo no Android e no Desktop: as duas causas valiam para os dois, o
relato veio do celular.

**Causa raiz.** Duas, as duas no contrato dos modelos ONNX, que tinha sido escrito por suposição em vez
de seguir a exportação de referência:

1. **O detector lia o output errado.** O `comic-text-detector` devolve `blk` — as caixas de texto já
decodificadas, como `cx, cy, w, h, confiança, horizontal, vertical` no espaço 1024×1024 —, mais `seg`
   (a máscara de pixels de texto). O código pegava `outputMap.get(0)` (o `blk`) e o tratava como mapa de
   probabilidade: as coordenadas viravam confiança, tudo ficava acima do limiar, e o preenchimento por
   vizinhança transformava **a página inteira numa caixa só**. A entrada ainda ia normalizada com média
   e desvio do ImageNet, quando o modelo pede RGB em 0..1 sem média nenhuma.
2. **O reconhecedor era chamado uma única vez.** O `manga-ocr.quant.onnx` é o encoder-decoder inteiro
   (`image` + `token_ids` → `logits`): a primeira chamada só diz qual token começa a frase, e cada
   chamada seguinte precisa dos tokens já lidos realimentados. Uma passada só devolvia um argmax — em
   geral `</s>`, que decodifica para a string vazia. E o recorte ia com média/desvio do ImageNet e com
   três canais de cor, quando o modelo lê `convert("L")` normalizado para -1..1 (média e desvio 0,5).

**Correção.**

- `core/.../manga/ComicTextDetector.kt`: lê `blk` pelo nome (com fallback para um mapa de canal único,
  para outras exportações), converte centro/tamanho para a imagem original, aplica supressão por
  sobreposição por classe e descarta duplicata exata entre classes. O limiar passou de 0,35 para 0,2
  **medido no modelo real**: balão vertical marca ~0,9, o mesmo balão impresso na horizontal marca
  0,20–0,25 — era metade do relato («nem horizontal»).
- `core/.../manga/MangaOcrPreprocessor.kt`: detector em RGB/255 (sem média) e reconhecedor em escala de
  cinza normalizada para -1..1.
- `core/.../manga/MangaOcrCoreEngine.kt`: decode guloso de verdade (começa em `</s>`, realimenta cada
  token, para no `</s>` ou em 80 tokens, como o autor do modelo faz) com `MangaOcrDecoding` separado do
  `OrtSession` para o laço ser testável sem um arquivo de 118 MB.

### R45 — A tela inteira que pulava a escolha  `[x]` (12/11/2026)

**Causa raiz.** A condição da tela de trechos era `outcome.blocks.size > 1`. Com o detector quebrado
todo mundo era um bloco só, então o painel abria direto — e a mesma coisa acontecia sempre que a página
tinha um balão só. O README já prometia «uma caixa sobre cada trecho» na tela inteira: o código era o
que estava errado.

**Correção.** `overlay/PickerDecision.kt` (novo) concentra a regra — tela inteira, ajuste ligado e pelo
menos uma caixa colocável — e `FloatingOcrService` passou a usá-la.

**Testes.** `:core` 153 executados (14 novos) e 1 opt-in; `:app` 408 (5 novos). As regras que mais doem
foram conferidas por mutação, cada uma falhando e voltando a passar ao restaurar:

| mutação | teste que acusa |
| --- | --- |
| não realimentar o token lido no decodificador | `every answer is fed back until the model says the line ended` |
| voltar a ler o output 0 como mapa de probabilidade | `reads a drawn page with vertical and horizontal text` (texto saiu `はは`, `立目千`, `7人一刀`) |
| normalizar a entrada do detector com média/desvio do ImageNet | `detection input is rgb scaled to zero one without a mean` |
| voltar o limiar da tela de trechos para «mais de um bloco» | `one recognised block still gets a box on the page` |

**Ponta a ponta.** Os três arquivos reais (94,7 MB + 118 MB + 30 KB) rodados no JVM do desktop, numa
página desenhada de 900×1400 com dois balões:

- `-Pmanga` ligado: 3,4 s, caixas `714,108-754,559` → `これはテストです` e `112,874-426,930` →
  `テストの文章です`; vertical e horizontal, texto exato.
- o mesmo teste sem a correção do detector (output 0 como mapa): 16 caixas de um caractere, texto
  `10 / もう / っ! / し、 / イー / … / 7人一刀` — não é leitura, é ruído.

**Aviso na UI.** Depois do primeiro teste no aparelho (12/11/2026) o motor foi marcado como
**experimental** nos ajustes: um texto em vermelho (`@color/warn`) logo abaixo da descrição diz que a
precisão e a velocidade são baixas e que a opção pode ser removida numa versão futura — a descrição
deixou de prometer «alta precisão». Strings `engine_manga_ocr_experimental` (pt/en).

**Limitação conhecida.** O modelo baixado é o combinado, então cada token custa uma passada inteira de
encoder: ~0,1 s por caractere no desktop. Numa página com muitos balões isso pesa no celular; dividir
encoder/decoder (exportações separadas do mesmo modelo) é o próximo passo de desempenho. A verificação
do caminho visual no aparelho (captura real, caixas sobre a tela, toque no balão) não foi feita aqui —
só o motor, o JVM e os testes de Robolectric.

## Notas de ordem

- B1 antes de tudo: é regressão visual e esconde um botão.
- B2 é trivial, mas a decisão é do usuário; entra logo depois.
- F1/F2/F3 não dependem de nada e são só UI.
- M2 depende de F3.
- M1 não depende de nada: mexe no motor de consulta e no cabeçalho de uma seção só.
- D2 depende de nada, mas é o mais arriscado dos dois «difíceis»; com o D1 pronto, a leitura em
  parênteses já existe e o D2 é a mesma inserção com outra fonte de leitura (o dicionário em vez da
  geometria), então convém fazê-lo logo em seguida.  `[x]` — feito em 18/10/2026.

Com o D2 fechado, o roadmap estava inteiro. O que veio depois é a seção **5. Pedidos novos**, que não
estava prevista: cada pedido novo entra como item próprio (o I1 — «Perguntar a uma IA» — foi o
primeiro), com a mesma disciplina: um teste de regressão antes de ser marcado `[x]`. O que ficou de
fora do roadmap, e por quê:

- **Rubi de verdade** (leitura impressa *acima* do kanji, com `Spannable`) em vez dos parênteses: a
  saída em texto é copiável, selecionável e é a convenção que o D1 já tinha, e o painel não usa
  `Spannable` em lugar nenhum. Fica como refinamento, não como pendência.
- **A leitura vinda da geometria do D1 alimentando o bloco**: desnecessário — se o mangá imprimiu o
  rubi, a frase já o tem entre parênteses e o D2 não escreve por cima.
- **A carga que vai para o Lens**: era a última dúvida aberta e está fechada com medições — ver
  «Registro — a carga que vai para o Lens», acima.

---

## Como rodar

```powershell
$env:ANDROID_HOME = "I:\Programas\Android\Sdk"

# testes de layout do painel (Robolectric, sem aparelho)
gradle :app:testDebugUnitTest

# testes do core, com dicionários de verdade
gradle :core:test -Pdicts="C:\caminho\para\os\dicionarios"

# o APK
gradle :app:assembleRelease
```

Todo bug corrigido ganha um teste de regressão aqui antes de ser marcado `[x]`; para os itens de UI a
rede de segurança é o `app:testDebugUnitTest`, que mede as telas de verdade sem precisar de aparelho.

---

## 25. Ronda 46 — pedidos de 21/11/2026

Pedidos do usuário, na ordem de execução: **bugs primeiro, depois o que é mais fácil**.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R46a | `[x]` | **«Consultar os dicionários instalados» sempre habilitado**: a caixa de seleção sai do menu principal e a consulta deixa de ter um estado «desligado» | fácil |
| R46b | `[x]` | **«Ignorar as leituras entre parênteses na busca»** sai da tela de dicionários e vai para o cartão **Dicionários** do menu principal, como no desktop | fácil |
| R46c | `[x]` | **Botões cinza viram o azul do app**; «Remover» fica vermelho, como no desktop, e a lista de prioridade perde o texto das setas | fácil |
| R46d | `[x]` | **Câmera no ícone flutuante**: tira a foto e roda a leitura de tela inteira, sem salvar a imagem | média |
| R46e | `[x]` | **Entrada de redirecionamento do Jitendex** (forma antiga do kanji): mostrar que a forma é antiga e os dados do kanji atual, em vez de só «redirected from» | média |

### R46a — «Consultar os dicionários instalados» sem estado desligado  `[x]` (21/11/2026)

**Pedido.** Retirar a opção e deixar a consulta sempre habilitada.

- **Causa raiz.** A consulta tinha um «desligado» que não servia a nada: `MainActivity.bindTranslationAndDictionaries()`
  gravava `AppSettings.dictionaryLookup` e `FloatingOcrService.analyse()` lia o valor para decidir entre
  `WordsState.Working()` e `WordsState.Hidden`. Com a caixa desmarcada o painel simplesmente deixava de ter
  seção «Dicionário» — o texto reconhecido continuava o mesmo. Nada externo (dicionário ausente, permissão,
  versão) desabilitava a caixa: o «desligado» era só uma preferência.
- **Correção.** A caixa sai de `activity_main.xml` e o vínculo sai de `MainActivity`. A preferência e a chave
  `dictionary_lookup` saem de `AppSettings`, e `analyse()` sempre mostra «Consultando…» e sempre roda
  `runDictionary`. O cartão **Dicionários** fica com o que ele de fato informa — o resumo («N instalados ·
  N ativos») e o botão «Gerenciar dicionários» —, sem o recuo que existia para alinhar o resumo sob a caixa.
  A string `setting_dictionary` sai das duas línguas.
- **Quem tinha a caixa desmarcada.** `dictionary_lookup=false` gravado no aparelho passa a ser ignorado (a
  chave não é mais lida) e esse aparelho volta a consultar depois da atualização. É a decisão registrada
  antes da implementação.
- **Arquivos.** `app/src/main/res/layout/activity_main.xml` · `app/src/main/java/dev/yabai/ocr/MainActivity.kt`
  · `app/src/main/java/dev/yabai/ocr/AppSettings.kt` · `app/src/main/java/dev/yabai/ocr/service/FloatingOcrService.kt`
  · `app/src/main/res/values/strings.xml` · `app/src/main/res/values-en/strings.xml`
  · `app/src/test/java/dev/yabai/ocr/DictionaryLookupAlwaysTest.kt`

Suíte do app (`:app:test`): **856 testes (428 em debug e 428 em release), 0 falhas**. Regressão em
`DictionaryLookupAlwaysTest`: a tela principal não tem mais nenhuma caixa com os dois rótulos antigos (o
teste abre a tela com `dictionary_lookup=false` gravado, que é o estado de quem usava a opção), o cartão
continua mostrando o resumo e o botão, e `AppSettings` não expõe nenhum interruptor de consulta a
dicionários. Mutações provadas:

| mutação | teste que acusa |
| --- | --- |
| A caixa «Consultar os dicionários instalados» volta ao cartão do menu principal | `the main screen has no switch for the dictionary lookup` |
| `AppSettings.dictionaryLookup` volta, e `analyse()` volta a consultar só com ele ligado | `no setting exposes a dictionary lookup toggle` |

- **Limite.** Não há E2E de aparelho: o painel real com o `FloatingOcrService` não é exercitado pela suíte
  (Robolectric mede a tela de ajustes, não uma captura). O que foi provado é a tela e a ausência de
  preferência; o caminho da captura é o mesmo código de antes, agora sem o `if`.

### R46b — «Ignorar as leituras entre parênteses na busca» no menu principal  `[x]` (21/11/2026)

**Pedido.** Mover a opção da tela de dicionários para o cartão **Dicionários** do menu principal, como no
desktop.

- **Causa.** O ajuste nasceu junto do recurso (R35d), na tela de dicionários, e lá ele ficava longe do
  lugar onde a busca acontece: quem abria o painel e digitava não tinha como saber que a limpeza existia,
  e a tela de dicionários é a que o usuário menos abre. No desktop a mesma opção fica nos Ajustes, ao lado
  dos dicionários. `DictionaryActivity.buildContent()` montava a caixa com `STRIP_READINGS_TAG` e a linha
  `dictionary_strip_readings_hint`; a preferência é `AppSettings.stripSearchReadings` e a política segue em
  `AppSettings.dictionaryQuery` / `DictionaryQuery.stripReadings`.
- **Correção.** A caixa e a explicação saem de `DictionaryActivity` e entram em `activity_main.xml`, no
  cartão **Dicionários**, logo abaixo de «Manter a última busca aberta»: `checkStripReadings` e a dica
  `stripReadingsHint`. `MainActivity.bindTranslationAndDictionaries()` lê e grava `stripSearchReadings`, no
  mesmo lugar em que o cartão já cuida do resumo e do botão «Gerenciar dicionários». O `STRIP_READINGS_TAG`
  e o cartão próprio da tela de dicionários saem. O texto das strings e o comportamento da busca não mudam:
  `dictionaryQuery` continua sendo o único ponto que decide o que vai para o dicionário.
- **Arquivos.** `app/src/main/res/layout/activity_main.xml` · `app/src/main/java/dev/yabai/ocr/MainActivity.kt`
  · `app/src/main/java/dev/yabai/ocr/DictionaryActivity.kt`
  · `app/src/test/java/dev/yabai/ocr/DictionaryStripReadingsTest.kt`

Suíte do app (`:app:test`): **858 testes (429 em debug e 429 em release), 0 falhas**. Regressão em
`DictionaryStripReadingsTest`: a caixa do menu principal mostra a preferência e a grava ao ser marcada, e a
tela de dicionários não tem mais nem o rótulo nem a explicação — uma cópia antiga lá pareceria a dona de
uma busca que ela não faz. Mutações provadas:

| mutação | teste que acusa |
| --- | --- |
| A caixa volta a ser montada em `DictionaryActivity.buildContent()` | `the dictionaries screen no longer offers the option` |
| A caixa fica no menu principal, mas `MainActivity` deixa de gravar a escolha | `the choice in the main menu is saved` |

- **Limite.** Sem E2E de aparelho: a suíte Robolectric cobre as duas telas, mas não uma busca real digitada
  no painel. A política de limpeza não foi tocada — os testes de `dictionaryQuery` seguem os mesmos.

### R46c — Botões do app na cor padrão  `[x]` (21/11/2026)

**Pedido.** Trocar os botões cinza pela cor azul do app e uniformizar os detalhes: «Descer» e «Subir»
viram só as setas (como na lista de dicionários), «Remover» fica vermelho como no desktop,
«Verificar atualizações», «Desligado», «Baixar», as setas de prioridade e os botões do cartão de
atualização acompanham.

- **Causa.** `Context.actionButton(...)` pintava todo botão de painel com `R.color.card_bg` (cinza
  translúcido) e `card_stroke`, com rótulo `text_primary` — o par que a correção de legibilidade do
  tema claro tinha escolhido. Legível, mas sem hierarquia: «Verificar atualizações», «Baixar», «Desligado»
  e «Remover» pareciam todos desabilitados, e «Remover» — o único botão de um cartão que apaga trabalho —
  tinha o mesmo peso das setas de prioridade ao lado dele. A lista de prioridade do motor usava
  `Button(..., buttonStyleSmall)` com `btn_priority_up`/`btn_priority_down` («▲ Subir» / «▼ Descer»),
  enquanto a lista de dicionários já usava só `↑`/`↓`.
- **Correção.** O fundo padrão de `actionButton` passa a ser `R.color.accent` com rótulo branco; o
  `card_stroke` sai do caminho (o azul é opaco). «Remover» passa a usar `R.color.warn` direto:
  é o único botão destrutivo do app, e um alias de uma linha não valia um nome novo. Quem pinta o
  próprio fundo passa a declarar a própria cor
  de rótulo: os «✕» transparentes de `ResultOverlay`, `AppPickerOverlay` e `KanjiOverlay` mantêm
  `text_primary`, os chips de dicionário (accent/accent_dark) e o «✕» sobre o preto do `KanjiOverlay`
  ficam brancos. A lista de prioridade do motor vira `actionButton("↑")` / `actionButton("↓")`, com
  `dictionary_up`/`dictionary_down` como descrição para leitor de tela; `btn_priority_up` e
  `btn_priority_down` saem das duas línguas.
- **Arquivos.** `app/src/main/java/dev/yabai/ocr/overlay/Widgets.kt` · `overlay/ResultOverlay.kt` ·
  `overlay/AppPickerOverlay.kt` · `overlay/KanjiOverlay.kt` · `app/src/main/java/dev/yabai/ocr/MainActivity.kt`
  · `app/src/main/java/dev/yabai/ocr/DictionaryActivity.kt` · `app/src/main/res/values/strings.xml` ·
  `values-en/strings.xml` · `overlay/OverlayController.kt` · `app/src/test/java/dev/yabai/ocr/overlay/ActionButtonThemeTest.kt`
  · `app/src/test/java/dev/yabai/ocr/ActionButtonWiringTest.kt` · `LANGUAGES.md`

- **Auditoria dos fundos próprios.** Todo call site que passa `background` sem `textColor` foi
  conferido depois da troca, porque o rótulo padrão virou branco. Ficaram brancos (fundo escuro de
  verdade): `KanjiOverlay` «✕» sobre `0xCC000000`, o botão «Imagem» e os chips (`accent`/`accent_dark`)
  do `ResultOverlay`, e a confirmação regional (`accent` com branco explícito). Precisaram de rótulo
  próprio: o «✕» do cabeçalho do `ResultOverlay` (transparente — era branco sobre o cartão claro) e as
  duas respostas de «Encerrar o OCR flutuante?», que passaram a ser só texto sobre o cartão: «Cancelar»
  com `text_primary` e «Encerrar» com o vermelho de sempre — sobre o azul o vermelho ficava ilegível e
  fazia do fechar a resposta fácil, o oposto do que a pergunta existe para evitar.

Suíte do app (`:app:test`): **868 testes (434 em debug e 434 em release), 0 falhas**. Regressão:
`ActionButtonThemeTest` (4) monta o botão real nos dois temas e exige `accent` com rótulo branco e
contraste ≥ 4,5:1, `warn` no destrutivo e `text_primary` em quem pinta o próprio fundo;
`ActionButtonWiringTest` (4) abre a tela principal e exige quatro setas com a tag do botão de painel, sem
«Subir»/«Descer» escrito, abre a tela de dicionários com um dicionário instalado e exige o fundo
`warn` no «Remover», e — no tema claro, forçado com `+notnight` — exige o «✕» do painel de resultado
legível sobre o cartão e as duas respostas do encerramento sem preenchimento, com o vermelho no texto.
Mutações provadas:

| mutação | teste que acusa |
| --- | --- |
| `actionButtonBackground` volta a `card_bg` com rótulo `text_primary` | 3 testes de `ActionButtonThemeTest` |
| «Remover» volta ao fundo padrão | `the dictionary card's remove button is the danger fill` |
| As setas de prioridade voltam a ser `Button` cinza com «▲ Subir» | `the engine priority list uses the accent arrows and no words` |
| O «✕» do cabeçalho do resultado perde o `text_primary` | `the result panel's close cross is readable on the light panel` |
| As respostas do encerramento voltam ao fundo padrão (accent) | `the close confirmation keeps its red on the panel, not on the accent` |

- **Limite.** Sem E2E de aparelho: o Robolectric mede o fundo real dos botões e a tela, não o painel
  flutuante sobre um app de verdade. Os chips, o botão «Imagem» e o `KanjiOverlay` não têm teste de cor
  própria; a conferência deles é a dos valores literais (`accent`, `accent_dark`, `0xCC000000`), todos
  escuros.

### R46d — Câmera no ícone flutuante  `[x]` (21/11/2026)

**Pedido.** Uma opção de câmera no ícone flutuante que tira a foto e faz o mesmo que a leitura de tela
inteira já faz — sem salvar a imagem no aparelho.

- **O que muda.** O menu da bolha ganha um sétimo ícone (`ic_menu_camera`, «Câmera») entre «Selecionar
  área» e «Ler kanji». Ele abre `CameraActivity`, um portal como o `StartOcrActivity`: pede a permissão
  da câmera, tira uma foto em `cacheDir/photos/` com `ActivityResultContracts.TakePicture` e a entrega
  ao serviço que já está rodando (`ACTION_PHOTO` + `EXTRA_PHOTO_PATH`). O serviço lê o arquivo com
  `CameraPhoto.loadUpright` — que o apaga em `finally` —, passa o bitmap pelo mesmo `OcrDispatcher` e
  abre o mesmo painel. O picker é pulado: a foto não tem tela atrás dela, então não há caixa para tocar.
  (O R47a volta atrás: a foto ganhou os mesmos balões de trechos, agora que eles desenham a própria página.)
- **Por que arquivo, e não `TakePicturePreview`.** O preview devolve uma miniatura, e uma página
  fotografada não é legível nela. O arquivo é o único caminho para a foto inteira, e ele vive só até ser
  decodificado: nada vai para a galeria.
- **Por que a foto é lida de pé.** A câmera grava a imagem como o sensor a viu e registra o giro no
  EXIF; uma página fotografada em pé chega 90° deitada, onde nenhum motor lê. `CameraPhoto` aplica
  `ROTATE_90/180/270`; as etiquetas de espelho ficam como estão, porque a página continua sendo lida da
  esquerda para a direita.
- **O que a retentativa faz.** A foto vira `lastPhoto`, e «tentar offline» roda no mesmo bitmap. Sem
  isso, a retentativa pediria uma captura de tela do que a câmera deixou no lugar da foto.
- **Sete ícones em 320 dp.** 7 × 46 + 6 × 2 = 334 dp não cabia na tela mais estreita que o
  `ActionMenuTest` mede; o quadrado do ícone desceu para 42 dp (306 dp). O alvo de toque continua sendo
  o quadrado inteiro.
- **Permissão e entrega.** `CAMERA` no manifesto, com `<uses-feature android.hardware.camera
  android:required="false">` para o app ainda instalar em aparelho sem câmera. A foto chega ao app da
  câmera por `FileProvider` (`${applicationId}.photos` → `cache-path photos/`), porque `file://` numa
  intent é recusado desde o Android 7. O `MediaProjection` não é tocado em nenhum ponto do caminho.
- **Arquivos.** `app/src/main/java/dev/yabai/ocr/CameraActivity.kt` (novo) ·
  `app/src/main/java/dev/yabai/ocr/capture/CameraPhoto.kt` (novo) ·
  `app/src/main/java/dev/yabai/ocr/service/FloatingOcrService.kt` ·
  `app/src/main/java/dev/yabai/ocr/overlay/OverlayController.kt` · `app/src/main/AndroidManifest.xml` ·
  `app/src/main/res/xml/camera_paths.xml` (novo) · `app/src/main/res/drawable/ic_menu_camera.xml` (novo) ·
  `values/strings.xml` · `values-en/strings.xml` ·
  `app/src/test/java/dev/yabai/ocr/CameraCaptureTest.kt` (novo) ·
  `app/src/test/java/dev/yabai/ocr/overlay/ActionMenuTest.kt` ·
  `app/src/test/java/dev/yabai/ocr/overlay/RecordingCallbacks.kt` · `LANGUAGES.md`

Suíte do app (`:app:test`): **874 testes (437 em debug e 437 em release), 0 falhas**. Regressão:
`CameraCaptureTest` (3) exige o giro do EXIF (e que as etiquetas de espelho não girem), que o arquivo
da foto não sobreviva à leitura, e que o manifesto declare a câmera, o portal e o `FileProvider`, que o
portal entregue `ACTION_PHOTO`/`EXTRA_PHOTO_PATH`, e que o serviço leia a foto pelo mesmo
`dispatcher.recognize` **sem** passar por `requestCapture`. `ActionMenuTest` (6) passa a exigir sete
ações na ordem, com a câmera em terceiro — o teste de «cada ícone roda a sua ação» cobre o par novo.
Mutações provadas:

| mutação | teste que acusa |
| --- | --- |
| `turnDegrees` devolve sempre 0 | `the EXIF turn is honoured and the mirroring ones are not` |
| `loadUpright` deixa de apagar o arquivo | `the photo file is deleted once it has been read` |
| O ícone da câmera chama `onRegionRequested` | `each icon runs its own action` |
| `onCameraRequested` chama `requestCapture` | `the camera asks for its permission and hands the file to the running service` |

- **Limite.** Sem aparelho: o Robolectric não abre o app da câmera. A conferência é do que este app
  decide (permissão, giro, apagar o arquivo, entrega ao serviço) e do menu medido; um JPEG real com
  etiqueta de giro não é decodificado no teste, então quem cobre a decisão é `turnDegrees`.

### R46e — Forma antiga do kanji no Jitendex  `[x]` (21/11/2026)

**Pedido.** No Jitendex (e em dicionários do mesmo formato), algumas entradas são só um redirecionamento: o
kanji reconhecido existe, mas é a grafia antiga, e o cartão mostra `old kanji form` e «redirected from»
sem nenhum significado. Exemplo: 兩膝 responde apenas com o redirecionamento; o interessante é mostrar que
é uma forma antiga **e** os dados do kanji atual (両膝), com o significado. Vale para a busca e para o
hover.

- **Causa.** O `DictionaryCompiler` achatava o `structured-content` em texto já na importação e o `href`
  do link era jogado fora: 兩膝 virava só «old kanji form» e «redirected from 兩膝» nas linhas, e o
  `LookupEngine` devolvia essa entrada como se fosse o verbete. Sem o alvo guardado não havia o que seguir.
- **De onde vem o alvo.** O `GlossaryRenderer` decide de forma **estrutural** se a entrada só aponta: quem
  tem um nó `data-content="glossary"` com texto próprio tem definição; quem só tem links não tem, e o alvo
  é o `?query=` do primeiro `<a>` (decodificado, sem os outros parâmetros). A frase «redirected from» nunca
  é lida — ela é do dicionário e muda de idioma.
- **Como o alvo viaja.** Um campo novo no blob de termo, depois das tags e antes da definição, com o termo
  de destino. `FORMAT_VERSION` sobe para 4 e `REDIRECT_FORMAT` diz ao leitor quando o campo existe:
  `CompactDictionary.decodeEntry` só o lê em cópias ≥ 4, então uma cópia de formato 3 continua abrindo com
  a definição logo depois das tags — e o banner de formato antigo já diz que reimportar vale a pena.
- **Como a busca resolve.** `LookupEngine.follow` troca a entrada pelo alvo **no mesmo dicionário** (cruzar
  dicionários responderia com outra revisão e outra renderização), ignora alvo igual ao próprio termo e,
  quando o alvo não existe ou também redireciona, mantém a entrada original. Vale para `search` (busca),
  `lookup` (hover e frase) e para a entrada alcançada por flexão. `TokenMatch.oldForm` leva o fato até a
  tela.
- **O que o cartão mostra.** A entrada exibida é a do alvo: leitura, tags e definição são de 両膝. O painel
  do Android já escrevia «兩膝 → 両膝» no cabeçalho e passa a listar «forma antiga» (`dictionary_old_form`,
  pt/en). No desktop a linha vem de `DictionaryMatchText.kt`, a mesma para o hover e a janela de resultado,
  e entra na altura estimada do cartão de hover.
- **Arquivos.** `core/src/main/kotlin/dev/yabai/ocr/core/dict/GlossaryRenderer.kt` ·
  `dict/DictionaryCompiler.kt` · `dict/CompactDictionary.kt` · `dict/DictionaryData.kt` · `dict/LookupEngine.kt` ·
  `app/src/main/java/dev/yabai/ocr/overlay/ResultOverlay.kt` · `app/src/main/res/values/strings.xml` ·
  `values-en/strings.xml` · `desktop/src/main/kotlin/dev/yabai/ocr/desktop/DictionaryMatchText.kt` (novo) ·
  `HoverTooltipWindow.kt` · `ResultWindow.kt` · `i18n/DesktopStrings.kt` ·
  `core/src/test/.../RedirectEntryTest.kt` (novo) · `app/src/test/.../DefinitionPanelTest.kt` ·
  `desktop/src/test/.../DictionaryMatchTextTest.kt` (novo) · `LANGUAGES.md`

Suíte: **core 208, app 878 (439 em debug e 439 em release), desktop 594, 0 falhas**. Regressão:
`RedirectEntryTest` (6) compila uma fixture Yomitan com a entrada de redirecionamento e o alvo, e exige o
alvo gravado, nenhum alvo numa entrada com definição própria, a busca e a frase do hover respondendo com o
verbete moderno mais `oldForm`, e a entrada comum sem a marca. `DefinitionPanelTest` (+1) exige «兩膝 → 両膝»
e «forma antiga» no painel. `DictionaryMatchTextTest` (3) exige a linha nos dois idiomas, sem a marca numa
flexão, e nada quando a palavra responde por si. Mutações provadas:

| mutação | teste que acusa |
| --- | --- |
| `redirectOf` devolve sempre vazio | `an entry that is only a link stores the link target`, `the search answers…`, `a sentence follows…` |
| `LookupEngine.follow` ignora o alvo | `the search answers with the modern entry and says it is an old form`, `a sentence follows the old form too` |
| O painel deixa de listar a forma antiga | `an old form is answered with the modern entry and says so` |
| `dictionaryFormLine` não marca a forma antiga | `an old form names both words, in the interface language` |
| `CompactDictionary.decodeEntry` lê o redirect em toda cópia, inclusive formato 3 | `a format 3 copy still reads its definitions` |

- **Limite.** As fixtures são Yomitan sintéticas no formato do Jitendex, não o Jitendex real: não há neste
  repositório uma cópia do dicionário para conferir o markup exato; a sexta regressão rebaixa a fixture
  para o formato 3 e exige que a definição continue legível. Dicionários já importados precisam ser
  reimportados para ganhar o campo (formato 4). Conferência nas cópias reais deste computador em
  21/11/2026: todas estão em `compiledWith` 3, então 兩膝 ainda responde com a própria entrada («1. ⟶ 両膝 /
  2. 両膝 / 3. redirected from 兩膝») no Jitendex e no GGJZ; 両膝 já existe nos dois com leitura りょうひざ.
  O painel do Android não foi olhado numa tela — o Robolectric
  mede o texto, não a pintura — e o hover do desktop não foi aberto.

## 5. Versão Desktop (`:desktop`)

Ordem de execução para a versão Desktop: **um item por vez**.

Legenda: `[ ]` pendente · `[x]` pronto · `[~]` parcial · `[-]` descartado

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| D1 | `[x]` | **Estruturação da tela de Ajustes/Configurações Desktop** | concluído |
| D2 | `[x]` | **Adaptar Painel de Resultados de OCR e Dicionários Completos** | concluído |
| D3 | `[x]` | **Navegação da Janela Principal**: Dicionário integrado na mesma janela com botão «Voltar ao Menu Anterior» | fácil |
| D4 | `[x]` | **Fix de Seleção de Área (Overlay de Captura)**: Cobrir 100% da tela inteira sem mini print deslocado | fácil |
| D5 | `[x]` | **Atalho Global Interativo**: Botão "Capturar Atalho" (Key Grabber) e botão "Restaurar Padrão" | médio |
| D6 | `[x]` | **Perguntar a uma IA Customizada**: Seleção de provedor/navegador e URL customizada com `{text}` (ChatGPT, Gemini, DeepSeek, Claude) | fácil |
| D7 | `[x]` | **Seleção da Pasta de Dicionários**: Permitir ao usuário escolher onde manter os dicionários | fácil |
| D8 | `[x]` | **Janela Compacta de Dicionário Rápido**: Dicionário compacto com persistência de tamanho (W, H) e posição (X, Y) | médio |
| D9 | `[x]` | **Fix do Movimento da Janela do Dicionário ao Digitar**: Corrigir reposicionamento involuntário ao digitar | fácil |
| D10 | `[x]` | **Design do Menu da Bandeja (System Tray)**: Estilização do menu da bandeja no padrão de design do app | médio |
| D11 | `[x]` | **Ícones Unificados Desktop**: Ícones da bandeja, barra de tarefas e janelas alinhados com o mobile | fácil |
| D12 | `[x]` | **Campos de Texto Selecionáveis**: Texto de OCR e definições selecionáveis para cópia (`SelectionContainer`) sem serem editáveis por padrão | fácil |
| D13 | `[x]` | **Filtro de Dicionário Ativo**: Seletor para filtrar buscas por dicionário específico ou "Todos" no OCR e na consulta | fácil |
| D14 | `[x]` | **Sugestões de Busca e Botões Apagar/Colar**: Sugestões automáticas (`LookupEngine.suggest`) e botões rápidos ❌ Apagar / 📋 Colar no campo de busca | médio |
| D15 | `[x]` | **Atalho Global para Busca Rápida**: Atalho de teclado independente (ex: `Ctrl + Shift + D`) para abrir o Dicionário Rápido em background | médio |
| D16 | `[x]` | **Catálogo Completo e Atribuições Legais**: Adicionar o catálogo completo de dicionários do mobile (Wiktionary, Onomatopeias, etc.) e dados legais de uso | fácil |
| D17 | `[x]` | **Menu Flutuante da Bandeja Customizado**: Popup do tray estilizado em Compose Desktop com tema escuro (`0xFF12141A`), bordas e hover | médio |
| D18 | `[x]` | **OCR em Tempo Real por Hover (Estilo Yomitan)**: laço contínuo do mouse, debounce, recorte limitado às bordas, palavra pela caixa da linha e cartão ao lado do cursor | avançado |
| D21 | `[x]` | **Limpeza da Tela de Ajustes**: Remover numeração dos cards de configurações ("1. ", "2. ", etc.) e remover a opção legada "Escolher trecho na captura da tela inteira" | trivial |
| D20 | `[x]` | **Iniciar na Bandeja ao Ligar o Computador**: Opção nas configurações para iniciar o aplicativo minimizado na bandeja ao ligar o PC (com chave de ativação/desativação) | fácil |
| D22 | `[x]` | **Créditos e Licenças dos Dicionários**: Opção/botão para visualizar créditos, licenças e informações de atribuição legal dos dicionários no desktop | fácil |
| D19 | `[x]` | **Gerenciamento e Verificação de Atualizações de Dicionários**: Verificação de atualizações de dicionários, botão para verificar e para atualizar todos ou individualmente | médio |
| D23 | `[x]` | **Prioridade e Ativação/Desativação de Dicionários**: Permitir definir a ordem de prioridade dos dicionários e opção de desabilitar/habilitar sem deletar | médio |
| D24 | `[x]` | **Fix de Layout em Textos Longos (Posição da Imagem)**: Corrigir posicionamento da imagem recortada quando a seleção de texto for muito longa para não ficar escondida abaixo da tela | médio |
| D25 | `[x]` | **Captura de Tela Inteira e Opções Avançadas**: Captura de tela inteira com escolha de trecho, atalho e botão próprios para o recorte de área, rubi sobre o texto e faixa indicativa legível | avançado |
| D26 | `[x]` | **Fix de Perda de Tela Cheia ao Alternar Telas**: Corrigir bug onde abrir o gerenciador de dicionários ou voltar ao menu anterior redefiniam o estado de maximizado/tamanho da janela principal | fácil |
| D27 | `[x]` | **Menu da bandeja no cursor, como o da Steam**: abrir no canto inferior esquerdo do ponteiro sobre a barra de tarefas, crescendo para cima, e inverter para a esquerda quando não couber | fácil |
| D28 | `[x]` | **Contraste dos botões nos dois temas**: acabar com `Color.White` fixo no desktop e no Android, que deixava botões como «Padrão» e «Verificar atualizações» ilegíveis no tema claro | médio |
| D95 | `[x]` | **Devolver o heap ao desligar o hover**: o G1 ocioso não recolhe o lixo nem devolve o commit; desligar o hover só torna os dicionários e as sessões ONNX inalcançáveis | médio |
| D96 | `[x]` | **Flags de higiene de heap no app empacotado**: `-Xms`, trim concorrente e GC periódico para o app que fica na bandeja | fácil |
| D97 | `[x]` | **O picker fechava e deixava o motor de dicionários aberto**: 123 MB de índice (+ tradução) vivos até o app sair | fácil |
| D98 | `[x]` | **Reutilizar a janela do cartão** em vez de criar uma `ComposeWindow` por palavra, que deixa superfícies nativas no glibc | difícil |
| D99 | `[x]` | **Reduzir a alocação por leitura do hover**: recorte de altura inteira + `BufferedImage` por leitura | médio |
| D100 | `[x]` | **Limitar o pior caso e medir o glibc no Linux**: teto de heap e `MALLOC_ARENA_MAX` com PSS antes/depois | fácil |

### D21 — Limpeza da Tela de Ajustes `[x]` (03/11/2026)

- Removida a numeração ("1. ", "2. ", "3. ", "4. ", "5. ", "6. ") dos títulos dos cards na tela de Ajustes (`SettingsWindow.kt`).
- Removida a opção legada "Escolher trecho na captura da tela inteira" da tela de Ajustes.

### D20 — Iniciar na Bandeja ao Ligar o Computador `[x]` (03/11/2026)

- Adicionado `StartupManager` para gerenciar registro de inicialização no Windows (`HKCU\Software\Microsoft\Windows\CurrentVersion\Run`).
- Adicionado argumento `--minimized` no fluxo de inicialização (`Main.kt`) para iniciar oculto na bandeja do sistema quando acionado pelo SO.
- Adicionado switch em Ajustes (`SettingsWindow.kt` e `DesktopSettings.kt`) para ativar ou desativar a inicialização automática.

### D22 — Créditos e Licenças dos Dicionários `[x]` (03/11/2026)

- Adicionada a seção expansível "Créditos e Licenças" na aba de dicionários locais (`DictionaryManagerWindow.kt`), seguindo o padrão da versão mobile.
- Exibe a origem e as informações detalhadas de atribuição e licença (CC BY-SA, EDRDG, Yomitan, Wikcionário, etc.) para cada dicionário instalado.

### D19 — Gerenciamento e Verificação de Atualizações de Dicionários `[x]` (03/11/2026)

- Adicionado botão global "🔄 Verificar Atualizações" para checar revisões remotas de todos os dicionários instalados.
- Exibição condicional do botão "🔄 Atualizar" apenas para dicionários que possuem uma versão/revisão mais recente disponível no catálogo.
- Adicionado botão "🔄 Atualizar Todos os Dicionários Disponíveis" para atualizar em lote todos os dicionários desatualizados de uma só vez, com barra de progresso.

### D26 — Fix de Perda de Tela Cheia ao Alternar Telas `[x]` (03/11/2026)

- Corrigido o bug em `Main.kt` onde a janela principal era recriada ao alternar entre Configurações e Gerenciador de Dicionários (perdulando o estado de maximizado/tela cheia). Agora utiliza `rememberWindowState` para preservar o estado e tamanho da janela durante a navegação.

### D23 — Prioridade e Ativação/Desativação de Dicionários `[x]` (03/11/2026)

- Adicionado suporte em `DesktopSettings` e `DesktopDictLoader` para gerenciar dicionários desativados (`disabled_dictionaries`) e ordem de prioridade (`dictionary_order`).
- Adicionados controles na aba de dicionários instalados (`DictionaryManagerWindow.kt`): checkbox de Ativo/Desativado e botões de reordenação (▲ / ▼) para definir a prioridade de consulta de verbetes.
- Integração em `QuickDictWindow`, `ResultWindow` e `HoverOcrManager` para respeitar a ordem de prioridade e os dicionários desativados.

### D24 — Fix de Layout em Textos Longos (Posição da Imagem) `[x]` (03/11/2026)

- Corrigido o layout horizontal no `ResultWindow.kt`: a imagem recortada foi reposicionada para o **topo** (acima da caixa de texto), evitando que ficasse oculta abaixo da tela quando o texto reconhecido fosse longo.
- Adicionadas restrições de altura (`heightIn(min = 100.dp, max = 240.dp)`) no campo de texto para garantir rolagem interna limpa sem comprometer a visibilidade do recorte original.

### D18 — Hover em tempo real: o que estava quebrado (03/11/2026) `[x]`

O item já estava marcado como pronto, mas **passar o mouse não fazia nada**: o cartão só aparecia se o
atalho fosse apertado. Três defeitos independentes se somavam:

1. **Não existia observador do mouse.** O que o atalho chamava (`triggerAtCursor`) fazia uma captura
   única de 320×120 no instante da tecla e desistia. Sem um laço lendo `MouseInfo`, ficar parado sobre
   uma palavra nunca podia disparar nada. Agora um laço de 60 ms roda enquanto o modo está ligado, e
   `HoverGate` só dispara depois que o ponteiro fica **420 ms** dentro de 5 px — sem repetir o mesmo
   ponto; a lembrança do disparo só se apaga depois que o ponteiro anda mais de **14 px** para longe.
   Fechar o cartão *não* libera o ponto: era isso que fazia a mesma palavra piscar em laço quando a
   caixa do OCR não continha o cursor com exatidão.
2. **A pasta escolhida pelo usuário não era lida.** Versões anteriores gravaram a chave
   `custom_dict_directory`; o código lia só `custom_dict_dir` e caía no padrão `~/.yabai_ocr/dicts`,
   vazio. Todo lookup respondia «Nenhum dicionário instalado» mesmo com os `.yabai` no lugar. A chave
   antiga voltou a ser lida e é migrada na primeira gravação.
3. **O atalho não casava com a tecla.** Os códigos `VC_*` do JNativeHook não são os do AWT
   (`VC_H` = 35, não 72), e a comparação antiga usava o texto da tecla; `Ctrl + Shift + H` passava
   batido. `HotkeyMatcher` compara código nativo com uma tabela explícita e exige os modificadores
   exatos.

A precisão do OCR também mudou: o recorte passou a 640×360 (mais contexto da linha, onde vive a
conjugação), limitado aos monitores — antes um recorte fixo perto da borda fazia o `Robot` lançar
exceção, engolida em silêncio; e a palavra é escolhida pela **caixa de cada linha** devolvida pelo
Lens, dividida entre os caracteres, em vez de adivinhada pela frase inteira. Funciona em texto
horizontal e vertical, e a caixa do token é convertida de volta para a tela considerando o HiDPI.
O cartão abre ao lado do cursor, vira para o outro lado nas bordas e fica aberto enquanto o ponteiro
está na palavra ou sobre ele (220 ms de carência para atravessar o vão), o que permite selecionar e
copiar a definição. Ele se reposiciona quando a própria janela muda de tamanho, e a captura usa o
monitor **sob o ponteiro**, não sempre o principal.

Controles: o atalho de hover (padrão `Ctrl + Shift + H`) **liga e desliga o modo**; ao ligar, já faz
uma leitura no ponto atual. Nos Ajustes há o cartão «Modo Hover» com Ativar/Desativar, o status e
«Testar no cursor atual»; o menu da bandeja mostra `[Ativo]`/`[Inativo]`; e o tooltip da bandeja repete
o último status, útil quando o cartão não aparece.

Testes de regressão em `desktop/src/test` (47 no total): `HotkeyMatcherTest` (7), `HoverGateTest` (7),
`HoverDismissGateTest` (6), `HoverGeometryTest` (14), `HoverTextSelectorTest` (10) e
`DesktopSettingsTest` (3). Cada um foi conferido por mutação: quebrar a regra correspondente faz o
teste falhar e restaurá-la o faz passar.

### D25 — Captura de tela inteira com seletor de trechos `[x]` (03/11/2026)

O D21 tinha removido o ajuste «Escolher trecho na captura da tela inteira» porque **nada o lia**: o
atalho sempre abria o recorte à mão. O D25 implementa de fato a captura da tela inteira com escolha
de parágrafo e, na revisão, transforma as duas capturas em **duas ações visíveis** — sem preferência
escondida decidindo o que um único atalho faz.

- **Duas capturas, dois atalhos.** `global_hotkey` (o atalho que já existia — `Insert` nesta
  máquina) é a **tela inteira**; o novo `region_hotkey` (padrão `Ctrl + Shift + A`) é o recorte à
  mão. A bandeja ganhou «🖥️ Capturar Tela Inteira (OCR)» e «✂️ Capturar Área (OCR)», e o clique
  esquerdo no ícone abre a tela inteira. `pick_text` foi removido do código e dos Ajustes.
- **Atalhos ao vivo.** `GlobalHotkeyManager` é criado com `remember` (não `DisposableEffect(Unit)`)
  e o callback dos Ajustes chama `updateHotkeys`; trocar a combinação vale na hora, sem reiniciar.
- **Seletor sobre a tela.** O picker mostra o screenshot em tela cheia e desenha uma caixa sobre cada
  `LensResult.blocks`. O OCR roda em `Dispatchers.IO` com o mesmo `LensClient` do resultado, e a
  janela mostra «Reconhecendo a tela inteira…» em vez de parecer travada; uma falha de rede exibe o
  motivo e deixa o recorte à mão disponível.
- **Clique é o recorte.** Um clique numa caixa recorta o bloco com 12 px de folga (descendentes e
  espaço entre glifos ajudam o segundo OCR), abre o `ResultWindow` com o texto **já reconhecido** e o
  cabeçalho «Trecho escolhido no seletor de tela» — sem um segundo Lens, que custaria outra ida à
  rede e poderia ler o mesmo traço de outro jeito. Esc, clique direito, «✕ Cancelar» ou fechar a
  janela cancelam; «✂️ Recortar à mão» devolve o **mesmo** screenshot ao overlay de arrasto.
- **Rubi e faixa indicativa.** `picker_reading` (RUBY/LABEL/OFF) decide o que o seletor desenha:
  - `RUBY`: as leituras vêm dos **dicionários instalados** (`engine.lookup` + `engine.readings`,
    isto é, `Furigana`), não do furigana impresso. O rubi vai **acima** de um trecho horizontal,
    centrado nele, ou **empilhado na calha à direita** de uma coluna vertical, como manda a
    composição japonesa. O tamanho é 55% do glifo, limitado também pela altura disponível por kana
    (cinco kana ao lado de dois kanji não podem ser esmagados), e abaixo de 7 px a leitura não é
    desenhada — borrão é pior que nada. Texto incerto sai em âmbar; certo, em branco, sempre com
    contorno escuro para sobreviver a fotos.
  - `LABEL`: cada caixa ganha uma faixa legível com a frase reconhecida, à direita, esquerda,
    abaixo e acima — nessa ordem —, presa à tela em todos os casos. Faixas já colocadas são
    desviadas ao longo do próprio lado antes de trocar de lado, então dois balões vizinhos não
    escrevem uma por cima da outra.
- **Dicionários abertos entre capturas.** `DesktopDictLoader.cachedEngine(settings)` reaproveita o
  `LookupEngine` enquanto pasta, conjunto habilitado, ordem de prioridade e nome/tamanho/data dos
  `.yabai` não mudarem; ao mudar, fecha o antigo e reabre. Abrir os dicionários é a parte lenta do
  lookup e o picker precisa deles logo depois da captura. `closeCached()` libera os arquivos.

**A captura é o framebuffer, não uma miniatura.** A janela do seletor usa os bounds AWT inteiros (a
área da barra de tarefas incluída; a barra em si continua por cima, como sempre) e é posicionada e
dimensionada com os valores AWT **diretos** em `.dp`: converter com `toDp()` aplicava a densidade
duas vezes e encolhia a janela para 2/3 da tela num display a 150%. `ScreenCapture.captureScreen()` passou a usar
`createMultiResolutionScreenCapture` e a pegar a variante **nativa** (1920×1080 num display de
1280×720 a 150%); `createScreenCapture` devolvia a variante de usuário (1280×720), e desenhá-la
sobre um canvas de 1920×1080 reamostrava — e borrava — cada glifo. O picker desenha 1:1 com
`FilterQuality.None`, em janela opaca e sem véu; o overlay de recorte usa o mesmo enquadramento e
também desenha 1:1, e o escurecimento só aparece **depois** de o usuário arrastar (fora da
seleção), como retorno visual.

A geometria ficou fora do Compose, em `PickerBoxes.kt` (escala imagem↔canvas, folga presa à imagem,
acerto pela **menor** caixa que contém o ponto — parágrafos e colunas se sobrepõem — e recorte nunca
vazio) e `PickerReadings.kt` (`PickerRuby.spot` e `PickerLabels.place`), para ser testável sem tela.
A janela em si é `SegmentPickerWindow.kt`.

Testes: **74 no total**, com 27 novos — `PickerBoxesTest` (9), `PickerReadingsTest` (13),
`DesktopSettingsTest` (2) e `ScreenCaptureTest` (3). As oito regras que mais doem foram conferidas
por mutação, cada uma falhando e voltando a passar ao restaurar:

| mutação | teste que acusa |
| --- | --- |
| `toCanvas` com escala 1:1 | `a box is scaled from the image to the canvas` |
| `hit` devolve a primeira caixa, não a menor | `the smallest containing box wins…` |
| faixa aglomerada anda no eixo errado | `a crowded strip below its box steps sideways…` (e mais 2) |
| rubi vertical limitado só pela largura | `a short run caps the reading by its height…` |
| recorte sem o limite da borda direita | `padding never leaves the image` |
| `region_hotkey` com o mesmo padrão da tela inteira | `full-screen capture and region crop have distinct shortcuts by default` |
| `region_hotkey` lendo a chave `global_hotkey` | `the region shortcut is read from its own key` |
| captura pedindo a resolução de usuário (sem escala) | `a display scaled at 150 percent asks for the native device size` |

Ponta a ponta, com o app rodando: em RUBY as caixas cobriram todos os parágrafos e `にほんご` foi
desenhado sobre `日本語`; um clique em (600, 300) abriu o resultado com `日本語を勉強します` e o
cabeçalho do seletor. Em LABEL as faixas apareceram ao lado das caixas, presas à tela e sem se
sobrepor. `Insert` abriu o picker cobrindo a tela inteira (1280×720) e `Ctrl + Shift + A` abriu
«Arraste para selecionar». A comparação pixel a pixel antes/depois do picker deu **0 diferenças**
em 25.200 px (página branca), 9.200 px (xadrez de 1 px), 16.000 px (fundo preto) e 22.500 px (área
vazia). O xadrez de 1 px é o teste decisivo: antes da correção, 6.900 dos 9.200 pixels mudavam.
A área do texto não entra na conta porque leva o rubi e o contorno das caixas por desenho, e a
barra de tarefas fica acima da janela (o acrílico do Windows muda de tom conforme o que está atrás).

Limitação conhecida: `ScreenCapture.captureScreen()` captura apenas o monitor principal; o hover já
usa o monitor sob o ponteiro, mas o seletor ainda não.

### D27 — Menu da bandeja no cursor, como o da Steam `[x]` (06/11/2026)

**Causa raiz.** O menu do tray (popup Swing próprio, D17) era mostrado em `(0, 0)` de um `JDialog`
invisível, sem consultar o ponteiro nem a área útil do monitor. Na prática ele abria no canto
superior esquerdo da tela, longe do ícone e, em monitores com escala ou múltiplos monitores, fora do
lugar onde o usuário clicou.

**O que mudou.** A regra saiu da janela e virou função pura em `TrayMenuPlacement.kt`: a **borda
inferior** do menu fica na altura do cursor e a **esquerda** no X do cursor, como o menu da Steam;
se não couber à direita, inverte para a borda direita no cursor e cresce para a esquerda; se não
couber acima, abre abaixo; o resultado é preso à **work area** do monitor sob o cursor (fora da
barra de tarefas, inclusive em monitores com X/Y negativos). `SystemTrayManager` ancora em
`MouseInfo.getPointerInfo()` — coordenadas de tela, não relativas ao ícone — e calcula a work area
com `getScreenInsets` do device sob o ponteiro.

**Testes e mutações.** `TrayMenuPlacementTest` (7 testes) cobre as quatro bordas, a inversão, o
monitor secundário e o menu maior que a área útil. Mutação provada: fixar o X no cursor (sem
inverter) derruba dois testes («flips to the left…» e «stays inside a secondary monitor…»).

**Limitação do E2E.** O ícone do Yabai fica no *overflow* da barra de tarefas e o menu fecha ao
perder o foco; a captura via `java.awt.Robot` não conseguiu manter o menu aberto para medir a
posição pixel a pixel. A regra geométrica é provada por teste; a abertura real foi confirmada
manualmente (o menu passa a aparecer junto ao cursor).

### D28 — Contraste dos botões nos dois temas `[x]` (06/11/2026)

**Causa raiz.** As janelas ganharam tema claro/escuro, mas a cor do texto continuou fixa em vários
pontos. No desktop, cada botão «Padrão» usava `outlinedButtonColors(contentColor = Color.White)`;
como o `OutlinedButton` é transparente sobre `surface`, no tema claro o rótulo ficava **branco sobre
branco**. No Android, `actionButton` (o construtor de botões dos painéis e da tela de dicionários)
usava `textColor = Color.WHITE` e fundo `0x22FFFFFF`: «Verificar atualizações» e «Desligado»
sumiam no tema claro. A tela `DictionaryActivity` também pintava cartões com `0x14FFFFFF` fixo.

**O que mudou.**

- **Desktop:** `DesktopTheme.kt` ganhou a paleta semântica `YabaiPalette` (campo, borda, chip,
  sucesso/perigo/aviso/info) escolhida por `paletteFor(darkTheme)`, e `desktopColors(darkTheme)`
  virou a fonte única das cores do Material. `SettingsWindow`, `QuickDictWindow` e
  `DictionaryManagerWindow` deixaram de usar branco/hex fixo para texto e fundo. O preenchimento do
  botão primário virou `palette.button`/`palette.onButton`, separado de `primary`, que continua
  servindo de acento sobre o fundo (um preenchimento precisa ser escuro para o branco; um acento
  precisa ser claro para o fundo escuro — uma cor só não faz os dois).
- **Android:** `actionButton` passou a ler `R.color.text_primary` e `R.color.card_bg`/
  `R.color.card_stroke` (os mesmos recursos `values`/`values-night` do resto do app), e
  `DictionaryActivity` usa `card_bg`/`card_stroke` em vez dos hex translúcidos fixos. Os overlays
  que são **sempre escuros por desenho** (resultado, picker de região, kanji) ficaram intocados.
- **Janelas sempre escuras** (`ResultWindow`, `SegmentPickerWindow`, `RegionSelectionWindow`,
  `HoverTooltipWindow`) também ficaram como estavam: são autoconsistentes e não seguem o tema.

**Testes e mutações.**

| arquivo de teste | o que prende | mutação que o derruba |
| --- | --- | --- |
| `DesktopThemeContrastTest` (5) | contraste WCAG de 4,5:1 para texto normal e rótulo de botão; 3:1 para o acento não textual | `secondary` branco no tema claro; `button` de volta a `#4C78FF` (3,86:1) |
| `ThemeWindowColorsTest` (2) | as três janelas temáticas não podem ter `Color.White`/`Color.Black` nem hex fixo fora de `isLight` | devolver `contentColor = Color.White` a um «Padrão» |
| `ActionButtonThemeTest` (3) | no tema claro o rótulo é `text_primary` (não branco) e o contraste sobre `card_bg` composto com `panel_bg` é ≥ 4,5:1 | `textColor = Color.WHITE` no `actionButton` |

O teste Android troca o tema pelo caminho real (`AppSettings.appTheme`, o mesmo dos rádios), porque
`applyTheme()` reescreve o `night mode` dos recursos e anula o qualificador `qualifiers` do
Robolectric.

**E2E (tema claro e escuro, app real).** Capturas em 1280×720 com o app rodando:

| botão | tema | cor do texto / fundo medidos | contraste |
| --- | --- | --- | --- |
| «Padrão» | claro | `#1C1C1E` / `#FFFFFF` | **17,01:1** |
| «Capturar» | claro | `#FFFFFF` / `#3D6BF3` | **4,57:1** |
| «Padrão» | escuro | `#FFFFFF` / `#1B1E28` | **16,63:1** |
| «Capturar» | escuro | `#FFFFFF` / `#3B63D9` | **5,29:1** |
| campo «Atalho: Insert» | claro | `#1C1C1E` / `#EFF1F5` | **15,05:1** |

Antes, «Padrão» no claro media **1,0:1** (branco sobre branco). O `settings.properties` foi
restaurado do backup com hash conferido e nenhum processo ficou aberto.

**Custo colateral.** A suíte Android passou a falhar de forma intermitente com `StackOverflowError`
do Robolectric ao inflar um `RadioButton` animado **sem** o teste novo (reproduzido removendo-o). O
worker de testes ganhou `-Xss4m` em `app/build.gradle.kts`; a suíte voltou a 394 testes verdes.

**Números.** Desktop: **89 testes**. Android: **394 testes**.

---

### D29 — Base Linux: sessão, autostart XDG, ícone e empacotamento `[x]` (07/11/2026)

Primeira fase (P1) do plano `LINUX.md`: o app continua idêntico no Windows, mas a base Linux deixa de
ser “só abre”. O que entrou:

- `DesktopPlatform.kt`: detecção pura de SO, sessão (`XDG_SESSION_TYPE`, `WAYLAND_DISPLAY`,
  `DISPLAY`), desktop (KDE/GNOME) e SteamOS (`/etc/os-release`), com testes por parâmetro.
- `PlatformServices.kt` + `Autostart.kt`: um único ponto escolhe o backend. `StartupManager` virou
  fachada; o registro do Windows foi movido sem alteração para `WindowsAutostart.kt`.
- `LinuxAutostart.kt`: grava `$XDG_CONFIG_HOME/autostart/yabai-ocr.desktop` com o caminho real do
  AppImage/jpackage e `--minimized`; desligar **apaga** o arquivo. Sem executável encontrado, não
  grava entrada quebrada.
- `LinuxDesktopEntry.kt`: `Exec` sempre entre aspas com escape de `"`, `` ` ``, `$` e `\`; descoberta
  do executável na ordem `APPIMAGE` → `jpackage.app-path` → comando → `~/.local/bin` → `/usr/bin` →
  `/opt` → `~/Applications/*.AppImage`.
- `AppIconGenerator.kt`: no Linux usa `ic_scan_linux.png` (256×256, extraído do ICO existente) e
  mantém o ICO como fallback; o Windows continua começando pelo `.ico`.
- `DesktopSettings.kt`: aceita `desktopHome` injetável e respeita `YABAI_HOME` no caminho padrão,
  para o E2E não tocar em `~/.yabai_ocr`.
- `desktop/build.gradle.kts`: `Deb`/`AppImage` + `packagePortableTarGz` só em host Linux; tarefas
  Inno/ZIP só em Windows; `packageName` `yabai-ocr` no Linux; `-Dfile.encoding=UTF-8`.
- `desktop/package/linux/` passa a existir como raiz de recursos Linux.

**Causa raiz coberta pelos testes.** O código anterior fazia `if (os.contains("win"))` e retornava
`false` para qualquer outro SO; não havia como o Linux criar autostart, e o executável era adivinhado
por caminhos fixos de Windows. Os testes novos amarram a decisão por plataforma e o conteúdo exato do
`.desktop`, não apenas “não lançou exceção”.

**Testes.** `:desktop:test` passou de 96 para **123** (27 novos): `DesktopPlatformTest`,
`LinuxAutostartTest`, `LinuxDesktopEntryTest`, `AutostartSelectionTest`, `IconResourceTest`,
`DesktopHomeSettingsTest`.

**Mutações provadas** (script `mutation-proof.ps1`, restauração com hash conferido):

| mutação | teste que acusou |
| --- | --- |
| `detectSessionType` ignora `WAYLAND_DISPLAY` | `wayland display alone is treated as wayland` |
| `desktopEntryArgument` não escapa a aspa | `exec arguments are always quoted and escaped` |
| `setEnabled(false)` não apaga o arquivo | `disabling deletes the entry instead of hiding it` |
| seleção Linux devolve o backend Windows | `linux selects the xdg implementation` |
| ícone Linux volta para a lista do Windows | `linux prefers a png and windows keeps the ico first` |
| `YABAI_HOME` ignorado | `yabai home overrides the data directory` |
| dicionário padrão ignora `desktopHome` | os dois de `DesktopHomeSettingsTest` |
| resolver aceita candidato inexistente | `resolver skips a missing appimage and picks the existing launcher` |

**Limitações desta fase.** Nada foi executado em Linux/Steam Deck: `packageAppImage`/`packageDeb` e
a abertura do AppImage ainda precisam de um host Linux. Captura via ScreenCast, atalhos por portal e
hover por KWin script são P2–P4. O Windows foi verificado por `:desktop:test` (123) e
`:desktop:createDistributable`; nenhum processo do app ficou aberto e `settings.properties` não foi
tocado.

---

### D30 — Captura Wayland: screencast, estados de permissão e picker fullscreen `[x]` (07/11/2026)

Segunda fase (P2) do plano `LINUX.md`. No Wayland o `Robot` não enxerga a tela real: cada aplicativo
só vê a própria superfície. A captura passa a usar o `org.freedesktop.portal.ScreenCast` através da
implementação do JDK 21 (`awt.robot.screenshotMethod=dbusScreencast`), com estado explícito de
permissão em vez de tela preta silenciosa.

- `ScreenshotMethod.kt`: `screenshotMethodOverride`/`configureScreenshotMethod` definem
  `dbusScreencast` **só** em Linux + Wayland + KDE. GNOME fica no default do JDK (RemoteDesktop) e
  Windows/X11 não são tocados. A propriedade é aplicada no início de `main`, antes de qualquer uso de
  AWT/Toolkit.
- `ScreenCapture.kt`: `CaptureAvailability` (`READY`, `NEEDS_PERMISSION`, `DENIED`, `UNSUPPORTED`),
  mensagens pt-BR distintas, `ScreenCaptureException` e a interface `ScreenCaptureBackend`.
  `RobotScreenCapture` preserva byte a byte o caminho histórico (variante nativa para HiDPI).
  `ScreencastScreenCapture` devolve `UNSUPPORTED` com JDK < 21, `NEEDS_PERMISSION` enquanto não há
  token de restore (`~/.java/robot/screencast-tokens.properties` ou
  `~/.awt/robot/screencast-tokens.properties`), `DENIED` depois de `SecurityException` e limpa a
  falha quando uma captura volta a dar certo.
- `PlatformServices.kt`: `captureBackendFor` escolhe screencast apenas em Linux Wayland; o resto
  continua no Robot.
- `PickerWindowPlacement.kt` + `RegionSelectionWindow.kt` + `SegmentPickerWindow.kt`: no Wayland os
  pickers abrem em `WindowPlacement.Fullscreen` com `WindowPosition.PlatformDefault`; no Windows/X11
  continuam com os bounds exatos capturados.
- `CaptureNoticeWindow.kt` + `Main.kt`: falha de captura vira janela visível (permissão negada,
  sessão sem suporte ou erro inesperado); antes só havia `printStackTrace()` no stderr.

**Causa raiz.** A escolha de backend não existia: todo SO capturava com `Robot`, e o Wayland nunca
definia `awt.robot.screenshotMethod`, então a captura caía no XWayland (ilha do próprio app) ou
falhava sem mensagem. O picker também dependia de posicionamento absoluto, que o Wayland ignora.

**Testes.** `:desktop:test` passou de 123 para **138** (15 novos): `ScreenshotMethodTest` (3),
`ScreencastCaptureTest` (6), `ScreenCaptureBackendTest` (4), `PickerPlacementTest` (2).

**Mutações provadas** (script `mutation-proof.ps1`, restauração com hash conferido):

| mutação | teste que acusou |
| --- | --- |
| método Wayland ignora o desktop (contamina GNOME) | `gnome wayland keeps the jdk default instead of forcing screencast` |
| Wayland volta para o backend Robot | `linux wayland uses the screencast backend` |
| JDK antigo deixa de ser `UNSUPPORTED` | `an old jdk is reported as unsupported` |
| token de restore ignorado | `an existing restore token means the session is ready` |
| negação do portal não é lembrada | `a denied portal is remembered and explained` |
| sucesso não limpa a falha anterior | `a successful capture after a denial clears the failure` |
| picker ignora fullscreen no Wayland | `wayland pickers open as fullscreen instead of absolute windows` |

**Limitações desta fase.** Nada foi executado em Wayland real: a captura via portal, o token de
restore e o comportamento do picker em fullscreen só podem ser confirmados no Steam Deck/Plasma. O
Windows foi verificado por `:desktop:test` (138) e `:desktop:createDistributable`. Atalhos por portal
(P3) e hover por KWin script (P4) seguem pendentes.



---

### D31 — Início com o Windows: entrada ausente/obsoleta sem reparo `[x]` (08/11/2026)

Sintoma relatado: o app não sobe com o Windows e, em «Aplicativos de inicialização», ele aparece
marcado. A tela do Windows parecia dizer o contrário do comportamento.

**Causa raiz.** O Windows executa apenas o valor em
`HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, mas a página «Aplicativos de inicialização» lê
também `StartupApproved\Run`: uma entrada apagada — ou apontando para um `.exe` que não existe mais —
continua listada como ativada. Na máquina do relato, `StartupApproved\Run\YabaiOCR` estava habilitado
(`02…`) enquanto o valor do `Run` **não existia**, e `settings.properties` dizia `start_on_boot=true`.
O código não detectava nem reparava esse descompasso:

- `StartupManager.isStartOnBootEnabled()` só verificava a *existência* do valor, nunca se o comando
  apontava para um executável existente;
- `getExecutablePath()` tinha um fallback que **inventava** `<cwd>\Yabai OCR.exe`; o valor era gravado
  sem validação, então um caminho inexistente virava uma entrada "ativada" que não iniciava nada;
- o único auto-reparo (`if (settings.startOnBoot) StartupManager.setStartOnBoot(true)`) vivia no
  `LaunchedEffect` da janela de Ajustes, que **nunca é criada** quando o Windows sobe o app com
  `--minimized`;
- `StartupManagerTest` escrevia e apagava o valor no registro **real** do usuário (`HKCU\...\Run`):
  a execução da suíte deixava exatamente o estado órfão observado (marca em `StartupApproved`, valor
  ausente).

**O que mudou.**

- `WindowsAutostart.kt`: o registro passou para a porta `AutostartRegistry` (`HkcuRunRegistry` em
  produção, fake nos testes); `currentExecutablePath()` devolve `String?` e só aceita arquivo que
  existe; o layout do jpackage foi corrigido (`<App>/runtime/bin/java.exe` → `<App>/Yabai OCR.exe`,
  um nível acima de `runtime`, antes calculado errado); o fallback fabricado foi removido.
- `setEnabled(true)` não grava caminho inexistente: registra no stderr e mantém a preferência.
- `isEnabled()` agora significa "a entrada existe **e** aponta para um executável existente";
  entrada obsoleta conta como desligada.
- `Autostart.reconcile(enabled)`: reescreve a entrada quando ela sumiu/está obsoleta e a preferência
  está ligada, remove quando está desligada; `StartupManager.reconcileStartOnBoot` chama isso no
  `main`, **antes de qualquer janela**, então vale também para `--minimized`.
- O auto-reparo dentro do `LaunchedEffect` da janela foi removido (redundante e tarde demais).

**Testes.** `:desktop:test` passou de 138 para **144** (6 novos; `StartupManagerTest` foi substituído
por `WindowsAutostartTest`, que não toca mais no registro real): gravação com aspas + `--minimized`,
`null` não grava, caminho inexistente não grava, entrada apontando para `.exe` removido conta como
desligada, `reconcile` reescreve entrada obsoleta, `reconcile` remove com a preferência desligada,
`setEnabled(false)` remove.

**Mutações provadas** (script `mutation-proof.ps1`, restauração com hash conferido):

| mutação | teste que acusou |
| --- | --- |
| `isEnabled()` volta a aceitar qualquer comando | `an entry pointing at a removed executable is reported as disabled`, `reconcile rewrites a stale entry to the current executable` |
| `setEnabled(true)` não valida o `.exe` | `enabling with an executable that does not exist does not write a broken entry` |
| `reconcile` não reescreve/remove | `reconcile rewrites a stale entry to the current executable`, `reconcile removes the entry when the preference is off` |

A primeira versão do teste de caminho inexistente passava `null` ao resolvedor e **não** derrubava a
mutação — foi reescrita para usar um caminho real que não existe (o bug histórico), e então acusou.

**Evidência ponta a ponta** (Windows real, `:desktop:createDistributable`, app iniciado com
`--minimized`, `settings.properties` salvo antes e restaurado com MD5 idêntico):

| passo | `HKCU\...\Run\YabaiOCR` observado |
| --- | --- |
| antes | ausente (só a marca órfã em `StartupApproved\Run`) |
| 1º início | `"…\desktop\build\distributions\main\app\Yabai OCR\Yabai OCR.exe" --minimized` |
| valor apagado à mão e 2º início | recriado com o mesmo comando |
| valor trocado para `"C:\Pasta Antiga\Yabai OCR.exe" --minimized` e 3º início | reescrito para o executável real |

Nenhum processo do app ficou aberto ao terminar.

**Limitações.** Um logon real do Windows não foi executado: a prova vai até a entrada correta no
registro (que é o que o Windows executa). O caminho gravado é o do executável em execução; quem roda
o app a partir de outra pasta precisa iniciá-lo de lá uma vez. No Linux o `reconcile` apenas mantém o
arquivo XDG coerente — a validação do `Exec=` do `.desktop` continua fora do escopo.

---

### D32 — Atalhos Wayland pelo portal `GlobalShortcuts` `[x]` (08/11/2026)

Terceira fase (P3) do plano `LINUX.md`. No Wayland o JNativeHook não recebe teclas de outras janelas,
então os atalhos globais passam pelo `org.freedesktop.portal.GlobalShortcuts`, com a combinação
proposta pelo app e confirmada (ou trocada) pelo usuário no diálogo do sistema.

- `XdgTrigger.kt`: `toXdgTrigger` converte `Ctrl + Shift + O` em `CTRL+SHIFT+O`, `Super` em `LOGO` e
  nomes como `Page Up`/`Enter` nos keysyms XDG. Recusa combinação sem modificador, com mais de uma
  tecla, com tecla fora da tabela ou string malformada — nesse caso nada é enviado ao portal.
- `HotkeyBindings.kt`: ids estáveis (`capture-fullscreen`, `capture-region`, `quick-dict`,
  `hover-toggle`, `dict-cycle`), descrições pt-BR mostradas no diálogo do sistema, `HotkeyCallbacks`
  para despachar a ação e `portalRequests` para descartar `preferred_trigger` inválido.
- `HotkeyBackend.kt`: interface `HotkeyBackend`, seleção `hotkeyBackendKindFor` (Windows/X11 →
  JNativeHook; Linux Wayland → portal), `JNativeHookHotkeys` preservando o `GlobalHotkeyManager`
  existente e `HotkeyService` com status na UI. Falha de start não derruba o app: vira
  `UNAVAILABLE` com a mensagem na tela de Ajustes.
- `PortalGlobalShortcuts.kt`: `PortalGlobalShortcuts` (contrato testável) e
  `DbusPortalShortcutsTransport` (`dbus-java` 5.1.1): `CreateSession`, `BindShortcuts`, escuta de
  `Activated` e das respostas de request; timeout de 30 s para a sessão e 120 s para o bind (o
  diálogo do sistema pode esperar o usuário). A sessão é criada uma vez e reaproveitada na edição.
- `Main.kt` + `SettingsWindow.kt`: os atalhos passam pelo `HotkeyService` e a tela de Ajustes mostra
  qual backend está ativo (ou o motivo da indisponibilidade).
- `desktop/build.gradle.kts`: dependências `dbus-java-core` e um transporte UNIX 5.1.1 (mesma
  biblioteca que a P4 usa para o serviço de hover; a D44 trocou o transporte para junixsocket).

**Causa raiz.** Não havia seleção de backend: `Main` instanciava o JNativeHook direto em qualquer SO.
No Wayland ele não falha de forma visível — apenas não recebe tecla nenhuma —, então o usuário ficava
sem atalhos e sem explicação. Também não existia conversão da preferência gravada para o formato XDG.

**Testes.** `:desktop:test` passou de 144 para **169** (25 novos): `XdgTriggerTest` (7),
`HotkeyBindingsTest` (5), `HotkeyBackendSelectionTest` (7), `PortalGlobalShortcutsTest` (6).

**Mutações provadas** (script `mutation-proof.ps1`, restauração com hash conferido):

| mutação | teste que acusou |
| --- | --- |
| atalho sem modificador é aceito | `a shortcut without a modifier is refused instead of bound globally` |
| `Super` vira `CTRL` no XDG | `super becomes logo and space keeps its keysym name` |
| pedido vazio abre sessão no portal | `shortcuts the portal cannot represent never open a session` |
| Wayland volta para JNativeHook | `linux wayland uses the portal instead of jnativehook` |
| falha de start não degrada o status | `a backend that cannot start degrades to unavailable instead of crashing` |
| falha de update não aparece na UI | `editing a shortcut rebinds without restarting and exposes a failure` |
| portal ausente é ignorado | `a missing or refused portal fails instead of pretending to bind` |

**Limitações desta fase.** O diálogo do portal e o `Activated` real só podem ser validados no KDE
Wayland (Steam Deck). O fallback KWin planejado no §5.2 do `LINUX.md` foi adiado para a P4, porque ele
depende do serviço D-Bus `dev.yabai.Ocr` e do script KWin que a P4 instala; sem portal, a P3 mostra
“indisponíveis” e mantém as ações no menu da bandeja. O Windows foi verificado por `:desktop:test`
(169) e `:desktop:createDistributable`; `settings.properties` não foi tocado e nenhum processo ficou
aberto.

---

### D33 — Hover Wayland pelo KWin e fallback de atalhos `[x]` (08/11/2026)

Quarta fase (P4) do plano `LINUX.md`. No Wayland o app não pode ler o ponteiro global com `MouseInfo`,
e o JNativeHook também não recebe teclas de outras janelas. A P4 liga o hover a um script KWin e
fecha o fallback de atalhos que a P3 havia deixado em aberto.

- `HoverCapability.kt`: `HoverBackendKind` (`AWT`/`KWIN`/`UNSUPPORTED`), escolha por SO/sessão/desktop
  (KDE Wayland → KWin; Wayland sem KWin → indisponível, sem poll inútil) e
  `hoverCaptureProvider` (screencast só no KWin; Windows/X11 continuam no `Robot` por dispositivo).
- `PointerSource.kt`: `AwtPointerSource` (`MouseInfo`) e `KwinPointerSource`, que guarda a última
  posição do script e descarta repetição para não rodar OCR com o mouse parado.
- `YabaiDbusService.kt`: publica `dev.yabai.Ocr` na sessão D-Bus e exporta
  `dev.yabai.Ocr.Hover.CursorMoved(x, y)` e `dev.yabai.Ocr.Shortcuts.ShortcutPressed(id)`; falha de
  start fecha o transporte em vez de deixar objeto meio exportado.
- `KWinHoverBridge.kt`: `KWinScript.install` copia `metadata.json`/`code/main.js` para
  `~/.local/share/kwin/scripts/yabai-ocr/` (ou `XDG_DATA_HOME`) só quando o conteúdo muda e usa
  `org.kde.KWin` `/Scripting` (`loadScript`, `start`, `isScriptLoaded`, `unloadScript`). Sem root,
  sem escrever em `/usr`, sem reiniciar o KWin.
- `KWinIntegration.kt`: ordem D-Bus → script, rollback e status visível; `Main.kt` só habilita o
  hover quando o bridge está ativo e informa o motivo quando não está.
- `KWinScript.kt` + recursos `kwin/yabai-ocr/`: script de ponteiro (`cursorPosChanged`, `callDBus`,
  100 ms) e bloco opcional de `registerShortcut`/`notifyShortcut` gerado só quando o portal terminou
  em `UNAVAILABLE`.
- `HoverOcrManager.kt`: recebe `backendKind`, `PointerSource` e `captureProvider`; no KWin não inicia
  o poll de `MouseInfo`, reage a `onPointerMoved` e captura pelo backend screencast. O caminho
  Windows/X11 permanece idêntico.

**Causa raiz.** O hover dependia exclusivamente de `MouseInfo` + `Robot`, ambos globais só no X11.
No Wayland o ponteiro devolvia uma posição que nunca mudava e a captura vinha da janela errada — sem
erro, apenas um hover que não reagia. Faltava também o serviço D-Bus que o fallback de atalhos da P3
precisava para receber `ShortcutPressed`.

**Testes.** `:desktop:test` passou de 169 para **198** (29 novos): `HoverCapabilityTest` (5),
`KwinPointerSourceTest` (3), `KWinScriptTest` (8), `YabaiDbusServiceTest` (4),
`KWinHoverBridgeTest` (6) e `KWinIntegrationTest` (3).

**Mutações provadas** (script `mutation-proof.ps1`, restauração com hash conferido):

| mutação | teste que acusou |
| --- | --- |
| Wayland sem KWin vira KWin | `wayland without kwin is unsupported instead of polling a dead pointer` |
| screencast usado em todos os backends | `only kwin replaces the robot with the screencast capture` |
| ponteiro repetido não é deduplicado | `the latest position is kept and repeated positions are dropped` |
| atalho sem modificador é registrado no KWin | `fallback block is empty when no shortcut can be represented` |
| script KWin não é reescrito quando muda | `install writes both files and does not rewrite unchanged content` |
| falha do D-Bus não fecha o transporte | `a transport that cannot start is closed and the service stays down` |
| script é dado como carregado sem checar | `a script that does not start or stay loaded is unloaded` |
| KWin sobe sem o serviço D-Bus | `a dbus failure stops before any kwin script is touched` |

**Limitações desta fase.** D-Bus real, `org.kde.KWin` e o script só podem ser validados numa sessão
KDE Wayland (Steam Deck); nada disso roda no host Windows usado aqui. O `keepAbove` do tooltip pelo
script ficou para o polimento (P5): por ora o tooltip continua `alwaysOnTop` como melhor esforço.
GNOME/Sway e o Game Mode do SteamOS não têm ponteiro global — o app mostra “indisponível” e mantém a
captura por atalho/bandeja. Windows foi verificado por `:desktop:test` (198) e
`:desktop:createDistributable`; nenhum processo ficou aberto.

**Correção de higiene da suíte.** `DesktopDictionaryUpdateCheckerTest` usava `DesktopSettings()` com o
arquivo real e gravava em `~/.yabai_ocr/settings.properties` a cada execução. Ele agora usa um arquivo
temporário e a suíte deixa o hash do arquivo do usuário intacto (verificado antes/depois). O arquivo
deste host já tinha sido gravado por execuções anteriores desta sessão (`dictionary_update_interval`
terminou em `WEEKLY` e `last_dict_update_check` com o valor de teste); não havia backup do carimbo
anterior para restaurar. Nenhuma outra preferência foi alterada pelo código da P4.

### D34 — Polimento Linux: bandeja nativa, `keepAbove`, integração e update por SO `[x]` (08/11/2026)

Quinta fase (P5) do plano `LINUX.md`. Fecha o que a P4 deixou em aberto no Wayland e no primeiro uso
do AppImage, sem mexer em `:core`/`:app`.

- `TrayMenu.kt`: `TrayMenuKind` (`NATIVE`/`CUSTOM`), `trayMenuKindFor(family, session)` e
  `shouldShowMainWindowOnStart(startMinimized, trayReady)`. Linux usa `java.awt.PopupMenu` em qualquer
  sessão; Windows mantém o `JPopupMenu` customizado.
- `SystemTrayManager.kt`: `setup(): Boolean` (só `true` quando o ícone entrou de fato), menu nativo
  no Linux, menu customizado com `MouseInfo` no Windows, `updateHover(active)` para o rótulo
  `Modo Hover [Ativo/Inativo]` e limpeza do `MenuItem` no `remove()`.
- `Main.kt`: `trayReady`; `--minimized` abre a janela quando a bandeja falhou; `closeToTray` só
  esconde com bandeja ativa; `LaunchedEffect(hoverState.active)` sincroniza o menu; cartão de
  integração Linux com estado e ação.
- `LinuxSystemIntegration.kt` + `defaultDataHome`: “Integrar ao sistema” grava
  `$XDG_DATA_HOME/applications/yabai-ocr.desktop` e
  `$XDG_DATA_HOME/icons/hicolor/256x256/apps/yabai-ocr.png`. Executável ausente/vazio ou ícone
  ausente cancelam tudo — nunca deixa atalho quebrado. Nada é escrito em `/usr`.
- `SettingsWindow.kt`: cartão “Integração com o Linux” (`linuxIntegrationStatus`,
  `onInstallLinuxIntegration`) só quando a ação existe.
- `DesktopAppUpdateChecker.kt`: `ReleaseAsset`, `preferredReleaseAsset(family, assets)` e
  `AppUpdateInfo.downloadUrl`. Linux → `.AppImage`; Windows → `.exe` e depois `.msi`; macOS →
  `.dmg`/`.pkg`. Sem asset do SO, a UI volta para a página do release.
- `kwin/yabai-ocr/code/main.js`: `keepAbove = true` e `skipTaskbar = true` na janela
  `Yabai OCR - Hover`, aplicados em `windowAdded` e nas janelas já abertas (`windowList`/
  `clientList`).

**Causa raiz.** No Wayland não existe ponteiro global para o `JPopupMenu` do Windows, então o menu
abria no lugar errado; `setup()` não informava falha e `--minimized` deixava o app invisível; o
tooltip dependia de `alwaysOnTop`, que o compositor ignora; e o primeiro uso do AppImage não criava
atalho no menu de aplicativos. O update checker abria sempre a página do release, mesmo quando o
release publicava um asset específico de SO.

**Testes.** `:desktop:test` passou de 198 para **214** (16 novos): `TrayMenuTest` (4),
`LinuxSystemIntegrationTest` (5), `UpdateAssetTest` (6) e um caso novo em `KWinScriptTest` (9 no
total).

**Mutações provadas** (script `mutation-proof.ps1`, restauração com hash conferido):

| mutação | teste que acusou |
| --- | --- |
| Linux volta a usar o menu customizado | `linux always uses the native menu on any session` |
| `--minimized` ignora a falha da bandeja | `a minimized start opens the window when the tray failed` |
| Windows prefere `.msi` antes de `.exe` | `windows prefers the exe over the msi` |
| integração grava atalho sem executável válido | `blank executable is refused` / `no executable means no broken entry` |
| script KWin não aplica `keepAbove` | `the hover window is kept above and out of the taskbar` |

**Limitações desta fase.** Bandeja StatusNotifier, posição real do `PopupMenu`, `keepAbove` e a
instalação do `.desktop` só podem ser vistos numa sessão KDE Wayland/Steam Deck; nada disso roda no
host Windows usado aqui. `:desktop:createDistributable` foi refeito (verde). Windows foi verificado
pela suíte (214) e nenhum processo ficou aberto. O hash de `~/.yabai_ocr/settings.properties` foi
conferido antes/depois da suíte e não mudou.

### D35 — Primeiro E2E no Steam Deck: D-Bus, atalhos do portal, bandeja e AppImage `[x]` (09/11/2026)

Primeira execução do pacote Linux no **Desktop Mode** (KDE Plasma + Wayland) do Steam Deck. Cinco
sintomas: ícone da bandeja quase invisível, clique direito sem menu, `packageAppImage` sem gerar
`.AppImage`, hover com “Não foi possível publicar o serviço D-Bus dev.yabai.Ocr” e atalhos gravados
que não disparavam nada.

**Causa raiz.**

1. **Runtime sem `jdk.net`.** O `jlink` do Compose montava o runtime só com `java.base`,
   `java.desktop`, `java.logging` e `jdk.crypto.ec`. O transporte unix-socket do `dbus-java` lê
   `ExtendedSocketOptions.SO_PEERCRED`, que vive em `jdk.net`; sem o módulo, `YabaiDbusService`,
   `KWinHoverBridge` e `DbusPortalShortcutsTransport` falhavam com `NoClassDefFoundError` antes de
   publicar `dev.yabai.Ocr` ou falar com o portal. O hover e os atalhos ficavam mortos pelo mesmo
   motivo, mas só o hover mostrava o erro.
2. **`session_handle` lido como String.** O `CreateSession` do `GlobalShortcuts` devolve
   `session_handle` como object path (`o`); o `dbus-java` materializa isso como `DBusPath`, e o cast
   `as? String` devolvia `null`. A sessão do portal nunca era criada.
3. **`Activated` tipado como String.** O sinal `Activated` também entrega `session_handle` como `o`.
   Com o construtor declarado `String`, o `dbus-java` não encontrava um construtor compatível e
   descartava o sinal: o atalho era registrado no portal, mas o app nunca recebia a ativação.
4. **Menu nativo do AWT no Wayland.** O KDE Wayland não entrega o `PopupMenu` nativo anexado ao
   `TrayIcon` quando o clique direito chega ao ícone. No X11 o caminho nativo continua correto.
5. **`TargetFormat.AppImage` não é AppImage.** O Compose 1.7 mapeia esse alvo para
   `jpackage --type app-image`, que produz a mesma pasta do `createDistributable`. O usuário precisava
   rodar `desktop/build/distributions/main/app/yabai-ocr/bin/yabai-ocr`.
6. **Ícone branco sobre fundo claro.** O PNG anterior era um glifo branco com transparência; no painel
   claro do Plasma ele sumia.

**O que mudou.**

- `desktop/build.gradle.kts`: `modules("jdk.net")` no runtime empacotado; tarefa
  `packageLinuxAppImage`, que chama `desktop/package/linux/build-appimage.sh`; `.tar.gz` portátil
  criado por `packagePortableTarGz`.
- `desktop/package/linux/build-appimage.sh`: monta `YabaiOCR.AppDir` (`opt/`, `AppRun`, `.desktop` e
  ícone) e chama `appimagetool` com `--no-appstream`. Sem `appimagetool`, o `.tar.gz` é a alternativa.
- `PortalGlobalShortcuts.kt`: `Activated.sessionHandle` é `DBusPath`; `portalSessionHandle` aceita
  `DBusPath` (e String por compatibilidade) e devolve o caminho; erros de `CreateSession` e
  `BindShortcuts` vão para `lastError` e para a linha de status.
- `TrayMenu.kt` + `SystemTrayManager.kt`: Linux Wayland usa `CUSTOM`; X11 e Windows mantêm o caminho
  anterior. O menu aceita `anchor: Point?`, cai para `trayMenuFallbackAnchor` e abre no canto inferior
  direito da área útil quando não há ponteiro global. No Linux só o botão direito abre o menu.
- `ic_scan_linux.png`: ícone azul opaco com glifo branco, legível em painel claro e escuro.
- `gradle.properties`: removido o `org.gradle.java.home` absoluto do Windows; o build usa `JAVA_HOME`.
- `gradlew` e `build-appimage.sh` voltam a ter bit de execução no Git.

**Testes.** `:desktop:test` passou de **222 para 224** (2 novos em `PortalGlobalShortcutsTest`, além
dos 8 casos de Linux/D-Bus/bandeja/ícone que já estavam no commit anterior). Suíte verde no Deck:
224 testes, 0 falhas, 0 erros.

**Mutações provadas** (restauração com hash conferido; no Linux, o mesmo procedimento do
`mutation-proof.ps1`):

| mutação | teste que acusou |
| --- | --- |
| remover `modules("jdk.net")` do runtime | `NativeRuntimeModulesTest > the packaged runtime declares jdk net for the dbus unix socket` |
| `YabaiDbusService` engole o motivo da falha | `YabaiDbusServiceTest > a transport failure keeps the reason for the status line` |
| Wayland volta a usar o menu nativo | `TrayMenuTest > linux wayland uses the custom menu because the native popup is not delivered` |
| remover o botão direito do menu customizado | `TrayMenuTest > linux opens the custom menu on the right button only` |
| âncora de fallback vai para a esquerda | `TrayMenuPlacementTest > without a global pointer the menu falls back to the bottom-right corner` |
| ícone volta a ser branco/translúcido | `IconResourceTest > the linux icon stays visible on light and dark trays` |
| `session_handle` só aceita String | `PortalGlobalShortcutsTest > create session reads the object path the portal returns` |
| `Activated.sessionHandle` volta a String | `PortalGlobalShortcutsTest > the activated signal declares the session handle as an object path` |

**Evidência no Deck.** O build foi refeito no SteamOS com JDK 21:
`lib/runtime/release` lista
`java.base java.datatransfer java.xml java.prefs java.desktop java.logging jdk.crypto.ec jdk.net`;
`packagePortableTarGz` gerou 66 MB e `packageLinuxAppImage` gerou 63 MB
(`Yabai_OCR-1.0.0-x86_64.AppImage`), ambos com o runtime acima. O hash de
`~/.yabai_ocr/settings.properties` foi conferido antes/depois da suíte e não mudou. A validação
visual (bandeja, menu, captura, hover e atalhos) é feita no Desktop Mode com o roteiro
`~/Downloads/yabai-e2e.sh`, que tira screenshots e coleta `busctl`/KWin/portal.

**Limitações.** O agente roda dentro do Flatpak do OpenCode, sem o XWayland nem o barramento da
sessão do host; por isso ele valida build, runtime, suíte e D-Bus de unidade, mas a confirmação
visual e o `GlobalShortcuts` real dependem do roteiro acima no Konsole. A captura pelo portal continua
sujeita ao token/restore do JDK e ao diálogo do KDE; no KDE Wayland esse caminho foi substituído pelo
portal `Screenshot` na D37.

### D36 — Bandeja nativa `StatusNotifierItem` no KDE Wayland `[x]` (09/11/2026)

O ícone aparecia no painel do Plasma, mas o clique direito não abria menu e o esquerdo não iniciava a
captura no Desktop Mode.

**Causa raiz.** `java.awt.TrayIcon` ainda publica um ícone XEmbed. O KDE Wayland não entrega os
eventos de clique do `xembedsniproxy` ao processo Java (KDE bug 498824), então o `TrayIcon` ficava
visível e inerte. O caminho AWT continua correto em X11 e Windows.

**O que mudou.**

- `StatusNotifierTray.kt`: publica `org.kde.StatusNotifierItem` em `/StatusNotifierItem`, pede o nome
  `org.kde.StatusNotifierItem-<pid>-1` e registra no `org.kde.StatusNotifierWatcher`.
- `Activate(x,y)` dispara a captura de tela inteira, `SecondaryActivate(x,y)` a captura de área e
  `ContextMenu(x,y)` abre o mesmo menu Swing na posição informada pelo Plasma.
- `IconPixmap` é ARGB32 em ordem de rede, gerado a partir do ícone do app; `ToolTip` expõe o status
  atual; `ItemIsMenu=false` deixa o host apenas encaminhar o clique.
- `SystemTrayManager.kt`: em Linux Wayland usa SNI; em X11/Windows mantém `SystemTray`. Se o SNI
  falhar, o AWT continua como fallback e o motivo vai para `lastError`.
- `stop()` libera o nome e fecha a conexão; uma falha no `RegisterStatusNotifierItem` também fecha a
  conexão para não deixar um nome invisível preso até o processo morrer.

**Testes.** `StatusNotifierTrayTest` cobre seleção SNI × AWT, nome do barramento, callbacks, pixels
ARGB32, tooltip, identidade e o tipo concreto `a(iiay)` do `IconPixmap`; `StatusNotifierDbusSmokeTest`
registra o objeto num watcher real e confere as propriedades. A suíte desktop ficou verde.

**Mutações provadas.**

| mutação | teste que acusou |
| --- | --- |
| Linux Wayland volta ao backend AWT | `StatusNotifierTrayTest > linux wayland publishes a status notifier item` |
| `IconPixmap` volta a `List` sem tipo explícito | `StatusNotifierTrayTest > the icon pixmap property declares the concrete struct array type` |
| pixels saem em RGBA em vez de ARGB32 | `StatusNotifierTrayTest > the icon is argb32 in network byte order` |
| `ContextMenu` não encaminha as coordenadas | `StatusNotifierTrayTest > activate and context menu reach the app callbacks` |

**Limitação.** O teste D-Bus é pulado quando o barramento da sessão recusa nomes privados (sandbox do
agente); a confirmação visual no painel do Plasma depende do roteiro no Deck.

### D37 — Menu fecha ao clicar fora e captura KDE sem repetir permissão `[x]` (10/11/2026)

Depois da D36, o menu abria no clique direito, mas continuava na tela até o usuário escolher uma
opção. A captura no KDE Wayland também voltava a pedir a tela a cada uso.

**Causa raiz.**

1. **Invólucro do menu sem foco.** O menu Swing era anexado a um `JDialog` 1×1 invisível e **não
   focável**. No Wayland o JDK só fecha o popup quando o invólucro perde foco (JDK-8307529); como ele
   nunca recebia foco, clicar fora não gerava evento e o menu ficava órfão.
2. **ScreenCast com token inválido no KDE.** O JDK 21.0.13 valida o *restore token* do portal como
   UUID (`g_uuid_string_is_valid` em `screencast_portal.c`), mas o KDE devolve um token base64
   (`O0ci...=`). O token nunca é aceito, então cada captura abria uma sessão nova e o KDE voltava a
   pedir a escolha da tela (JDK-8396405).

**O que mudou.**

- `TrayMenuInvoker.kt`: o invólucro do menu passa a ser focável, com `autoRequestFocus`, recebe foco
  depois de o popup aparecer e fecha o menu no próprio evento de perda de foco. O handler só fecha se
  o popup ainda estiver visível, para não fechar duas vezes.
- `SystemTrayManager.kt`: um segundo clique substitui o menu anterior; o menu ativo é limpo quando o
  popup fica invisível ou é cancelado.
- `PortalScreenshotCapture.kt`: no KDE Wayland a captura usa
  `org.freedesktop.portal.Screenshot` com `interactive=false`. O portal devolve um PNG do workspace;
  o app recorta o monitor principal ou a região pedida, respeitando offset e escala nativa, e apaga
  o arquivo depois de ler.
- `PlatformServices.kt`: KDE Wayland usa o portal `Screenshot`; GNOME/outros Wayland mantêm
  `ScreencastScreenCapture`; Windows/X11 mantêm `RobotScreenCapture`.
- A permissão do `Screenshot` é separada do token do JDK: o KDE pergunta uma vez
  **«Permitir que aplicativos tirem capturas de tela?»**, grava a resposta no portal e as capturas
  seguintes, inclusive hover, são silenciosas. Rejeitar vira estado `DENIED`, não crash.

**Por que não usar outras rotas.**

- `ScreenCast` do JDK no KDE: token inválido (JDK-8396405) e diálogo de tela repetido.
- `org.kde.KWin.ScreenShot2` direto: exige `X-KDE-DBUS-Restricted-Interfaces` no `.desktop` e pode ser
  negado a um AppImage.
- `RemoteDesktop` pré-autorizado: pede permissões de controle remoto e notificações que o app não usa.
- Patch no JDK/nativo ou reescrita do token: frágil e sem teste sustentável.

**Testes.** `PortalScreenshotCaptureTest` cobre as opções não interativas, recorte do monitor, escala
nativa, offset de monitor negativo, negação, portal ausente e remoção do PNG. `TrayMenuInvokerTest` cobre foco, fechamento uma
única vez e evento atrasado. `ScreenCaptureBackendTest` cobre a escolha KDE × outros Wayland ×
Windows/X11. Suíte desktop: **249 testes, 0 falhas, 2 pulados** no sandbox (teste GUI sem display e
smoke D-Bus com barramento filtrado).

**Mutações provadas.**

| mutação | teste que acusou |
| --- | --- |
| invólucro do menu deixa de ser focável | `TrayMenuInvokerTest > the invoker becomes focusable and focused before the menu can dismiss` |
| KDE Wayland volta ao ScreenCast | `ScreenCaptureBackendTest > kde wayland uses the screenshot portal instead of the JDK screencast` |
| recorte do portal ignora o offset do monitor | `PortalScreenshotCaptureTest > virtual screen offset is removed before cropping` |
| captura do portal volta a ser interativa | `PortalScreenshotCaptureTest > screenshot request is non interactive so KDE stores the permission once` |

**Limitações.** O agente roda dentro do Flatpak do OpenCode, sem `DISPLAY`/XWayland e sem o
barramento do host; a prova visual do menu e do diálogo único de permissão depende de
`~/Downloads/yabai-e2e.sh` no Konsole do Deck. O portal `Screenshot` captura o workspace inteiro; em
layout multi-monitor com escalas diferentes o recorte usa a escala média e ainda precisa de validação
no host. A primeira captura pode bloquear enquanto o diálogo está aberto, porque a chamada ainda roda
na thread da ação.

### D38 — Manga-OCR no desktop: o motor que existia e não era chamado  `[x]` (12/11/2026)

**Relato.** «Testei agora na versão desktop e ele tem a opção do manga-ocr, mas não parece ter sido
implementado.»

**Causa raiz.** Os Ajustes ofereciam `AUTO`, `LENS` e `MANGA_OCR`, guardavam a escolha e a lista de
prioridade — e nenhum dos três pontos de OCR lia a configuração: `ResultWindow`,
`SegmentPickerWindow.scanScreen` e `HoverOcrManager.lookupAt` chamavam `LensClient().recognize(...)`
direto. Também não havia downloader de modelos no desktop, então mesmo que alguém lesse a escolha não
havia com o que rodar o motor.

**Correção.**

- `core/.../manga/MangaOcrModelDownloader.kt` (novo): os três arquivos, em `.tmp` e pulando o que já
  existe. O Android continua com o downloader dele.
- `desktop/.../DesktopMangaOcr.kt` (novo): sessões ONNX em cache (abrir 213 MB não pode ser por
  captura, e o hover dispara duas vezes por segundo) e a tradução `MangaOcrOutcome` → `LensResult`
  — balão vira bloco, coluna vira segmento —, para as janelas não precisarem saber qual motor leu.
- `desktop/.../DesktopOcr.kt` (novo): `engineOrder` (escolha fixa roda sozinha; `AUTO` segue a
  prioridade e para no primeiro motor que devolve texto) e `recognize`, que junta o motivo de cada
  falha na mensagem; um motor sem texto não conta como resposta, então o `AUTO` continua offline
  quando o Lens recusa.
- `DesktopSettings.mangaOcrDir` (`~/.yabai_ocr/manga_ocr`) e os três pontos de OCR passando a usar
  `DesktopOcr`. No hover, o cliente Lens em cache saiu junto.
- Ajustes: o mesmo aviso **vermelho** de experimental do Android e um botão «Baixar modelos do
  Manga-OCR (~213 MB)», com progresso e estado (instalado / não instalado / falha). Um download
  fechado com sucesso derruba as sessões antigas (`DesktopMangaOcr.close()`).
- `DesktopStrings` em pt/en e README com a seção de motores do desktop.

**Testes.** `:desktop` **255** (5 novos no `DesktopOcrOrderTest` + 1 opt-in de ponta a ponta).

| mutação | teste que acusa |
| --- | --- |
| `AUTO` ignora a lista de prioridade e usa o Lens | `automatic mode follows the priority list`, `automatic mode still has an engine when the list is empty or nonsense`, `the same engine is not tried twice` |

**Ponta a ponta.** Com os modelos reais, `-Pmanga=<home>` (arquivos em `home/manga_ocr`):
`DesktopOcr.recognize` com `engine = MANGA_OCR` leu a página desenhada de 900×1400 em **3,3 s** —
`これはテストです` em `(714,108)-(754,559)` e `テストの文章です` em `(112,874)-(426,930)`, dois blocos,
texto exato.

**Limitação.** A janela de Ajustes e o painel de resultado não foram vistos rodando (só compilação,
testes e o motor com os modelos reais); o download em si não foi exercitado contra a rede neste
ambiente — os arquivos foram colocados à mão na pasta de modelos.

### D39 — PP-OCRv6_manga no lugar do Manga-OCR, motor honrado em todo lugar, resultado traduzido  `[x]` (12/11/2026)

**Relato.** «Migre para o PP-OCRv6_manga e remova o manga-OCR»; «quando fiz o recorte, ele informa
que detectou pelo Lens»; «a tela de resultado ainda está com os textos hardcodados».

**Causa raiz.** Três coisas distintas:

1. o motor offline do desktop era o Manga-OCR — 213 MB e um passo do decoder por caractere, ~0,1 s
   por letra — e o próprio modelo era a limitação de precisão;
2. o rótulo «Reconhecido em 5052ms • Google Lens» era uma **string fixa** no `ResultWindow`: mesmo
   depois de a escolha passar a valer, um recorte lido pelo motor offline continuava dizendo Lens;
3. o `ResultWindow` ainda tinha oito textos em português escritos no código, então o app em inglês
   mostrava «Texto Reconhecido», «Copiar», «Recortar» e «Entries & Dictionary» misturados.

**Correção.**

- `core/.../ppocr/` (novo): `PPOcrSpec` (960 de lado maior ou orçamento de página 3:4 para tiras,
  limiar 0,15, caixa 0,25, unclip 1,4, altura 48), `PPOcrPreprocess` (detector com média/desvio
  ImageNet; linha girada à esquerda e normalizada em -1..1), `PPOcrPostprocess` (componentes conexos
  na máscara DB, confiança média, unclip, ordem de leitura direita→esquerda) e `PPOcrEngine`
  (sessões ONNX + CTC guloso). Tudo JVM puro, sem OpenCV — o app Android pode usar o mesmo motor.
- `PPOcrModelStore`/`PPOcrModelDownloader`: det 1,73 MB + rec 21,33 MB (FP32, para não depender de
  kernel FP16 do runtime) + dicionário de 19.053 símbolos, em `~/.yabai_ocr/ppocr_v6`.
- Desktop: `DesktopPPOcr` substitui `DesktopMangaOcr`; `DesktopEngineChoice.MANGA_OCR` virou
  `PP_OCR` e um `settings.properties` antigo cai em `AUTO`; `DesktopOcr.recognize` devolve
  `DesktopOcrAnswer(result, engine)` e os três caminhos (tela inteira, recorte, hover) usam o motor
  escolhido.
- Removidos: `DesktopMangaOcr.kt`, o downloader e a UI/strings do Manga-OCR no desktop, e o
  downloader que só o desktop usava no `:core`. O Manga-OCR do Android não foi tocado.
- `ResultWindow`: título da janela, cabeçalho, botões, rótulo do campo e «Flexão» agora vêm de
  `DesktopStrings` (pt/en), e o motor exibido é o que respondeu de fato.

**Testes.** `:core` **196** (16 novos no `PPOcrTest`), `:desktop` **261** (6 no `DesktopOcrOrderTest`,
4 no `DesktopEngineMigrationTest`, 1 opt-in de ponta a ponta).

| mutação | teste que acusa |
| --- | --- |
| coluna vertical girada no sentido horário (erro original) | `a vertical line is turned a quarter turn counter-clockwise` |
| CTC sem colapso de repetição | `repeated classes collapse and blanks are dropped`, `decoding reads the best class at every step` |
| `AUTO` ignora a lista de prioridade e usa o Lens | `automatic mode follows the priority list`, `automatic mode still has an engine when the list is empty or nonsense`, `the same engine is not tried twice` |

**Ponta a ponta.** Modelos reais, `-Pppocr=<home>` (arquivos em `home/ppocr_v6`):
`DesktopOcr.recognize` com `engine = PP_OCR` leu a página desenhada de 900×1400 em **260 ms**
(contra **3.300 ms** do Manga-OCR na mesma página) — `これはテストです` em `(707,110)-(759,559)` e
`テストの文章です` em `(110,880)-(421,923)`, texto exato. O giro errado foi pego aqui antes de fechar:
saía `F2ーと上判れこ` na coluna vertical.

**Limitação.** A janela de Ajustes e o painel de resultado não foram vistos rodando; a conferência
visual do rótulo do motor depende de abrir o app. O download em si não foi exercitado contra a rede
(arquivos colocados via `curl` na pasta de modelos).

### D40 — PP-OCRv6_manga no Android, e o Manga-OCR sai do app  `[x]` (12/11/2026)

**Relato.** «Agora me ajude a fazer o mesmo na versão mobile».

**Causa raiz.** O app Android ainda oferecia o **Manga-OCR** como motor offline: 213 MB de modelo,
uma passada do decoder por caractere e o aviso vermelho de «experimental» na própria opção. O motor
`PPOcrEngine` que o desktop passou a usar já estava no `:core` (JVM puro), então a migração é de
ligação — não de modelo novo.

**Correção.**

- `EngineChoice.MANGA_OCR` → `PP_OCR`; `AppSettings.ppOcrDir` = `files/ppocr_v6`; a lista de
  prioridade padrão é `LENS,PP_OCR,MLKIT`; um `engine=MANGA_OCR` gravado antes cai em `AUTO`.
- `OcrDispatcher`: o ramo offline passou a usar `PPOcrEngine` (detector + CTC), com a sessão em cache
  e fechada em `close()`; no `AUTO` o motor só entra quando os três arquivos existem. `OcrBlock` e
  `OcrPiece` continuam vindo dos blocos do core, então o seletor de balões e as leituras no overlay
  não mudaram.
- Download: o app passou a usar o **mesmo** `PPOcrModelDownloader` do `:core` (o downloader
  específico do app foi apagado), com a UI em `MainActivity` renomeada para `btnDownloadPpOcr` /
  `txtPpOcrStatus`.
- `activity_main.xml` e `strings.xml` (pt/en): «Somente PP-OCRv6 (offline)», descrição nova, textos
  de download com **~23 MB** (eram «~45 MB»), e o **aviso vermelho de experimental removido**.
- README atualizado: o motor offline do Android agora é o PP-OCRv6.

**Testes.** `:app` verde (Robolectric): `AppSettingsEnginePriorityTest` atualizado — prioridade
padrão, `LENS,MANGA_OCR,MLKIT` legado virando `LENS,MLKIT,PP_OCR`, `engine=MANGA_OCR` → `AUTO`, pasta
`ppocr_v6` — e `PpOcrDispatchTest` novo: com `engine = PP_OCR` e sem modelos, o dispatcher devolve
`PP-OCRv6 (Offline)` com o aviso de download em vez de cair no Lens. `:core` ganhou
`PPOcrModelDownloaderTest` (arquivos instalados não são baixados de novo; pasta incompleta não conta
como disponível).

| mutação | teste que acusa |
| --- | --- |
| `parseEnginePriority` desconhece `PP_OCR` | `default priority list contains LENS, PP_OCR and MLKIT` |
| `engine` não mapeia `PP_OCR` (volta para `AUTO`) | `the engine choice survives a round trip through the preferences` |
| ramo do dispatcher chama o Lens em vez do offline | `pp-ocr chosen without models reports the missing download` |

**Limitação.** O motor em si roda com os modelos reais no JVM (`:desktop:test -Pppocr`, mesmas
classes do `:core`); no Android a verificação foi de ligação e de preferências, não de uma captura
no aparelho. O aviso vermelho saiu do layout — uma instalação antiga que ainda o mostra é só cache de
build.

### D41 — Varredura de código morto, ARCHITECTURE.md e README público  `[x]` (12/11/2026)

**Relato.** «Quero fazer uma varredura em código morto», «criar um documento que mostra toda a
estrutura do projeto» e «alterar o README para conter apenas as informações gerais» (para abrir o
código no GitHub).

**Como a varredura foi feita.** Um script cruzou todas as declarações top-level do `src/main` com as
referências no repositório (produção + testes + XML + docs), separando três casos: (a) sem nenhuma
referência, (b) referenciada só fora do arquivo, (c) referenciada só dentro do próprio arquivo. O caso
(a) é o que foi removido; o (c) costuma ser função privada de Compose, usada no mesmo arquivo.

**Removido.**

- `core/.../manga/` inteiro (6 arquivos: `ComicTextDetector`, `MangaOcrCoreEngine`, `MangaOcrPreprocessor`,
  `MangaOcrModelStore`, `MangaOcrTokenizer`, `MangaOcrDecoding`) e os 5 testes dele. Depois da D38/D40
  nenhuma plataforma chamava o Manga-OCR: o pacote vivia de testes próprios. O `BlockColumns` de
  `core/layout/` continua, usado pelo PP-OCR e pelo ML Kit.
- A opção `-Pmanga` do `:core` (só existia para o teste ponta a ponta removido).
- `desktop/.../TemporaryImageViewerWindow.kt` (51 linhas, nunca aberta) e o wrapper
  `DictionaryManagerWindow(...)` (a janela real usa `DictionaryManagerContent`, chamado do `Main.kt`).
- `Rect.clampTo` em `app/.../util/Dimensions.kt` (o resto dos helpers `dp`/`sp` continua em uso).
- Três strings sem uso em `values/` e `values-en/`: `dictionary_attribution`, `kanji_no_page`,
  `theme_toggle_btn`.

**Mantido de propósito.** `contrastRatio` (usado pelos testes de contraste do tema), interfaces D-Bus
(instanciadas por reflexão/transporte), `YabaiOcrApplication` e `YabaiTileService` (declaradas no
manifest) e os `@Composable` privados usados no próprio arquivo.

**Resultado.** `:core` 177 testes (eram 196 — as 19 do manga saíram com o pacote), `:desktop` 261,
`:app` compila e resolve recursos; nenhuma falha.

**Documentação.** `ARCHITECTURE.md` novo: visão de uma página, comandos e flags de teste, árvore de
diretórios, papel de cada arquivo nos três módulos, fluxos (captura, recorte, hover, picker, Android,
download de modelos), tabela «onde mexer para cada pedido», convenções/armadilhas e índice de
documentos. O `README.md` foi reescrito para ser a vitrine pública: o que o app faz, plataformas,
atalhos, motores de OCR, dicionários, como compilar e links para a documentação técnica — o detalhe
de comportamento (que estava no README) segue no ROADMAP e no ARCHITECTURE. `LANGUAGES.md` trocou as
referências ao Manga-OCR pelas do PP-OCRv6.

**Pendência.** O repositório ainda não tinha `LICENSE`; o README registrava que a licença precisava ser
escolhida antes de publicar. Resolvido na D42.

### D42 — Licença MIT, AGENTS com estrutura e skills, tamanho real dos dicionários  `[x]` (12/11/2026)

**Relato.** «Adicione uma licença para código aberto, quem quiser pode copiar a vontade o código. Se
faltar outra coisa importante para deixar um código aberto, pode adicionar também», «no AGENT precisa
ser atualizado a estrutura sempre que for feito alguma alteração que altere/remove/adicione algo na
estrutura», «no AGENT as skills que tem no código e quando usar cada uma delas; se alguma delas não
fizer sentido ou for redundante, pode remover» e o bug: na aba de dicionários do desktop «o tamanho de
todos eles aparece como "0.0 MB" ao invés do tamanho total dos arquivos na pasta».

**Causa raiz do 0.0 MB.** `DictionaryManagerWindow` mostrava `file.length()` do item instalado, mas um
`CompactDictionary` compilado é uma **pasta** `.yabai` (`DictionaryManagerWindow.kt:997` cria
`File(dictDir, "${entry.id}.yabai")` como diretório). `File.length()` responde `0` para diretório em
qualquer sistema de arquivos — daí `0.0 / 1024 / 1024` em todos os cards, sem exceção.

**Correção.** `DesktopDictLoader.sizeBytes(entry)`: arquivo → `length()`; pasta → soma de todos os
arquivos dentro (`walkTopDown`). O card usa esse total. A mesma expressão arredonda para `0.0` um
dicionário de 40 KB, por isso a regra de formatação (`%.1f MB`) ficou como estava — o alvo era o total
do diretório, não a unidade.

**Teste e mutação.** `DesktopDictLoaderSizeTest` monta uma pasta `.yabai` com dois arquivos e uma
subpasta (4096 + 1024 + 512 bytes) e exige esse total; o segundo teste cobre o arquivo único. Mutação:
voltar o card/chamada a `file.length()` derruba `aCompiledDictionaryFolderReportsTheTotalOfItsParts`
(esperado 5632, obtido 0).

**Licença e repositório aberto.** `LICENSE` com MIT (copyright «Yabai OCR contributors»), seção
«Licença» do README reescrita, e `CONTRIBUTING.md` novo (compilar/testar, regras inegociáveis, como
enviar a mudança, licença das contribuições). `.gitignore` já cobria `local.properties`, `build/`,
`.idea/`, `*.class`, `*.apk` e `.artifacts/` — nada de máquina estava rastreado.

**AGENTS.md.** Duas seções novas: «Estrutura mudou? Atualize o `ARCHITECTURE.md` na mesma tarefa»
(arquivo/pasta/módulo novo, removido, renomeado ou com outro papel, e fluxo novo; aponta as seções
afetadas) e a tabela de skills reescrita com «quando usar» por skill, incluindo a combinação para uma
tarefa típica. Números de suíte atualizados (desktop 261, core 177, app 411).

**Skills removidas.** `ponytail`, `ponytail-audit` e `ponytail-review` (`.opencode/skills/`): a
primeira repetia a regra de menor mudança que o AGENTS já impõe, e as outras duas eram auditoria/revisão
genéricas de repositório, sem nada específico do Yabai. Ficaram as seis que cobrem as regras do projeto
(`yabai-regression`, `yabai-desktop-e2e`, `yabai-ocr-quality`, `yabai-android-app`, `yabai-docs`,
`systematic-debugging`).

**Resultado.** `:desktop` 263 testes, 0 falhas (261 + os 2 novos).

### D43 — Exceção fatal ao trocar de dicionário, docs bilíngues e release automática por tag  `[x]` (12/11/2026)

**Relato.** «Às vezes ao trocar de dicionário selecionando manualmente, aparece alguns avisos de erro
"fatal exception..."»; «se for devido um dicionário não ter significado para aquela palavra, adicione
no popup um aviso que aquele dicionário não tem aquele significado»; UI não deve ser só pt-BR, e sim
«sempre nas línguas disponíveis no aplicativo»; documentos «readme, architecture, etc também em
inglês»; e configurar Actions para gerar versão a cada tag (APK de release, portable, setup, AppImage,
`.tar.gz`).

**Causa raiz do «Fatal exception in coroutines machinery».** No `ResultWindow` o `LaunchedEffect` que
reconsulta o dicionário quando o filtro muda (`selectedFilter`) chamava `engine.lookup(...)` **sem
proteção e no dispatcher da UI**. Trocar de dicionário remapeia aquele `.yabai` (que é uma pasta com
partes mapeadas em memória); se a leitura falha — dicionário em atualização, pasta movida, parte
corrompida — a exceção sobe pelo `LaunchedEffect` e o Compose a reporta como exceção fatal da máquina
de corrotinas, dentro do frame da animação (`Transition.animateTo`), num diálogo do sistema com texto
irrelevante para o usuário. A busca rápida (`QuickDictWindow`) tinha o mesmo problema, e pior:
`engine.suggest(...)` rodava **durante a composição**.

**Correção.** `DictLookup.run(text, only, query)` roda a consulta, captura a falha e devolve
`DictLookupState(matches, error)`. `ResultWindow` e `QuickDictWindow` consultam via `withContext(
Dispatchers.Default) { DictLookup.run(...) }` e mostram o erro no lugar dos verbetes
(`lookupFailed(detail)`), em vez de derrubar a janela; as sugestões passaram a ser `runCatching`.
Quando o filtro escolhido simplesmente não tem verbete para aquela palavra, o popup mostra
`selectedDictHasNoEntry(title)` — «O dicionário selecionado (X) não tem verbete para esta palavra.
Escolha «Todos os dicionários» para buscar nos outros.» — que é o aviso pedido, sem drama de erro.

**Testes e mutação.** `DictLookupTest` (2 testes): consulta que lança vira `error` preenchido e lista
vazia; consulta boa devolve os matches e `error == null`. Mutação provada: removido o `try/catch` de
`DictLookup.run`, `aBrokenDictionaryIsReportedInsteadOfThrown` falha; restaurado, passa.

**Regras e documentos.** AGENTS: a UI passa a existir em **todas as línguas disponíveis** (pt-BR e en),
com string nova nos dois idiomas na mesma tarefa; documentos públicos são bilíngues e o par
(`*.en.md`) é atualizado junto (novo item na definição de pronto). Criados `README.en.md`,
`ARCHITECTURE.en.md` e `CONTRIBUTING.en.md` com seletor de idioma no topo; `README.md`, `ARCHITECTURE.md`
e `CONTRIBUTING.md` ganharam o seletor e a regra. `LANGUAGES.md` registra a regra atual.

**Release por tag.** `.github/workflows/release.yml`: `push` de tag `v*` roda três jobs — Android
(`:app:testDebugUnitTest` + `:app:assembleRelease` → `Yabai_OCR_<versão>.apk`), Windows
(`createDistributable` + Inno Setup + zip portátil) e Linux (`createDistributable` + `.tar.gz` +
AppImage via `appimagetool` extraído) — e um quarto job publica tudo numa release do GitHub. A versão
do build vem de `-PyabaiVersion=<tag>`: `packageVersion`, nome do zip/tar e `YABAI_VERSION` do script do
AppImage, além de `iscc /DMyAppVersion`. Sem a flag, continua `1.0.0`.

**Resultado.** `:desktop` 265 testes, 0 falhas, 3 pulados (GUI e D-Bus sem sessão).

### D44 — Menu da bandeja no KDE, atalhos aplicados na hora e hover sem diálogo  `[x]` (12/11/2026)

**Relato.** No KDE Wayland/Steam Deck o menu da bandeja só fechava às vezes ao clicar fora; um atalho
editado continuava disparando a combinação antiga; e o hover demorava e voltava a pedir permissão de
tela. Windows, X11, GNOME e Android não podiam mudar.

**Causa raiz do menu.** No Wayland o app publicava o `StatusNotifierItem`, mas o único menu era o
`ContextMenu(x,y)` que abria um `JPopupMenu` Swing dentro de um invólucro cuja janela não recebe foco
de forma confiável no Plasma. O clique fora chegava ao compositor, não ao popup Java; quando o foco
era perdido, o menu fechava, e quando não era, ficava aberto. Não era um problema de posição: era o
host errado desenhando/fechando o menu.

**Causa raiz dos atalhos.** `PortalGlobalShortcuts.update` reutilizava a sessão/transporte já criado.
Depois de uma falha de `start` (`UNAVAILABLE`), `HotkeyService.updateHotkeys` chamava `backend.update`
num backend sem sessão viva; a edição era aceita na UI, mas o portal continuava com o bind antigo. O
fallback KWin também só era escrito no início, então o script mantinha as combinações anteriores.

**Causa raiz do hover.** O caminho KDE era o portal `org.freedesktop.portal.Screenshot` com
`interactive=false`. No KDE isso **não** é silencioso: cada chamada pode criar/apresentar um
`QuickDialog`, e o hover paga esse diálogo e o PNG do workspace inteiro a cada captura.

**Correção.**
- `DbusMenu.kt` publica `com.canonical.dbusmenu` no `SNI_MENU_PATH`: `GetLayout`,
  `GetGroupProperties`, `GetProperty`, `Event`, `EventGroup`, `AboutToShow` e `AboutToShowGroup`.
  O Plasma pede o layout, desenha o menu e o fecha nativamente. `ContextMenu()`/Swing continuam como
  fallback para hosts sem DBusMenu. O layout usa wrappers `DBusSerializable` (`DbusMenuLayout`,
  `DbusMenuAboutToShow`) em volta de payloads `Struct`, porque o dbus-java 5.1.1 não exporta campos
  `Map`/`List` crus de struct retornado.
- `PortalGlobalShortcuts.update` cria uma **sessão nova** e só fecha a antiga depois do bind novo.
  Se o bind falhar, fecha a candidata e também a antiga, para não deixar atalho fantasma; se não houver
  atalhos representáveis, fecha a sessão e devolve indisponível.
- `HotkeyService.updateHotkeys` volta a chamar `start` quando `activeKind == UNAVAILABLE`, expõe
  `lastError` e marca `UNAVAILABLE` em falha.
- `KWinHoverBridge.updateShortcuts` regrava e recarrega o script quando a combinação muda; se o KWin
  recusar, restaura o script anterior. `KWinIntegration.updateShortcuts`/`isRunning` e `Main.kt`
  reaplicam ou limpam o fallback quando os atalhos são editados.
- `KWinScreenshotCapture.kt` usa `org.kde.KWin.ScreenShot2` (`CaptureWorkspace` e `CaptureArea`) por
  FD, converte o QImage cru `RGBX8888`/`RGBA8888` com `stride` e mantém o portal `Screenshot` como
  fallback. `KdeWaylandCapture` desativa o caminho direto depois da primeira falha e passa ao portal.
- O transporte D-Bus é `dbus-java-transport-junixsocket:5.1.1`, não o
  `dbus-java-transport-native-unixsocket`: o transporte nativo do JDK (JEP 380) não envia
  `SCM_RIGHTS`, então o FD do `ScreenShot2` nunca chegaria ao KWin e a captura cairia no portal com
  diálogo. O junixsocket implementa o `ISocketProvider` com `FileDescriptorCast` nativo;
  `KWinScreenshotCapture` usa esse caminho, sem refletir em `java.io.FileDescriptor.fd` e sem
  `--add-opens`.
- Mensagens novas do KWin entraram em `DesktopStrings` nos dois idiomas. README, ARCHITECTURE,
  LINUX e STEAMOS foram reconciliados com o novo caminho.

**Testes e mutações.** A suíte `:desktop:test` fechou em **287 testes, 3 pulados, 0 falhas** (JDK 21).
As provas de mutação usaram `/tmp/opencode/mutation-proof.py`; 10 foram capturadas no script. As de
`serialize()` vazio, do `GetLayout` sem `u revision` e a do transporte UNIX sem suporte a FD
exigiram `--rerun-tasks` por cache incremental do Gradle, falhando então no teste/smoke indicado.
Todas as restaurações tiveram SHA-256 igual e o teste focado voltou verde.

| mutação | teste que acusou |
| --- | --- |
| rebind reutiliza a sessão antiga | `PortalGlobalShortcutsTest` |
| rebind recusado deixa a sessão antiga viva | `PortalGlobalShortcutsTest` |
| edição depois de `UNAVAILABLE` não tenta `start` | `HotkeyBackendSelectionTest` |
| script KWin não é reescrito quando o atalho muda | `KWinHoverBridgeTest` |
| KDE Wayland volta ao screencast | `ScreenCaptureBackendTest` |
| conversão raw troca R/B | `KWinScreenshotCaptureTest` |
| falha do `ScreenShot2` não cai no portal | `KWinScreenshotCaptureTest` |
| SNI anuncia caminho de menu errado | `StatusNotifierTrayTest` |
| DBusMenu serializa layout vazio | `StatusNotifierDbusSmokeTest` |
| `GetLayout` devolve só o struct, sem `u revision` | `DbusMenuTest` / `StatusNotifierDbusSmokeTest` |
| DBusMenu perde os filhos do nó raiz | `DbusMenuTest` |
| `KWinIntegration` ignora o resultado do bridge | `KWinIntegrationTest` |
| transporte UNIX sem suporte a FD no classpath | `KWinScreenshotCaptureTest` |

**Evidência.** `:desktop:test` verde em 12/11/2026 com 287 testes; o smoke
`StatusNotifierDbusSmokeTest` conversa com um barramento D-Bus real usando o transporte junixsocket e
valida `layout.id`, `revision`, quantidade de filhos, a assinatura `(ia{sv}av)` e `AboutToShowGroup`
devolvendo os dois `ai`. O teste do watcher continua pulado no sandbox porque o barramento filtrado
recusa nomes privados de watcher; o smoke de DBusMenu usa o nome único do processo. A conversão real
de FD foi conferida com `FileDescriptorCast.using(raf.fd).as(Integer.class)` no JDK 21 sem
`--add-opens`: devolve `fd=6`. O provider junixsocket reporta
`CAPABILITY_FILE_DESCRIPTORS=true`, `CAPABILITY_UNSAFE=true` e
`isFileDescriptorPassingSupported()=true`; com o transporte nativo antigo o mesmo caminho não teria
`SCM_RIGHTS`.

**Limitações.** O sandbox Flatpak do OpenCode não vê a sessão gráfica do host nem o
`org.kde.KWin.ScreenShot2`/portal `GlobalShortcuts` reais. A validação visual (menu fechando sempre,
atalho novo sem reiniciar, hover sem diálogo e captura HiDPI) depende de rodar o AppImage no Desktop
Mode e segue o roteiro de `LINUX.md` §13.4. Nada foi commitado.

### D45 — Hover silencioso no KDE, atalho novo valendo na hora e script KWin obsoleto  `[x]` (13/11/2026)

**Relato.** Depois de gerar um AppImage novo: o hover demorava e abria uma janela «Portal» na barra de
tarefas a cada leitura; um atalho editado continuava sem funcionar; e, ao fechar e abrir o app, o hover
mostrava «Could not activate the KWin bridge (o KWin bridge recusou carregar o script…)». Seleção de
região e captura de tela inteira seguiam rápidas.

**Causa raiz do hover.** O `org.kde.KWin.ScreenShot2` do KWin 6.7.3 recusa quem não está autorizado:
`ScreenShotDBusInterface2::checkPermissions` lê `X-KDE-DBUS-Restricted-Interfaces` da entrada
`.desktop` cujo `Exec` — canonicalizado — é igual a `/proc/<pid>/exe` (`utils/serviceutils.h`). O app
nunca escrevia essa entrada, então toda captura direta voltava
`org.kde.KWin.ScreenShot2.Error.NoAuthorized` e o `KdeWaylandCapture` passava ao portal `Screenshot`
— que no KDE cria um `QuickDialog` por chamada (a janela «Portal») e devolve o workspace inteiro em
PNG. Como o código desativava o caminho direto **para sempre** depois da primeira recusa, o hover
ficava no portal até reiniciar. Havia ainda um segundo defeito escondido atrás do primeiro: o KWin
responde com `format = 6` (`QImage::Format_ARGB32_Premultiplied`, bytes B,G,R,A) e o
`kwinRawImage` só aceitava 16/17 (RGBX/RGBA), ou seja, mesmo autorizado o caminho direto falharia e
voltaria ao portal.

**Causa raiz dos atalhos.** O fallback KWin registra as combinações via `registerShortcut` dentro do
script, e o KGlobalAccel guarda o valor que já conhece para a ação — `registerShortcut` só fornece o
*default*. O `kglobalshortcutsrc` mostrava `yabaiOcrQuickDict=Ctrl+Shift+O,none,…` enquanto o script
em disco já tinha `Ctrl+Shift+D`: recarregar o script não mudava o valor ativo. Além disso,
`KWinHoverBridge.updateShortcuts` retornava cedo quando o arquivo do script já estava com o conteúdo
desejado — exatamente o estado em que a UI mostra o atalho novo e o teclado continua no antigo.

**Causa raiz do bridge.** `org.kde.kwin.Scripting.loadScript(path, pluginName)` devolve `-1` quando já
existe um script carregado com aquele nome (medido no host: `i 1` na primeira chamada, `i -1` na
segunda). Depois de fechar o app sem descarregar (ou de um encerramento abrupto), o script antigo
continuava no KWin e a nova instância interpretava o `-1` como «o KWin recusou carregar o script».

**Correção.**
- `LinuxDesktopEntry`/`LinuxSystemIntegration`: a entrada visível passa a declarar
  `X-KDE-DBUS-Restricted-Interfaces=org.kde.KWin.ScreenShot2`, e o app grava no início (KDE Wayland)
  uma entrada oculta `yabai-ocr-screenshot.desktop` cujo `Exec` é `/proc/self/exe` — o binário que o
  KWin compara. Para o AppImage isso é o runtime extraído em `/tmp/.mount_*`, por isso a entrada é
  reescrita a cada início e mantida separada da entrada do menu.
- `KWinScreenshotCapture`: `rawChannelOrder` mapeia `RGB32`/`ARGB32`/`ARGB32_Premultiplied`/`BGRA8888`
  como B,G,R,A e mantém 16/17 como R,G,B; formato desconhecido continua recusado (nunca adivinhar
  canal). `KdeWaylandCapture` troca o desligamento permanente por um cooldown de 10 s, para o índice
  do KWin (`KSycoca`) poder alcançar a entrada recém-gravada; uma captura direta bem-sucedida zera o
  cooldown.
- `KGlobalAccel.kt`: cliente D-Bus mínimo (`unregister(component, action)`) usado pelo
  `KWinHoverBridge` para esquecer as ações `kwin/yabaiOcr*` antes de recarregar o script, o que faz o
  reload aplicar as combinações novas. `updateShortcuts` só dispensa o reload quando não há fallback e
  o script já está igual.
- `KWinHoverBridge.start` descarrega um script com o mesmo nome antes de carregar; `stop` fecha também
  a conexão do KGlobalAccel.

**Testes e mutações.** `:desktop:test` fechou em **296 testes, 0 falhas, 1 pulado** no Steam Deck
(KDE Wayland real), em três execuções seguidas. Testes novos: formato `ARGB32_Premultiplied` (6),
retry depois do cooldown, script obsoleto descarregado, limpeza do KGlobalAccel no início e na edição,
edição com arquivo já atual ainda recarregando, e as entradas `.desktop` (chave do KWin + entrada
oculta). Provas de mutação, todas restauradas com SHA-256 idêntico:

| mutação | teste que acusou |
| --- | --- |
| formato 6 decodificado como R,G,B | `KWinScreenshotCaptureTest` |
| caminho direto desligado para sempre | `KWinScreenshotCaptureTest` |
| script obsoleto não é descarregado | `KWinHoverBridgeTest` |
| edição dispensada quando o arquivo já está igual | `KWinHoverBridgeTest` |
| bindings do KGlobalAccel não são esquecidos | `KWinHoverBridgeTest` |

**Evidência.** No host (Plasma 6.7.3, KWin 6.7.3): `busctl … ScreenShot2.CaptureArea` sem entrada
autorizada devolve `Error.NoAuthorized`; com uma entrada cujo `Exec` casa com `/proc/<pid>/exe` a
mesma chamada devolve `{'format': <uint32 6>, 'width': 100, 'height': 100, 'stride': 400}` e grava
40 000 bytes. O transporte real do app (`DbusKWinScreenshotTransport`) capturou `200x100` em **68 ms**
sem diálogo, com 12 cores distintas e canais na ordem certa. `kglobalshortcutsrc` tinha
`yabaiOcrQuickDict=Ctrl+Shift+O,none,…` com o script em `Ctrl+Shift+D`; após `unregister` + reload o
valor passou a `Ctrl+Shift+D` e `shortcut()` devolveu `0x06000044`. `loadScript` devolveu `i 1` e, na
chamada seguinte com o mesmo nome, `i -1`.

**Limitações.** O hover não foi exercitado ponta a ponta com o app rodando (a janela/ponteiro do
usuário não foram manipulados); a autorização real do pacote depende de o KWin indexar a entrada
oculta, o que o cooldown cobre mas não elimina na primeira leitura. O nome de componente do
KGlobalAccel (`kwin`) foi confirmado no KDE 6.7.3; outro compositor/versão que use outro nome apenas
mantém o comportamento antigo (a limpeza não encontra a ação). Nada foi commitado.

### D46 — Botão de IA no SteamOS, monitor da captura e saída do botão «Dicionário»  `[x]` (13/11/2026)

**Relato.** No SteamOS o botão de IA do resultado não fazia nada; o botão «Dicionário» do resultado
não tinha uso; e a captura de tela inteira/recorte sempre usava o monitor principal, mesmo com um
monitor externo ligado no Deck.

**Causa raiz do botão de IA.** O clique chamava `Desktop.getDesktop().browse(URI.create(url))` dentro
de `runCatching`. O peer X11 do JDK precisa de um `gio`/`xdg-open` funcional e de um contexto de
aplicativo lançado pelo desktop; dentro do AppImage (runtime extraído em `/tmp/.mount_*`) a chamada
falha, e a exceção era engolida — nenhuma URL aberta e nenhum aviso. O mesmo vale para a
`ai_prompt`/provedor: a URL estava correta, quem não abria era o navegador.

**Causa raiz do botão «Dicionário».** Sobrou da época em que o resultado não tinha painel de
dicionário; hoje o painel direito já mostra os verbetes e o botão só reabria a janela de gestão com a
frase como busca.

**Causa raiz do monitor.** `ScreenCapture.screenBounds()`/`captureScreen()` são, por definição, o
monitor primário (`defaultScreenDevice` e o recorte do workspace). Não havia preferência nem caminho
por monitor, então um monitor externo nunca era capturado, exceto pelo hover (que usa o ponteiro).

**Correção.**
- `ExternalBrowser.kt`: `DesktopUrlOpener` tenta os lançadores do próprio desktop na ordem
  `xdg-open`, `gio open`, `kde-open6`, `kde-open5`, `sensible-browser` (Linux) e `rundll32
  url.dll,FileProtocolHandler` (Windows), com `Desktop.browse` como último recurso e 4 s de espera
  por lançador. O clique roda em `Dispatchers.IO`; se nada abrir, o link vai para a área de
  transferência e o resultado mostra um aviso (pt-BR/en).
- `ResultWindow`: remoção do botão «Dicionário» e do parâmetro `onOpenFullDictRequested`; string
  `btnFullDictionary` apagada dos dois idiomas.
- `ScreenCapture`: `CaptureDisplay` (índice, rótulo do `getIDstring()`, bounds, escala),
  `captureDisplays`, `resolveCaptureDisplay` (`PRIMARY`/`MOUSE`/`DISPLAY:<n>`, com `MOUSE` usando
  `HoverGeometry.monitorAt`), `captureMonitor` no backend (`Robot` escolhe o `GraphicsDevice` do
  retângulo e pede a variante nativa; KWin mantém o recorte do workspace para o principal e usa
  `CaptureArea` para os demais; portal recorta do workspace) e `captureScreenWithRobot(bounds)`
  com `deviceWithBounds` separado para teste.
- `DesktopSettings.captureMonitor` (`capture_monitor`, padrão `PRIMARY`) e seção nova nos Ajustes
  («Captura de Tela — Qual Monitor») com «Monitor principal», «Monitor onde está o ponteiro» e um
  item por monitor conectado. `MOUSE` usa a posição do script KWin, que já chega a cada movimento do
  ponteiro, então funciona também no Wayland.

**Testes e mutações.** `:desktop:test` fechou em **316 testes, 0 falhas, 1 pulado**. Testes novos:
`CaptureMonitorTest` (12: preferência, rótulo, backend, KWin, dispositivo) e `DesktopUrlOpenerTest`
(6: ordem dos lançadores, URL como último argumento, fallback, Windows, falha), mais dois em
`DesktopSettingsTest` (padrão/round-trip e valor em branco). Mutação provada e restaurada com
SHA-256 idêntico:

| mutação | teste que acusou |
| --- | --- |
| preferência `MOUSE` ignorada | `CaptureMonitorTest` |
| Linux sem lançadores (só `Desktop.browse`) | `DesktopUrlOpenerTest` |
| KWin captura o workspace em qualquer monitor | `CaptureMonitorTest` |

**Evidência.** `xdg-open`, `gio` e `kde-open5` existem no host (`/usr/bin`), então a primeira tentativa
do Linux está disponível sem depender do JDK. A suíte roda em 13/11/2026 com 316 testes.

**Limitações.** O clique do botão de IA não abriu um navegador neste ambiente (não havia navegador
gráfico para receber a URL) — o que foi verificado é a ordem dos lançadores, o último argumento e o
aviso de falha. No Wayland a janela do seletor abre em tela cheia na tela em que o compositor a
colocou, então escolher explicitamente outro monitor é confiável no Windows/X11 e depende do
compositor no Wayland; o hover continua sempre no monitor do ponteiro. O hover num Windows com
vários monitores permanece como antes (captura de região pelo dispositivo primário). Nada foi
commitado.


### D47 — Bandeja no Ubuntu/GNOME, verificação de atualização que não respondia e botões achatados  `[x]` (13/11/2026)

**Relato.** No Ubuntu 22.04 o ícone não aparecia na bandeja; no Windows o botão de verificar
atualização não fazia nada; vários botões ficavam achatados ao estreitar a janela (visto no
gerenciador de dicionários). Pedido também de auditoria de botões sem função no desktop e no Android.

**Causa raiz da bandeja.** `trayBackendFor` só escolhia StatusNotifierItem em Linux **Wayland**; em
X11 ia direto ao `SystemTray` do AWT. O GNOME 42 (Ubuntu 22.04) não desenha ícones XEmbed — o
`tray.add` "funciona" e o ícone simplesmente não existe —, e no Wayland o AWT nem recebe clique. Como
o `setup()` devolvia `true` quando o AWT aceitava o ícone, o app ainda acreditava ter bandeja.

**Causa raiz da verificação de atualização.** Duas coisas. A versão era a constante `1.0.0`, embora
o empacotamento use `-PyabaiVersion`: o About mostrava um número que não era o do build. E o
resultado da checagem de dicionários era escrito em estado do Compose a partir de `Dispatchers.IO`
(`checkMessage`, `updateAvailableMap`, progresso) — escrita de estado fora da thread da UI não agenda
recomposição, então o botão parecia morto: o rótulo virava «Verificando…» e nada mais aparecia.

**Causa raiz dos botões achatados.** Nas linhas com `Arrangement.SpaceBetween`, o texto à esquerda
não tinha `weight`: ele reivindicava toda a largura natural e os botões ficavam com o resto, que
encolhia até o mínimo e cortava o rótulo. Nos cabeçalhos do gerenciador de dicionários isso deixava
«Verificar Atualizações», «Importar» e «Pasta» ilegíveis/achatados.

**Correção.**
- `trayBackendFor` tenta SNI em **todo** Linux (Plasma desenha nativo; GNOME com a extensão
  AppIndicator lê o mesmo protocolo) e mantém o AWT como fallback quando não há watcher.
  `SystemTrayManager.lastFailure`/`failureMessage()` registram o motivo (`NO_WATCHER`,
  `AWT_UNSUPPORTED`, `AWT_ADD_FAILED`) e os Ajustes mostram o texto — com o caminho de ação no GNOME
  (instalar a extensão AppIndicator) em vez de rodar invisível em silêncio.
- `desktop/build.gradle.kts` gera `yabai-version.properties` a partir de `appVersion` e o empacota;
  `DesktopAppUpdateChecker.resolveVersion` usa recurso → `YABAI_VERSION` → constante (ordem testada),
  e o About e a comparação de releases passam a usar a versão real do build. O botão de download
  passou a usar `DesktopUrlOpener` (mesma cadeia do botão de IA) e avisa quando nenhum navegador abre.
- `DictionaryManagerWindow` escreve o estado da checagem/download na thread da UI
  (`withContext(Dispatchers.Main)` e `onUiThread`/`SwingUtilities.invokeLater` para os callbacks de
  progresso).
- Layout: `weight(1f)` no texto/coluna à esquerda dos cabeçalhos do gerenciador de dicionários, no
  cabeçalho do resultado, na linha de status do hover e na prioridade de motores; o título do verbete
  também cede espaço ao nome do dicionário. Os botões mantêm a largura intrínseca e o texto é que
  encolhe.

**Auditoria de botões sem função.** Android: os 31 `setOnClickListener` têm corpo real (nenhum vazio)
e todos os 10 botões declarados nos layouts são referenciados no Kotlin; nenhum botão declarado sem
listener. Desktop: nenhum `onClick = {}`; os handlers que existiam sem efeito eram o de IA (D46), o de
download de atualização (agora com abridor e aviso) e o de verificação de dicionários (thread). Nenhum
botão foi removido além do «Dicionário» do resultado, já removido na D46.

**Testes e mutações.** `:desktop:test` fechou em **321 testes, 0 falhas, 1 pulado**. Novos:
`StatusNotifierTrayTest` (Linux X11/UNKNOWN agora SNI; Windows/macOS seguem AWT),
`DesktopAppUpdateCheckerTest` (precedência recurso → ambiente → constante, três casos) e
`UpdateUiThreadTest` (a escrita vinda de thread de trabalho chega ao EDT). Mutações provadas e
restauradas com SHA-256 idêntico:

| mutação | teste que acusou |
| --- | --- |
| SNI só no Wayland (AWT de novo no X11) | `StatusNotifierTrayTest` |
| versão constante, recurso ignorado | `DesktopAppUpdateCheckerTest` |
| `onUiThread` executa no thread de trabalho | `UpdateUiThreadTest` |

**Evidência.** `-PyabaiVersion=1.0.2` gerou `yabai-version.properties` com `version=1.0.2` dentro do
jar empacotado (`unzip -p … yabai-version.properties`) e o AppImage `Yabai_OCR-1.0.2-x86_64.AppImage`;
a suíte fecha em 321 testes.

**Limitações.** A bandeja no GNOME sem a extensão AppIndicator continua sem ícone — nenhum código do
app pode desenhar ícone onde o shell não tem host; o que mudou é o SNI ser tentado sempre e o motivo
aparecer nos Ajustes. O layout só foi verificado por compilação: redimensionar as janelas no Desktop
Mode é o teste visual pendente, assim como clicar em «Verificar Atualizações» no Windows. Nada foi
commitado.


### D48 — Checagem de atualização nos dois apps, preview das notas e menu mobile reorganizado  `[x]` (13/11/2026)

**Relato.** O desktop tinha o botão de verificar atualização, mas nenhum dos dois apps deixava o
usuário escolher se quer checar ao abrir, nem mostrar as notas do release. No Android, o menu
principal tinha seções numeradas, o tema aparecia duas vezes (botão no topo e seção de rádio) e a
ordem das opções não era a pedida: idioma primeiro, OCR flutuante em segundo.

**Causa raiz (o que faltava).** `DesktopAppUpdateChecker` devolvia `releaseNotes` (o `body` do
release) e ninguém lia o campo; o Android não tinha verificador de atualização de app nenhum, só o de
dicionários. E as release notes do GitHub são **Markdown**: jogá-las num `TextView`/`Text` cru
mostraria `##`, `**` e links como texto.

**Correção.**
- `:core/md/Markdown.kt`: parser mínimo (títulos 1–6, parágrafos, listas com `-`/`*`/`+`, citações,
  blocos cerrados, divisórias, negrito/itálico/código/link inline). O que não entende fica como
  texto — nada é descartado, porque esconder parte das notas é pior que mostrá-la sem estilo.
- Desktop: `ReleaseNotesWindow.kt` desenha os blocos com Compose (`LinkAnnotation` nos links) e abre
  pelo botão «📄 Notas da versão» no cartão de atualização; `DesktopSettings.checkUpdatesOnStart`
  (`check_updates_on_start`, padrão ligado) controla a checagem em `Main.kt`; o cartão ganhou o
  checkbox «Verificar atualizações ao abrir o aplicativo».
- Android: `update/AppUpdateChecker.kt` lê o último release (tag, body, asset `.apk`) e compara com
  `BuildConfig.VERSION_NAME` pelo `:core/update/VersionCompare.kt` (compartilhado com o desktop);
  `update/MarkdownView.kt` converte os blocos em `Spanned` (negrito, código monoespaçado,
  `URLSpan` + `LinkMovementMethod`). `AppSettings.checkUpdatesOnStart` (padrão ligado), cartão
  «Atualizações» novo com versão instalada, botão de checar, checkbox, status, «📄 Ver notas da
  versão» e «⬇️ Baixar atualização». A checagem automática roda uma vez por abertura e é silenciosa
  quando falha.
- Menu principal Android: «Idioma da Interface» passou a ser a primeira seção e o cartão de iniciar o
  OCR flutuante a segunda; a seção «Tema do Aplicativo» saiu (o botão do canto superior direito
  continua sendo o único controle de tema); os números das seções (`1.`, `2.`, …) saíram dos dois
  idiomas, e as strings da seção removida foram apagadas.

**Testes e mutações.** `:core:test` 192 testes (15 novos: 11 de Markdown, incluindo notas reais, e 5
de comparação de versões); `:desktop:test` 322 (novo caso do `checkUpdatesOnStart`), ambos verdes.
Mutações provadas e restauradas com SHA-256 idêntico:

| mutação | teste que acusou |
| --- | --- |
| listas deixam de ser reconhecidas no Markdown | `MarkdownTest` |
| componente ausente da versão conta como 1 | `VersionCompareTest` |
| checagem ao abrir desligada por padrão | `DesktopSettingsTest` |

Uma mutação equivalente foi descartada: remover `substringBefore('-')` do `normalize` não muda nada,
porque `mapNotNull { toIntOrNull() }` já descarta o componente sujo — não é teste fraco, é mutante
equivalente.

**Evidência.** `:core:test` 192/0 falhas e `:desktop:test` 322/0 falhas (1 pulado) no host;
`-PyabaiVersion=1.0.3` gerou o AppImage com o `yabai-version.properties` correspondente.

**Limitações.** O módulo `:app` **não foi compilado** nesta máquina: o projeto fixa toolchain JDK 17,
só há JDK 21 instalado, e não existe Android SDK em `ANDROID_HOME`/`local.properties`. A parte Android
foi validada por inspeção estática (XML bem-formado, todas as `@string` usadas presentes nos dois
idiomas, todos os `R.id` referenciados com view correspondente) e pela lógica compartilhada testada no
`:core`; compilar o APK e ver o preview de Markdown no aparelho continua pendente. No desktop o
preview não foi visto renderizado: só compilação. Nada foi commitado.


### D49 — Atualização baixada e instalada pelo próprio app  `[x]` (13/11/2026)

**Relato.** O botão de atualização só abria o navegador — no desktop e no Android. Quem quisesse a
versão nova precisava baixar o arquivo no navegador e instalar à mão.

**Causa raiz (o que faltava).** `DesktopAppUpdateChecker` já expunha `downloadUrl` do asset certo por
SO, e o Android já tinha `apkUrl`, mas nenhum dos dois baixava nada: o clique terminava em
`DesktopUrlOpener`/`ACTION_VIEW`.

**Correção.**
- Desktop: `UpdateInstaller.kt` baixa com progresso para um `.part`, confere o tamanho e só então
  move para o destino. Num AppImage (caminho em `APPIMAGE`) o arquivo em execução é renomeado para
  `<nome>.old` — escrever por cima de um binário em execução dá ETXTBSY —, o novo entra no lugar,
  recebe `+x` e o app se reinicia pelo botão «Reiniciar agora»; se o swap falhar, o antigo volta.
  Builds que não são AppImage (por exemplo `gradle run`) e Windows/macOS não se substituem sozinhos:
  Windows/macOS baixam e abrem o instalador, o resto abre a página do release com aviso explícito.
- Android: `ApkInstall.kt` baixa o APK pelo `DownloadManager` (continua com a tela desligada e
  aparece na gaveta de notificações) e entrega o arquivo ao instalador do sistema via `FileProvider`
  (`${'$'}{applicationId}.updates`, `res/xml/update_file_paths.xml`, permissão
  `REQUEST_INSTALL_PACKAGES`). Como o Android nunca instala sozinho, o passo final continua sendo a
  confirmação do usuário; se a permissão «instalar apps desconhecidos» estiver desligada, o app abre
  a tela dessa permissão em vez de baixar para nada. O recebimento do broadcast de download completo
  é registrado com `RECEIVER_NOT_EXPORTED` e casado pelo id do download.

**Testes e mutações.** `:desktop:test` 329 testes (7 novos de `UpdateInstallerTest`, incluindo
download contra um `HttpServer` de loopback, sem rede externa), `:core:test` 192, ambos verdes. O
primeiro par de testes **pegou um defeito real**: um download interrompido deixava o `.part` no
disco; a limpeza passou a acontecer em qualquer falha. Mutações provadas e restauradas com SHA-256
idêntico:

| mutação | teste que acusou |
| --- | --- |
| swap falho não devolve o build antigo | `a failed swap puts the old build back` |
| qualquer build Linux se declara atualizável | `only an appimage build replaces itself…` |
| o build novo não recebe permissão de execução | `replacing keeps the old file as a backup…` |
| download parcial fica no disco | `a truncated download is refused…` |

Mutante equivalente: desligar a conferência `written != total` não quebra teste, porque o OkHttp já
recusa um corpo menor que o `Content-Length`; a conferência fica como segunda barreira e está
documentada como tal.

**Evidência.** `:core:test` 192/0 e `:desktop:test` 329/0 (1 pulado). AppImage 1.0.4 gerado com o
código novo (classes `UpdateInstaller` presentes no jar empacotado).

**Limitações.** O `:app` continua **sem compilação nesta máquina** (toolchain JDK 17 ausente, sem
Android SDK): `ApkInstall.kt`, o provider do manifesto e o wiring do `MainActivity` foram conferidos
por inspeção estática (XML válido, strings nos dois idiomas, `R.id` existentes, autoridade do
`FileProvider` igual à usada no código), mas baixar e instalar um APK de verdade só pode ser testado
num aparelho. No desktop, o fluxo completo (baixar, trocar o AppImage, reiniciar) não foi executado
de ponta a ponta: a lógica de download e de troca está testada isoladamente, e a chamada real
depende de um release novo com asset. Nada foi commitado.

> **Revisão (D54, 13/11/2026).** A parte Android desta entrada não vale mais: o app deixou de instalar
> o APK. O botão apenas baixa, a permissão `REQUEST_INSTALL_PACKAGES`, o `FileProvider` e o
> `update_file_paths.xml` saíram, e o receptor passou a usar `RECEIVER_EXPORTED` — ver D54.


### D50 — CI: StackOverflowError do tema no Android e versão literal no Windows  `[x]` (13/11/2026)

**Relato.** O release falhou nas duas plataformas: no Android, `AppThemeTest > toggling theme from
button …` estourou a pilha em `LayoutInflater`; no Windows, o Gradle recusou a versão com
`Illegal version for 'Exe': '$env:VERSION' is not a valid version`. O Linux passou.

**Causa raiz.**
1. *Android:* `AppSettings.applyTheme` chamava `AppCompatDelegate.setDefaultNightMode` e
   `resources.updateConfiguration` **sem conferir se o valor já era aquele**. `MainActivity.onCreate`
   chama `applyTheme`, e cada aplicação repetida faz o AppCompat reexecutar `applyDayNight` nos
   delegates vivos, que recriam a activity — que chama `applyTheme` de novo. Em Robolectric a
   recursão termina em `StackOverflowError` dentro do `LayoutInflater`. O teste também ainda
   procurava `R.id.themeGroup`/`themeLight`, que a D48 removeu.
2. *Windows:* o passo do workflow passava `-PyabaiVersion=$env:VERSION`; a variável chegou ao Gradle
   como texto literal e o plugin do Compose validou a string como versão de MSI.

**Correção.**
- `applyTheme` só toca no delegate quando o modo muda e só reescreve `uiMode` quando a máscara atual
  difere — o laço de recriação deixa de existir, sem mudar o comportamento de troca de tema.
- `AppThemeTest` foi reescrito para a UI da D48: clica o botão do topo, confere texto e ciclo
  DARK → LIGHT → SYSTEM → DARK, e que a escolha sobrevive à recriação.
- `desktop/build.gradle.kts` normaliza a versão: tira `v`, corta sufixo de pré-release, exige
  dígitos e completa para `MAJOR.MINOR.BUILD` (o instalador exige três partes). Valor inválido agora
  falha com `yabaiVersion must be MAJOR.MINOR.BUILD after the leading v (got '…')`, nomeando o
  problema em vez do erro de MSI.
- `.github/workflows/release.yml` usa `${{ env.VERSION }}` (expandido pelo Actions) nos três jobs,
  então o valor não depende mais do shell.

**Evidência.** Configuração do desktop conferida por linha de comando: `-PyabaiVersion=v1.0.4` e
`-PyabaiVersion=1.2` configuram (1.2 → 1.2.0); `-PyabaiVersion='$env:VERSION'` falha com a mensagem
nova. `:desktop:test` continua verde (329). No Android, o teste atualizado é a regressão do laço —
**não executado aqui** por falta de SDK/JDK 17, então é o primeiro item a confirmar no CI.

**Limitações.** Sem Android SDK nesta máquina, nem o `StackOverflowError` nem o teste reescrito foram
reproduzidos localmente; a correção é por análise do laço de recriação. O workflow só roda no GitHub.


### D51 — Workflow inválido (expressão em comentário) e aviso de projeto gratuito  `[x]` (13/11/2026)

**Relato.** Depois da D50 o `release.yml` deixou de ser aceito: `Invalid workflow file: (Line: 61,
Col: 14): An expression was expected`. E o LICENSE/README não diziam que o projeto é gratuito e sem
fins lucrativos.

**Causa raiz.** O comentário que eu tinha escrito dentro do bloco `run: |` continha `` `${{ }}` ``.
O GitHub varre o arquivo inteiro atrás de expressões, inclusive comentários, e o par vazio não é uma
expressão válida — o workflow inteiro é recusado antes de rodar.

**Correção.** O comentário passou a descrever a expressão sem escrevê-la. `LICENSE` ganhou a seção
bilingue «Free, non-commercial project / Projeto gratuito e sem fins lucrativos», e `README.md`,
`README.en.md`, `CONTRIBUTING.md` e `CONTRIBUTING.en.md` dizem o mesmo em uma frase, no começo do
texto (README) e na seção de licença.

**Evidência.** `release.yml` faz parse YAML e os três passos de empacotamento usam
`-PyabaiVersion=${{ env.VERSION }}`; `git diff --check` limpo.

**Limitações.** A validação final do workflow é a do GitHub: só o próximo push confirma. O aviso de
«não vender» é declaração de intenção dentro de um projeto MIT — o MIT continua permitindo uso
comercial; para tornar a restrição jurídica seria preciso trocar a licença (por exemplo PolyForm
Noncommercial), o que não foi feito sem pedir.


### D52 — Licença PolyForm Noncommercial 1.0.0 (uso livre, venda proibida)  `[x]` (13/11/2026)

**Relato.** O projeto é gratuito e existe para quem estuda japonês. A licença MIT dizia o contrário:
permitia uso comercial, inclusive revenda. O aviso «não é para venda» acrescentado na D51 era
declaração de intenção, não restrição jurídica.

**Causa raiz.** MIT é uma licença permissiva por definição; não existe forma de proibir venda dentro
dela. Enquanto o `LICENSE` fosse MIT, qualquer aviso adicional seria apenas um pedido.

**Correção.** `LICENSE` passou a ser **PolyForm Noncommercial 1.0.0** (texto oficial, sem alterações,
com a linha `Required Notice: Copyright (c) 2026 Yabai OCR contributors` no topo, como a licença
exige). Ela libera uso, cópia, modificação e redistribuição para **qualquer finalidade não
comercial** — estudo pessoal, hobby, pesquisa, escolas, organizações sem fins lucrativos e órgãos
públicos — e proíbe vender, cobrar pelo acesso ou embutir o software num produto pago. Uso comercial
passa a exigir licença separada do autor.

`README.md`, `README.en.md`, `CONTRIBUTING.md`, `CONTRIBUTING.en.md` e a árvore do
`ARCHITECTURE.md`/`.en.md` foram atualizados para a licença nova, com a mesma frase nos dois idiomas.
O `ROADMAP.md` mantém as entradas antigas (D42, D51) como histórico do que valia na época.

**Evidência.** Texto conferido contra a versão oficial em texto puro do Polyform Project
(<https://polyformproject.org/licenses/noncommercial/1.0.0>); nenhuma menção a MIT sobrou fora do
histórico do ROADMAP (`grep -rn "MIT"`); `git diff --check` limpo. A suíte não muda: licença não tem
código.

**Limitações.** Licença é escolha do titular, não um teste automatizado: a verificação é a leitura do
arquivo. Projetos derivados que já tenham recebido o código sob MIT continuam podendo usá-lo sob MIT
— trocar a licença não é retroativo para quem já obteve cópia.


### D53 — Licença dentro do app, campo de doação e suporte no README  `[x]` (13/11/2026)

**Relato.** A PolyForm exige que quem recebe uma cópia receba também os termos, e os pacotes
(AppImage, zip/tar portátil, instalador) saíam sem `LICENSE`. Além disso, faltava um lugar para quem
quisesse apoiar o projeto: Ko-fi e Pix.

**Causa raiz.** A licença existia só na raiz do repositório; nenhuma tarefa de empacotamento a
copiava, e os apps não tinham como mostrá-la. Os dados de doação não existiam.

**Correção.**
- `desktop/build.gradle.kts`: `generateLicenseResource` copia o `LICENSE` para
  `build/generated/yabai-license/LICENSE.txt` (entra no jar, para o app exibir) e
  `copyLicenseToDistributable` copia o mesmo arquivo para a pasta do distributable, que é de onde
  Inno Setup, `packagePortableZip`, `packagePortableTarGz` e o script do AppImage empacotam — uma
  fonte de verdade, quatro pacotes cobertos.
- `app/build.gradle.kts`: `generateLicenseAsset` copia o `LICENSE` para
  `build/generated/license-assets`, incluído nos *assets* do APK (`preBuild` depende da tarefa).
- Desktop: `LicenseWindow.kt` (texto em monoespaçada, rolável) aberto por «📜 Licença» na seção de
  apoio; `DonationSection` em `SettingsWindow.kt` com Ko-fi, chave Pix e cópia para a área de
  transferência.
- Android: cartão «Apoiar o Projeto» no fim da tela com Ko-fi, chave Pix, «📋 Copiar chave Pix»
  (`ClipboardManager`) e «📜 Licença» (diálogo rolável lendo `assets/LICENSE`).
- `:core/DonationInfo.kt`: URL do Ko-fi e chave Pix num só lugar, usados pelos dois apps.
- `README.md`/`README.en.md`: seção «Sugestões e Suporte» com Ko-fi, Pix e QR (`docs/pix-qr.png`).

**Testes e mutações.** `:core:test` 194 (2 novos: chave Pix é e-mail válido, link do Ko-fi é https),
`:desktop:test` 330 (novo `LicenseResourceTest`, que falha se o `LICENSE.txt` não estiver empacotado
ou vier truncado). Mutações provadas com SHA-256 restaurado: chave Pix com espaço
(`DonationInfoTest`) e licença não empacotada (`LicenseResourceTest`). Evidência de pacote: o
`LICENSE` (4.620 bytes) está em `distributions/main/app/yabai-ocr/`, dentro do AppDir e dentro do
jar como `LICENSE.txt`; AppImage 1.0.5 gerado.

**Limitações.** `:app` continua sem compilar nesta máquina (sem Android SDK/JDK 17): layout, strings
e `MainActivity` passaram só por inspeção estática. O QR do Pix precisa ser salvo como
`docs/pix-qr.png` pelo autor — a imagem veio anexada na conversa, não como arquivo do repositório.
Nada foi commitado.


### D54 — Atualização que não responde, aviso no arranque e o AppImage somente leitura  `[x]` (13/11/2026)

**Relato.** Uma leva de defeitos em volta da atualização:

- No desktop (Windows e Ubuntu), clicar em «🔄 Verificar Atualizações» estourava
  `IllegalStateException: Module with the Main dispatcher is missing` em `SettingsWindow.kt:1693` e o
  botão não respondia.
- No Android o botão prometia «Baixar e instalar» e, para instalar, exigia a permissão «instalar apps
  desconhecidos»; quando o download terminava, o status ficava preso em «Baixando atualização…».
- Com «Verificar atualizações ao abrir» ligado, nada avisava o usuário — no desktop a descoberta ia
  só para o menu da bandeja, e quem abre o app minimizado (`--minimized`) nunca via.
- No AppImage (Ubuntu/SteamOS) o app falhava ao abrir com
  `UnsatisfiedLinkError: .../libJNativeHook-2.2.2.x86_64.so (Read-only file system)`, e o hover e a
  captura de tela inteira derrubavam o processo com `SIGSEGV` em `libstdc++.so.6`
  (`std::ostream::_M_insert<long>`).

**Causas raiz.** Cinco, independentes:

1. **Main dispatcher.** `Dispatchers.Main` só existe quando um artefato instala um despachante
   principal. O desktop declarava `kotlinx-coroutines-core` e mais nada; qualquer
   `withContext(Dispatchers.Main)` — a checagem de atualização entre elas — morria na primeira linha.
2. **JNativeHook read-only.** O carregador do JNativeHook descompacta a `.so` ao lado dos jars. Um
   AppImage monta o próprio conteúdo **somente leitura**, então a gravação falhava antes da primeira
   janela. O Windows resolve a biblioteca pelo `java.library.path` (uma pasta temporária gravável) e
   nunca teve o problema.
3. **SIGSEGV do Compose.** É o CMP-9994: no Ubuntu 24.04+, uma biblioteca nativa que formata número
   por `std::ostream` (o ONNX Runtime do PP-OCR, por exemplo) encontra o `libstdc++` num estado que o
   setup de Swing do Compose 1.7 deixa quebrado. A correção definitiva está no Compose **1.10**; o
   projeto usa 1.7.3 (e subir para 1.10 exigiria Kotlin ≥ 2.2).
4. **Broadcast do download.** `DownloadManager` roda em outro processo. Um receptor registrado com
   `RECEIVER_NOT_EXPORTED` **não recebe** o `ACTION_DOWNLOAD_COMPLETE` dele, e a tela ficava em
   «Baixando atualização…» para sempre.
5. **Aviso de arranque.** A checagem automática do desktop vivia dentro do `LaunchedEffect` da janela
   de Ajustes; no arranque minimizado a janela nem existe.

**Correção.**
- `gradle/libs.versions.toml` + `desktop/build.gradle.kts`: `kotlinx-coroutines-swing` (mesma versão
  do `coroutines`, 1.9.0), que instala `Dispatchers.Main` no event thread do AWT/Swing.
- `NativeLibraryPath.kt` (novo): fixa `jnativehook.lib.path` em `~/.cache/yabai-ocr/native` antes de
  qualquer uso de `GlobalScreen`; só no Linux, e nunca substitui um valor já definido pelo usuário.
  `Main.kt` chama `configureJNativeHookLibraryPathForThisSession()` na primeira linha.
- `desktop/build.gradle.kts`: `-Dcompose.application.configure.swing.globals=false` nos
  `jvmArgs` **apenas** em builds Linux — o workaround recomendado para o CMP-9994 sem trocar o
  Compose. Windows fica exatamente como estava.
- Desktop, aviso de arranque: `Main.kt` roda a checagem uma vez em `LaunchedEffect(Unit)` no escopo da
  aplicação (vale com `--minimized`) e `AppUpdateNoticeWindow.kt` (novo) mostra versão, download com
  progresso, «Reiniciar agora» e «📄 Notas da versão». `shouldCheckAppUpdateOnStart` e
  `startupUpdateNotice` em `DesktopAppUpdateChecker.kt` concentram as duas regras; `btnLater` entrou
  em `DesktopStrings` (pt/en).
- Android: `ApkInstall.kt` virou **somente download** (`DownloadManager` para a pasta de downloads do
  app, apagando o destino antes de enfileirar); `isDownloaded`/`fileName`/`downloadedApk` para o
  status. `MainActivity` chama `downloadUpdate()`, o receptor usa `RECEIVER_EXPORTED`, e a checagem
  automática abre `showUpdateFoundDialog` (título «Atualização disponível», ação «⬇️ Baixar»).
  Saíram a permissão `REQUEST_INSTALL_PACKAGES`, o `FileProvider` `${'$'}{applicationId}.updates` e
  `res/xml/update_file_paths.xml`; as strings de instalação foram removidas dos dois idiomas e
  `update_download_done` entrou no lugar.
- `README`/`README.en`: «Baixar» agora significa baixar (sem instalar) e o aviso de arranque está
  descrito; `ARCHITECTURE`/`ARCHITECTURE.en`: `AppUpdateNoticeWindow.kt` e `NativeLibraryPath.kt`
  entraram na tabela, `ApkInstall.kt` atualizado.

**Testes e mutações.** `:desktop:test` **337 testes em 58 classes, 0 falhas**; são 7 novos em
`DesktopMainDispatcherTest` (1), `NativeLibraryPathTest` (3) e `AppUpdateStartupTest` (3).
`:app:testDebugUnitTest` **416 testes em 57 classes, 0 falhas**; são 5 novos em
`UpdateDownloadOnlyTest` (manifesto sem `REQUEST_INSTALL_PACKAGES`, `ApkInstall` sem entrada de
instalação, flags do receptor, rótulo do botão, nome do arquivo). Cada regra foi mutada, o teste
nomeado caiu e voltou a passar ao restaurar:

| mutação | teste que acusou |
| --- | --- |
| remover `kotlinx-coroutines-swing` do desktop | `the main dispatcher posts to the AWT event thread` |
| inverter a checagem de Linux em `configureJNativeHookLibraryPath` | `windows keeps the default lookup` e `linux gets a writable cache directory` |
| `shouldCheckAppUpdateOnStart` sem `!alreadyStarted` | `the automatic check runs once, only when the user asked for it` |
| receptor do download com `RECEIVER_NOT_EXPORTED` | `the completion receiver accepts the system download broadcast` |

**Evidência.** `:desktop:cleanTest :desktop:test` → BUILD SUCCESSFUL, 337/0 (resultados com
timestamp da rodada); `:app:testDebugUnitTest` → BUILD SUCCESSFUL, 416/0. As duas mutações do
receptor e da dependência foram executadas de verdade, não deduzidas.

**Limitações.** Os três defeitos do Linux (JNativeHook read-only, SIGSEGV do Compose, hover/captura)
e o fluxo do Android **não foram reproduzidos neste host Windows**: exigem Ubuntu 24.04/SteamOS e um
aparelho. A correção do CMP-9994 fica como mitigação (`configure.swing.globals=false`), não como
prova — subir o Compose para 1.10 é o caminho definitivo e foi deixado fora desta rodada por exigir
Kotlin ≥ 2.2 e um reteste completo. O `settings.properties` do usuário não foi tocado. Nada foi
commitado.

> **Revisão (14/11/2026).** O workaround não resolveu. No AppImage 1.0.7 sob Temurin 17.0.21 e
> Ubuntu 24.04 o processo continua morrendo com `SIGSEGV (0xb) at pc=0x0000000000000002` em
> `libstdc++.so.6` (`std::ostream::_M_insert<long>+0x8a`) ao ativar o hover, ao capturar a tela
> inteira e ao recortar. O caminho definitivo virou a D55: Compose 1.12.2 com Kotlin 2.4.21.

### D55 — Ubuntu 24.04: SIGSEGV em `libstdc++` no hover/captura (Compose 1.12)  `[x]` (14/11/2026)

**Relato.** No AppImage 1.0.7, com Ubuntu 24.04 e o runtime empacotado (Temurin 17.0.21), o app
fecha o processo assim que o hover, a captura de tela inteira ou o recorte vão ler algo:

```text
[Hotkey] gancho global de teclado ativo.
[Hover] Modo Hover ativo — pare o mouse sobre um texto japonês.
SIGSEGV (0xb) at pc=0x0000000000000002
C  [libstdc++.so.6+0x13d11a]  std::ostream& std::ostream::_M_insert<long>(long)+0x8a
```

**Causa raiz.** É o **CMP-9994**, com o upstream do JDK registrando o mesmo defeito em
[JDK-8379560](https://bugs.openjdk.org/browse/JDK-8379560) e a Microsoft em
[microsoft/openjdk#670](https://github.com/microsoft/openjdk/issues/670). Em Ubuntu 24.04+, quando
uma biblioteca nativa que formata texto por `std::ostream` — o ONNX Runtime do PP-OCR é uma — é
chamada **dentro de uma aplicação Compose**, a conversão de caracteres (`codecvt`) do `libstdc++`
recebe um estado inválido e o JVM morre. Não é o OCR em si: o mesmo bloco roda sem crash fora de um
app Compose. A JetBrains confirmou no CMP-9994 que o problema **foi corrigido no Compose 1.10** e a
Microsoft fechou o #670 depois do teste com o Compose atualizado.

O `-Dcompose.application.configure.swing.globals=false` da D54 cobre apenas parte dos pontos de
crash — o do hover continua, que é exatamente o que o usuário viu.

**Correção.**
- `desktop/build.gradle.kts`: plugin `org.jetbrains.compose` **1.7.3 → 1.12.2** e
  `org.jetbrains.kotlin.plugin.compose` **2.1.21 → 2.4.21** (o compilador Compose acompanha o
  Kotlin).
- `gradle/libs.versions.toml`: `kotlin` **2.1.21 → 2.4.21**. O KGP 2.2.x só suporta Gradle até
  8.14; o projeto está no Gradle **9.6.0**, e o KGP **2.4.20–2.4.21** é o primeiro com suporte
  integral (7.6.3–9.7.0, AGP 8.5.2–9.3.1) para essa faixa. AGP segue 8.9.2, dentro da faixa.
- O flag `configure.swing.globals=false` foi mantido nos builds Linux como cinto de segurança; com
  a causa raiz corrigida ele é redundante, mas não custa nada e já está em produção.
- `ComposeLinuxCrashFixTest` (novo) falha se a versão do plugin Compose voltar para antes de 1.10 —
  um downgrade deixa a suíte verde no Windows e o crash volta só no Linux.

**Testes e mutações.** `:desktop:test` **340 testes em 60 classes, 0 falhas**; `:core:test` **194
testes em 20 classes, 0 falhas**; `:app:testDebugUnitTest` **416 testes em 57 classes, 0 falhas** —
as três suítes rodaram depois do upgrade de Kotlin/Compose. Mutação executada:

| mutação | teste que acusou |
| --- | --- |
| plugin Compose de volta a `1.7.3` | `the compose desktop plugin is at least 1_10 for the libstdc++ fix` |

**Limitações.** O crash é nativo e só acontece em Ubuntu 24.04+ com a JDK empacotada; não há
reprodução neste host Windows. A prova possível aqui é de build e de regressão de versão. O AppImage
precisa ser gerado no CI (`ubuntu-latest`) ou numa máquina Linux e testado com hover, captura
inteira e recorte — e, se ainda cair, o `hs_err_pid*.log` passa a ser obrigatório para separar
execução do Compose da execução do ONNX Runtime.

### D56 — Hover que reconhece palavra partida entre linhas/colunas  `[x]` (15/11/2026)

**Pedido.** Hoje o hover lê **uma linha só**. `HoverTextSelector.select` escolhe o `LensSegment`
mais próximo do ponteiro e chama `LookupEngine.lookup` só com o texto dele; `LookupEngine` para na
fronteira da "run" de caracteres de palavra, então `一生懸` no fim de uma linha e `命` no começo da
seguinte nunca formam `一生懸命`. O pedido é reconhecer o token que atravessa a fronteira, tanto em
texto horizontal (linha abaixo) quanto em vertical (coluna à direita, que é a "próxima" no japonês).

**Desenho acordado.**
1. **Ordem de leitura, não "abaixo/esquerda".** Usar `LensResult.blocks[].pieces`, que já vem em
   `ReadingOrder` (horizontal: linhas de cima para baixo; vertical: colunas da direita para a
   esquerda). Fixar "abaixo" quebra o vertical, onde a coluna seguinte fica à direita.
2. **Só perto da borda.** Tentar a junção quando o índice do caractere sob o ponteiro estiver nos
   primeiros ~2–3 caracteres da linha (palavra começou antes) ou nos últimos ~2–3 (palavra continua
   depois). Isso mantém o custo e o falso positivo baixos.
3. **Candidato curto e sem separador.** Sufixo da linha anterior até `MAX_TERM_LENGTH` (16) + linha
   atual, ou linha atual + prefixo da seguinte. **Sem espaço nem newline** entre CJK: um separador
   faria o `LookupEngine` parar na fronteira, que é justamente o que se quer atravessar.
4. **O token tem de cruzar a fronteira.** Só aceitar o `TokenMatch` cujo `start` cai antes do offset
   da linha atual e `end` depois dele; token inteiro dentro de uma linha mantém o comportamento
   atual.
5. **Mesmo bloco.** Reaproveitar a regra de `LensResultBuilder.belongsToSameBlock` (mesma direção,
   faixa compartilhada, gap < ~0,75 glifo) ou os grupos de `BlockColumns` do PP-OCR. Balões
   diferentes não se concatenam.
6. **Furigana fora.** `blocks[].pieces` já vem sem as leituras; usar os `segments` crus poderia
   colar uma leitura de furigana na frase.
7. **Caixa do destaque.** `tokenBox` pode devolver a união dos pedaços; se o retângulo entre duas
   linhas incomodar, guardar as caixas por linha.

**Desempenho.** Irrelevante: o que domina o hover é o OCR (HTTP no Lens, inferência ONNX no
PP-OCR), não o dicionário. A consulta extra só acontece a 2–3 caracteres de uma borda, com
candidato de no máximo ~32 caracteres. Polling de 60 ms, debounce de 420 ms e recorte de 640×360
**não mudam**.

**Riscos.** Juntar linhas de blocos diferentes e achar palavra inexistente (mitigado por 4+5);
preferir a palavra quebrada a uma palavra real inteira na linha (só quando o ponteiro está na borda
e o token cruzado é mais longo); linha vizinha fora do recorte (o recurso simplesmente não ativa).

**Testes previstos.** `HoverTextSelectorTest`: horizontal partido, vertical partido, linhas de
blocos diferentes, token que não cruza a fronteira, furigana adjacente, caixa do destaque. Mais a
mutações exigidas pela skill `yabai-regression`.

**Implementação.** `HoverTextSelector.select` passou a tentar, além da linha sob o ponteiro, o
candidato que atravessa a fronteira:

- o vizinho vem de `LensResult.blocks[].pieces` (leitura correta, sem furigana) e a ordem é a de
  leitura — abaixo no horizontal, à direita no vertical —, nunca "a linha que está embaixo na tela";
- só a 3 caracteres de uma borda (`BRIDGE_EDGE_CHARS`), com até 16 caracteres do vizinho
  (`BRIDGE_CHARS`, o teto de `LookupEngine.MAX_TERM_LENGTH`);
- sem espaço nem quebra entre os pedaços: é a ausência de separador que faz o `LookupEngine`
  atravessar a fronteira;
- o `TokenMatch` precisa cobrir o caractere do ponteiro **e** cruzar a fronteira (`start` antes,
  `end` depois);
- a palavra cruzada só ganha quando é mais longa que a palavra inteira que a linha já oferece;
- `HoverHit.tokenBox` é a união das duas metades, e `line` continua sendo a linha sob o ponteiro, que
  é a âncora do cartão.

**Testes e mutações.** Cinco testes novos em `HoverTextSelectorTest`: linha horizontal partida,
colunas verticais partidas, linhas de balões diferentes (não juntam), token que não cruza (rejeitado),
palavra completa vencendo uma travessia curta. Mutações derrubadas: (1) desligar a travessia →
os dois testes de palavra partida falham; (2) remover a exigência de cruzar a fronteira →
«a token that does not cross the line boundary is rejected» falha; (3) trocar `blocks[].pieces` por
`result.segments` → «lines of different blocks are never joined» falha. Restauradas, 409 testes
verdes no `:desktop`.

**Limitações.** O teste injeta o lookup, então o que está provado é a geometria e a escolha do
candidato, não o dicionário real. A caixa de destaque de uma palavra partida é a união das duas
linhas — o retângulo inclui o vão entre elas; se incomodar visualmente, o próximo passo é guardar uma
caixa por linha. Não foi verificado no app rodando.

### D57 — Ubuntu 24.04: captura recusada, avisos em 800×600 e atualização que não reabria  `[x]` (14/11/2026)

**Relato.** No AppImage do Ubuntu 24.04 (Wayland/GNOME 46), três defeitos independentes:

- hover, tela inteira e recorte terminavam em «Captura de tela indisponível nesta sessão. É preciso
  JDK 21…», embora o portal de tela estivesse ativo;
- os avisos de captura e de atualização abriam num quadro 800×600 com o texto espremido e o rodapé de
  botões fora da tela;
- a atualização baixava e instalava, mas o app não voltava e o arquivo trocado continuava com o nome
  da versão antiga (`Yabai_OCR-1.0.7-…` contendo o build 1.0.8).

**Causas raiz.**

1. **Piso do `dbusScreencast` errado.** `ScreencastScreenCapture` recusava qualquer JVM abaixo do
   JDK 21, mas o backend foi **backportado** para as linhas LTS antigas (JDK-8335485): 21, 17.0.13+,
   11.0.26+ e 8u441+. O runtime empacotado no AppImage é o 17.0.21 — tinha o backend e era barrado
   antes de tentar. É por isso que a captura por portal funciona nesta máquina quando exercitada
   direto pelo AWT.
2. **Tamanho de janela.** `rememberWindowState()` sem tamanho cai no padrão do toolkit (800×600). Os
   avisos são `Column`s curtos; o de atualização ainda tinha `verticalScroll`, que não tem altura
   intrínseca, e o `Surface` sem `fillMaxSize` deixava o conteúdo no canto. O resultado era texto
   cortado e botões inalcançáveis — o mesmo defeito de layout do B1, agora nas janelas de aviso.
3. **Troca do AppImage em execução.** O download era gravado **em cima** do arquivo em uso (no Linux
   isso falha com `ETXTBSY` ou deixa o mount antigo), e o relançamento herdava `APPIMAGE`, `APPDIR`,
   `ARGV0`, `OWD` e `LD_LIBRARY_PATH` do mount que o restart acabara de desmontar. O processo novo
   apontava para um diretório inexistente.

**Correção.**

- `ScreenCapture.kt`: `screencastSupported(featureVersion, updateVersion)` — 21+ ou 17.0.13+, os dois
  únicos casos que podem rodar este build (bytecode 17). `jdkUpdateVersion` lê o `java.version`
  completo e a mensagem `UNSUPPORTED` passou a citar «JDK 17.0.13+ (ou 21+)».
- `CaptureNoticeWindow` / `AppUpdateNoticeWindow`: `rememberWindowState(size = DpSize.Unspecified)`
  + `Surface(fillMaxSize)`; o aviso de atualização perdeu o `verticalScroll`; `title`/`heading`
  viraram parâmetros. `ReleaseNotesWindow(alwaysOnTop)` abre as notas **acima** do aviso, e as notas
  abertas por ele usam `alwaysOnTop = true`.
- `UpdateInstaller.kt`: `installedPath` (nome vindo do asset da release), `updateTarget` (`.update`
  ao lado quando o nome é o do processo em execução), `replaceAppImage(running, downloaded,
  finalPath)`, `relaunchEnvironment` sem as variáveis do runtime e `restart()` com `launch()`,
  checagem de vida de 1,5 s e fallback `--appimage-extract-and-run` quando o relançamento não sobe.
- `DesktopStrings`: `captureNoticeTitle`/`Heading`, `hoverNoticeTitle`/`Heading`,
  `hoverWaylandUnsupported`, `gnomeIntegrationActive`, `gnomeBridgeInstalled`,
  `gnomeBridgeFailed(detail)` (pt/en).
- `Main.kt`: as notas abertas pelo aviso vão com `alwaysOnTop = true`; o aviso de hover aparece
  quando o hover não pôde ser ligado (UNSUPPORTED ou ponte do GNOME ainda não carregada).

**Testes.** `ScreencastCaptureTest` (gate 17.0.13, piso por linha, token/permissão/negação),
`NoticeWindowLayoutTest` (as três janelas de aviso), `UpdateInstallerTest` (+3: nome do release,
renomeação na instalação e ambiente do relançamento) e `HoverCapabilityTest` (GNOME). Suíte desktop
em **358 testes** naquele momento (o total subiu com as tarefas seguintes).

**Mutações provadas.**

| mutação | teste que acusou |
| --- | --- |
| `featureVersion == 17 -> false` (volta o piso 21) | `jdk 17 with the screencast backport captures instead of being rejected`, `the screencast starts at 17_0_13 and is unconditional from 21` |
| `updateTarget` devolve sempre o arquivo em execução | `the update keeps the release file name, not the running one`, `installing a newer release renames the file to the new version` |
| `relaunchEnvironment` devolve o ambiente intacto | `the relaunch does not inherit the running appimage mount` |
| `rememberWindowState(size = DpSize.Unspecified)` removido do aviso de captura | `notice windows fit their content instead of the default 800x600 frame` |

**Correção de 15/11/2026 (janelas de aviso).** `Surface(fillMaxSize)` saiu dos avisos: com
`rememberWindowState(size = DpSize.Unspecified)` a primeira medida reportava o quadro em vez do
conteúdo, então a janela empacotada terminava acima do rodapé — o botão «OK» aparecia como uma pílula
cortada, sem texto, como no print do Ubuntu. O teste `the notice surface wraps its content instead
of filling the packed frame` prende a regra.

**Limitações.** A suíte prova o gate, o recorte de nomes e o ambiente do relançamento, mas não
instala um AppImage de verdade: a troca de arquivo em uso e o relançamento só podem ser vistos com o
build publicado. A primeira captura por portal nesta máquina levou ~72 s (o diálogo do sistema
aparece uma vez); a segunda já é direta.

### D58 — Hover no GNOME Wayland pela extensão do Shell  `[x]` (14/11/2026)

**Pesquisa.** No GNOME Wayland não existe rota nativa para um app comum saber onde está o ponteiro:
`MouseInfo`/JNativeHook só veem eventos sobre janelas X11 (XWayland) e nenhum portal expõe a posição
(RemoteDesktop *injeta* entrada, não a lê). As saídas possíveis eram: (a) extensão do Shell que leia
`global.get_pointer()` e entregue a posição ao app — é o que Kando e OBS fazem; (b) depender de uma
extensão de terceiros já instalada; (c) desistir do hover no GNOME. A opção (b) não é confiável
(nenhuma extensão publica esse dado de forma estável), então o app instala a **sua própria**
extensão — mesma decisão de arquitetura do script KWin, e sem exigir configuração manual.

**Contrato.** A extensão fala o **mesmo D-Bus** que o script do KWin, então o resto do app não sabe
quem é a fonte:

- `dev.yabai.Ocr` / `/dev/yabai/Ocr/Hover` / `dev.yabai.Ocr.Hover.CursorMoved(x, y)`, a 10 Hz,
  com dedup do último ponto;
- a extensão também **possui** o nome `dev.yabai.GnomePointer`; o app consulta `NameHasOwner` para
  distinguir «instalada» de «carregada» — o GNOME 46 só lê extensões no início da sessão, então uma
  instalação nova fica pendente até sair e entrar;
- coordenadas: `global.get_pointer()` vem em pixels de palco; a extensão divide pela escala do
  monitor quando o palco não está em 1:1, para o app continuar recebendo unidades lógicas como no
  KWin.

**Implementação.** `GnomeHoverBridge.kt` (novo) grava `metadata.json` + `extension.js` em
`$XDG_DATA_HOME/gnome-shell/extensions/yabai-ocr@yabai.dev/` (só quando o conteúdo muda), ativa e
devolve três estados: pronto, «ativa, mas só depois de sair e entrar» ou falha com o motivo.

**Ativação (correção de 15/11/2026).** A primeira versão chamava `gnome-extensions enable`, e o
teste com o AppImage mostrou que isso **não funciona para instalação nova**: o comando pergunta à
Shell em execução, que só lista as extensões encontradas no início da sessão, e responde
`Extension “yabai-ocr@yabai.dev” does not exist`. A ativação passou a ser `gsettings set
org.gnome.shell enabled-extensions`, com leitura prévia da lista para não descartar as extensões do
usuário (`enabledExtensionsWith`), que é exatamente o que o GNOME lê no próximo login. No AppImage
1.0.8 rodado aqui, a chave ficou `['yabai-ocr@yabai.dev']` e o app não registrou mais a falha de
ativação.

**Detecção (correção de 15/11/2026).** Na revisão seguinte a checagem de «extensão carregada» passou
a usar `DBusConnection.getNames()` — que lista os nomes da **própria conexão**, não os do barramento.
Resultado: com a extensão `State: ACTIVE` e dona de `dev.yabai.GnomePointer`, o app continuava
mostrando «instalada e ativada, saia e entre de novo» a cada início, e o hover nunca ligava. Voltou
para `getDBusOwnerName` (via `dbusOwnerOrNull`), tratando «no such name» como «ainda não carregada»
em vez de erro — é o estado normal antes do primeiro login. `HoverCapability` ganhou `HoverBackendKind.GNOME` (seleção
GNOME → GNOME, captura screencast incluída), `PointerSource` virou `BridgePointerSource` (as duas
pontes usam a mesma fonte; o `typealias KwinPointerSource` da primeira versão foi removido),
`HoverOcrManager` não faz
polling nesse backend e o status diz «ponteiro do GNOME Shell». `Main.kt` compartilha o serviço
D-Bus entre KWin e GNOME e mostra o aviso de hover quando a ponte não está pronta.

**Testes.** `GnomeHoverBridgeTest` (11): contrato lido dos recursos empacotados (nome, caminho,
`CursorMoved`, nome de prontidão, módulo ESM), instalação idempotente, `XDG_DATA_HOME`, extensão já
carregada sem tocar no disco, instalação nova pedindo um log out, recusa do CLI, CLI que estoura
(não pode virar «ativa»), pasta que não pode ser gravada, sessão D-Bus indisponível e a lista de
extensões habilitadas preservando as do usuário sem duplicar a nossa.

**Mutações provadas.**

| mutação | teste que acusou |
| --- | --- |
| falha do CLI tratada como sucesso (`getOrDefault(true)`) | `an enable that throws is a failure, not a pending log out` |
| `enabledExtensionsWith` sem acrescentar a UUID | `the enabled list keeps the user's extensions and gains ours once` |
| «carregada» voltar a `getNames()` | `GnomeHoverBridgeTest > the bridge is found through the owner of its bus name` |
| aviso volta a `fillMaxSize` | `NoticeWindowLayoutTest > the notice surface wraps its content instead of filling the packed frame` |

A mutação encontrou um bug de verdade: o código tratava exceção do CLI como «ativa» e o
usuário veria «saia e entre na sessão» para uma extensão que nunca foi ativada. Corrigido para
`getOrDefault(false)` e coberto pelo teste novo.

**Limitações.** Na sessão em que a D58 foi escrita a extensão ainda **não** estava carregada: o
GNOME 46 só a lê no início. Depois do relogin do usuário ela aparece como `State: ACTIVE` e dona de
`dev.yabai.GnomePointer` (pid do gnome-shell em `busctl --user list`), e um `CursorMoved` forjado no
`dev.yabai.Ocr` é aceito — o que faltava era o app reconhecê-la (ver «Detecção» acima). A escala
fracionária continua sem teste aqui (monitor em 1.0).


### D59 — Segunda instância derrubava hover e atalhos (nome D-Bus duplicado)  `[x]` (14/11/2026)

**Relato (Steam Deck, AppImage 1.0.8 do GitHub).** O hover funcionou uma vez (pelo portal), parou
depois de fechar e abrir o app, e os atalhos globais pararam junto. Os Ajustes mostravam duas linhas
de erro: hover «Não foi possível publicar o serviço D-Bus dev.yabai.Ocr (DbusException: Failed to
register bus name)» e atalhos «O portal de atalhos do sistema não respondeu … (1) Underlying
transport returned -1».

**Causa raiz.** Havia **duas instâncias** rodando na sessão. Fechar a janela de Ajustes não encerra o
processo — ele continua na bandeja —, e abrir o AppImage de novo criava uma segunda cópia. As duas
disputam o nome D-Bus `dev.yabai.Ocr`, a bandeja e os atalhos globais, que têm dono único. A segunda
perdia a corrida: `requestBusName` lançava, o `YabaiDbusService` falhava inteiro (hover **e** atalhos
de fallback passam por ele) e o portal também não respondia no processo novo. Evidência colhida na
máquina: `busctl --user list` com dois processos `yabai-ocr`, o antigo (13074) dono de `dev.yabai.Ocr`
e o novo (14164) sem o nome.

**Correção.** `InstanceLock.kt`: trava de arquivo em `~/.yabai_ocr/instance.lock`, adquirida na
primeira linha de `main()`, antes de qualquer janela, bandeja ou D-Bus. Se outra instância já tem a
trava, a nova avisa («O Yabai OCR já está aberto… abra os ajustes pelo ícone da bandeja»), escreve o
motivo no stderr e sai com código 0 — sem bandeja, sem D-Bus, sem confusão. Em `--minimized`/`--tray`
(autostart) sai em silêncio, sem diálogo. O sistema libera a trava quando o processo morre, então
travamento ou `kill -9` não deixam o app bloqueado; se a trava em si não puder ser criada, o app
**abre** — recusar abrir por causa do arquivo de trava seria pior que a duplicata. Quando o nome
D-Bus já tem dono, a linha de status agora diz isso em linguagem de gente, em vez de mostrar a
`DBusException` crua.

**Testes e evidências.** `InstanceLockTest` (3): a primeira instância passa e a segunda é recusada;
depois de liberar, a próxima passa; adquirir duas vezes no mesmo processo não é conflito. Mutação
«segunda instância é aceita» derrubada. Mutação `release()` sem fechar o canal também é derrubada;
trocar só `lock.release()` por vazio **não** derruba nada porque fechar o canal já solta a trava —
mutante equivalente, registrado aqui.

Prova ponta a ponta na máquina (`YABAI_HOME=/tmp/yabai-instance-test`, build 1.0.9): a primeira cópia
subiu e ficou viva; a segunda imprimiu «Yabai OCR: já existe uma instância aberta; esta cópia vai
sair.» e terminou com código 0, com o `instance.lock` criado. AppImage 1.0.9 gerado.

**Limitações.** O hover e os atalhos em si não foram exercitados na sessão real nesta rodada — a
evidência é a ausência do erro de D-Bus e a recusa da segunda instância. Nada foi commitado.

### D60 — Atalhos no KDE iam para um portal sem app id, e o cartão do hover nascia no canto  `[x]` (14/11/2026)

**Relato (Steam Deck, AppImage 1.0.9 já com a trava de instância).** Com uma instância só, os
atalhos globais continuavam mortos e o stderr mostrava `DBusExecutionException: An app id is
required` em `DbusPortalShortcutsTransport.createSession`. O hover voltou, mas o cartão aparecia
sempre no canto superior esquerdo da tela, longe do ponteiro, e o diálogo do portal abria a cada
hover (captura lenta).

**Causa raiz dos atalhos.** `hotkeyBackendKindFor` mandava **todo** Wayland para o portal
`org.freedesktop.portal.GlobalShortcuts`. O portal identifica o chamador pelo *app id*, que vem da
entrada `.desktop` que iniciou o app (`XDG_DESKTOP_FILE`); um AppImage aberto do gerenciador de
arquivos não tem nenhuma. Testes na máquina descartaram as saídas de contorno: exportar
`XDG_DESKTOP_FILE` apontando para a entrada oculta do app, para uma entrada no
`~/.local/share/applications` real e para uma entrada cujo `Exec` é exatamente o
`/proc/<pid>/exe` do processo — nos três casos o portal respondeu `An app id is required`.
Relançar o app com a variável exportada também não se sustentou: o filho do runtime do AppImage
morre antes do `main` (imprime o *usage* do `java`), então esse caminho **não** foi incluído.

**Correção dos atalhos.** No KDE Wayland os atalhos passam a vir do script do KWin, que já existia
para o ponteiro: `registerShortcut` → KGlobalAccel → `dev.yabai.Ocr.Shortcuts.ShortcutPressed` →
`HotkeyCallbacks`. Novo `HotkeyBackendKind.KWIN` com `KWinScriptHotkeys` (delega o registro e nunca
falha sozinho — quem sabe se o script carregou é `KWinIntegration`, e a linha de status dos Ajustes
agora mostra «Atalhos globais pelo KWin (KGlobalAccel)» ou o motivo da falha). Nada de portal, nada
de app id, nada de diálogo. O portal continua sendo o backend do Wayland **sem** KDE.

**Causa raiz do cartão.** A posição vinha de `window.setLocation` dentro do `LaunchedEffect`, que
roda enquanto a janela ainda está sendo mapeada. O KWin decide a posição no mapeamento e ignora o
`setLocation` que chega antes; o cartão ficava em (0,0). **Correção:** a posição é calculada por
`HoverGeometry.placeTooltip` **antes** da janela existir, entregue no `WindowState`
(`WindowPosition`) e reaplicada 120 ms depois, quando a janela já está na tela.

**Diagnóstico da captura por hover.** `KdeWaylandCapture` agora escreve no stderr por que a captura
direta pelo `ScreenShot2` foi recusada antes de cair no portal; sem essa linha o único sintoma era o
diálogo de permissão a cada hover, sem causa visível.

**Testes e mutações.** 364 testes desktop, 0 falhas (1 pulado): `kde wayland uses the kwin script
instead of the portal`, `the kwin backend delegates registration to the script and never fails on its
own` e `a refused direct capture says why it fell back to the portal`. Mutação «KDE volta ao portal»
derrubada; mutação «não registrar o motivo da recusa» derrubada.

**Limitações.** A posição do cartão e o disparo real dos atalhos com tecla física não foram
observados nesta rodada (a sessão do usuário estava em uso com o app aberto): a evidência é a suíte e
o cálculo de `HoverGeometry`. O AppImage `1.0.9` não pôde ser regerado porque o app do usuário estava
rodando a partir dele; foi gerado um `1.0.9-e2e` para os testes. Nada foi commitado.

### D61 — Captura sempre pelo portal (ScreenShot2 impossível em AppImage) e janelas nascendo na barra  `[x]` (14/11/2026)

**Relato.** Com atalhos e cartão já corretos, o hover continuava lento e o log do AppImage mostrava
`The process is not authorized to take a screenshot` a cada leitura, seguido de «captura direta pelo
KWin recusada …; usando o portal». O usuário também relatou «todos os menus abrindo minimizados»:
Busca Rápida, Gerenciador de Dicionários e o Resultado do recorte apareciam só na barra de tarefas.

**Causa raiz da captura.** KWin (`ScreenShotDBusInterface2::checkPermissions` →
`fetchRestrictedDBusInterfacesFromPid`) autoriza `org.kde.KWin.ScreenShot2` apenas quando o
`/proc/<pid>/exe` do chamador é o `Exec` de uma entrada `.desktop` que declare
`X-KDE-DBUS-Restricted-Interfaces` **e que viva em diretório privilegiado** (`/usr/share/applications`;
KDE D29407 e bug 446628 dizem isso explicitamente). Dentro de um AppImage o exe é
`/tmp/.mount_Yabai_XXXX/opt/yabai-ocr/bin/yabai-ocr`, com sufixo novo a cada abertura: nenhuma entrada
fixa pode casar. O `yabai-ocr-screenshot.desktop` em `~/.local/share/applications` nunca autorizou
nada — a captura sempre caiu no portal, que abre o diálogo a cada leitura (a lentidão). O caminho do
ScreenCast do JDK também não resolve nesta máquina: o portal 1.22.1 passou a emitir tokens que não são
UUID e o JDK até 21.0.12 os rejeita (JDK-8396109, integrado só em 11/2026 num JDK futuro).

**Correção da captura.** Opção explícita em Ajustes («Captura silenciosa no KDE»): grava
`~/.config/environment.d/yabai-ocr.conf` com `KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1`, lido pelo
`systemd --user` no login. Não precisa de root, vale para todas as aberturas depois de um único
relogin, e desmarcar apaga o arquivo (a checagem volta no próximo login). O texto do cartão diz que a
variável vale para a sessão inteira — é uma escolha do usuário, não um padrão.

**Causa raiz das janelas «minimizadas».** Numa sessão KDE Wayland o app é um cliente XWayland que
normalmente **não** está ativo (o usuário estava sobre outro programa). Uma janela criada nesse
estado não é elevada pelo KWin e fica na barra de tarefas. Não reproduzi o caso em sessão isolada
(`_NET_WM_STATE_FOCUSED`, 480×572 em +765+174), então a correção é preventiva e explícita:
`WindowActivation.kt` limpa o bit `ICONIFIED` e pede ativação (`isAutoRequestFocus`, `toFront`,
`requestFocus`) nas janelas que o usuário pediu — Resultado, Busca Rápida e a janela principal quando
aberta pela bandeja. O cartão do hover continua proibido de roubar foco.

**Testes e mutações.** 372 testes desktop, 0 falhas (1 pulado). `KdeCapturePermissionTest` (6):
escreve a variável documentada, apaga ao desligar, ignora arquivo alheio, `=0` não conta, escrever
duas vezes é idempotente, relogin pendente até a sessão carregar a variável. `WindowActivationTest`
(2): o bit `ICONIFIED` sai e o `MAXIMIZED` fica. Mutações derrubadas: «escrever a variável errada» e
«não limpar o bit de minimizado».

**Limitações.** A elevação das janelas e o cartão novo não foram vistos na tela nesta rodada (a
verificação visual depende da sessão real); a evidência é a suíte, as mutações e o exame por
`xprop`/`xdotool` que não reproduziu o minimizado. O efeito da variável só aparece depois de sair e
entrar na sessão. Nada foi commitado.

### D62 — Hover no GNOME abrindo uma sessão de captura por leitura  `[x]` (15/11/2026)

**Relato.** Depois do relogin a extensão aparece como `State: ACTIVE` e dona de
`dev.yabai.GnomePointer`, o app liga o hover (sem o aviso de relogin), mas nenhum texto aparece; a
cada tentativa surge um ícone/indicador do sistema na barra e a leitura demora vários segundos.

**Causa raiz.** `PlatformServices.captureBackendFor` mandava o GNOME Wayland para
`ScreencastScreenCapture` — `Robot` sobre o portal `ScreenCast` do JDK. O GNOME não reaproveita o
*restore token* do JDK, então **cada** captura abria uma sessão nova: indicador de compartilhamento
do Shell, escolha de fonte e segundos de espera. Quando a imagem chegava, o ponteiro sintético já
tinha se movido além de `STALE_RADIUS_PX` (56 px) e o resultado era descartado; sem cartão, o usuário
só via o ícone piscando.

**Correção.** No GNOME Wayland a captura passa a usar `PortalScreenshotCapture`:
`org.freedesktop.portal.Screenshot` com `interactive=false` e `modal=false`, uma chamada por leitura,
sem sessão, sem diálogo e sem indicador. Medido nesta máquina: **128 ms** até o PNG existir e
1280×800 lidos; o app recorta a região do ponteiro e apaga o arquivo (o portal sempre salva o
workspace inteiro). `captureBackendFor` ganhou o parâmetro `gnomeCapture` (genérico para qualquer
compositor que honre o portal) e o KDoc de `PortalScreenshotCapture` deixou de ser «só KDE».

**Por que não um ScreenCast persistente.** Seria manter um PipeWire e um token que o GNOME não
reusa pelo JDK — estado, fio e erro a mais para o mesmo recorte. O portal `Screenshot` é o caminho
one-shot que o próprio GNOME usa para a tecla Print.

**Testes e mutações.** 382 testes desktop, 0 falhas (1 pulado).
`ScreenCaptureBackendTest > gnome wayland uses the one-shot screenshot portal instead of the JDK
screencast` e `other wayland desktops keep the screencast backend`; mutação «GNOME volta ao
ScreenCast» derruba o primeiro. E2E no AppImage: hover ligado pelo menu da bandeja sem aviso,
`[Hover] Modo Hover ativo (ponteiro do GNOME Shell)`, e um `CursorMoved` forjado no `dev.yabai.Ocr`
executou a captura pelo portal (o PNG apareceu e foi apagado pelo app) e seguiu o pipeline até a
checagem de dicionário.

**E2E do cartão (15/11/2026).** Com Jitendex PT-BR instalado, o ponteiro forjado sobre「です」mostrou
o cartão (`です 【です】 · jitendex PT-BR [2026-09-29]` com acepções e exemplo) sobre a página, e uma
leitura completa — captura, OCR e lookup — levou **593 ms** com `[Hover] が — Jitendex PT-BR` no log.
O `Cannot create Linux GL context` do Skiko nesta VM (VirtualBox sem 3D) aparece no log, mas o
Skiko cai para software e a janela `Yabai OCR - Hover` mapeia normalmente (460×178, `IsViewable`,
`_NET_WM_STATE_ABOVE`); a mesma leitura com `SKIKO_RENDER_API=SOFTWARE` mostra o mesmo cartão.

**Limitações.** A escala fracionária continua sem teste aqui (monitor em 1.0). A latência depende
do motor OCR: sem os modelos do PP-OCR o app usa o Lens (rede), então a primeira leitura depois de
um tempo parado pode passar de um segundo. Nada foi commitado.

### D63 — Dicionários do JMdict falhando por publicação vazia do upstream  `[x]` (15/11/2026)

**Relato.** No Gerenciador de Dicionários, JMdict (inglês), JMnedict (nomes próprios) e KANJIDIC
(inglês) falham; Jitendex, ggjz.org, os três do Wikcionário e as onomatopeias instalam.

**Causa raiz.** A publicação `2026-11-14` de `yomidevs/jmdict-yomitan` é um build quebrado do lado
de lá: `JMdict_english.zip` tem **2303 bytes** com apenas `index.json` (título «JMdict [unknown]») e
`tag_bank_1.json`; `JMnedict.zip` 1682 e `KANJIDIC_english.zip` 1369 — nenhum `term_bank_*.json` nem
`kanji_bank_*.json`. O `DictionaryCompiler` recusa com «Nenhum verbete encontrado», que para quem
está instalando o primeiro dicionário parece defeito do app. A publicação anterior (`2026-11-13`)
tem os assets completos (15.628.711 / 11.432.840 / 721.032 bytes), e o repositório alterna builds
vazios e completos (12/11 vazio, 11/11 completo) — fixar um tag envelheceria.

**Correção.** `DictionaryReleaseFallback.kt`: `yomitanArchiveHasEntries` exige um
`term_bank_*`/`kanji_bank_*.json` de pelo menos 2 KiB; `releaseDownloadTarget` reconhece as duas
formas de URL de release do GitHub; `fallbackAssetUrls` lista até 5 publicações anteriores que têm
o **mesmo asset** com pelo menos 64 KiB; `dictionaryDownloadUrls` monta a lista primária + fallbacks.
O instalador baixa a candidata, valida o ZIP e só então compila — se todas falharem, a mensagem é
«Nenhuma publicação recente de X tem verbetes agora. Tente de novo mais tarde.» (pt/en). O
`temp_<id>.zip` agora é apagado num `finally`: as tentativas anteriores deixaram três arquivos no
`~/.yabai_ocr/dicts`.

**Testes e mutações.** `DictionaryReleaseFallbackTest` (6): URL `latest` e `download/<tag>`,
stub × dicionário real × dicionário só de kanji, arquivo que não é ZIP, ordenação e filtro das
publicações anteriores, primária primeiro. Mutações derrubadas: «todo ZIP tem verbetes» e «tamanho
mínimo do asset ignorado». E2E com rede: o `temp_jmdict-en.zip` de 2303 bytes que sobrou na máquina
reprova em `yomitanArchiveHasEntries`; o asset `2026-11-13` baixado (15.628.711 bytes) compila em
**527.778 verbetes** e reabre pelo `CompactDictionary` com a mesma contagem.

**Limitações.** A checagem de atualização continua comparando assinatura (ETag/Last-Modified/tamanho)
com a publicação mais recente, então um dicionário instalado do `2026-11-13` pode aparecer como
«atualização disponível» enquanto o topo estiver quebrado; instalar de novo rebaixa o mesmo
`2026-11-13` (passa pelo fallback) em vez de falhar. Os três dicionários em si não foram instalados
por mim na pasta do usuário: a correção ficou provada pelo teste ao vivo e ele pode instalar pela
UI. Nada foi commitado.

### D64 — Revisão do fallback, atalhos no GNOME pela extensão e quatro regressões do dia  `[x]` (15/11/2026)

**Revisão com olhos frescos (D63).** Dois leitores de assinatura divergentes: o instalador gravava
`signature.properties` dentro do `*.yabai`, e o verificador de atualização lia `<nome>.sig` ao lado do
arquivo — arquivo que nunca existiu. Como consequência, a checagem considerava todo dicionário
«sem assinatura» e oferecia atualização sempre. Além disso `checkUpdates` filtrava `it.isFile`, e um
`.yabai` é **diretório**: a checagem periódica não via nenhum dicionário instalado. Consolidado em
`DictionarySignature.kt` (etag, lastModified, tamanho e tag da publicação), com um leitor e um
escritor para os dois lados. `DictionaryReleaseFallback.kt` ficou com uma única leitura de assets
(`usableReleaseAssets`) usada por download, fallback e checagem; o instalador perdeu o índice não
usado e o `var` desnecessário.

**Atualização que rebaixava a mesma versão.** A checagem comparava tamanho/ETag do asset `latest`,
que é justamente o stub vazio de 2303 bytes: quem instalou o fallback de `2026-11-13` recebia
«atualização disponível» para a publicação vazia de `2026-11-14`, e baixar de novo reinstalava o
mesmo `2026-11-13`. Agora a instalação grava a tag (`releaseTagFromUrl`) e a checagem compara
publicações (`dictionaryUpdateAvailable`): tag igual = em dia; instalações antigas sem tag caem na
comparação por tamanho. O mesmo helper serve o botão «Verificar atualizações» e a checagem periódica.

**Quatro relatos do usuário (build do GitHub) e o que cada um era.**

1. **«Yabai OCR is already running» logo após atualizar pelo próprio app.** O novo AppImage sobe
   enquanto o antigo ainda está saindo, e a trava de arquivo sobrevive alguns instantes ao `main`.
   `acquireInstanceLockWithRetry` espera até 3 s (12 tentativas de 250 ms) antes de declarar segunda
   instância; uma segunda instância real continua recusada.
2. **Atalhos globais com `UnknownMethod: no such interface org.freedesktop.portal.GlobalShortcuts`.**
   Correto na mensagem, inútil na prática: o GNOME 46 não implementa esse portal. No GNOME os
   atalhos passam a vir da **mesma extensão** que já entrega o ponteiro: `HotkeyBackendKind.GNOME`
   (sem portal), `ShortcutsDbusObject.ShortcutBindings()` publica `id → acelerador do GNOME`
   (`gnomeAccelerator`: «Ctrl + Shift + O» → «<Control><Shift>o», F1–F12 com maiúscula), e a extensão
   registra com `Main.keybindingManager.addHotKey`, chamando `ShortcutPressed(id)` de volta a cada
   acionamento e relendo os aceleradores a cada 5 s para pegar uma edição nos Ajustes. Exige **um**
   relogin para o Shell carregar o `extension.js` novo.
3. **Hover dizendo «a permissão de captura foi negada» sem nunca ter pedido.** O código 2 do portal
   (`other error`) era mapeado como `DENIED`. Agora só o código 1 (diálogo cancelado) é negação;
   qualquer outro falha como `UNSUPPORTED` com a mensagem «falhou no portal do sistema (código N).
   Nenhuma permissão foi pedida» — que é o texto certo para procurar o problema no log. O hover em si
   já não usa ScreenCast no GNOME desde a D62.
4. **Ícone genérico na bandeja.** O item SNI não publicava `IconName` e o PNG só era instalado no
   «Integrar ao sistema»; sem o arquivo no tema hicolor o AppIndicator desenhava o engrenagem
   genérica. Agora `IconName = yabai-ocr` e `ensureTrayIcon()` grava o PNG a cada início no Linux.

**Testes e mutações.** 395 testes desktop, 0 falhas (1 pulado). Novos: regra de atualização
(tag igual, tag nova, sem tag por tamanho), round-trip da assinatura, `usableReleaseAssets`,
`newestReleaseFor` com fetch falso, espera da trava, `portalCaptureFailure`, `IconName`,
`ensureTrayIcon`, conversão de aceleradores e contrato do `extension.js`. Mutações derrubadas:
ignorar a tag na comparação; uma única tentativa de trava; mapear todo código de portal como
negação; `IconName` vazio; GNOME voltando ao portal; acelerador sem conversão.

**Limitações.** Os atalhos no GNOME só entram depois de sair e entrar (o Shell já carregou o
`extension.js` antigo nesta sessão); não houve prova visual deles aqui. O ícone novo aparece no
próximo refresh da bandeja. Nada foi commitado.

### D65 — `Properties.Get` do SNI devolvia o tipo cru e o AppIndicator desenhava a engrenagem  `[x]` (15/11/2026)

**Sintoma.** Mesmo com `IconName = "yabai-ocr"` publicado (D64), o GNOME continuava mostrando a
engrenagem; e `busctl get-property` no item respondia
`Error Executing Method org.freedesktop.DBus.Properties.Get: object is not an instance of declaring class`.

**Como o AppIndicator lê.** O `dbusProxy.js` da extensão do Ubuntu expõe `getProperty`/`refreshProperty`,
que chamam `org.freedesktop.DBus.Properties.Get` por propriedade com tipo de resposta esperado
`(v)` (`TUPLE_VARIANT_TYPE`); `GetAll` é usado só no `getProperties`. A issue #628 do projeto mostra
o efeito: um `Get` que falha derruba o proxy e o ícone cai no `FALLBACK_ICON_NAME`. Ou seja: **lê por
`Get`**, e a assinatura importa.

**Causa raiz.** `@DBusBoundProperty` no `dbus-java` 5.1.1 responde ao `Get` com o tipo do getter, não
com um variant: `dbus-send --print-reply` devolvia `string "yabai-ocr"` onde a spec (e o GDBus)
esperam `variant string "yabai-ocr"`. O `GetAll` já sai certo porque o handler monta `a{sv}`. O
`busctl` só era o mensageiro: ele recusa a resposta e o GJS também, cada um com sua mensagem.

**Correção.** O item SNI passou a publicar `org.freedesktop.DBus.Properties` por conta própria
(`Get`/`Set`/`GetAll` retornando `Variant`, com `Set` recusado por ser somente leitura). As
getter continuam na interface, marcadas `@DBusIgnore` para não serem exportadas como métodos (um
`List` de retorno quebra o `exportObject`), e um `@DBusProperties` mantém a introspecção com os
tipos concretos (`a(iiay)` no `IconPixmap`). O `dbus-java` não intercepta mais nada: sem
`@DBusBoundProperty` ele delega ao método do objeto.

**Prova.** `dbus-send` contra o app rodando: `variant string "yabai-ocr"` (antes: `string`), `GetAll`
com `a{sv}` e `gdbus` devolvendo `(<'yabai-ocr'>,)`. 399 testes desktop, 0 falhas (1 pulado).
Novos: `Get` de todas as propriedades devolve `Variant` com as assinaturas `s`/`o`/`i`/`b`/`a(iiay)`,
propriedade ou interface desconhecida recusada, `GetAll` com os mesmos variants e
`no property is published as a dbus-java bound getter` (o invariante que impede a regressão).
Mutações derrubadas: `Get` devolvendo o valor cru em vez do variant (2 testes), `IconName` fora do
mapa (4 testes, inclusive o smoke test no barramento real) e `@DBusBoundProperty` de volta no
`getIconName` (4 testes).

**Limitações.** A verificação visual do ícone na bandeja continua dependendo do Shell recarregar o
item; a prova aqui é o envelope D-Bus que o host consome. Nada foi commitado.

### D66 — Propriedades do SNI numa fonte só e o `StartupWMClass` que faltava no ícone  `[x]` (15/11/2026)

**Duas listas de propriedades.** A D65 deixou o nome e o tipo em `@DBusProperties` (introspecção) e
os valores num `propertyVariants()` literal: adicionar uma propriedade exigia editar as duas, e
esquecer uma delas só aparecia em tempo de execução, num host específico. Agora a anotação é a única
fonte: `propertyVariants()` percorre `@DBusProperties`, chama `get<Name>()` por reflexão e monta o
`Variant` com a assinatura declarada (`Marshalling.getDBusType`, a mesma que a introspecção usa).
Propriedade sem getter ou com tipo que não serializa falha alto na hora da leitura, não em silêncio.
Um `DBusPath` é convertido para a string do caminho (o `o` vem da anotação).

**O ícone continuava engrenagem.** O PNG e o `.DirIcon` do AppImage são o ícone certo (hash igual ao
recurso empacotado), então o problema era associação: `xprop` mostra que a janela AWT carrega
`WM_CLASS = dev-yabai-ocr-desktop-MainKt`, e nenhum `.desktop` declarava `StartupWMClass`. Sem isso
o GNOME Shell não liga a janela à entrada e desenha a engrenagem no dock. Além do `StartupWMClass`
(entrada do menu, entrada de autostart e o `.desktop` dentro do AppImage), o `ensureTrayIcon()` agora
**atualiza uma entrada existente** — a do usuário apontava para um AppImage antigo e não tinha o
campo — sem criar entrada nova (isso continua sendo o "Integrar ao sistema"). O ícone só é reescrito
quando os bytes mudam (antes bastava o mesmo tamanho para não atualizar).

**Testes e mutações.** 403 testes desktop, 0 falhas (1 pulado). Novos: a anotação é a fonte única
(nomes e assinaturas iguais aos da introspecção), `StartupWMClass` nas duas entradas e a entrada
velha sendo corrigida com o AppImage atual. Mutações derrubadas: uma propriedade na anotação sem
getter (**6 falhas**), `StartupWMClass` fora da entrada e o refresh da entrada removido (**2 falhas**).

**Limitações.** Continua sem prova visual do dock/bandeja aqui; a associação é verificada pelo
`WM_CLASS` real e pelo conteúdo das entradas. Nada foi commitado.
### D67 — O atalho do GNOME morria num `TypeError` silencioso e o `WM_CLASS` passou a ser derivado  `[x]` (15/11/2026)

**Revisão da D66.** As duas mudanças se sustentam: a anotação é mesmo a fonte única das propriedades
do SNI (o `Get` responde `variant` e o `AppIndicator` desenha o ícone) e o `ensureTrayIcon()` apenas
reescreve a entrada quando os bytes mudam. Duas arestas: o getter é achado por convenção (`get<Name>`),
então uma propriedade renomeada sem renomear o getter falha alto na leitura — coberto por teste — e o
`StartupWMClass` era uma constante repetida em três lugares (entrada do menu, autostart e o `.desktop`
dentro do AppImage), que quebraria no dia em que a classe `main` fosse renomeada.

**`WM_CLASS` derivado.** `wmClassForMainClass(sun.java.command)` (dots → dashes, `null` para jar/vazio)
e `runningWmClass()` alimentam o `LinuxSystemIntegration` e o `LinuxAutostart`; quando a classe não é
conhecida a entrada **omite** `StartupWMClass` em vez de publicar uma string errada. O literal que
sobra no `build-appimage.sh` é o do processo empacotado e está amarrado por teste.

**O atalho que não fazia nada.** A extensão chamava `Main.keybindingManager.addHotKey`, dentro de um
`try/catch` que engolia o erro — e o `main.js` do GNOME Shell 46 **não exporta `keybindingManager`**
(só `wm`, `extensionManager`, `panel`, …), então *toda* tentativa de registro era um `TypeError`
descartado: nenhum atalho registrado, nenhum erro no app, nenhum log. Agora a extensão usa o par que o
próprio Shell usa no `shellDBus.js`: `global.display.grab_accelerator(accel, NONE)`,
`Meta.external_binding_name_for_action`, `Main.wm.allowKeybinding(name, Shell.ActionMode.ALL)` e o
sinal `accelerator-activated` do display; o `ungrab_accelerator` libera no `disable()`/refresh. Falha
de grab (combinação já tomada) vai para o journal como erro, não em silêncio. As mensagens usam
`console.log`/`console.error`, que aparecem no journal do GJS.

**O que foi testado de verdade.** Com o app aberto (`:desktop:run --args="--minimized"`,
`dev.yabai.Ocr` no barramento), `ShortcutPressed(capture-fullscreen)` abriu a janela
`Yabai OCR - Escolher trecho` (1440x716) — o lado do app responde. A extensão instalada é carregada
pelo Shell e reporta `ACTIVE`. **Não foi possível sintetizar a tecla física**: não há
`wtype`/`ydotool`/`xdotool`, injeção no Wayland exige `/dev/uinput` com privilégio de administrador, e
o Shell 46 mantém o módulo ESM já carregado em cache (`ReloadExtension` devolve `NOT_SUPPORTED`),
então o `extension.js` novo só roda no próximo login. O `GrabAccelerator` por D-Bus é recusado
(`AccessDenied`) para quem não é `org.gnome.Settings`, o que confirma por que o registro precisa
acontecer dentro do Shell.

**Testes e mutações.** Suíte desktop verde (contada em 404 na época; o número real é 517 — o contador
só lia parte dos XMLs, ver D84). O teste de contrato da extensão passou
a exigir `grab_accelerator`/`allowKeybinding`/`accelerator-activated`/`ungrab_accelerator` e a
**proibir** `Main.keybindingManager`; a derivação do `WM_CLASS` ganhou um caso com outra classe
(`com.example.Other`), porque só `MainKt` passaria com uma constante fixa. Mutações derrubadas: voltar
a chamar `Main.keybindingManager.addHotKey` (**1 falha**) e trocar a derivação por uma constante
(**1 falha**).

**Limitações.** A prova do atalho de ponta a ponta depende de um login e de uma tecla física; o ícone
no **Nautilus** continua sendo o MIME genérico (`application/x-executable`, a engrenagem) mesmo com o
PNG certo em `~/.local/share/icons/hicolor/256x256/apps/yabai-ocr.png`: o Nautilus não consulta
`StartupWMClass`/`Icon=` de entradas `.desktop` para arquivos soltos. No SteamOS o Dolphin lê o
`.DirIcon` do AppImage (o mesmo PNG, hash `44c98976…`), então lá o ícone deve sair certo. Nada foi
commitado.

### D68 — Paisagem com a imagem ligada abria o painel só com cabeçalho e rótulo  `[x]` (15/11/2026)

**Relato.** Em jogos DMM em **paisagem**, tocar numa caixa do picker abria o painel «Texto reconhecido»
com o cabeçalho (título, «Google Lens · 4857 ms · ja», chevron, ✕) e o rótulo «Texto reconhecido (toque
para corrigir; a análise se atualiza)» — e **mais nada**: nem editor, nem «Copiar original», nem
«Fechar», nem «Imagem». O app continuava vivo (nada de exceção), e o mesmo jogo em retrato, com a tela
dividida, abria o painel completo. O print mostrava o cartão preenchido de `panel_bg` até o fim da tela.

**Causa raiz.** O caminho não é o OCR nem o picker: é a **imagem ao lado do texto** (`showImage`, o botão
«Imagem», que grava um ajuste e vale para as capturas seguintes). `freeHeightForImage()` devolve o que
sobra da altura depois do cabeçalho, do texto e das ações — e numa janela curta (paisagem) sobra **zero**.
O `CappedImageView` passava esse zero como `AT_MOST(0)` para o `ImageView`; com `adjustViewBounds` e
`maxHeight`, o cálculo de proporção do `ImageView` divide a altura vazia e **estoura**: mediu
`16 777 198 px` de largura (reproduzido em Robolectric NATIVE: `cap=0 -> 16777198x0`). A linha do texto
dava essa largura à imagem, e o `EditText` (peso 1) ficava com **0 px** de largura — presente, editável,
invisível — mas com 1122 px de altura. O cartão virava uma parede de `panel_bg` e, maior que a janela, o
`LinearLayout` espremia o último filho até zero: o `ActionGrid` desaparecia inteiro. É o mesmo mecanismo
do B1 (LinearLayout encolhendo quem não cabe), agora alimentado pela imagem.

**O que mudou.** Uma guarda no `CappedImageView.onMeasure`: tampa menor ou igual ao padding vertical da
própria view significa que não há altura para um pixel de imagem — mede `0×0` e não desenha. É a regra
que o painel já seguia («a imagem é a única parte que pode ceder sem nada ficar inalcançável»): em
paisagem o texto e os botões aparecem e a imagem simplesmente não tem espaço. Nenhum outro arquivo de
produção mudou.

**Evidência numérica** (Robolectric, `w780dp-h360dp-xxhdpi`, janela 2340×1080, imagem 320×90, texto
`職員さんに聞いたら`):

| | antes | depois |
| --- | --- | --- |
| `CappedImageView` | `16777198×0` | `0×0` |
| `EditText` | `0×1122` (invisível) | `2196×101` |
| `ActionGrid` | `2196×0` (nenhum botão) | `2196×488` (4 linhas de `101 px`) |
| cartão | maior que a janela | `810 px` de `1080` |

**Testes e mutações.** 418 testes do `:app`, 0 falhas (eram 416). Novos, em `ResultOverlayLayoutTest`:
`a short landscape screen keeps the text and the actions with the picture on` — paisagem de celular,
imagem ligada, mede a janela inteira e exige `editor.width > 0` e **todos** os botões com altura `> 0` —
e `the picture still shows when the screen has room for it`, que impede a guarda de virar um painel que
nunca mostra imagem. Mutação derrubada: removida a guarda do `CappedImageView`, o primeiro falha com
«the text field was squeezed to 0px wide» (1 falha); restaurada, passa.

**Limitações.** Sem aparelho/emulador aqui: a reprodução é Robolectric com as métricas do celular, não um
print do jogo. O caso exige o ajuste «Imagem» ligado — com ele desligado o `CappedImageView` nem entra na
linha e o painel em paisagem nunca quebrou. Em telas ainda mais curtas que 360 dp (janela dividida em
paisagem), o `ActionGrid` continua sujeito ao encolhimento do B1; não foi mexido nesta entrada. Nada foi
commitado.

---

### D69 — «Tentar offline» saiu do painel e a grade de ações caiba na paisagem  `[x]` (15/11/2026)

**Relato.** Com o painel em paisagem já abrindo (D68), o usuário viu que **faltava o último botão** — o
«Tentar offline», cortado embaixo — e pediu três coisas: remover esse botão (não faz sentido para ele),
garantir que nada fique cortado embaixo com o celular deitado e reduzir os botões, que em paisagem
«cobrem quase todo o espaço útil» e atrapalham a leitura dos significados.

**Causa raiz.** O botão «Tentar offline» era a sétima ação do painel. A grade era de **duas colunas**, então
sete ações davam quatro linhas de `101 px`; com a imagem ligada, o cartão passava da janela de `1080 px`
em cerca de `77 px` e o `LinearLayout` cortava o último filho — exatamente o «Tentar offline». Mesmo sem a
imagem, seis ações em duas colunas gastavam três linhas (`369 px`) sobre o texto que o usuário estava
lendo; em paisagem isso é quase um terço da tela.

**O que mudou.**

- `ResultOverlay.showResult` não recebe mais `offlineAvailable` e não acrescenta o botão. O parâmetro saiu
  também de `OverlayController.showResult` e da chamada em `FloatingOcrService.showResultFor`, junto com o
  campo `lastForceOffline`, que só existia para essa conta. O «Tentar offline» **continua no cartão de
  erro** (`showError`), onde é o caminho de volta quando o motor principal falhou — é outra situação, e
  ela não tem grade.
- `ActionGrid` escolhe as colunas pela largura da tela: **três** quando
  `screenWidthDp >= 600` (paisagem), **duas** no retrato. Seis ações passam de três linhas para **duas**.
- `Widgets.applyGridStyle`, aplicado a cada botão que entra na grade: `13sp`, `includeFontPadding = false`,
  padding `10/6 dp` e `minHeight` de `30 dp`, no lugar de `14sp` e `14/10 dp`. Fecha em `90 px` de altura
  a densidade 3 (antes `101 px`), sem mexer nos botões que ficam fora da grade (cabeçalho, busca, cartão de
  erro).

**Evidência numérica** (Robolectric, `w780dp-h360dp-xxhdpi`, janela 2340×1080, texto longo + nota +
tradução + dicionário + imagem ligada):

| | antes | depois |
| --- | --- | --- |
| botão da grade | `101 px` (33,7 dp) | `90 px` (30 dp) |
| `ActionGrid` | `488 px` (4 linhas, com o «Tentar offline») | `228 px` (2 linhas de 3) |
| cartão | `~1157 px` — `77 px` além da janela | `897 px` de `1080` |
| última ação | cortada | dentro da janela |

**Testes e mutações.** 422 testes do `:app`, 0 falhas (eram 418). Novos, todos em
`ResultOverlayLayoutTest`: `a short landscape screen keeps every action inside the window` (pior caso com
imagem, exige todo botão com altura `> 0`, `bottom` dentro da janela e altura `<= 32 dp`),
`a phone held up keeps two actions on a row` e `a phone turned sideways puts three actions on a row`.
A lista esperada de ações em `the panel offers the actions it is meant to` e em `ImagePanelTest` perdeu o
«Tentar offline». Mutações derrubadas, uma por causa:

| mutação | teste que caiu |
| --- | --- |
| voltar a acrescentar o «Tentar offline» ao resultado | `the panel offers the actions it is meant to` e `the image is offered as a button only when there is one` |
| remover `applyGridStyle` do `ActionGrid` | ««Copy Original» is 101px tall, more than the 32 dp a grid action takes» |
| fixar as colunas em 2 | `a phone turned sideways puts three actions on a row` |

**Limitações.** Sem aparelho/emulador: as medidas são Robolectric com as métricas de um celular
`xxhdpi` em paisagem, não um print do jogo. Em janelas mais curtas que 360 dp (tela dividida em
paisagem) a grade compacta cabe melhor que a antiga, mas continua sujeita ao encolhimento do B1 — nada
além do acima foi mexido. Nada foi commitado.

---

### D70 — Ícone da notificação era um quadrado branco  `[x]` (15/11/2026)

**Relato.** A notificação «OCR ativo» mostrava um **quadrado branco** no lugar do ícone — o print do
usuário tem o círculo azul do sistema com o quadrado dentro.

**Causa raiz.** `FloatingOcrService.buildNotification` usava `R.drawable.ic_scan` como `setSmallIcon`. A
barra de status desenha o ícone **só pelo canal alfa**: `ic_scan.png` é 144×144 RGBA com alfa `255` em
**todos** os 20 736 pixels (fundo preto opaco, medido com Pillow). O sistema pintou cada pixel de branco —
o quadrado. O mesmo bitmap é o certo no balão do overlay e no seletor, onde a imagem inteira é desenhada,
então ele não podia ser trocado.

**O que mudou.** Novo vetor `res/drawable/ic_stat_scan.xml` (24 dp, só os colchetes de scan, fundo
transparente), usado no `setSmallIcon`. O ícone do `YabaiTileService` no manifest passou a ser o mesmo: o
Quick Settings também tinge o ícone do sistema, e um bitmap opaco virava o mesmo quadrado lá. `ic_scan`
continua no balão (`OverlayController`) e em nada mais.

**Testes e mutações.** Novo em `CaptureNotificationTest`:
`the status bar icon has transparency to be drawn from` — lê o id do ícone pequeno, recusa `ic_scan`,
desenha o drawable num bitmap `96×96` com `@GraphicsMode(NATIVE)` e exige mais de 25 % de pixels
transparentes. Mutações derrubadas: (1) `setSmallIcon(R.drawable.ic_scan)` → «the status bar still uses
the opaque picture drawable»; (2) um retângulo branco opaco somado ao vetor novo → «only 0px of 9216 are
transparent: the system draws the icon as a solid square». Restaurados, 422 testes verdes.

**Limitações.** O Robolectric confirma a transparência e o id do recurso, não a aparência da notificação
no aparelho. Não há aparelho aqui para conferir a barra de status.

### D71 — Painel deitado mais alto e bolha alcançando a borda do display  `[x]` (15/11/2026)

**Relato.** «Aumentar um pouco o espaço que o aplicativo cobre da tela na aba de reconhecimento quando o
celular está de lado — não quero que cubra 100% da tela, mas dá pra aumentar uns 5/10% pra cima e pra
baixo» e «a bolha flutuante parece ter um limite… na parte de baixo quando o celular está em pé e na
parte da direita quando o celular está deitado virado para a esquerda; gostaria de deixar a borda da
bolha chegar até onde tem os botões do aparelho».

**Causa raiz do painel.** `ResultOverlay.availableHeight()` subtraía a altura da barra de status **e** da
barra de navegação em qualquer orientação. Deitado, a barra de navegação fica na lateral: a altura dela
não rouba nada da altura do painel, mas era descontada igual — o card saía uma barra de navegação mais
baixo do que o espaço que a janela overlay pode ocupar.

**Correção do painel.** O cálculo virou `availablePanelHeight(displayHeight, statusBar, navigationBar,
landscape, topMargin)`: em landscape desconta só a barra de status. Retrato continua igual (o painel já
cabia). O ganho é a altura da barra de navegação, ~7-13% conforme a densidade — dentro do «5/10% a mais»
pedido.

**Causa raiz da bolha.** O limite do arrasto vinha de `resources.displayMetrics`, que é a área em que um
app desenha: a barra de navegação **já está fora** dela, no lado em que a orientação a coloca. A janela
da bolha é adicionada com `FLAG_LAYOUT_IN_SCREEN`, ou seja, suas coordenadas são coordenadas do display
inteiro — mas o clamp usava a métrica do app. Resultado: uma faixa morta exatamente com a largura da
barra de navegação embaixo (retrato) e à direita (deitado para a esquerda), justo onde ficam os três
botões do sistema, e a bolha não entrava ali.

**Correção da bolha.** `OverlayController.displayBounds()` lê o display inteiro (`Display.getRealMetrics`,
que funciona de API 26 ao 36 e no Robolectric; cai para `displayMetrics` se vier vazio) e o limite do
arrasto e o encaixe nas bordas usam esse retângulo: `clampBubbleTo(params, bounds)` e
`snapToEdgeIn(params, bounds)`.

**Testes e mutações.**

| teste | mutação que o derruba |
| --- | --- |
| `ResultOverlayLayoutTest > o painel deitado nao perde a altura da barra de navegacao` | voltar a descontar `navigationBar` em landscape → esperado 330, obtido 282 |
| `BubblePlacementTest > a bolha alcanca a borda do display inteiro` | trocar `bounds.right/bottom` por `displayMetrics.width/heightPixels` no clamp |

**Limitações.** O Robolectric não distingue métricas reais das do app, então o teste do clamp prende a
função (recebe o retângulo e precisa usá-lo); a leitura do display real só se prova no aparelho.
Nenhum dos dois ajustes foi verificado visualmente no celular.

**Resultado.** `:app` 424 testes, 0 falhas (os dois novos incluídos).

### D72 — Hover apontando para a palavra errada, D56 sem efeito em novels e o erro do AWT  `[x]` (15/11/2026)

**Relato.** Três sintomas com a mesma origem aparente: «Apareceu esse erro do primeiro print ao tentar
mudar de dicionário» (`Reentry into ignoringRedrawRequests is not allowed`), «Nas novels essa
configuração nova não parece estar surtindo efeito» e, nos prints 3 e 4, o mouse em cima de 納
mostrando 捉えて, em cima de 振 mostrando に — sempre a palavra *acima* do ponteiro.

**Causa raiz 1 — snap para a palavra vizinha.** Quando a linha tem verbetes mas nenhum cobre o
caractere sob o ponteiro, `select` caía no `snap` de até `TOKEN_SNAP_CHARS` (3): em 「…捉えて納」 com o
ponteiro em 納, a resposta era 捉えて, três caracteres acima. Num kanji no fim da coluna — exatamente
onde a palavra continua na coluna seguinte — o resultado era sempre o vizinho, e por isso parecia
pior quanto mais para baixo na página.

**Causa raiz 2 — fallback para outra coluna.** Sem `snap` cobrindo, o laço seguia para o próximo
candidato e podia responder com uma palavra de *outra* linha, várias colunas de distância (print 4: を
para o ponteiro em 迎).

**Causa raiz 3 — D56 preso ao bloco.** A travessia só usava `blocks[].pieces`. Nas novels o Lens
entrega cada coluna como um bloco próprio, então não havia vizinho para juntar e o recurso parecia
morto.

**Causa raiz 4 — AWT.** O cartão do hover redimensiona a própria janela (`window.setSize`) sempre que
o conteúdo muda — e mudar de dicionário muda o conteúdo. Com o menu «Selecionar Dicionário» (um popup,
ou seja, outra janela) abrindo ou fechando naquele instante, o `setSize` reentra na contabilidade de
redesenho do componente e o AWT lança «Reentry into ignoringRedrawRequests is not allowed» num diálogo
de erro.

**Correção.**

- `snapAllowed`: o snap só vale quando o caractere é pontuação/espaço. Kanji, kana e letras não
  adivinham — a leitura incerta continua incerta, como manda a regra de ouro.
- Uma linha que **tem** verbetes mas nenhum cobrindo o caractere encerra a busca: não se responde com
  outra linha. Uma linha **sem nenhum** verbete continua deixando o ponteiro passar (comportamento que
  já existia e está pinado por teste).
- `neighbourhoodOf`: usa `blocks[].pieces` quando o bloco tem duas ou mais peças; caso contrário usa os
  próprios `segments` em ordem de leitura, e a junção passa a ser decidida pela **geometria**
  (`adjacent`: mesma direção, faixa compartilhada, vão menor que 0,75 glifo) — a mesma regra do
  construtor de blocos. Duas colunas da mesma frase se juntam mesmo em blocos diferentes; dois balões
  continuam sem se juntar.
- `HoverTooltipWindow`: o estado do menu subiu para a janela e o redimensionamento roda em
  `SwingUtilities.invokeLater`, com `isDisplayable` conferido, e não roda com o menu aberto.

**Testes e mutações.** Três testes novos em `HoverTextSelectorTest` (kanji sem verbete não responde a
palavra de cima; linha com verbetes não responde de outra linha; colunas adjacentes em blocos
diferentes se juntam) e o teste de snap foi corrigido para usar pontuação (、) em vez de を — kana é
letra e não deve adivinhar. Mutações derrubadas: (1) `snapAllowed` sempre verdadeiro → falha o teste do
kanji; (2) remover o corte de fallback → falha «a line with entries does not answer from another line»;
(3) remover `adjacent` do vizinho anterior → falha «lines of different blocks are never joined»;
(4) aceitar bloco com uma única peça → falha «adjacent columns in different blocks still join».
Restauradas: **412 testes, 0 falhas** no `:desktop`.

**Limitações.** O erro do AWT não foi reproduzido nesta máquina: a correção é a causa provável (o
`setSize` concorrendo com o popup) e o efeito só se confirma clicando no menu do cartão no aparelho do
usuário. `applySizeAndPlacement` deferido não tem teste (é uma janela Swing real). A escolha do
caractere continua usando divisão uniforme da caixa da linha: se o OCR reportar uma caixa que não
corresponde ao número de caracteres, o ponteiro pode cair um caractere ao lado — o snap e o fallback é
que não mascaram mais isso. Nada foi verificado visualmente no app.

### D73 — O menu de dicionário do cartão, a «reentrância» que voltou e a ordem das colunas  `[x]` (15/11/2026)

**Relato.** «O botão para alterar o dicionário não me permite selecionar nenhum, clico e é como se ele
nem existisse, o clique vai direto no que está atrás»; o «Reentry into ignoringRedrawRequests is not
allowed» voltou — agora ao trocar de dicionário pelo atalho; e a continuação da palavra entre colunas
continua sem ser reconhecida (os kanji, agora, são identificados corretamente).

**Causa raiz 1 — o menu era um popup.** O `DropdownMenu` do cartão é uma janela própria, posicionada
*abaixo* do botão. O cartão fica no rodapé da tela, então o menu abria fora da área visível e era
dispensado no mesmo instante: ficava «como se não existisse». Uma janela a mais para o AWT reconciliar
a cada abertura, ainda por cima.

**Causa raiz 2 — dois donos do mesmo tamanho.** O item anterior adiou o `setSize` do cartão, mas o
`WindowState` continuava carregando `height = estimatedHeight.dp`. Como o Compose **também** aplica o
tamanho do `WindowState`, cada mudança de conteúdo (trocar de dicionário, por atalho inclusive) redimensionava
a janela durante a composição — caminho que não passava pelo adiamento. Era esse `setSize` que o AWT
recusava com «Reentry…».

**Correção.** A lista de dicionários passou a ser desenhada **dentro do cartão** (o cartão cresce, a
lista sempre cabe na tela e não há segunda janela); e o `WindowState` virou um tamanho constante,
criado uma única vez, deixando `applySizeAndPlacement` como o único código que muda os limites da
janela — um evento depois. O `estimateHeight` agora conta as linhas da lista aberta, senão as últimas
ficariam cortadas (e cortadas = não clicáveis).

**Causa raiz 3 — ordem das colunas.** Em novela o Lens entrega cada coluna como um bloco próprio, então
a travessia do D56 caía no fallback `result.segments` — e usava a **ordem crua** do OCR. O próprio
`LensResultBuilder` documenta que «as colunas de um balão podem chegar como direita, meio, esquerda em
qualquer arranjo». Com a coluna apontada em primeiro lugar, «a próxima» era a coluna da *direita*, já
lida, e nenhuma palavra atravessava. A ordem agora vem da geometria (`ReadingOrder`, o mesmo objeto que
o corpo do projeto usa para montar as frases), e a adjacência continua exigindo encostar de verdade.

**Testes e mutações.** Um teste novo em `HoverTextSelectorTest` (colunas reportadas fora da ordem de
leitura ainda se juntam — falha se a ordem crua voltar: mutação aplicada e revertida) e dois em
`HoverTooltipHeightTest` (a lista aberta entra na altura; mais dicionários, cartão mais alto). **415
testes, 0 falhas** no `:desktop`.

**Limitações.** O «clique que atravessa» não foi reproduzido aqui: a explicação (popup abrindo fora da
tela) é a mais provável, mas se o clique também não chegar à lista desenhada dentro do cartão, a causa é
outra — nesse caso o próximo passo é investigar como o KWin está entregando eventos a esta janela
(`focusable = false`, transparente, always-on-top). A troca por atalho continua funcionando. Nada foi
verificado visualmente no app.

### D74 — O hover nas novels: o recorte não alcançava a coluna vizinha  `[x]` (15/11/2026)

**Relato.** «O hover ainda continua não funcionando com as novels» — os kanji já apareciam certos,
mas a continuação da palavra entre colunas não. E o aviso: não podia parar de funcionar o que já
funcionava, e manga/horizontal também precisava ser conferido.

**Como foi medido (sem chute).** Um probe temporário (`HoverPageProbeTest`, apagado no fim) renderizou
páginas de novela com o **texto real dos prints** e passou cada caractere pelo caminho de verdade:
recorte → `DesktopOcr.recognize` (Lens) → `HoverTextSelector.select` com os dicionários do usuário
(`~/yabai_ocr/dicts` + `E:\Documentos\DicionariosJapones`). Três páginas: colunas verticais, colunas
com furigana e um parágrafo horizontal.

**Causa raiz.** O recorte do hover é `planCrop(point, screen, 640, 360)`. Numa novela vertical a
palavra partida tem metade no **fim** de uma coluna e a outra metade no **começo** da coluna ao lado —
centenas de pixels acima. O recorte de 360 px de altura nunca continha as duas metades, então
`crossingMatch` não tinha vizinho para juntar, por mais correta que fosse a regra. Num ponto a 607 px
de altura, o recorte reconheceu só «こが、なにより救», «しそこにはだ»… — a coluna «いだった…» estava
fora, e a resposta era 救 sozinho.

**Correção.** `HoverGeometry.hoverCrop(pointer, screen)`: 640 px de largura por **toda a altura do
monitor**. É uma linha de mudança no `HoverOcrManager`, e a largura continua a mesma.

**Evidência do pipeline real (Lens, dicionários do usuário).** Mesmos pontos, dois recortes:

| página | ponto | 640×360 | altura inteira |
| --- | --- | --- | --- |
| vertical | 救 (fim da 1ª coluna) | 救 | **救い** |
| vertical | 振 (fim da coluna) | 振 | **振り向かせて** |
| vertical | り (início da coluna seguinte) | り | **振り向かせて** |
| com furigana | 救 / 振 / り | 救 / 振 / り | **救い / 振り向かせて / 振り向かせて** |
| com furigana | leituras | じゅうたん, だれか, すくい, かたわ | 絨毯, 誰か, 救い, 片端 |

Todos os outros caracteres das três páginas passaram a responder o mesmo que antes ou melhor (com
640×360, o 「──っ！」 respondia 短い; com a altura inteira responde っ). No horizontal, início e fim de
cada linha continuam respondendo o próprio (という, じゃ, ないか, 梅雨, ダルさ, いた, ああ, 思う).

**Testes e mutações.** Dois testes novos em `HoverGeometryTest`: o recorte precisa conter a metade
impressa no topo da coluna vizinha, e continua com 640 px de largura e a altura do monitor. Mutação
provada: voltar a altura para 360 derruba os dois; restaurado, verde. Também corrigido o corte dos
nomes na lista de dicionários: a linha tinha `height(26.dp)` fixo com 12 sp de texto e o nome aparecia
cortado; agora cresce pela própria linha, e a conta de altura usa 32 px por linha. **417 testes, 0
falhas** no `:desktop`.

**Limitações.** 1) O recorte maior inclui o próprio cartão do hover; ele fica ao lado do ponteiro e a
linha apontada tem distância 0, então nada mudou na prática, mas o cartão entra na imagem enviada. 2)
Com o texto juntado, um verbete que começa na coluna anterior pode vencer: em «…そこにはだ» + «らしなく»
o ponteiro em ら responde はだら (斑) — antes respondia らし (螺子), igualmente errado; é ambiguidade do
dicionário, não da geometria. 3) Não testei com Lens sobre as suas imagens reais (os prints do chat não
são arquivos): as páginas foram renderizadas com o texto deles. Se algum caso continuar errado, o
caminho é repetir o probe com o PNG da página. 4) Nada foi verificado visualmente no app.

### D75 — Hover no horizontal, ordem do texto reconhecido e o «Reentry» pela raiz  `[x]` (15/11/2026)

**Relato.** Nas novels funcionou. No horizontal (bloco de notas) reconhece até certo ponto e depois
para — «adicionei だが no começo e parou»; o erro «Reentry into ignoringRedrawRequests is not allowed»
ainda aparece ao trocar de dicionário várias vezes mexendo o mouse; e o texto reconhecido vem fora de
ordem («え入れる。» antes de «…血の絨毯がまた誰かを迎»).

**Medido, não deduzido.** Outro probe temporário (apagado no fim) renderizou uma página de bloco de
notas com a linha quebrada exatamente como no print — «だがこにはだ短い悲鳴が上がって、血の絨毯がまた
誰かを迎» / «え入れる。» — e passou o ponteiro em 迎 pelo caminho real (recorte → Lens → seletor com os
dicionários do usuário).

**Causa raiz 1 — o vão entre linhas.** As duas linhas ficam a 19 px uma da outra, cada uma com 25 px de
altura. O critério de adjacência (0,75 glifo, o mesmo do construtor de blocos) recusava: 19 > 18,75. Por
isso funcionava numa diagramação e não em outra — e mexer no texto mudava o ajuste da linha o suficiente
para virar o resultado. O hover agora julga vizinhança com **1,25 glifo**, que é o espaçamento normal de
um bloco de notas ou de um livro; dois balões de mangá ficam bem mais longe, e a palavra continua
tendo de ser mais longa que a da própria linha para vencer.

**Causa raiz 2 — a largura do recorte.** A metade que falta de uma palavra quebrada não fica abaixo do
ponteiro: fica na **margem esquerda da linha seguinte**, a mais de mil pixels de distância. Daí o
recorte passou a ser o **monitor inteiro** (antes: 640 px de largura; no item D74 já era a altura toda).

**Causa raiz 3 — ordem do texto.** `LensResult.joinLines()` concatenava na ordem em que o Lens devolve,
e o Lens devolve as colunas de uma página vertical «em qualquer arranjo». Agora a ordem vem das caixas,
com o mesmo `ReadingOrder` que monta os blocos — vale para o painel de resultado, para a cópia e para a
tradução.

**Causa raiz 4 — o «Reentry», enfim localizada.** Não é AWT nem o nosso `setSize`: está no Compose
(`SwingWindow.desktop.kt`), que ao tornar uma janela visível chama `renderImmediately()` de dentro do
próprio frame; o `SwingInteropContainer` recusa a renderização reentrante com essa mensagem, e o
tratador padrão responde com o diálogo de erro. O cartão do hover é criado exatamente quando aparece,
então trocar de dicionário e mexer o mouse (cartão nascendo e morrendo) cai nesse caminho. A correção
**não** é reescrever o Compose: a janela do cartão passou a usar um `WindowExceptionHandlerFactory`
próprio que descarta **somente** essa mensagem (a renderização que faltou acontece no frame seguinte) e
repassa todo o resto para o tratador padrão.

**Evidência do pipeline real (Lens + dicionários do usuário).** Bloco de notas com a linha quebrada:
recorte 640 → 迎; monitor inteiro → **迎え入れる**. Novel vertical re-verificada depois das mudanças:
救 → **救い**, 振 → **振り向かせて**, 絡 → 絡む (nenhuma linha perdida com o critério novo).

**Testes e mutações.** Quatro testes novos: palavra quebrada entre duas linhas com espaçamento comum
(`HoverTextSelectorTest`), recorte = monitor inteiro (`HoverGeometryTest`), ordem das colunas e das
linhas em `joinLines` (`LensResultOrderTest`, no `:core`) e reconhecimento da mensagem de reentrância
(`HoverTooltipHeightTest`). Mutações provadas e restauradas: vão de volta a 0,75 derruba o teste do
horizontal; `joinLines` sem `ReadingOrder` derruba os dois testes de ordem. **`:core` 197, `:desktop`
419, 0 falhas.** Também corrigido o corte dos nomes na lista de dicionários do cartão (linha com altura
fixa de 26 dp e texto de 12 sp).

**Limitações.** 1) O diálogo do «Reentry» deixou de aparecer, mas a renderização reentrante que o
Compose tenta continua existindo — o frame seguinte desenha o cartão; se algum dia o cartão não
aparecer, é aí que se olha. 2) O recorte do hover agora é a tela inteira: mais pixels por leitura
(~3× o anterior) e o próprio cartão entra na imagem enviada ao Lens. 3) O probe usa páginas
renderizadas com o texto dos prints (as imagens do chat não são arquivos); não rodei a GUI. 4) Nada
foi verificado visualmente no app.

### D76 — Ícones sem cor na bandeja, instalador que não fechava o app, «Recortar» escondendo o alvo e o modo «Trechos»  `[x]` (16/11/2026)

**1. O menu da bandeja sem cores (Windows).** Cada linha era um `JButton` cujo rótulo começava com
um emoji (`🖥️`, `✂️`, `🔍`…) e a fonte era a Segoe UI Emoji. O Swing não desenha emoji colorido: ele
pede o glifo à fonte como texto e o Windows devolve a silhueta monocromática, por isso o menu saía
branco. A correção não depende de fonte nenhuma: `TrayGlyphs.kt` desenha com Java2D um ícone de 16 px
por linha, cada um com a sua cor (monitor azul, tesoura laranja, lupa violeta, balão verde, livro
âmbar, engrenagem ciano, sair vermelho), e o rótulo perde o emoji antes de ir para o botão
(`withoutEmojiPrefix`) — o menu nativo do Linux continua recebendo o texto com emoji, é ele que o
desenha.

**2. O instalador não fechava o app — e o desinstalador também não.** O `installer.iss` não pedia
nada, e o pedido educado do Inno (Restart Manager) também não resolveria: com `close_to_tray=true` a
janela do app só se esconde, o processo continua vivo e os jars não podem ser substituídos. Agora
`CloseApplications=no` (o fechamento educado é declarado inútil, e não o é por acaso) e
`[Code] PrepareToInstall` roda `TerminateRunningApp` (`taskkill /IM "Yabai OCR.exe" /F`) antes de
copiar os arquivos, com 600 ms de folga para os handles caírem; o `[Run]` que já existia reabre o app
no fim. O desinstalador tropeçava no mesmo processo: o Inno só apaga depois do passo `usUninstall`, e
o app aberto mantém os jars e o próprio `.exe` abertos, então a remoção parava no meio. O mesmo
`TerminateRunningApp` passou a ser chamado em `CurUninstallStepChanged`, quando
`CurUninstallStep = usUninstall` (um `[UninstallRun]` seria tarde demais para os arquivos em uso).
O nome `"Yabai OCR.exe"` foi conferido contra a origem: `packageName = "Yabai OCR"` em
`desktop/build.gradle.kts` (ramo do host Windows) é o nome do pacote gerado, e há teste para os dois
lados não divergirem.

O autostart é do app, não do instalador: quem escreve **`YabaiOCR`** em
`HKCU\Software\Microsoft\Windows\CurrentVersion\Run` é `WindowsAutostart.kt` (`APP_NAME`,
`REG_KEY`, comando `"<exe>" --minimized`), e por isso o Inno não tinha nada para remover sozinho — a
entrada sobrevivia à desinstalação apontando para um `.exe` que já não existia. O `[Code]` do
desinstalador ganhou `RemoveAutostartEntry`, chamado no mesmo passo `usUninstall`, que apaga a chave
`Run` **e** a flag `...\Explorer\StartupApproved\Run` de mesmo nome (sem essa segunda, uma
reinstalação futura apareceria como «desativada» em «Aplicativos de inicialização»). O teste extrai a
chave e o nome do valor do próprio `WindowsAutostart.kt` — não de uma cópia escrita à mão no teste —
e exige que o `.iss` os apague no passo de desinstalação.

**3. «Recortar» no resultado capturava a própria janela.** O botão chamava `onClose()` e pedia a
captura na mesma hora, com a janela ainda no ecrã: o recorte pegava o que estava por baixo *e* a
própria janela, e o texto escondido por ela era impossível de recortar. Agora a janela é escondida
(`window.isVisible = false`), espera-se `RESULT_WINDOW_HIDE_DELAY_MS` (180 ms — um ou dois frames para
o compositor tirar a janela de cena) e só então se fecha e se abre a seleção.

**4. Modo «Trechos».** No picker de tela cheia, escolher um bloco fechava o picker e a captura era
esquecida; para ler outro parágrafo era preciso capturar a tela de novo. O `Main` passou a guardar a
captura e o scan (`snippetsCapture`, `pickerScanCache`) de onde o resultado veio, e a janela de
resultado ganhou o botão **Trechos** (`onBackToPicker`), que se esconde e reabre o picker com **a mesma
imagem e as mesmas caixas** — `SegmentPickerWindow` recebeu `initialScan`/`onScanReady` e, com o scan
guardado, não gasta uma segunda ida ao Lens nem muda as caixas debaixo do utilizador. O botão só existe
quando o resultado veio do picker; um recorte à mão não tem para onde voltar.

**Revisão das mudanças com olhos frescos.** Três coisas apareceram e foram corrigidas: (a) o scan
guardado para o modo «Trechos» era reaplicado a cada troca de modo de leitura dentro do picker, porque
o `LaunchedEffect(screenImage, readingMode, dictionaryFilter)` voltava a ler `initialScan` — agora o
scan guardado é consumido **uma vez**, e trocar de modo volta a reconhecer a tela; (b) varreduras que
**falharam** não são guardadas, senão reabrir o picker mostraria o mesmo erro para sempre; (c)
`WindowState` do Compose Desktop 1.12 não tem `isVisible` (o código nem compila com isso) — a ocultação
usa a janela AWT, que é descartada em seguida junto com o resultado; o comentário no código diz isso em
vez de deixar a dúvida.

As regras de (a) e (b) saíram da janela para `PickerScanMemory.kt`, que não depende de Compose:
`PickerScanMemory.consume()` entrega o `PickerScan.Ready` guardado no máximo uma vez, e
`reusablePickerScan()` transforma um scan terminado em algo reutilizável — só `Ready`, nunca `Failed`
nem `Scanning`. O `SegmentPickerWindow` ficou com `val scanMemory = remember { PickerScanMemory(initialScan) }`
e o `Main` guarda `PickerScan.Ready?` (`onScanReady = { pickerScanCache = reusablePickerScan(it) }`).
Isso permitiu testar as duas regras sem GUI, em `PickerScanMemoryTest` (5 testes: scan entregue uma vez
e a segunda chamada nula; falha nunca reutilizada; scan em andamento nunca reutilizado; só `Ready` é
guardável; a captura reaberta mantém os blocos).

**Correção de método (importante).** As provas de mutação das rodadas anteriores deste item estavam
**inválidas** por dois motivos, e foram refeitas: 1) o `gradlew.bat` era chamado por `subprocess` sem
shell no Windows, então a "falha" observada era `'.' não é reconhecido…` (código 1 do interpretador,
não teste vermelho); 2) depois disso, `:desktop:test` era servido **do build cache** (`Task
:desktop:test FROM-CACHE`), porque o `.iss` não é input declarado da tarefa — as mutações no script do
Inno nem chegavam a rodar os testes. Agora `desktop/build.gradle.kts` declara
`package/windows/installer.iss` como `inputs.file` (com `PathSensitivity.RELATIVE`), e as provas rodam
com `--no-build-cache`. Ainda no mesmo espírito: o teste do autostart passou a exigir a **declaração
inteira** (`AutostartRunKey = '...\Run';`) em vez de `contains`, porque `...\RunOnce` contém
`...\Run` e a versão antiga aceitava a mutação.

**Testes e mutações.** Oito testes novos em `TrayGlyphsTest`: os sete rótulos sem emoji, texto simples
intacto, cada glifo pintado com a sua cor saturada e distinta (pixels lidos do `BufferedImage`, não a
constante), o tamanho do ícone, o script do instalador (tem de dizer `CloseApplications=no`, chamar
`taskkill` com `/F` e fazê-lo em `PrepareToInstall`), o passo `usUninstall` do desinstalador a chamar o
mesmo encerramento, o nome do executável igual ao `packageName` do `:desktop` (lido do
`build.gradle.kts`, não escrito à mão no teste) e o desinstalador a apagar a entrada de autostart com
a chave e o nome do valor lidos de `WindowsAutostart.kt`. Mutações provadas e restauradas (cada uma derruba o seu teste): rótulo a manter o emoji,
`CloseApplications=yes` outra vez, glifo do monitor em cinzento, o encerramento do desinstalador
removido, `RemoveAutostartEntry` fora do passo de desinstalação, o nome do valor trocado para
`Yabai OCR`, a chave trocada por `RunOnce`, a chave do `StartupApproved` trocada, o `/IM` com um nome
que o empacotamento não gera, o scan entregue duas vezes (`consume` sem limpar) e qualquer scan aceito
como reutilizável. **`:core` 197, `:desktop` 432, 0 falhas.**

**Limitações.** 1) Ícones, instalador, desinstalador e janelas só se veem no Windows de verdade: aqui
compilei e testei o desenho dos ícones, os textos e o conteúdo do script, mas **não** abri o menu da
bandeja nem corri o `ISCC` (o Inno Setup não está instalado nesta máquina) — a sintaxe Pascal do
`[Code]` é a documentada (`PrepareToInstall`, `CurUninstallStepChanged`, `RegDeleteValue`,
`TerminateRunningApp`) e não foi compilada; a desinstalação com o app aberto e a remoção do autostart
não foram executadas. 2) O autostart só é apagado do `HKCU` do usuário que roda o desinstalador: se o
app foi ativado na conta A e o desinstalador roda na conta B, a entrada de A fica (limitação do
`PrivilegesRequired=lowest`). 3) O atraso de 180 ms é uma espera fixa, não uma confirmação de que o
compositor já tirou a janela; num sistema muito lento ainda pode escapar um frame. 4) O picker reaberto
com o scan guardado não relê a tela: se algo mudou por baixo, as caixas são as da captura original — que
é exatamente o que «trechos» significa. 5) Nada foi verificado visualmente no app.

### D77 — Catálogo sem os Wikcionários localizados, hover sem troca de dicionário, menu com rolagem e remoção que funciona  `[x]` (16/11/2026)

**1. Wikcionário pt e en fora do catálogo.** As entradas `wty-ja-pt` e `wty-ja-en` existiam no
catálogo do desktop (`DesktopDictionaryCatalog.kt`) e no do app (`app/dict/DictionaryCatalog.kt`) e
apresentavam as definições de forma errada nos dois idiomas; os dicionários que ficam já cobrem mais,
e quem quiser aqueles importa o arquivo de outra fonte. Só o monolíngue (`wty-ja-ja`) ficou — é o
único cujas definições são escritas na língua que se está aprendendo. O `ggjz-pt` (Jitendex traduzido)
continua. Dois testes de atualização do app usavam `wty-ja-pt` como fixture e passaram a usar
`wty-ja-ja` (o caso que eles exercitam — descritor antigo contra arquivo novo — não mudou).

**2. O hover não troca mais de dicionário sozinho.** Havia **dois** «fallbacks» em
`HoverOcrManager`: quando o dicionário escolhido não tinha o verbete, a busca era refeita em todos e o
cartão mostrava a definição de um dicionário que o usuário não havia escolhido, sem dizer nada. Os dois
saíram. A consulta foi para `HoverLookup.kt` (`dictionaryScope`, `scopedHoverHit`), que é o único lugar
que decide o escopo, e o caso «não tem aqui» vira um aviso **dentro do próprio cartão**
(`DesktopStrings.hoverDictionaryMissingEntry`, com `HoverStateHolder.missingEntryIn` e o parâmetro
`notice` de `HoverTooltipWindow`), mais o status na bandeja. A primeira tentativa abria a janela de
aviso do hover (`CaptureNoticeWindow`); ela foi descartada porque uma janela por leitura se abre em cima
do texto que está sendo lido e cobre justamente o que se quer ver — o cartão já está ali. `estimateHeight`
conta o bloco do aviso (`NOTICE_BLOCK_HEIGHT`), senão a mensagem seria cortada na base do cartão. Trocar
de dicionário pelo cartão e continuar sem verbete não recaptura a tela: a decisão saiu do Compose para
`cachedHoverAnswer` (em `HoverLookup.kt`, ao lado de `scopedHoverHit`), que devolve
`Hit`/`MissingEntry`/`NeedsLookup` a partir da leitura já guardada (`lastLensResult`), daí `showMissingEntry`
responder na hora. Sem filtro (ou só espaços) não há dicionário escolhido a quem atribuir a falta: aí, e só
aí, o ponteiro volta a ser lido. (O âncora do cartão saiu errado nesta rodada — nulo —, e é o D78.)

**3. A lista de dicionários do cartão encolheu e rola.** Ela era uma `Column` com todas as linhas
dentro do cartão, e o cartão crescia até o teto de 640 px: num install completo as linhas abaixo do
teto ficavam cortadas e não davam para clicar. Agora a lista tem no máximo 6 linhas
(`DICTIONARY_MENU_MAX_ROWS`) com `VerticalScrollbar`, e `estimateHeight` conta a lista limitada — o
cartão não cresce mais com o número de dicionários.

**4. «Remover» que não removia.** A causa: o cartão de cada dicionário abria o `CompactDictionary`
para ler título/revisão e só o fechava no fim da composição — no Windows um blob aberto é o que faz
`deleteRecursively()` ser um no-op, e a falha era silenciosa (o botão parecia morto). A metadata passou
a ser lida e fechada na hora (`CompactDictionary.open(file)?.use { it.info }`), e a remoção agora
**reporta** a falha (`DesktopStrings.dictRemoveFailed`). No catálogo, cada entrada instalada ganhou o
seu próprio botão «Remover», que apaga só `${entry.id}.yabai` (`installedDictionaryFilesFor`) — nunca
um dicionário importado à mão que esteja na mesma pasta. E o download ganhou **Cancelar**
(`DictionaryDownloadCancelled`, com `checkCancelled` no laço de 64 KB e antes de compilar; o `finally`
que já existia apaga o `temp_<id>.zip`, então um cancelamento não deixa lixo nem `.yabai` pela metade).

**Cancelar travava o cartão em «baixando».** O Cancelar só levanta a flag (`cancelRequested`) e o
download para no próximo bloco de 64 KB — de propósito, para não interromper um `read` no meio. Só que o
reset do cartão vivia num `finally` que fazia `withContext(Dispatchers.Main)`: assim que a tarefa era
cancelada, esse `withContext` lança na hora, o reset nunca acontecia e o cartão ficava eternamente em
«baixando», sem o botão de cancelar e sem nada rodando atrás. O ciclo de instalação foi para
`DictionaryInstallRun.kt` (`runCatalogueInstall` + `CatalogueInstallHooks`): `installed`, `cancelled`,
`failed` e `finished` são *suspend* (a produção os despacha para a UI) e `finished` roda em
`NonCancellable`, porque é o único que **precisa** acontecer mesmo com a tarefa cancelada. O relato de
cancelamento também roda em `NonCancellable`: o Cancelar cancela a tarefa *e* levanta a flag, então sem
isso o cartão se resetaria em silêncio, sem dizer «cancelado».

**5. A pasta da importação é lembrada.** `DesktopSettings.lastDictionaryImportDirectory`
(`last_dict_import_dir`) guarda a pasta do último arquivo importado; o `JFileChooser` do gerenciador
reabre nela, e ignora o valor se a pasta não existir mais.

**Testes e mutações.** Novos: `DictionaryCatalogEntriesTest` (pt/en fora, monolíngue dentro, entrada do
catálogo só apaga o que ela própria instalou, cancelamento interrompe no checkpoint),
`DictionaryInstallRunTest` (o cartão volta ao normal depois de um cancelamento — inclusive com a tarefa
já cancelada; cancelar é «cancelado», não «falha»; nada roda depois de instalar; erro reporta falha e
ainda reseta), `HoverLookupTest` (dicionário escolhido responde e nada mais é consultado; sem filtro
busca em todos; aviso nomeia o dicionário; troca de dicionário sem verbete responde da leitura guardada,
com verbete responde com ele, sem filtro ou com filtro de espaços volta a ler o ponteiro, e sem leitura
guardada não há o que responder), teto da lista e bloco do aviso em `HoverTooltipHeightTest`,
um de ida e volta em `DesktopSettingsTest` e um no `DictionaryCatalogTest` do app. Mutações provadas e
restauradas (cada uma derruba o seu teste): entrada `wty-ja-en` de volta ao catálogo, fallback
reintroduzido em `scopedHoverHit`, `minOf` do teto removido, `installedDictionaryFilesFor` devolvendo
todos os arquivos, `checkCancelled` sem efeito, o getter da pasta de importação fixo em `""`, o reset
fora do `NonCancellable`, cancelamento caindo no `catch` genérico, o aviso fora de `estimateHeight`, a
falta de verbete voltando a pedir captura, espaços tratados como dicionário escolhido e o `Hit` da leitura
guardada jogado fora. **`:desktop` 452, `:core` 197, `:app` 836, 0 falhas.**

**Limitações.** 1) Nada disto foi visto na GUI: o cartão, a rolagem, o botão do catálogo e o aviso
foram compilados e testados na lógica, mas não abertos no app. 2) O cancelamento só age entre blocos
de 64 KB (a leitura bloqueia); num link lento pode levar um instante. 3) Dicionários instalados antes
desta rodada continuam no disco — inclusive um Wikcionário localizado — e se removem na aba
«Instalados». 4) O aviso de «não tem o verbete» vive enquanto o cartão estiver aberto (é o próprio cartão que o
mostra); ao sair do texto ele desaparece com o cartão.

### D78 — O cartão de «sem verbete» piscava e aparecia em qualquer lugar da tela  `[x]` (16/11/2026)

**Sintomas.** Com um dicionário escolhido no hover, o cartão de «dicionário sem o verbete» 1) piscava
em vez de ficar parado sobre a palavra, e 2) aparecia e desaparecia em qualquer ponto da tela — menu,
parágrafo em português, área vazia — não só sobre texto japonês.

**Causa raiz (1).** `showMissingEntry` mostrava o cartão com `targetBox = null`. O `HoverDismissGate` usa
essa caixa como o alvo que mantém o cartão vivo: sem alvo, um ponteiro *parado em cima da palavra* conta
como «saiu», a carência de 220 ms vence, `dismiss()` fecha o cartão — e, pior, `dismiss()` faz
`gate.reset()`, que esquece o ponto já atendido. O `observe` volta a disparar sobre a mesma palavra, o
`lookupAt` repete o OCR, o cartão reabre e fecha de novo: o piscar. Um hit nunca teve esse problema
porque sempre passa a caixa do token.

**Causa raiz (2).** No caminho de erro o `lookupAt` só olhava `result.joinLines().isBlank()`. O recorte do
hover é o monitor inteiro (`HoverGeometry.hoverCrop`), então esse texto quase nunca está em branco: qualquer
tela com qualquer texto reconhecido — inclusive em português — e um filtro escolhido caíam em
`showMissingEntry`. Não havia nenhuma pergunta «existe uma palavra japonesa aqui?». Faltava também o
teste de ponteiro velho: o caminho do hit conferia `STALE_RADIUS_PX`, o do erro não, e um OCR lento
mostrava o cartão na posição antiga.

**O que mudou.** A decisão saiu do gerenciador para duas funções puras em `HoverLookup.kt`:

- `japaneseWordAt(result, ponto, lookup)` — a palavra japonesa sob o ponteiro, com a caixa dela, sem
  passar pelo dicionário escolhido. O ponteiro tem de estar **dentro** da caixa da linha: a tolerância de
  36 px serve para *responder* uma palavra, não para afirmar que uma palavra *falta* num dicionário; e o
  caractere na posição tem de ser letra japonesa (`JapaneseText.isKana`/`isKanji`, mais `々`) — pontuação
  não conta. A caixa é a do termo quando algum dicionário o conhece, e a da célula do caractere quando
  nenhum conhece: nos dois casos ela contém o ponteiro.
- `classifyHoverMiss(...)` devolve `NoText` (nada reconhecido), `MissingEntry(filtro, caixa)` (há palavra
  japonesa e o dicionário escolhido não tem) ou `NoMatch(texto)` (há texto, mas nenhum dicionário escolhido
  a quem culpar). Só o `MissingEntry` mostra cartão; os outros dois fazem o status de sempre e fecham.

O `MissingEntry` passou a carregar a caixa da palavra (`CachedHoverAnswer.MissingEntry(filtro, caixa)`, que
o `cachedHoverAnswer` da troca de dicionário reaproveita via `classifyHoverMiss`), e `showMissingEntry`
agora **usa essa caixa como `targetBox`** — o cartão fica parado enquanto o ponteiro está na palavra, e o
`gate` não volta a disparar no mesmo ponto. O caminho de erro ganhou o mesmo teste de ponteiro parado e de
`isEnabled` que o caminho do hit já tinha antes de abrir o cartão.

**Âncora em tela com escala.** A caixa do `HoverMiss` está nos pixels da imagem que o Lens recebeu, e
numa tela a 150% essa imagem é maior que o recorte. A troca de dicionário pelo cartão convertia a âncora
com `cropRect.width/height` enquanto o caminho do hit usava `image.width/height`: numa tela com escala a
âncora caía fora da palavra, o ponteiro parado na palavra contava como «saiu» e o cartão voltava a
piscar *nesse caminho*. O `HoverOcrManager` passou a guardar o tamanho real da última leitura
(`lastImageSize`, gravado junto com `lastLensResult`) e a troca de dicionário converte os boxes com ele;
`HoverGeometry.toScreen(box, crop, imageSize?)` é a conversão única, com o recorte como estimativa só
quando nada foi guardado. O caminho do hit também passou a usar o mesmo `imageSize`.

**Revisão com olhos frescos (simplificações).** As consultas ao motor estavam sendo montadas em dois
lugares (o `lookup` do `scopedHoverHit` e um lambda equivalente no `lookupAt`, mais um terceiro na troca
de dicionário); agora existe `hoverQuery(engine)`, um só lugar para o teto de 96 e o escopo, e o `lookupAt`
usa a mesma consulta para o hit e para o miss. O overload `scopedHoverHit(engine)` e o
`HoverTextSelector.select(engine)` ficaram sem uso depois disso e saíram — sobrou um único caminho de
consulta, com o lookup injetado. `japaneseWordAt` mede a distância uma vez por segmento (antes media
duas: na ordenação e no filtro).

**Testes e mutações.** Novos em `HoverLookupTest`: ponteiro sobre texto latino nunca é «sem verbete»;
ponteiro no vazio (longe, 15 px fora da caixa — dentro da tolerância — e 60 px abaixo da linha) fecha em
vez de abrir; a falta de verbete vem ancorada na célula do caractere e a âncora contém o ponteiro; com
essa âncora o `HoverDismissGate` não fecha o cartão nem com 60 s parado; a troca de dicionário pelo cartão
devolve `MissingEntry` com âncora. Mutações provadas e restauradas: qualquer caractere contado como
japonês; tolerância da linha usada no lugar de «dentro»; âncora trocada por um ponto de 1 px; falta de
verbete deixando de ser reportada. Em `HoverGeometryTest`, âncora convertida com o tamanho da imagem
reconhecida (2x: caixa reduzida à metade e deslocada para a coordenada de tela) e, sem tamanho guardado,
com o recorte; mutação: converter sempre com o recorte derruba o teste. O caso do `targetBox` em si
(gerenciador) não é testável sem Compose nem `Robot` — o contrato coberto é «o miss devolve a caixa da
palavra» **e** «com essa caixa o gate não fecha»; o mesmo vale para `lastImageSize`, coberto na conversão,
não no gerenciador.

**Limitações.** 1) Nada disto foi visto na GUI: a lógica está coberta, mas o cartão parado no app não foi
observado. 2) A âncora sendo a célula de um caractere (quando nenhum dicionário conhece a palavra) é menor
que a palavra inteira — se a caixa do OCR estiver deslocada, o ponteiro na borda pode cair fora e o cartão
fechar depois da carência. 3) O aviso continua só em `:desktop`; o app Android não tem hover.
**`:desktop` 457, `:core` 197, `:app` 836, 0 falhas.**

### D79 — O app morria (crash nativo) ao trocar de dicionário, no segundo monitor  `[x]` (16/11/2026)

**Sintoma.** Ao escolher outro dicionário na lista do cartão, com o hover num texto no segundo monitor, o
processo morria com `EXCEPTION_ACCESS_VIOLATION (0xc0000005)` em `skiko-windows-x64.dll` — sem exceção
Java, sem diálogo, o `:desktop:run` saindo com código 1. Dois `hs_err_pid*.log` da mesma sessão tinham a
**mesma** pilha, os dois na `AWT-EventQueue-0`:

```
PictureRecorder.finishRecordingAsPicture  (nativo)
SkiaLayer.update → AWTRedrawer.update → Direct3DRedrawer.renderImmediately
SkiaLayer.reshape → Component.setBounds → ComposeContainer.setBounds
ComposeWindowPanel.setBounds ← BorderLayout.layoutContainer ← Container.validate
Window.dispatchEventImpl (COMPONENT_RESIZED)
```

Isto é, o JVM caía **redimensionando** a janela do cartão, com o redrawer Direct3D renderizando de forma
síncrona de dentro do `reshape` do AWT.

**Causa raiz.** Redimensionar rápido uma janela Compose no Windows com o renderizador Direct3D derruba o
JVM — é bug conhecido do Skiko (`compose-multiplatform#4425`, `skiko#883`; a saída documentada é
`-Dskiko.renderApi=SOFTWARE`), e o cartão é justamente uma janela `undecorated` + `transparent`. O nosso
código piorava isso de três maneiras: (a) `applySizeAndPlacement` chamava `setSize` **duas** vezes por
mudança de conteúdo (na hora e 120 ms depois); (b) o `LaunchedEffect(estimatedHeight, mousePoint, window)`
reiniciava a cada mudança de `mousePoint` e reaplicava o `setSize` **com a mesma altura**; (c) a guarda era
`isDisplayable`, que é verdadeira também para uma janela em processo de mapeamento ou de descarte — sem
superfície Compose ainda. Trocar de dicionário produz exatamente a rajada que o bug precisa: o aviso de
«sem verbete» aparece, a definição chega, o aviso volta, cada passo mudando a altura em ~58 px.

**O que mudou.** O cartão agora se redimensiona **uma vez**, e só quando precisa:
- `planCardPlacement(bounds, pointer, height, screen)` (puro) devolve `CardPlacement(resize, location)`;
  `resize` só é verdadeiro quando a altura pedida (ou a largura, que o WM pode ter mexido) difere da atual,
  e a posição continua sendo recalculada sempre — um cartão perto da base da tela vira para cima.
- `applySizeAndPlacement` passou a exigir `window.isShowing` (não `isDisplayable`) e a chamar `setSize`
  apenas quando `resize` é verdadeiro; `placeWindow` foi absorvido por ela.
- O `LaunchedEffect` espera `TOOLTIP_PLACEMENT_DEBOUNCE_MS` (80 ms) antes de tocar na janela: uma rajada
  de mudanças de altura colapsa num único redimensionamento do tamanho final, e a janela que ainda está
  sendo mapeada termina antes. A segunda passada (correção de posição do WM) agora só move, porque o
  tamanho já está certo.

**O crash voltou.** Um terceiro `hs_err_pid29132.log`, com a **mesma** pilha, depois desta correção: o
usuário alternou ~20 dicionários seguidos com o mouse em movimento, e cada troca ainda redimensiona o
cartão uma vez (a definição e o aviso têm alturas diferentes). Ou seja: tirar a rajada reduz a chance, não
elimina o defeito — um único `setSize` nessa máquina ainda pode cair dentro da corrida do Direct3D.

**Recuperação depois do crash.** Como o crash mata o processo em código nativo, nada dentro do JVM pode
pegá-lo: quem vê é a execução seguinte. `RendererRecovery.kt` (novo) escreve um marcador
(`~/.yabai_ocr/run-in-progress`, ou sob `YABAI_HOME`) em toda partida e o apaga num encerramento normal; se
o marcador existir na partida, a execução anterior morreu. A preferência em si mora em
`settings.properties` (`skiko_renderer`), num enum `SkikoRenderer` com uma **escada de queda**: a primeira
morte sai do Direct3D para **OPENGL** (continua usando a placa de vídeo) e só uma morte em OpenGL cai para
**SOFTWARE**. `decideRendererApi` aplica o nível em `skiko.renderApi` **antes** de qualquer janela existir,
grava o nível novo nos Ajustes e imprime:

```
[Renderer] A execução anterior foi encerrada por um crash nativo do Skiko (Direct3D), sem exceção Java.
[Renderer] Iniciando com skiko.renderApi=OPENGL para não repetir o crash.
[Renderer] Para trocar: Ajustes → Renderizador gráfico, ou iniciar com --renderer=automatic.
```

O nível lembrado é o que impede o vaivém: sem ele o app voltaria ao Direct3D a cada partida limpa e
crasharia de novo em seguida. Um `-Dskiko.renderApi=…` definido pelo usuário nunca é sobrescrito — o log
só registra que ele foi respeitado.

**Escolher e voltar pela interface ou por argumento.** O app empacotado é um `.exe`: não há `-D` para
passar. Por isso o renderizador virou preferência visível. Nos Ajustes, o cartão **«Renderizador
gráfico»** (Automático / OpenGL / Software, com uma linha dizendo quais evitam o Direct3D, o aviso de que
a mudança vale na próxima abertura, e — quando a execução subiu de nível por crash — uma linha vermelha
dizendo com qual renderizador o app abriu). Na linha de comando, `--renderer=automatic|opengl|software`
(aceita `--renderer opengl`, aliases `auto`/`default`/`gl`/`sw`/`cpu`, e maiúsculas/minúsculas). Precedência:
`-Dskiko.renderApi` > `--renderer` > escada de crash > preferência salva. O argumento também **persiste** a
escolha, e um valor inválido é dito no log (`--renderer=vulkan não é válido...`) em vez de ser ignorado em
silêncio.

**Posicionamento com novas tentativas.** A guarda `isShowing` (que existe para não redimensionar uma janela
sem superfície Compose) tinha um efeito colateral: se o efeito rodava antes de a janela aparecer, o cartão
ficava para sempre na altura inicial. Agora `applySizeAndPlacement` devolve se conseguiu aplicar, e o efeito
tenta de novo a cada 25 ms por até 20 tentativas (`shouldRetryCardPlacement`) — meio segundo, para não
ressuscitar o redimensionamento infinito que o debounce veio evitar.

**Testes e mutações.** `HoverTooltipHeightTest`: mesma altura não redimensiona (só move, na posição que
`placeTooltip` dá), altura maior redimensiona, largura diferente redimensiona, e a nova tentativa para de
pedir depois de `CARD_PLACEMENT_MAX_ATTEMPTS` (com o total ≤ 1 s). `RendererRecoveryTest` (11): um run que
não terminou é notado pelo seguinte; a saída limpa apaga o marcador; a escada é Direct3D → OpenGL →
Software (e Software fica); o primeiro crash depois do Direct3D abre em OpenGL, é lembrado e o log nomeia o
renderizador, explica o crash e mostra as duas saídas (`Ajustes` e `--renderer=automatic`); crash em
OpenGL cai para Software; máquina sem crash usa a preferência salva sem reescrevê-la nem logar; máquina
sem histórico não escolhe nada; o argumento é lido em `--renderer=x` e `--renderer x` (com aliases e
maiúsculas) e um valor inválido vira `Invalid`; o argumento vence a preferência e é persistido; o
argumento também vence a escada de crash; `-Dskiko.renderApi` explícito vence tudo e só é mencionado no
log; valor desconhecido em `settings.properties` volta para Automático. Mutações provadas e restauradas:
`resize = true` sempre; crash ignorado no `decideRendererApi`; marcador nunca notado; saída limpa mantendo
o marcador; tentativa de posicionamento sem limite; primeiro crash pulando direto para Software;
argumento da linha de comando ignorado; argumento deixando de vencer a preferência.

**Limitações.** 1) O defeito de fundo é nativo do Skiko/Direct3D; o app agora se recupera dele, não o
conserta. 2) A recuperação age na execução **seguinte**: a que crashou morre e o usuário precisa abrir o
app de novo. 3) O nível escolhido continua valendo nas próximas aberturas até ser trocado nos Ajustes ou
por `--renderer`. (A versão original desta entrada dizia que matar o processo à mão também mudava o
renderizador; isso era o bug corrigido na D80.) 4) Se a pasta do usuário não for gravável, o marcador não é
escrito e o app segue no Direct3D. 5) O `--renderer` só vale para o app do desktop; o Android não usa
Skiko. 6) O crash em si não foi reproduzido aqui: a verificação foi a pilha dos três `hs_err`, os testes e
as mutações. 7) O cartão pode aparecer com a altura inicial por ~80 ms antes de crescer.
**`:desktop` 470, `:core` 197, `:app` 836, 0 falhas.**

### D80 — O cartão não aparecia no OpenGL/Software, e fechar com CTRL+C era contado como crash  `[x]` (16/11/2026)

**Sintomas.** Dois, e os dois vieram dos renderizadores alternativos da D79:

1. Com `skiko.renderApi=OPENGL` ou `SOFTWARE`, o log mostrava o hover funcionando — `[Hover] 意識 — Jitendex
   PT-BR [2026-09-29]` — e **nada** aparecia na tela. O cartão existia, era posicionado e não desenhava um
   pixel.
2. Fechar o app com `CTRL+C` no `gradlew :desktop:run` (ou pelo Gerenciador de Tarefas) fazia a partida
   seguinte anunciar «A execução anterior também terminou em crash nativo do Skiko» e descer um degrau da
   escada — OpenGL, Software, Software. Só houve crash de verdade no Direct3D.

**Causa raiz (1).** `HoverTooltipWindow` fazia `window.background = java.awt.Color(0, 0, 0, 0)` na primeira
composição. No Windows, `transparent = true` faz o Compose perguntar ao Skiko qual fundo AWT o renderizador
em uso quer (`PlatformOperations.transparentWindowBackgroundHack`): Direct3D pede a cor transparente, e
**OpenGL e Software pedem `null`**, porque a transparência deles vem de uma chamada DWM
(`DwmEnableBlurBehindWindow`) no componente nativo. Escrever a cor transparente no `background` do AWT
desfaz essa chamada: a janela continua lá, opaca ou sem superfície — e o Compose não pinta nada. O Direct3D
não era afetado porque a cor pedida por ele é justamente essa. Ou seja: a linha era necessária para um
renderizador e fatal para os outros dois, e o único jeito de servir aos três é não tocar no `background`.

**Causa raiz (2).** O marcador `run-in-progress` era a única testemunha: se ele estivesse lá, a execução
anterior tinha morrido. Isso confunde crash com qualquer encerramento que não passe pelo `onCloseRequest` —
`CTRL+C`, `Stop-Process`, queda de energia.

**O que mudou.** A linha do `background` saiu; o fundo da janela do cartão passa a ser exatamente o que o
Compose decidiu para o renderizador em uso. E o marcador passou a gravar o **pid** (`pid=<pid>`), e a
partida seguinte só acredita em crash quando encontra `hs_err_pid<pid>.log` — o relatório que o próprio JVM
escreve ao morrer em código nativo — em `user.dir` ou na pasta do marcador, com menção a `skiko` (ignorando
caixa) e não mais antigo que o marcador (`CRASH_REPORT_TIME_SLACK_MS = 2 s`, para relógios grosseiros; o
que também evita o relatório de um processo anterior que teve o mesmo pid). O resultado ficou em duas
metades testáveis: `RendererRecovery.previousRun()` devolve `PreviousRun(endedAbnormally, skikoCrashReport)`,
e `endedAbnormally && skikoCrashReport == null` é `stoppedFromOutside` — o caso em que `decideRendererApi`
mantém o renderizador e explica no log:

```
[Renderer] A execução anterior foi encerrada fora do app (Ctrl+C, processo finalizado ou queda de energia) e não deixou relatório de crash do Skiko. Renderizador mantido: Automático.
```

Marcador antigo, escrito antes desta mudança (sem `pid=`), não é tratado como crash: não há relatório para
casar com ele. A escada da D79 continua a mesma (Direct3D → OpenGL → Software).

**Testes e mutações.** `HoverCardTransparencyTest` (2): o cartão pede `transparent = true` e o arquivo não
tem nenhum `window.background =`. `RendererRecoveryTest` cresceu para 19: saída limpa não deixa rastro; um
run morto por fora não é crash; relatório do Skiko marca crash; relatório de **outra** biblioteca nativa não
marca; relatório mais velho que o marcador não marca; relatório em outra pasta é encontrado (e não é
encontrado na pasta errada); marcador legado sem pid não muda o renderizador; o marcador grava o pid do
processo atual e o relatório desse pid é reconhecido; a escada e as decisões da D79 (crash real, argumento,
`-Dskiko.renderApi`, preferência salva); run parado por fora mantém o renderizador e diz por quê. Mutações
provadas e restauradas: `window.background = java.awt.Color(0,0,0,0)` de volta (derruba
`HoverCardTransparencyTest`); marcador sozinho voltando a significar crash (derruba 5 testes); `markRunning`
sem gravar o pid (derruba o teste do pid); checagem temporal do relatório removida (derruba o relatório
antigo); checagem de `skiko` no relatório removida (derruba o relatório de outra biblioteca).

**Evidência ponta a ponta.** O app real, `--minimized`, com uma sonda Java que abre uma janela branca opaca
com «意識 瞬間 俺» em 56 pt, liga o Hover pelo atalho global e mede uma região de 470×330 px logo
abaixo/direita do ponteiro (1 428 px escuros já são o texto da própria janela):

| renderizador | antes | com o ponteiro sobre 意識 | depois de sair |
| --- | --- | --- | --- |
| OpenGL (`--renderer=opengl`) | `dark=1428/155100` | `dark=121644/155100` | `dark=1428/155100` |
| Software (`--renderer=software`) | `dark=1428/155100` | `dark=121654/155100` | `dark=1428/155100` |
| Automático (Direct3D), sem argumento | `dark=1428/155100` | `dark=121644/155100` | `dark=1428/155100` |

O log das três partidas trouxe `[Hover] 意識 — Jitendex PT-BR [2026-09-29]`. Com a linha do `background`
reintroduzida (mutação), OpenGL ficou em `dark=1428/155100` **com a mesma linha no log** — o defeito
original, reproduzido e medido. O `CTRL+C` foi provado no caminho real: a partida seguinte, iniciada sem
`--renderer`, imprimiu a mensagem de «encerrada fora do app» e continuou em Automático. `settings.properties`
restaurado byte a byte (SHA-256 conferido) e nenhum processo deixado aberto.

**Limitações.** 1) A causa do crash em Direct3D não mudou: o cartão continua sendo a janela transparente que
o Skiko redimensiona. 2) `hs_err_pid<pid>.log` só existe se o JVM conseguir gravar (o diretório de trabalho
ou `-XX:ErrorFile`); sem ele, um crash verdadeiro fica indistinguível de um processo morto por fora, e o app
prefere **não** trocar o renderizador a trocar por engano. 3) A correção do cartão foi verificada numa tela
(1280×720 de usuário, 150%), não no segundo monitor nem com escala diferente. 4) Um encerramento pelo
`onCloseRequest` do próprio app não deixa marcador: só o que acontece por fora precisa ser adivinhado.
**`:desktop` 480, `:core` 197, `:app` 836, 0 falhas.**

### D81 — Hover lento no Steam Deck: o cartão lia os índices dos dicionários, e nada dizia qual captura foi usada  `[x]` (16/11/2026)

**Relato (SteamOS 3.9.5, Desktop Mode, KDE Wayland, AppImage 1.0.9).** O cartão do hover demora
**vários segundos** para aparecer depois que o ponteiro para sobre o japonês — muito mais do que na
mesma build no Windows. O usuário também perguntou como saber se o app está de fato usando o
`org.kde.KWin.ScreenShot2` ou caindo em outro caminho de captura.

**Medição nesta máquina antes de mexer em nada** (1280×800, `KdeWaylandCapture`, sem nenhuma linha
`captura direta pelo KWin recusada` — ou seja, o `ScreenShot2` **estava** sendo usado, com
`KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1` já ativo na sessão):

| etapa do hover | medição (1ª / seguintes) |
| --- | --- |
| captura pelo KWin `ScreenShot2` (tela inteira) | 381 ms / **75 ms** |
| OCR — Lens (rede) | 2 009 ms / **1 230 ms** |
| OCR — PP-OCRv6 offline (mesmos modelos, mesma imagem) | 3 254 ms / 2 349 ms |
| lista de dicionários do cartão (código antigo) | 103 ms / 52 ms / 42 ms |
| lista de dicionários do cartão (agora) | 32 ms / **1–2 ms** |

Ou seja: o Lens domina a espera (é rede, e o `AUTO` tenta ele primeiro), o PP-OCR é **mais lento**
que ele neste APU, e o que sobrava de desperdício era a lista de dicionários do cartão.

**Causa raiz (1) — o cartão abria todos os dicionários para saber o nome deles.**
`HoverOcrManager.getInstalledDictionaryTitles()` (e a mesma cópia em `ResultWindow`,
`QuickDictWindow`, `SettingsWindow` e `DictionaryManagerWindow`) chamava `CompactDictionary.open`
em cada `.yabai` só para ler `info.title`. `open` lê o **índice de chaves inteiro** — `terms.keys`,
`keyoff`, `keystart`, `refs`, `eflags` e o lado kanji — em memória: **69 MB** nos cinco dicionários
instalados nesta máquina (16 + 16 + 16 + 19 + 0 MB). O cartão pede essa lista a cada composição, do
fio da interface, então a leitura de 69 MB acontecia **em cada hover**, atrasando o desenho de um
cartão que já tinha a resposta. Num AppImage com o cache de página frio (eMMC do Deck) isso custa
bem mais que os 42–103 ms medidos com o arquivo já em cache.

**Causa raiz (2) — nada dizia qual captura estava em uso.** A única testemunha era uma linha no
`stderr` quando o `ScreenShot2` era **recusado**; um AppImage aberto pelo menu não tem `stderr`
visível, e não havia linha nenhuma quando o caminho direto funcionava. O usuário não tinha como
distinguir «captura direta, 75 ms» de «portal com diálogo, segundos» — e o sintoma dos dois é o
mesmo: hover lento.

**O que mudou.**

- `DesktopDictLoader.dictionaryTitle` / `installedTitles` leem **só o `meta.json`** de cada
  dicionário; o índice não é tocado. As cinco telas passaram a usar essa função (o cartão, o painel
  de resultado, a busca rápida, os Ajustes e o gerenciador). O nome de um dicionário é o dado que a
  lista mostra, e ele vive no `meta.json`.
- `ScreenCaptureBackend.lastCaptureRoute()`: cada backend diz qual transporte serviu a última
  captura (`Robot`, `KWin ScreenShot2`, `portal Screenshot`, `ScreenCast do JDK`), e o
  `KdeWaylandCapture` diz **qual dos dois** dele respondeu de fato. `CaptureDiagnostics`
  (`ScreenCapture.kt`) mede o tempo da captura e escreve **uma linha por transporte novo**:
  `[Captura] captura: KWin ScreenShot2 · 1280×800 · 47 ms`.
- A mesma medida entra no fim da linha de status do hover (visível nos Ajustes):
  `が — Jitendex PT-BR · captura: KWin ScreenShot2 · 1280×800 · 47 ms`. Textos em pt/en
  (`DesktopStrings.captureSummary`, `captureRouteUnknown`).
- `Main` imprime no arranque o caminho **esperado** para a sessão
  (`[Captura] caminho esperado nesta sessão: KWin ScreenShot2 (KDE Wayland)`), o que responde a
  pergunta antes de qualquer hover; `captureBackendLabel` é a função pura que escolhe esse nome.
- A medição é a única coisa acrescentada ao caminho de captura: imagem, retângulo e backend são os
  mesmos.

**Testes e mutações.** `:desktop:test` **492 testes, 0 falhas** (1 pulado, o opt-in do PP-OCR).
Novos: `DictionaryTitlesTest` (6) — o primeiro é o da causa raiz (um `.yabai` com `meta.json` e
**sem** índice é nomeado; com `CompactDictionary.open` ele sumia da lista), mais pasta que não é
dicionário, dicionário sem título, pasta inexistente, `meta.json` quebrado que não leva os outros
nomes junto; `CaptureDiagnosticsTest` (6) — captura direta diz `KWin ScreenShot2`, recusa do direto
diz `portal Screenshot`, nada antes da primeira captura, nomes por sessão, e o resumo com
transporte/tamanho/tempo.

| mutação | teste que acusa | resultado |
| --- | --- | --- |
| `dictionaryTitle` voltar a usar `CompactDictionary.open` | `DictionaryTitlesTest` (4 dos 6) | falhou e voltou a passar |
| `KdeWaylandCapture` não registrar o transporte do direto | `a direct capture says ScreenShot2 served it` | falhou e voltou a passar |

`HoverLookupTest > the notice names the dictionary that has no entry` passou a fixar o idioma
(`DesktopLanguageChoice.PT_BR`) antes de afirmar a palavra «verbete»: ele dependia do locale da
máquina e falhava neste Deck (`LANG=en_GB`), ao contrário da máquina Windows onde foi escrito.

**Evidência ponta a ponta (app real, ponteiro sobre japonês).** Harness em `/tmp/yabai-e2e`:

- uma sonda Java desenha uma janela opaca 900×300 em (80,120) com 「日本語を勉強する」 e
  「猫が魚を食べた」 em Noto Sans CJK 44 pt e publica o ponto de tela do meio de 「食べ」;
- o ponteiro chega pelo **mesmo caminho que o script do KWin usa** —
  `dev.yabai.Ocr.Hover.CursorMoved(x,y)` no barramento da sessão —, e o hover é ligado por
  `dev.yabai.Ocr.Shortcuts.ShortcutPressed("hover-toggle")`;
- a latência é o intervalo entre a **chegada do ponteiro em 「食べ」** e a linha `[Hover]` que o app
  escreve quando o cartão está pronto (só as linhas depois de cada chegada contam). O `t0` inclui a
  espera de repouso do `HoverGate`, porque é isso que o usuário sente.

| build | leituras | mediana | min | max | captura reportada |
| --- | --- | --- | --- | --- | --- |
| distributable (`:desktop:createDistributable`) | 5 | **1 455 ms** | 1 331 ms | 1 630 ms | 61–91 ms |
| **AppImage `1.0.10-e2e`** | 3 | **1 623 ms** | 1 427 ms | 1 708 ms | 51–88 ms |

Linha de uma leitura completa, como o app a escreve:

```
[Hover] 食べる — Jitendex PT-BR [2026-09-29] · captura: KWin ScreenShot2 · 1280×800 · 88 ms
[Captura] captura: KWin ScreenShot2 · 1280×800 · 113 ms
```

O AppImage roda de `/tmp/.mount_*` e **mesmo assim** pegou o `ScreenShot2` (51–88 ms por captura, sem
diálogo): com `KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1` na sessão a checagem do KWin não acontece. As
1 400–1 700 ms se decompõem em ~420 ms do repouso do `HoverGate`, ~70 ms de captura e **~960 ms de
OCR + dicionário + cartão** (o Lens é rede).

**O ganho, medido do jeito certo (A/B).** O mesmo harness rodou contra uma build **mutada** (a
mutação confirmada dentro do jar do app: `readMeta` presente = corrigido, ausente = antigo):

| build | leituras | mediana |
| --- | --- | --- |
| corrigido | 5 | 1 455 ms |
| antigo (`CompactDictionary.open` para o título) | 5 | 1 376 ms |

A diferença está **dentro do ruído** do Lens (300–700 ms entre leituras). Ou seja: a correção tira
trabalho real do caminho do cartão — 42–103 ms medidos no componente — mas **não** muda a espera
percebida de ~1,5 s neste hardware; quem manda nela é o OCR (~0,96 s) e o repouso do gate (0,42 s).
Dizer o contrário seria vender o que a medição não mostra.

**Limitações.** 1) A correção da lista de dicionários **não** move o número de ponta a ponta (tabela
acima); o que ela garante é que o cartão não lê 69 MB de índice a cada hover. 2) O **Lens** continua
sendo ~0,96 s do total e não foi tocado; o PP-OCRv6 mediu 2,3 s na mesma imagem neste APU, então
trocar de motor piora. 3) O repouso de 420 ms do `HoverGate` é **29%** da espera sentida e ficou como
está: reduzi-lo é decisão de comportamento (dispara mais cedo ao passar sobre o texto), não de bug.
4) O ponteiro do E2E é forjado no D-Bus do app — o mesmo caminho do script do KWin, mas não um mouse
físico — e o texto é sintético (Noto Sans CJK), não uma página real. 5) Um dicionário com `meta.json`
válido e índice corrompido passa a **aparecer** na lista de nomes (antes sumia ao falhar no `open`);
escolhê-lo responde «sem verbete», como um dicionário desabilitado. 6) O `AppImage 1.0.10-e2e`
(`desktop/build/distributions/appimage/`, 158 480 888 bytes, sha256
`137ae9ba8596…6e3e7d`) foi gerado para teste e **não** é um release: nada foi commitado nem publicado.

### D82 — Repouso do hover a 250 ms (medido A/B), por que o PP-OCR offline é mais lento, e um renderizador só no Linux  `[x]` (16/11/2026)

**O que estava errado.** Três coisas que o usuário cobrou:

1. **Os 420 ms de repouso** do `HoverGate` eram **29%** da espera sentida (~1,4 s). O gate não precisa
   saber que o leitor terminou de ler: precisa saber que o ponteiro **parou**. 420 ms é conservador
   para quem lê palavra por palavra.
2. **«Por que o PP-OCR offline é mais lento que o Lens?»** Offline tira a rede do caminho, mas põe no
   caminho local **um passo de rede neural por linha de texto** — e a tela tem dezenas de linhas.
3. **A escada de renderizador** mostrava «Automático», «OpenGL» e «Software» em todo sistema, e o texto
   de recuperação falava em Direct3D. No Linux o Skiko desenha com OpenGL e «Automático» **é** OpenGL:
   eram dois nomes para a mesma escolha, mais uma opção que ali não existe.

**O que mudou.**

- `DesktopSettings.hoverRestMillis` (`hover_rest_millis`): padrão **250 ms**, faixa 120–1000, presets
  **150 / 250 / 420** em **Ajustes → Modo Hover → «Espera para abrir o card»**.
- `HoverGate.stableMillis` deixou de ser `val` de construção: é `@Volatile var` com clamp (80–2000), então
  mudar o ajuste vale para o **próximo** movimento, sem reabrir o app. `HoverOcrManager.setRestMillis()`
  aplica na hora e `setEnabled(true)` relê a preferência.
- `offeredRenderers(isLinux)`: no Linux a tela oferece **só «Software»** mais uma nota dizendo que o
  padrão ali já é OpenGL; no Windows/macOS continuam as três. `rendererAfterCrash` deixou de citar
  Direct3D e fala em «crash nativo do renderizador gráfico (Skiko)».
- Testes: `HoverGateTest` (2), `DesktopSettingsTest` (2), `RendererOffersTest` (3, novo).
  Suíte `:desktop:test`: **499 testes, 0 falhas, 1 pulado**.

**Medição A/B (o antes e o depois).** Mesmo harness (`/tmp/yabai-e2e/hover_e2e.py`), mesma build, mesma
imagem, mesma janela, ponteiro forjado pelo mesmo caminho do script do KWin; só a chave
`hover_rest_millis` mudou entre as rodadas, e os ajustes do usuário foram restaurados no fim
(`sha256 50540e6f…b2964` conferido):

| repouso | leituras | mediana | min | max | captura |
| --- | --- | --- | --- | --- | --- |
| **420 ms** (o valor antigo) | 5 | **1 375 ms** | 1 163 ms | 1 417 ms | 43–88 ms |
| **250 ms** (novo padrão) | 5 | **1 128 ms** | 1 001 ms | 1 333 ms | 53–92 ms |
| 150 ms (opção «Rápido») | 5 | 1 165 ms | 1 000 ms | 1 418 ms | 48–71 ms |

**−247 ms** de mediana (−18%) indo de 420 para 250. De 250 para 150 **não** há ganho medível: o ruído
do Lens (±300 ms entre leituras) engole 100 ms de repouso, e é por isso que 250 ms é o padrão e 150 fica
como escolha de quem quiser. A espera restante é OCR (~0,96 s) e captura (~70 ms).

**Onde está o custo do PP-OCR** (medido com uma sonda temporária, `DesktopPPOcr.recognize` na tela real
e num recorte, e o decodificador CTC isolado):

| medida | valor |
| --- | --- |
| tela cheia 1280×800 | 3 129 ms, **56 linhas** |
| recorte 360×260 | 490 ms, 9 linhas |
| cópia `getRGB` → `IntArray` da tela | 21 ms |
| `PPOcrDecoding.decode` de **uma** linha (T=64, V=19 055) | **0,8 ms** (≈45 ms para 56 linhas) |

O decodificador — o primeiro suspeito, o argmax em Kotlin sobre `[1,T,19 055]` — **não** é o gargalo.
Resolvendo o sistema com os dois primeiros pontos (o detector normaliza o lado maior para 960, então
custa quase o mesmo nos dois casos): **~50 ms de detecção** + **~49 ms por linha** no reconhecedor
(49 + 56×49 ≈ 2 793 ms, exatamente o `engineMs` medido). O PP-OCR é offline, mas reconhece **uma linha
por vez** num APU de 4 núcleos sem inferência de GPU: 56 linhas = 56 passadas do CNN + LSTM. O Lens
gasta os mesmos ~1,2 s fazendo **uma** chamada de rede e o trabalho pesado na máquina do Google. Offline
não é rápido: é só sem rede.

**Prova de mutação.** Cada correção foi desfeita de propósito e o teste que a cobre falhou:

| mutação | teste que falhou | resultado |
| --- | --- | --- |
| `HoverGate.stableMillis` com setter que ignora o valor | `a shorter rest time fires sooner…` + `a rest time below the floor…` | falhou e voltou a passar |
| `offeredRenderers` voltando a devolver as três no Linux | `RendererOffersTest > linux offers only the software renderer` | falhou e voltou a passar |
| `HOVER_REST_DEFAULT_MS` de volta a 420 | `the hover rest time defaults to 250 ms and round-trips` | falhou e voltou a passar |

(A primeira tentativa de mutação do gate trocou o `var` por `val` e **quebrou a compilação** — isso não
prova nada, e por isso o teste de mutação foi refeito com um setter que aceita e descarta o valor.)

**Limitações.** 1) O E2E continua com ponteiro forjado no D-Bus e texto sintético, não um mouse físico
numa página real. 2) O ganho de 247 ms foi medido **neste** hardware; num Deck mais frio o Lens pode
dominar ainda mais a espera. 3) A tela de Ajustes com um único renderizador no Linux foi compilada e
coberta por teste, mas **não** foi olhada nesta rodada — quem for validar visualmente: Modo Hover deve
mostrar o seletor «Espera para abrir o card» com «250 ms — Padrão» e o cartão «Renderizador gráfico»
deve mostrar **só «Software»** com a nota do OpenGL. 4) Matar o **runtime** de um AppImage em execução
não mata o app filho: ele fica vivo segurando `~/.yabai_ocr/instance.lock` e a próxima cópia sai com
«já existe uma instância aberta». Ao encerrar um AppImage à força, mate o processo `yabai-ocr` em si.
5) O binário usado no A/B é o `createDistributable` desta árvore, porque é o mesmo caminho de código; o
AppImage de teste foi regerado depois com o D82 (`desktop/build/distributions/appimage/`,
158 489 080 bytes, sha256 `23768f18ac2f…8d69b8`) e um smoke run de 14 s abriu a janela sem exceção de
composição — mas o cartão novo **não** foi olhado. Nada foi commitado nem publicado.

### D83 — O PNG de 1,5 MB era o gargalo do hover, o switch do renderizador e o ícone que vinha de outro app  `[x]` (16/11/2026)
**Sintomas.** Três, todos relatados pelo usuário no Steam Deck: (1) o hover sobre um mangá aberto no
navegador levando **3–4 s**, às vezes 2 s, e a palavra nova só sendo reconhecida depois de sair e
voltar o mouse; (2) no Linux, o cartão «Renderizador gráfico» ficando com um **rádio solto,
desmarcado**; (3) o ícone da barra de tarefas antigo enquanto o da bandeja já era o novo.

**Causa 1 — o envio, não o reconhecimento.** O hover manda **o monitor inteiro** para o Lens (decisão
antiga: palavra partida entre colunas). Num desktop de texto isso dá ~150 KB de PNG; numa página de
mangá, arte não comprime e o PNG passa de 1,5 MB. Medido nesta máquina, mesmo cliente, só o conteúdo
mudando:

| imagem | PNG | Lens |
| --- | --- | --- |
| tela de desktop, texto claro | 149 KB | 688–793 ms |
| faixa de 700 px da página de mangá | 852 KB | 2 022–2 273 ms |
| página de mangá inteira | 1 540 KB | 3 220–4 498 ms |

A tela de desktop tinha **mais** segmentos (46) que a página de mangá (65) e era cinco vezes mais
rápida: o tempo acompanha **bytes na rede**, não texto na página. É por isso que só o SteamOS sofre —
o wifi do Deck é o gargalo.

**Correção.** `LensPayload`: PNG até **400 KB** (o caso comum, sem perda nenhuma); acima disso JPEG
**q85**, e só se ficar menor. Medido na página de mangá: **299 KB**, **916–1 183 ms** em vez de
3 370–3 541 ms, e **nenhum segmento perdido** contra quatro execuções PNG da mesma página (achou um
「ツ」 solto a mais, ruído de arte). **q75 foi rejeitado**: trocou 「プレイして」 por 「ブレイして」 e
「NEW」 por 「HEWE」 — exatamente o dano que a regra de precisão proíbe.

**Ponta a ponta no app real** (mangá em tela cheia, ponteiro pelo caminho do script do KWin, status do
próprio app):

| build | Lens medido pelo app | leitura completa (hover) |
| --- | --- | --- |
| antiga (mutação: tudo PNG) | 3 340 / 3 428 / 3 454 / 3 648 ms | — |
| **com o `LensPayload`** | **540–1 609 ms** | mediana **1 712 ms** em 3 pontos (repouso 150 ms + captura ~55 ms + cartão) |

**Causa 2 — a fila do hover.** `HoverOcrManager` tinha um `busy` que **travava o hover inteiro**
durante a leitura: com 3–4 s de Lens, toda palavra que o ponteiro alcançava nessa janela era
descartada — daí «tenho que sair e voltar o mouse». Agora `HoverLookupTracker` faz **o mais novo
vencer**: a leitura nova começa mesmo com a antiga em voo, e a resposta velha é jogada fora em vez de
ser desenhada sobre a palavra atual. No máximo 2 leituras simultâneas; quando as duas estão ocupadas,
o portão é resetado para a palavra nova ser pega assim que uma vaga abrir. A linha `[Hover]` (e o
status nos Ajustes) passou a trazer **motor e tempo** — «Google Lens 1 180 ms» —, que é como as
medições acima puderam ser lidas do app em vez de adivinhadas.

**Causa 3 — o rádio solto.** No Linux «Automático» **é** OpenGL, então a lista de rádios oferecia uma
decisão que não existe e, com o padrão guardado como `automatic`, o único rádio («Software») aparecia
desmarcado. O cartão agora mostra **um interruptor**: «Renderização por software» — ligado grava
`software`, desligado **preserva** o caminho de placa que estava guardado (`opengl` continua `opengl`,
`automatic` continua `automatic`; só quem veio de Software volta para «Automático»). Windows/macOS
seguem com os três rádios. Screenshot em `docs/img/ajustes-renderizador-grafico.png`.

**Causa 4 — o ícone da barra de tarefas.** O `.desktop` do app é regravado a cada arranque, e o
`Exec=` apontava para **outro aplicativo**: `resolveLinuxExecutable` confiava em `$APPIMAGE`, que é
herdado por todo processo filho — inclusive por este app quando ele é iniciado de dentro de outro
AppImage (aqui, o Freebuff). Sem entrada que case com o binário em execução, a barra de tarefas não
tem de onde tirar o ícone e mostra o marcador genérico; a bandeja continuava certa porque recebe os
**pixels** (`IconPixmap`), não um nome de tema. Duas correções: `appImageIsOurs` compara `$APPDIR` com
o binário em execução e ignora o `$APPIMAGE` herdado de outro app; e o `Icon=` passou a ser o
**caminho absoluto** do PNG instalado, que não depende do cache de ícones do tema.

**Prova de mutação.** Cada correção foi desfeita e o teste que a cobre falhou:

| mutação | teste que falhou |
| --- | --- |
| `rendererAfterSoftwareSwitch` sempre `AUTOMATIC` ao desligar | `turning it off keeps the graphics card path that was stored` |
| `LensPayload.encode` sempre PNG | `an image PNG cannot compress goes as JPEG` |
| `HoverLookupTracker.isCurrent` sempre `true` | `a newer lookup makes the older one stale` (+1) |
| `Icon=` de volta ao nome do tema | `the entry names the installed icon file by absolute path` |
| `add(env["APPIMAGE"])` sem validação | `an APPIMAGE inherited from the launching app is ignored` |

Suíte `:desktop:test`: **515 testes, 0 falhas, 1 pulado**.

**Limitações.** 1) A página de teste é o print do mangá do usuário reduzido a 1280×800 (o original é
768×557), então os bytes não são os do navegador dele naquele instante — a relação bytes→tempo é que
foi medida. 2) O Lens **não é determinístico**: quatro execuções PNG da mesma página não devolvem
exatamente o mesmo conjunto de segmentos, então «nenhum segmento perdido» vale com essa ressalva. 3) O
limite de 400 KB é heurística tirada destas medições, não um número do Lens. 4) O **pixel** da barra de
tarefas não foi verificado: o Plasma guarda o ícone da sessão em curso, então a entrada foi provada por
teste e conferida no arquivo, mas a troca visual só aparece depois de sair e entrar na sessão (ou
reiniciar o Plasma). 5) Os ajustes do usuário (`engine=LENS`, `hover_rest_millis=150`,
`skiko_renderer=SOFTWARE`) foram deixados como estavam. 6) Nada foi commitado nem publicado.
### D84 — Build no Linux de verdade: CRLF no checkout, `jlink` na pasta compartilhada e modo 0770  `[x]` (16/11/2026)

**Sintoma.** Seguindo o passo a passo do `docs/STEAMOS.md` numa Ubuntu 24.04 em VirtualBox (repo em
`/media/sf_yabai_ocr`, pasta vboxsf), `./gradlew :desktop:packageLinuxAppImage` não saía do lugar:
`bash: ./gradlew: cannot execute: required file not found`, e com `bash ./gradlew` vinha
`line 2: $'\r': command not found` até `syntax error near unexpected token`.

**Três causas, nenhuma no código do app.**

1. **CRLF no checkout.** `git ls-files --eol` mostra `i/lf w/crlf` em `gradlew`, `gradlew.bat` e
   `build-appimage.sh`: o índice está certo, a árvore de trabalho foi salva com CRLF (Windows/pasta
   compartilhada). O kernel lê o `#!` do `gradlew` como `/bin/sh\r` e não acha o interpretador — daí
   "required file not found" em vez de erro de sintaxe. Em bash, o `\r` vira comando.
2. **`jlink` não escreve na pasta compartilhada.** Mesmo com o `gradlew` corrigido, o build parava em
   `:desktop:createRuntimeImage`: `java.nio.file.FileSystemException: .../runtime/legal/... :
   Operation not permitted`. É a tarefa do Compose chamando o `jlink` do JDK 21; a vboxsf recusa a
   cópia/atributo que ele usa. Não há flag do Gradle que conserte: o build precisa de um disco local.
3. **Modo 0770 herdado da pasta.** O `appimagetool` recusa empacotar com
   `ERROR: ... has mode 0770 (rwxrwx---)` — a cópia do PNG e do AppDir veio com o modo da pasta
   compartilhada/umask 007.

**O que mudou.** `.gitattributes` novo fixa `gradlew` e `*.sh` em `eol=lf` e `*.bat` em `eol=crlf`, e o
`build-appimage.sh` aplica `chmod -R a+rX "$APPDIR"` antes de chamar o `appimagetool` — os dois
problemas deixam de depender do sistema de arquivos de quem compila. Aqui a árvore também foi
normalizada (`gradlew`, `gradlew.bat`, `build-appimage.sh`), então `./gradlew` roda direto.
`docs/STEAMOS.md` §3.1 ganhou o diagnóstico em tabela (sintoma → causa → correção), a receita de
compilar fora da pasta compartilhada e onde apontar o `APPIMAGETOOL`; `README.md` e `README.pt-BR.md`
passaram a mostrar os comandos `./gradlew` (só existia PowerShell) e o mesmo par de erros de checkout.

**Evidência.** `./gradlew --version` → Gradle 9.6.0. Receita documentada (rsync para `~/yabai-build`,
`APPIMAGETOOL=~/appimagetool ./gradlew :desktop:packageLinuxAppImage`) → `BUILD SUCCESSFUL in 2m 7s`,
`Yabai_OCR-1.0.9-x86_64.AppImage` com 157.178.360 bytes. Na pasta compartilhada o mesmo comando falha
em `createRuntimeImage` com o `FileSystemException` acima (log preservado no relatório da tarefa).

**Testes e mutações.** Suíte desktop **517 testes, 0 falhas, 1 pulado**. Dois testes novos em
`LinuxPackagingScriptTest`: os scripts não podem ter CR (`\r`) e o `chmod` precisa existir *antes* do
`appimagetool`. Mutações derrubadas: acrescentar uma linha CRLF ao `build-appimage.sh` (**1 falha**) e
remover o `chmod` (**1 falha**). **Lição registrada:** as duas primeiras rodadas de mutação passaram
em falso porque o `:desktop:test` estava `UP-TO-DATE` e nenhum teste rodou (o arquivo mutado não é
input do Gradle); com `--rerun-tasks` as duas falharam como esperado. Mutação de script só vale com
rerun forçado.

**Correção de contagem.** O número "403/404 testes" das D66/D67 veio de um somatório que só lia os
primeiros 600 bytes de cada XML e exigia a ordem exata dos atributos de `<testsuite>`, então ignorava
parte dos arquivos. A contagem correta, por `<testcase>` em todos os XMLs, é **517** (81 classes).

**Limitações.** Nada foi rodado em SteamOS/Deck; a receita foi validada só nesta VM. O `jlink` na
pasta compartilhada continua impossível — é limitação da vboxsf, não do projeto. Nada commitado.
### D85 — "Integrar ao sistema" parecia não atualizar: o autostart continuava na versão anterior  `[x]` (16/11/2026)

**Sintoma.** No SteamOS, depois de trocar o AppImage por uma versão nova, a integração continuava
abrindo a versão anterior mesmo apertando «Integrar ao sistema» — sem erro, sem mudança visível. A
mesma coisa aqui no Ubuntu (vboxsf/VM), então não era específico do Deck.

**Causa raiz.** A integração é feita de **duas** entradas, e só uma era mantida atualizada:

- `~/.local/share/applications/yabai-ocr.desktop` (menu) — o `ensureTrayIcon()` já reescrevia a cada
  início quando o arquivo existia (D66) e o botão reescreve via `install()`;
- `~/.config/autostart/yabai-ocr.desktop` (login) — **nunca** era reescrita. `LinuxAutostart.reconcile`
  só comparava a *existência* do arquivo (`isEnabled() != enabled`), embora o contrato do
  [Autostart] dissesse "escreve quando falta **ou aponta para um executável que não existe mais**".
  O efeito em cadeia: o login subia o AppImage velho → esse processo rodava `ensureTrayIcon()` e
  `install()` com o **seu próprio** caminho → o menu voltava a apontar para a versão antiga. O botão
  parecia inerte porque o binário que o apertava já era o antigo.

**O que mudou.** `LinuxAutostart.refresh()` reescreve a entrada **existente** no lugar (nunca cria nem
apaga: isso é do interruptor «iniciar com o sistema»), comparando o conteúdo antes de gravar.
`reconcile(enabled = true)` chama `refresh()` quando o arquivo existe — então todo início de app
conserta a deriva, como o contrato sempre prometeu. O botão «Integrar ao sistema» passou a chamar
`StartupManager.reconcileStartOnBoot(settings.startOnBoot)` depois do `install()`, para o conserto
valer na hora e não só no próximo login.

**Evidência ponta a ponta (Ubuntu 24.04, AppImage, `YABAI_HOME`/`XDG_*` isolados).** Entrada do menu e
autostart apontando para `Yabai_OCR-1.0.9-revisao3.AppImage`, `start_on_boot=true`, app aberto a partir
da `revisao4`:

- antes da correção: `menu=…revisao4` e `autostart=…revisao3` (a divergência que causava o sintoma);
- depois da correção, com a `revisao5`: `menu=…revisao5`, `autostart=…revisao5` e
  `StartupWMClass=dev-yabai-ocr-desktop-MainKt` no autostart.

**Testes e mutações.** Suíte desktop **519 testes, 0 falhas, 1 pulado**. Dois testes novos em
`LinuxAutostartTest`: entrada antiga atualizada pelo `reconcile` (caminho novo, `--minimized` mantido,
`StartupWMClass` atualizado) e executável não resolvido deixando a entrada intacta. Mutação
derrubada: remover `if (enabled) refresh()` do `reconcile` (**1 falha**).

**Limitações.** O clique no botão não foi automatizado (não há como sintetizar teclado/clique nesta
VM sem privilégio); o que está provado é o caminho de início de app ponta a ponta e, por unidade, as
duas chamadas que o botão faz (`install()` e `reconcile`). Se o menu do Deck continuar mostrando a
entrada antiga **depois** de a versão nova rodar, o suspeito passa a ser o cache `ksycoca` do Plasma,
não os arquivos. Nada commitado.
### D86 — «Integrar ao sistema» numa função testável, `reconcile` com um só escritor e a entrada que só valia até fechar o app  `[x]` (16/11/2026)

**Revisão da D85.** O conserto estava certo, mas com duas gorduras e um buraco. Gorduras: `refresh()`
era um método interno que só o `reconcile` chamava, e o `Main.kt` repetia a lógica de "instalou → então
reconcilia" dentro do lambda do botão, sem teste. Buraco: nada impedia a entrada de ser escrita com o
caminho **de dentro do AppImage** (`/tmp/.mount_*`), que deixa de existir quando o app fecha — e aí só
o autostart do próximo login traz o app de volta, o que dá exatamente a impressão de "preciso reiniciar
o computador para abrir de novo".

**O que mudou.**

1. **Um escritor só.** `LinuxAutostart` passou a ter `writeEntry()` (privado): cria e atualiza, gravando
   apenas quando o conteúdo muda. `setEnabled(true)` e `reconcile(true)` usam o mesmo caminho — o
   `refresh()` separado sumiu.
2. **Botão testável.** Novo `integrateLinuxSystem(integration, autostart, startOnBoot)` em
   `LinuxSystemIntegration.kt`: grava entrada + ícone e, **só se o `install()` deu certo**, reconcilia o
   autostart com o estado do interruptor. O lambda do `Main.kt` ficou sendo uma chamada e a escolha da
   mensagem.
3. **Guarda do mount.** `resolveLinuxExecutable` recusa candidatos dentro de `$APPDIR`
   (`isInsideAppImageMount`): `/tmp/.mount_*` não sobrevive ao fechamento, então escrever essa entrada
   só funciona até o usuário sair. O `.AppImage` continua sendo o primeiro candidato quando `$APPIMAGE`
   é do próprio processo (D83).

**Evidência ponta a ponta (Ubuntu 24.04, AppImage, `YABAI_HOME`/`XDG_*` isolados).** Entrada do menu e
autostart apontando para a `revisao3`, app aberto a partir da `revisao6`:

- depois do início: `menu: Exec="/home/eric/Downloads/Yabai_OCR-1.0.9-revisao6.AppImage"` e
  `autostart: Exec="…revisao6.AppImage" "--minimized"`;
- app fechado: `/tmp/.mount_*` desapareceu (nenhuma montagem restante) e nada ficou rodando;
- **reaberto pelo `Exec` exato da entrada** (o que o menu faz): processo `yabai-ocr` de volta e a janela
  `Yabai OCR` presente — sem logout, sem reboot. Foi o cenário que você descreveu.

**Testes e mutações.** Suíte desktop **523 testes, 0 falhas, 1 pulado** (82 classes). Novos:
`LinuxIntegrationActionTest` (3 — instala e reconcilia; reconcilia com o estado do interruptor; falha de
instalação não toca no autostart) e a guarda do mount em `LinuxDesktopEntryTest` (mount real num
diretório temporário). Mutações derrubadas: remover a chamada ao `reconcile` (**2 falhas**), chamá-la
mesmo quando o `install()` falha (**1 falha**), remover a guarda do mount (**1 falha**) e remover o
`writeEntry()` do `reconcile` (**1 falha**).

**Lição de método.** A primeira mutação da guarda do mount *passou*: o teste usava um caminho
`/tmp/.mount_…` que não existia no disco, então `isFile` já o rejeitava e o teste não media nada. Só com
uma estrutura de mount de verdade (diretório temporário + binário executável) a mutação caiu. Mutação
que não derruba teste = teste que não cobre a causa.

**Limitações.** O clique no botão continua não automatizado (o que está coberto é a função que ele
chama, mais o caminho de início e o de reabertura pelo `Exec`). A guarda do mount cobre `/tmp/.mount_*`
do próprio AppImage; se um dia o app for executado de outro lugar efêmero, a regra precisa acompanhar.
Nada commitado.
### D87 — Hover em laço no GNOME (o «barulho de captura») e o cartão que não aparece no OpenGL da VM  `[x]` (17/11/2026)

**Relato (Ubuntu 24.04, GNOME 46 Wayland, VirtualBox, AppImage 1.1.0).** Três sintomas ao ligar o hover:
o app «faz barulho de captura a todo momento»; a VM «parece travada» quando o renderizador é a placa de
vídeo (com o software rendering não acontece); e, sem software rendering, o cartão não aparece sobre as
palavras. Pedido junto: remover o botão «Testar no cursor atual» de todas as versões desktop.

**Causa raiz do barulho e do travamento: o `dismiss()` rearmava o portão.** `HoverGate` existe para
disparar uma leitura por repouso e lembrar o ponto que já disparou; a memória só expira quando o ponteiro
anda `repeatRadiusPx`. Mas `HoverOcrManager.dismiss()` — chamado toda vez que a leitura **não** achou
texto ou não achou entrada — chamava `gate.reset()`, que apaga o `lastTrigger`. Sequência em laço, sem o
usuário mover o mouse: leitura acha «nada aqui» → cartão fecha → `reset()` → um intervalo de repouso
depois o **mesmo** ponto é capturado e reconhecido outra vez → e outra. No GNOME cada captura é uma
chamada ao portal `Screenshot`, que toca o som de obturador e dá o flash do Shell (por desenho do
compositor, não há opção de silenciar no portal), e custa centenas de milissegundos a segundos.

**Evidência de campo (journal da sessão do usuário, `~/.yabai_ocr` intacto).** O relógio do painel, sob o
ponteiro parado, foi lido e descartado repetidamente: `[Hover] Text read, but no dictionary match:
Nov 17 16:52` às 16:52:35, 16:52:47, 16:53:00 e 16:53:04 — e `…` às 16:58:34 e 17:01:15. Cada volta
carrega uma captura: `[Captura] capture: portal Screenshot · 1920×955 · 1306 ms` (e 2056 ms, 426 ms,
334 ms em outras). O barulho e o «travamento» eram o mesmo laço, não duas coisas.

**O que mudou.**

1. **`HoverGate.keepQuiet()`.** Novo método que reinicia só o relógio de repouso e **mantém** a
   supressão do ponto que disparou. `dismiss()` passou a usá-lo; andar `repeatRadiusPx` continua
   liberando a palavra seguinte. O ramo «as duas vagas de leitura estão ocupadas» segue com `reset()`:
   ali não houve resposta nenhuma para o ponto, então repetir é o comportamento certo.
2. **Botão «Testar no cursor atual» removido.** Saíram o bloco em `SettingsWindow`, a string
   `btnTestHoverCursor` (pt-BR e en) e o parâmetro `onHoverTest`/a ligação no `Main.kt`. Ligar o hover
   continua disparando a primeira leitura como antes; `triggerAtCursor` ficou só com esse uso.
3. **Cartão invisível com a placa de vídeo: é o OpenGL da VM.** O journal registra
   `org.jetbrains.skiko.RenderException: Cannot create Linux GL context` na inicialização e de novo a
   cada cartão criado (16:51:12, 16:51:13, 16:52:00 — esta última no mesmo segundo de um acerto
   `[Hover] 職員 — Jitendex PT-BR`). Com essa exceção a janela do cartão não desenha nada, enquanto o OCR
   e o log seguem funcionando; e cada tentativa de contexto GL custa caro, o que explica a sensação de
   travamento. Com «software rendering» ligado o Skiko não toca no GL e o cartão aparece. Não há mudança
   de código aqui: é o OpenGL do VirtualBox, e a escolha de renderizador já existe nos Ajustes.

**Testes e mutações.** Suíte desktop **526 testes, 0 falhas, 1 pulado**. Três novos em `HoverGateTest`:
o ponto onde o cartão fechou não é lido de novo (`keepQuiet` + repouso longo), a palavra seguinte ainda
abre o cartão, e um guarda de fonte (como `HoverCardTransparencyTest`) que exige `gate.keepQuiet()` e
proíbe `gate.reset()` dentro de `dismiss()` — desenhar cartão precisa de display, então o caminho é
verificado no texto do `HoverOcrManager`. Mutações derrubadas: `keepQuiet()` chamando `reset()`
(**2 falhas**) e o `dismiss()` voltando a chamar `gate.reset()` (**1 falha**).

**Limitações.** (a) Uma leitura de verdade continua tocando o som de obturador do GNOME: é o preço do
portal `Screenshot`, que o app usa porque o ScreenCast do JDK não reaproveita o token do GNOME (D83).
Silenciar de vez exigiria manter um stream ScreenCast aberto — mudança de outra ordem. (b) O cartão no
OpenGL não foi verificado visualmente nesta VM (não há como injetar ponteiro nem julgar desenho sem
tela); o que está provado é a exceção do Skiko no journal e o comportamento que você descreveu.
Nada commitado.

---

## D88 — Hover tenta de novo o mesmo ponto quando a leitura falha (17/11/2026)

**Pedido.** O hover deve poder tentar de novo o mesmo ponto quando a leitura falhou por erro de
captura ou de OCR, mantendo o silêncio quando não há texto.

**Problema.** O D87 ensinou `dismiss()` a manter o ponto quieto, e isso acabou com o laço de capturas —
mas o `dismiss()` não distingue «a leitura respondeu que não há nada» de «a leitura não chegou a
acontecer». Um recorte vazio e uma exceção dentro do `lookupAt` também caem no `fail()`, que chama
`dismiss()`; o ponto onde o erro aconteceu ficava mudo até o ponteiro andar `repeatRadiusPx`. Numa falha
transitória (portal `Screenshot` que estoura o tempo, Lens que cai no meio) o usuário tinha de sacudir o
mouse para a mesma palavra ser lida de novo — a palavra em que ele estava parado era justamente a que
não voltava.

**O que mudou.** `HoverGate.retryLater(point, now)` (novo) apaga o `lastTrigger` e agenda outra tentativa
do **mesmo** ponto: 400 ms (`RETRY_BASE_MILLIS`), dobrando a cada nova falha no mesmo lugar, e no máximo
`MAX_RETRIES` (3) tentativas. O teto não é enfeite: sem ele uma falha permanente voltaria a capturar o
mesmo pixel a cada intervalo de repouso, que é exatamente o laço de obturador do D87; esgotado o
orçamento, o ponto fica quieto como um ponto respondido até o ponteiro andar `repeatRadiusPx`. `observe()`
passou a respeitar a pausa (`retryNotBefore`) e a zerar o orçamento quando o ponteiro sai
(`forgetFailures()`), e `reset()` zera tudo. No `HoverOcrManager` só duas saídas pedem retentativa —
`fail("Recorte de tela vazio.", point)` e `fail(hoverOcrFailure(...), point)` —; «nenhum texto», «sem
entrada no dicionário», «sem dicionário instalado» e hover desligado continuam só fechando o cartão.

**Testes e mutações.** Suíte desktop **530 testes, 0 falhas, 1 pulado**. Quatro novos em `HoverGateTest`:
ponto com falha é lido de novo depois da pausa; 20 falhas seguidas param em exatamente `MAX_RETRIES`
leituras; andar para longe e voltar libera o orçamento; e um guarda de fonte (como o do D87) exigindo
`point` nas duas falhas e proibindo `retryLater` no ramo «nenhum texto». Mutações derrubadas:
`retryLater` mantendo o `lastTrigger` (**3 falhas**), teto trocado por `if (false)` (**2 falhas**) e
`fail("Recorte de tela vazio.")` sem o ponto (**2 falhas**).

**Limitações.** O teto é por ponto, não por sessão: se a captura estiver quebrada de forma permanente, o
usuário ainda vê três leituras por pouso (400/800/1600 ms), não uma. O caminho de retentativa em si (o
cartão reaparecendo depois de um erro) não foi visto em tela nesta VM: falha de captura não se injeta sem
portal, e o cartão depende do renderizador (D87). O que está provado são as regras do portão e a ligação
das duas falhas. Nada commitado.

---

## D89 — A barra de rolagem da lista de dicionários do cartão redimensionava o cartão (17/11/2026)

**Pedido.** A lista de dicionários do cartão do hover não pode ser rolada com a barra: ao pousar o
ponteiro nela, o cursor vira o de redimensionamento e o arrasto redimensiona o cartão.

**Causa raiz.** `HoverTooltipWindow` abre o cartão com `undecorated = true` e deixava `resizable` no
padrão do Compose, `true`. O Compose 1.12 responde a essa combinação com uma borda de redimensionamento
própria: `ComposeWindow.setResizable` faz `undecoratedWindowResizer.enabled = isUndecorated &&
isResizable`, e o `UndecoratedWindowResizer` cria uma faixa de **8 dp** em cada borda da janela
(`WindowDecorationDefaults.resizerThickness`) onde o ponteiro vira cursor de redimensionamento e o
arrasto muda o tamanho da janela. A barra de rolagem da lista fica nos últimos **12 dp** da direita
(`DICTIONARY_SCROLLBAR_GUTTER_DP = 10` de recuo do texto + a barra encostada em `Alignment.CenterEnd`),
ou seja, dentro da faixa: o `ScreenShot` do ponteiro era trocado antes de o `ScrollbarDragModifier`
receber o clique. Não era o gerenciador de janelas — um `JFrame` sem decoração com `Robot` na borda
não é redimensionado pelo Mutter (medido: janela de 460×400, arrasto na borda, tamanho inalterado), e
o `xwd`/XFixes mostram `left_ptr` nessa mesma borda; a única diferença do cartão é o `SkiaLayer`.

**O que mudou.** `resizable = false` na janela do cartão. O `UndecoratedWindowResizer` fica desligado,
a faixa de 8 dp some e a barra de rolagem volta a receber o arrasto. O cartão não tinha o que oferecer
ao usuário nesse gesto — largura fixa (`TOOLTIP_WIDTH = 460`) e altura vinda do conteúdo — e o app
continua redimensionando por `window.setSize` em `applySizeAndPlacement` (`resizable` limita só o
arrasto do usuário; o próprio Compose documenta que a aplicação ainda pode mudar o tamanho pelo
estado). Os dois outros janelas sem decoração do desktop (`SegmentPickerWindow`,
`RegionSelectionWindow`) já passavam `resizable = false`.

**Testes e mutações.** `HoverCardResizeTest` (2 testes novos): o `Window(...)` do cartão exige
`undecorated = true` **e** `resizable = false`, e o arquivo não pode chamar `setResizable(`. Guarda de
fonte porque desenhar o cartão exige display, como `HoverCardTransparencyTest` e `ScrollbarCoverageTest`.
Suíte desktop **532 testes, 0 falhas, 1 pulado**. Mutação derrubada: apagando `resizable = false,` do
cartão, o guarda falha (**1 falha**) e, restaurado, volta a passar.

**Limitações.** O arrasto da barra em si não foi exercitado em tela nesta VM: exige o cartão aberto com
uma lista longa, que depende de captura pelo portal e do renderizador (D87). O que está provado é a
causa raiz (o resizer do Compose ligado por `undecorated && resizable`), a bandeja de 8 dp contra os
12 dp da barra, e o guarda que impede a volta do `resizable` padrão. Nada commitado.

---

## D90 — «Integrar ao sistema» gravava a entrada nova, mas o menu do Plasma abria o AppImage anterior (17/11/2026)

**Relato (Steam Deck, AppImage 1.1.0).** Depois de apertar «Integrar ao sistema» na build nova, abrir
o app pela aba de aplicativos do sistema continuava levantando a versão anterior.

**Causa raiz.** As duas entradas já estavam corretas no disco — a do menu apontava para
`Yabai_OCR-1.1.0-x86_64.AppImage` e o autostart para o mesmo arquivo — mas o Plasma mantém o índice
`ksycoca` e não refez o cache quando o `.desktop` foi reescrito. Na máquina do relato, o
`~/.local/share/applications/yabai-ocr.desktop` era de **21:54** e o `ksycoca6` mais recente de
**21:10**: o menu ainda lia a entrada antiga, abria o AppImage velho, e esse processo reescrevia a
entrada para si mesmo (o laço já descrito na D85/D86). A D85 já apontava o `ksycoca` como suspeito
para este sintoma; faltava agir sobre ele.

**O que mudou.** `LinuxDesktopEntry.kt` ganhou `refreshLinuxApplicationMenu()`: em KDE roda
`kbuildsycoca6 --noincremental`, com `kbuildsycoca5 --noincremental` de fallback; nos outros desktops
roda `update-desktop-database <applications>`. O comando é disparado depois que `install()` grava a
entrada e depois que `ensureTrayIcon()` muda uma entrada existente — não a cada início quando o
conteúdo já está igual. A execução é limitada a 5 s, tem saída descartada e falha em silêncio: a
entrada já está no disco, então um helper ausente não pode desfazer a integração. `Main.kt` injeta o
refresh real; o default do `LinuxSystemIntegration` é vazio para os testes não tocarem no cache do
host.

**Testes e mutações.** Sete testes novos: três em `LinuxSystemIntegrationTest` (o `install()` chama o
refresh; uma entrada alterada chama uma vez e a seguinte não chama; falha do refresh não derruba a
instalação) e quatro em `LinuxMenuRefreshTest` (`kbuildsycoca6` antes do 5, parada no primeiro sucesso,
`update-desktop-database` nos outros desktops e o ambiente do helper sem `LD_LIBRARY_PATH`/`APPDIR` do
AppImage). Suíte desktop **539 testes, 0 falhas, 3 pulados** (aqui, sem display nem barramento de
sessão; no host o baseline continua 1 pulado). Mutações derrubadas: remover o refresh do `install()`
(**1 falha**), remover o refresh da entrada alterada (**1 falha**) e trocar a ordem/tirar o
`kbuildsycoca6` (**2 falhas**).

**Limitações.** O comando externo não foi executado nesta VM (não há Plasma nem `kbuildsycoca6`); o
que está provado é a chamada no ponto certo, a escolha dos comandos e o comportamento não fatal. A
confirmação final é no Deck: integrar na build nova e abrir pelo menu sem sair da sessão. Se o
`kbuildsycoca6` não existir no sistema, o fallback manual está em `STEAMOS.md`. Nada commitado.

---

## D91 — O ícone da bandeja mostra o estado do hover (17/11/2026)

**Pedido.** O ícone da bandeja, no Windows e no Linux, deve mostrar de relance se o hover está ligado,
sem abrir o menu nem esperar o tooltip.

**Causa raiz.** `AppIconGenerator.createTrayImage()` devolvia sempre o mesmo PNG/ICO. O estado do hover
só existia no rótulo do menu e no cartão; a bandeja — a única parte do app sempre visível — não dizia
nada. No Windows o `TrayIcon` AWT ficava com a mesma imagem para sempre; no Linux o
`StatusNotifierItem` publicava um `IconPixmap` fixo e um `IconName` fixo.

**O que mudou.** `createTrayImage(hoverActive)` compõe o ícone base com um selo no canto inferior
direito: ponto verde quando ligado, ponto vermelho com traço branco quando desligado. O anel branco e o
contorno escuro mantêm o selo legível em painel claro e escuro, e o traço é a pista de forma para quem
não distingue as cores. `trayHoverBadgeGeometry()` mantém o selo inteiro dentro do quadrado (8 px de
diâmetro a 16 px, 12 px a 32 px e 86 px a 256 px). `SystemTrayManager.setup()` lê `isHoverActive()` e
já cria o primeiro ícone no estado certo; `updateHover()` troca `TrayIcon.image` no AWT e chama
`StatusNotifierTray.updateHover()`. No SNI, `StatusNotifierItem` guarda os dois pixmaps, troca de
`IconName` (`yabai-ocr-hover-on`/`-off`) e `setHoverActive()` diz se houve mudança; `NewIcon` faz o
host reler. `LinuxSystemIntegration` grava as duas variantes de tema junto do PNG base, para o
AppIndicator do GNOME que resolve `IconName` pela tema também mostrar o ponto.

**Testes e mutações.** Suíte desktop **546 testes, 0 falhas, 3 pulados** (sandbox sem display nem
barramento de sessão; no host o baseline continua 1 pulado). Sete testes novos: quatro em
`TrayHoverStatusIconTest` (cor do ponto ativo, ponto inativo com traço, geometria que não corta a 16 px
e guarda de fonte das duas ligações AWT/SNI), dois em `StatusNotifierTrayTest` (o pixmap e o `IconName`
seguem o estado; o item já começa no estado pedido) e um em `LinuxSystemIntegrationTest` (as duas
variantes de tema são gravadas e são imagens diferentes). Mutações derrubadas: apagar o desenho do selo
(**2 falhas**), `setHoverActive` não atualizar o estado (**1**), remover `trayIcon?.image = ...` (**1**),
não repassar o estado inicial (**1**), fixar `IconName` no nome desligado (**1**) e não gravar as
variantes de tema (**1**).

**Limitações.** O painel real do Windows, do Plasma e do GNOME não foi visto nesta VM: não há Windows,
display nem sessão gráfica. O que está provado são os pixels gerados pela função de produção (prévias
inspecionadas a 16, 32 e 256 px), a troca de estado no SNI e as duas ligações que não rodam sem display,
cobertas por guarda de fonte. A confirmação final é abrir a build no Deck e no Windows e alternar o
hover pelo atalho e pelo menu. Nada commitado.

## D92 — A checagem periódica de dicionários não rodava fora da janela, e o achado não aparecia (18/11/2026)

**Pedido.** A verificação automática dos dicionários (diária/semanal/mensal) não parecia funcionar nem
no Windows nem no Ubuntu. Em vez de um segundo popup, um só: o mesmo aviso do aplicativo, carregando
app e dicionários quando as duas coisas têm versão nova.

**Causa raiz.** Três defeitos na mesma cadeia. (1) A checagem morava no `LaunchedEffect(Unit)` de dentro
do `Window` que só existe `if (showMainWindow)` — com `--minimized` (bandeja) ou depois de fechar para a
bandeja ela nunca rodava, e abrir a janela uma vez gravava `last_dict_update_check`, adiando o próximo
período inteiro. (2) Mesmo quando rodava, o resultado ia só para `SystemTrayManager.setStatus()`, que é
tooltip do ícone: no GNOME sem AppIndicator nem bandeja existe, então nada aparecia. (3) Nada guardava o
que já tinha sido avisado, então um app de bandeja repetiria o mesmo aviso a cada período.

**O que mudou.** `DictionaryUpdateMonitor.kt` (novo) é o laço: acorda a cada 15 min, pergunta o
intervalo a `DesktopDictionaryUpdateChecker.shouldCheck`, só então toca a rede, e faz backoff de 1 h
depois de falha; `Main.kt` cria o monitor no escopo da aplicação, ao lado da checagem do app (o bloco
saiu de dentro da janela). O monitor grava `last_dict_update_notice` com a chave
`arquivo:revisão`, a mesma ideia do `UpdateStateStore.notifiedKey()` do Android, e só um conjunto novo
de revisões abre aviso de novo; sem oferta, a chave é limpa para uma revisão futura voltar a aparecer.
`DesktopDictCheckResult` ganhou `installedRevisionMap` para o aviso imprimir «instalada → nova».
`UpdatesNoticeWindow.kt` (novo, no lugar de `AppUpdateNoticeWindow.kt`) é a janela única: seção do app
(baixar, reiniciar, notas) e seção dos dicionários (nome, revisões, «Atualizar dicionários»); enquanto
o download do app está em curso ou há «Reiniciar agora» pendente, a seção de dicionários fica
desabilitada com o motivo escrito, porque o reinício mataria o download pela metade. A instalação
reaproveita `downloadAndInstallDictionary` do gerenciador (agora `internal`), com `closeCached()` já no
caminho. `SystemTrayManager` ganhou o item «⬆️ Atualizações… (N)» que reabre o aviso sem checar de novo.

**Testes e mutações.** Suíte desktop **555 testes, 1 falha** (a mesma antes e depois desta mudança:
`LinuxDesktopEntryTest` falha nesta máquina porque `Files.createTempDirectory` devolve o caminho curto
`ERICPC~1`; provado com `git stash` na árvore limpa). Nove testes novos: quatro em
`DictionaryUpdateMonitorTest` (o período vencido checa sem janela e checa de novo no período seguinte;
«desativado» nunca toca a rede; falha espera o backoff em vez de repetir a cada acordar; o laço segue
sozinho até ser cancelado) e cinco em `DictionaryUpdateOffersTest` (oferta com as duas revisões;
mesma revisão remota anunciada uma vez; revisão nova anuncia de novo; dicionário sem entrada no catálogo
não é ofertado; checagem vazia não anuncia nada). Mutações derrubadas: `shouldCheck` trocado por
`true` (**3 falhas**), laço trocado por `repeat(1)` (**1**), chave de aviso ignorada na dedupe (**1**) e
arquivo sem catálogo caindo no primeiro item da lista (**1**).

**Evidência ponta a ponta.** `YABAI_HOME` temporário (o `settings.properties` do usuário não foi
tocado), `dictionary_update_interval=DAILY`, `last_dict_update_check=0`, `--minimized`: o log trouxe
`[Dicionários] verificação automática: 0 atualização(ões) disponível(is)` com o app só na bandeja, e o
arquivo temporário terminou com `last_dict_update_check=1795023332151`. Antes desta mudança essa linha
não podia existir sem a janela de Ajustes composta. Processo encerrado por PID.

**Limitações.** Não houve checagem com dicionário realmente desatualizado (o E2E usou a pasta vazia):
prova que o gatilho roda minimizado, não que a janela lista os itens. A janela em si não foi vista
rodando; o layout é o mesmo da janela anterior, com a guarda `NoticeWindowLayoutTest` apontada para o
arquivo novo. Nada commitado.

## D93 — Revisão da rodada anterior: uma checagem só, e o E2E com dicionário de verdade (18/11/2026)

**Pedido.** Olhar o D92 com olhos frescos: parâmetros que só existem para o teste, lógica duplicada entre
a janela nova e `DictionaryManagerWindow` (o botão «Verificar Atualizações» parecia uma cópia do checker),
simplificar sem mudar comportamento, e um E2E com um `.yabai` realmente antigo.

**O que estava duplicado.** O botão «Verificar Atualizações» do gerenciador carregava a própria caminhada
de rede (OkHttp, leitura do `index.json`, fallback de release do GitHub e um `compareRevisions` próprio):
uma segunda cópia de `DesktopDictionaryUpdateChecker.checkUpdates`, que já existia para a checagem
automática. A regra «qual entrada do catálogo é este arquivo» aparecia em três lugares (checker,
«Atualizar todos» e a lista de instalados), e `compareRevisions` tinha duas versões no desktop.

**O que mudou.** O botão agora chama `DesktopDictionaryUpdateChecker.checkUpdates(settings)` e só leva o
resultado para a UI; a cópia interna e o `compareRevisions`/`revisionParts` do gerenciador saíram. A
regra do catálogo virou `catalogEntryForDictionaryFile()` em `DesktopDictionaryCatalog.kt`, usada pelos
três pontos. O monitor perdeu `idleMillis` e `retryMillis` do construtor (eram só botões de teste,
idênticos às constantes) e ficou com `now` e `check`, o mínimo para provar o relógio e a rede sem
esperar um dia; `retryAt` e as constantes continuam internos. Efeito colateral aceito: a checagem manual
também grava `last_dict_update_check`, então o próximo período conta a partir dela.

**Teste que pega o bug original.** `UpdateCheckScopeTest` lê `Main.kt` (como `NoticeWindowLayoutTest`) e
exige a criação do monitor e o `LaunchedEffect(dictionaryMonitor)` com quatro espaços de indentação,
antes do bloco `// Main Window`; a chamada a `run` precisa cair entre os dois. Mutação: monitor movido
para dentro do `Window` → **1 falha** na asserção da declaração; restaurado e conferido por `grep`.

**E2E com dicionário desatualizado.** Com `YABAI_HOME` temporário, um `jmdict-en.yabai` compilado pelo
próprio `DictionaryCompiler` com `revision=2020-01-01`, `dictionary_update_interval=DAILY` e
`last_dict_update_check=0`, iniciado com `--minimized`: o log trouxe «1 atualização(ões) disponível(is)»,
o `settings.properties` terminou com `last_dict_update_notice=jmdict-en.yabai:2026-11-18`, e a janela
(capturada por `GetWindowRect` num processo DPI-aware) listou «JMdict (inglês) —
2020-01-01 → 2026-11-18» com o botão «Atualizar dicionários». O clique real no botão baixou o release
(15.639.076 bytes), compilou e gravou `revision=JMdict.2026-11-18` com 528.070 verbetes, mais o
`signature.properties`; `DesktopDictLoader.cachedEngine` com o mesmo `YABAI_HOME` — o provedor do picker —
passou a responder「日本」, que a compilação antiga (só「食べる」) não tinha. Janela e processo encerrados
por PID.

**Suíte e limitações.** `:desktop:test` fechou em **556 testes, 1 falha, 6 pulados**; a falha é a mesma de
sempre (`LinuxDesktopEntryTest`, caminho curto `ERICPC~1` do `Files.createTempDirectory` no Windows). A
primeira captura de tela saiu cortada porque o PowerShell não é DPI-aware (a janela é 726×433 físicos num
display a 150%); refeita com `SetProcessDPIAware`. Nada commitado.

## D94 — O hover devolvia memória ao desligar e reconhecia a página inteira a cada leitura (18/11/2026)

**Pedido.** Dois problemas relatados pelo usuário: no Windows o app passava de 1 GB de RAM só aberto na
bandeja, **com o hover desativado**; e no Ubuntu (VM, GNOME Wayland) o hover às vezes levava mais de 5 s,
com palavras que só eram identificadas depois de tirar o mouse e voltar.

Medido antes de mexer (VM de 4 núcleos, 12 GB, 9 dicionários, `engine=PP_OCR`):

| o quê | número |
| --- | --- |
| índice dos 9 dicionários abertos | **123 MB** de heap (431 ms para abrir) |
| uma leitura PP-OCR da tela (1920×892) | **3,0–5,9 s** para 54–55 linhas |
| captura pelo portal `Screenshot` (GNOME) | 1,2 s na primeira, **~300 ms** nas seguintes |
| sessões ONNX do PP-OCR depois de uma leitura | **+245 MB** nativos, e `close()` devolvia 166 MB |

- **Causa raiz.** Três, independentes.
  - O hover abria o `LookupEngine` e as sessões ONNX na primeira leitura e **nunca os soltava**:
    `setEnabled(false)` parava o poll e fechava o cartão, mas o índice dos dicionários e as sessões
    do PP-OCR ficavam no processo. Desligar o recurso para parar o trabalho não devolvia nada.
  - O hover reconhecia **todas** as linhas do monitor (`PPOcrEngine.recognizePixels` sem foco). O
    detector já roda num lado maior de 960 px e custa o mesmo; o reconhecedor custa ~100 ms por
    linha, e a página tinha cinquenta. O hover é uma pergunta sobre uma palavra.
  - Nos bridges do compositor (KWin/GNOME) o `HoverOcrManager` só era acordado pelo evento de
    ponteiro. O gate que espera a parada, a contagem da carência de fechamento e a **retentativa**
    de uma leitura que falhou (D88) só avançam em `tick()`, e uma leitura recusada por já haver
    duas em voo (D83) chama `gate.reset()` — que depende de novos ticks para rearmar. Com o mouse
    parado não havia tick nenhum: a palavra ficava sem resposta até o ponteiro sair e voltar.
- **Correção.**
  - `HoverDictionaryCache` (novo): mantém o motor enquanto a pasta não muda e `release()` o fecha;
    `HoverOcrManager.setEnabled(false)` chama `release()` e `releaseOcrSessions()` (padrão:
    `DesktopPPOcr.close()`), então desligar o hover devolve dicionários **e** sessões ONNX.
  - `PPOcrEngine.recognizePixels(..., focus)` e `linesToRecognize` (novo, em
    `PPOcrFocus.kt`): o detector continua vendo a imagem inteira — é o que mantém legível uma palavra
    partida entre duas colunas — e só as linhas a até 160 px do ponteiro são reconhecidas. Cada
    linha é lida nos próprios pixels, então o texto das linhas lidas não muda. O hover passa o ponto
    local (`DesktopPPOcr`/`DesktopOcr` ganharam o parâmetro `focus`); janela de resultado, picker e
    Android continuam sem foco, lendo tudo.
  - O hover passa a fazer poll também nos bridges (`BRIDGE_PUMP_MILLIS = 120 ms`): só alimenta o
    gate com a última posição conhecida, nunca captura duas vezes no mesmo ponto.
  - `jvmArgs += "-Xmx1g"`: sem teto o G1 cresce até 1/4 da RAM da máquina antes de coletar.
  - Menores: a lista de dicionários do cartão é lida uma vez por cartão (`remember`), não a cada
    recomposição da raiz; a captura de tela do «Trechos» é liberada ao fechar a janela de resultado.
- **Arquivos.** `desktop/.../HoverOcrManager.kt` · `desktop/.../HoverDictionaryCache.kt` ·
  `core/.../ppocr/PPOcrFocus.kt` · `core/.../ppocr/PPOcrEngine.kt` · `desktop/.../DesktopPPOcr.kt` ·
  `desktop/.../DesktopOcr.kt` · `desktop/.../Main.kt` · `desktop/build.gradle.kts`

Testes: **562 no desktop** (6 novos: `HoverDictionaryCacheTest` com 3, `HoverOcrManagerLifecycleTest`
com 3) e **202 no core** (5 novos em `PPOcrFocusTest`). Quatro mutações, cada uma derrubando o teste
certo e voltando ao verde ao restaurar:

| mutação | teste que acusa |
| --- | --- |
| `linesToRecognize` ignora o raio e devolve todas as linhas | `PPOcrFocusTest > dropsEveryLineOfARestingPageWhenThePointerIsOnNothing` (e mais duas) |
| `setEnabled(false)` deixa de chamar `dictionaryCache.release()` | `HoverOcrManagerLifecycleTest > turningTheHoverOffReleasesTheDictionaries` |
| `setEnabled(false)` deixa de chamar `releaseOcrSessions()` | `HoverOcrManagerLifecycleTest > turningTheHoverOffAlsoReleasesTheOfflineReaderSessions` |
| poll só no backend AWT (como antes) | `HoverOcrManagerLifecycleTest > aBridgeBackendRetriesAFailedReadWithoutMovingThePointer` |

Ponta a ponta (app rodando em `--minimized`, GNOME Wayland, 9 dicionários, `engine=PP_OCR`; hover
ligado e desligado pelo `ShortcutPressed("hover-toggle")` do D-Bus): RSS **176 MB** recém-aberto →
**605 MB** com o hover ligado depois de duas leituras → **281 MB** ao desligar e coletar. Com só a
liberação dos dicionários (medida intermediária da mesma sessão) o ciclo ia a **717 MB e voltava a
453 MB**: as sessões ONNX, ~172 MB, continuavam no processo. No mesmo screenshot, leitura completa × focada
com o mesmo motor: **4081–4700 ms / 54–55 linhas** contra **543–1434 ms / 4–6 linhas**, com a linha
sob o ponteiro **idêntica** nos três pontos medidos (`identical=true`).

Confirmado no vertical do navegador (modo タテ読み do Comic Walker, páginas reais lidas do canvas do
Chromium): 6 quadros 1920×955 e **48 leituras focadas** contra a completa nos mesmos pontos (centro
das linhas e borda de coluna) — **palavra, caixa do token e texto da linha idênticos nas 48**. A
leitura completa levou 320–736 ms para 7–16 linhas; a focada, **285 ms em média para 2,8 linhas**.

Teto de heap provado: harness com o mesmo `DesktopPPOcr` e o mesmo `LookupEngine` do app, rodando com
`-Xmx1g` exato (1024 MB de máximo), sobre um quadro **4K (3840×2160)** com oito páginas de mangá e
**os 9 dicionários abertos** — 85 linhas lidas em 2015 ms, **pico de heap de 295 MB**, 171 MB
retidos depois do GC e **587 MB de RSS de pico** do processo; nenhum OOM e ~71% de folga no heap. O
`-Xmx1g` fica.

Limitação conhecida: com `engine=PP_OCR` a primeira leitura depois de religar o hover paga de novo
a abertura das sessões (~3 s nesta VM) — é o preço de não manter 245 MB parados. A captura pelo
portal do GNOME (~300 ms) e o detector sobre a imagem inteira continuam no caminho. A medição de 4K
é da pilha OCR + dicionários num harness, não do app inteiro: esta VM não tem saída 4K, e a janela
Compose/skiko não entra nesse número de heap.

## D95 — Devolver o heap ao desligar o hover  `[x]`

**Pedido.** O app na bandeja, com o hover desativado, ficava em 600 MB no Ubuntu X11 e não voltava
depois de usar o hover (que chegava a passar de 1 GB). O D94 já libera os dicionários e as sessões
ONNX ao desligar; faltava o passo seguinte, que é fazer o processo devolver o heap.

- **Causa raiz.** Desligar o hover torna o índice dos dicionários e as sessões ONNX inalcançáveis,
  mas um G1 ocioso não tem alocação que dispare ciclo nenhum: o heap fica no pico (medido: 409 MB de
  commit com 9,5 MB vivos depois de um `GC.run`) e o RSS acompanha o commit.
- **Correção.**
  - `HoverOcrManager` ganhou `trimHeap` injetável (padrão `System.gc()`), chamado 1,5 s depois de
    `dictionaryCache.release()` e `releaseOcrSessions()`, no mesmo `scope.launch` — o cartão some
    antes da pausa e o trim não roda na thread da UI.
  - `clearLastRead()` limpa `lastLensResult`, o ponto, o recorte, o tamanho da imagem, o `lastAnswer`
    e o mapa de `Robot` ao desligar e ao fechar o app.
- **Arquivos.** `desktop/.../HoverOcrManager.kt` ·
  `desktop/src/test/.../HoverOcrManagerLifecycleTest.kt`

Testes: **566 no desktop** (1 novo: `turningTheHoverOffTrimsTheHeap`). Mutação provada:

| mutação | teste que acusa |
| --- | --- |
| `setEnabled(false)` deixa de chamar `trimHeap()` | `turningTheHoverOffTrimsTheHeap` |

Ponta a ponta (19/11/2026, Ubuntu 24.04/GNOME 46/Wayland, app empacotado com `--minimized`, 9
dicionários instalados, `engine=PP_OCR`, clip de japonês em tela cheia; hover ligado e desligado pelo
`ShortcutPressed("hover-toggle")` do D-Bus, sem tocar no mouse real):

| momento | heap commit/usado (`jcmd GC.heap_info`) | RSS |
| --- | --- | --- |
| repouso, recém-aberto | 65 536K / 24 760K | 191 MB |
| 7 leituras (散歩しよう, 誰にも, できなかった…) | 638 976K / 268 423K | 893 MB |
| `hover-toggle` off, +1 s | 638 976K / 311 431K | 710 MB |
| +3 s (trim) | **65 536K / 17 124K** | **370 MB** |
| +25 s | 65 536K | 377 MB |

Um ciclo a menos de 3 s devolve o commit inteiro do pico (638 MB → 65 MB) e o RSS cai de 893 MB
para 370 MB. O que **não** volta é o repouso de 191 MB: o resíduo de ~180 MB é nativo (arenas do
glibc, superfícies do Skiko, sessões ONNX fechadas mas não mapeadas de volta), e é o insumo de
D98/D100.

## D96 — Flags de higiene de heap no app empacotado  `[x]`

**Pedido.** Mesmo com o trim do D95, o app ocioso pode acumular lixo jovem e o commit inicial é
grande em máquinas com muita RAM.

- **Causa raiz.** Sem `-Xms`, o JVM commita `1/64` da RAM no start (768 MB numa máquina de 48 GB);
  sem `G1PeriodicGCInterval`, o app parado não roda ciclo algum.
- **Correção.** `desktop/build.gradle.kts` passou a levar `-Xms64m`,
  `-XX:+ExplicitGCInvokesConcurrent`, `-XX:G1PeriodicGCInterval=60000` e
  `-XX:G1PeriodicGCSystemLoadThreshold=0` junto de `-Dfile.encoding=UTF-8 -Xmx1g`.
- **Arquivos.** `desktop/build.gradle.kts`

Evidência de empacotamento: `:desktop:createDistributable` gerou `app/Yabai OCR.cfg` com as cinco
opções (`-Xmx1g`, `-Xms64m`, `-XX:+ExplicitGCInvokesConcurrent`,
`-XX:G1PeriodicGCInterval=60000`, `-XX:G1PeriodicGCSystemLoadThreshold=0`).
Ponta a ponta (19/11/2026, mesmo ambiente): o app real ocioso por **120 s** ficou em 65 536K de
commit com 15 741K usados e o RSS andou de 377 200 kB para **377 248 kB** (+48 kB, ruído), com
YGC 27→29 e CGC 20 sem nenhuma coleta concorrente nova e FGC 0 — o GC periódico não manteve o
heap ocupado nem causou pausa perceptível no hover que sucedeu a medição.

## D97 — O picker fechava e deixava o motor de dicionários aberto  `[x]`

**Pedido.** Fechar o seletor de tela inteira devolvia a memória da captura, mas o índice dos
dicionários continuava aberto pelo resto da sessão.

- **Causa raiz.** `Main.kt` entrega `DesktopDictLoader.cachedEngine` ao `SegmentPickerWindow` e o
  `onDismiss` só limpa `pickerCapture`; o motor só é fechado pelo gerenciador de dicionários.
- **Correção.**
  - `PickerDictionaryLease` (novo) empresta o motor no `engine(settings)` e o devolve no `close()`,
    uma vez só; um picker cancelado sem consultar nada não fecha um motor que não abriu.
  - `Main.kt` cria o lease com `remember`, passa `pickerDictionary.engine(settings)` ao picker e o
    fecha num `DisposableEffect(picker)`, cobrindo parágrafo, recorte à mão e Escape.
- **Arquivos.** `desktop/.../PickerDictionaryLease.kt` · `desktop/.../Main.kt` ·
  `desktop/src/test/.../PickerDictionaryLeaseTest.kt`

Testes: **566 no desktop** (3 novos em `PickerDictionaryLeaseTest`). Mutações provadas:

| mutação | teste que acusa |
| --- | --- |
| `close()` deixa de chamar o `closer` | `theEngineIsClosedWhenThePickerGoesAway` (e `theEngineIsClosedOnlyOnce`) |
| `close()` ignora o `borrowed` e fecha sempre | `aPickerThatNeverLookedAnythingUpClosesNothing` |

Ponta a ponta (19/11/2026, mesmo ambiente do D95): `capture-fullscreen` pelo D-Bus abriu o picker e
carregou o índice — heap **484 352K / 172 836K usados**, RSS **975 012 kB** (repouso anterior:
65 536K / 377 436 kB). Um Escape fechou a janela e devolveu o motor: heap **75 776K / 21 994K** e
RSS **615 144 kB** (estável em +10 s, 47 threads, YGC 90, CGC 62). O índice de ~150 MB sai do heap
na hora; o que sobra em RSS é o resíduo nativo já descrito no D95.

## D98 — Reutilizar a janela do cartão  `[x]`

**Pedido.** O RSS nativo do Linux não voltava ao valor de repouso depois de muitos hovers.

- **Causa raiz.** Cada palavra criava e destruía uma `ComposeWindow`/`SkiaLayer`; as superfícies
  nativas liberadas ficam nas arenas do glibc (a parte que nem `System.gc()` devolve).
- **Correção.**
  - `HoverTooltipWindow` ganhou `visible`; a janela é criada **uma vez** pelo overlay e depois só
    escondida/mostrada. Enquanto escondida ela devolve `onBoundsChanged(null)` (o hover não pode
    achar que o ponteiro está num cartão que não está na tela) e, quando volta, é medida e
    posicionada antes do debounce de 80 ms — sem isso a palavra seguinte aparecia no canto da
    anterior.
  - `Main.kt` só cria a composição depois do primeiro cartão (`cardCreated`), então um app que só
    vive na bandeja continua sem pagar janela nenhuma; e o menu de dicionários fecha a cada cartão
    novo.
- **Arquivos.** `desktop/.../Main.kt` · `desktop/.../HoverTooltipWindow.kt` ·
  `desktop/src/test/.../HoverCardWindowReuseTest.kt`

Testes: **573 no desktop** (3 novos em `HoverCardWindowReuseTest`). Mutações provadas:

| mutação | teste que acusa |
| --- | --- |
| `Window(...)` deixa de receber `visible = visible` | `the card window follows the visible flag` |
| o overlay volta a chamar o cartão dentro de `if (hPoint != null …)` | `the card is shown and hidden instead of created per word` |
| o cartão escondido deixa de limpar `onBoundsChanged(null)` | `a hidden card holds no bounds` |

Ponta a ponta (19/11/2026, mesmo protocolo: app recém-aberto, clip japonês em tela cheia, 12
posições × 2 colunas pelo `CursorMoved` do D-Bus, hover desligado e medido 35 s depois):

| build | repouso (RSS/PSS) | com hover (19–20 leituras) | desligado +35 s (RSS/PSS) | resíduo |
| --- | --- | --- | --- | --- |
| anterior | 192 724 / 183 441 kB | 1 015 384 kB | **485 576 / 411 098 kB** | +293 MB |
| D98+D99 | 209 056 / 199 780 kB | 853 556 kB | **395 256 / 382 547 kB** | +186 MB |

Um segundo ciclo seguido no build novo (24 leituras mais, e mais um desligamento) levou o repouso de
395 MB para **434 MB**: +39 MB, contra os +186 MB do primeiro ciclo, que ainda pagava o aquecimento
(JIT, primeira janela, bibliotecas nativas). Não há acúmulo por palavra.

Inspeção visual na mesma sessão (19/11/2026, clip japonês em tela cheia, ponteiro só pelo
`CursorMoved` do D-Bus): o cartão é **um só** — janela `Yabai OCR - Hover` de 460×158 em `+218+138`
com o ponteiro em (200, 120), sobre 散歩しよう; ao ir para (260, 240), 誰にも言えなかった, a mesma
janela passou para `+278+258` e 460×314 com a entrada de 言える no Jitendex PT-BR — visível na
tela ao lado da palavra; em (220, 600), 楽しかった, foi para `+238+618`; ao sair do texto, o
`Map State` da mesma janela virou `IsUnMapped` (nada de janela nova, nada de cartão órfão). O log
da sessão não tem nenhum `Reentry into ignoringRedrawRequests` nem exceção, e o cartão apareceu no
primeiro frame de cada palavra.
Limitação: é uma execução por build, numa VM (VirtualBox, 1920×955) — ruído de ±30 MB é esperado;
Windows não foi medido nesta rodada (o cartão é o mesmo código, mas o renderizador não).

## D99 — Reduzir a alocação por leitura do hover  `[x]`

**Pedido.** O hover contínuo enche o heap de lixo mais rápido do que o G1 o recolhe.

- **Causa raiz.** Cada leitura alocava o `IntArray` do recorte (altura inteira do monitor: ~8 MB a
  1080p) e um `BufferedImage`; com duas leituras em voo eram dezenas de MB por segundo.
- **Correção.** `PixelBuffer` (novo) guarda o `IntArray` e o devolve enquanto o tamanho da imagem
  não muda; `DesktopPPOcr.recognize` copia a imagem para ele (`@Synchronized`, então nenhuma
  leitura divide o buffer com outra) e o recorte continua com a altura inteira, que é intencional
  (palavra partida entre colunas, D94). O `BufferedImage` não é reaproveitável: `Robot` e o portal
  do GNOME alocam um por captura, e copiar para um buffer fixo só somaria trabalho.
- **Arquivos.** `desktop/.../PixelBuffer.kt` · `desktop/.../DesktopPPOcr.kt` ·
  `desktop/src/test/.../PixelBufferTest.kt`

Testes: **573 no desktop** (4 novos em `PixelBufferTest`). Mutação provada: `of()` alocando sempre
(`pixels = IntArray(size)` sem a comparação) derruba `the same size gives the same buffer back` e
`the buffer can come back after a size change`.

Sem número isolado: a troca de buffer só muda o lixo entregue ao G1, não o pico do processo, e foi
medida junto do D98 (o ciclo de 19–20 leituras do build novo usou no máximo 183 MB de heap vivo,
contra 224 MB no anterior).

## D100 — Limitar o pior caso e medir o glibc no Linux  `[x]`

**Pedido.** Fechar o teto do pior caso e provar o que o alocador nativo segura.

- **Causa raiz.** `-Xmx1g` é folgado para o pior caso medido (295 MB de pico de heap) e o glibc
  pode segurar páginas do Skiko depois de liberá-las.
- **Medição do glibc** (19/11/2026, PSS do `/proc/<pid>/smaps_rollup`; não há `smem` nesta
  máquina). Os ciclos só entram na mesma comparação quando são do mesmo tipo: um ciclo com cartão
  cria e move a janela do cartão, um ciclo sem correspondência no dicionário não a mostra, e o
  resíduo nativo não é o mesmo nos dois casos.

Ciclo **sem cartão** (texto do chat sob o ponteiro, leituras sem correspondência, 24 posições):

| configuração | repouso (RSS/PSS) | pico no ciclo | desligado +35 s (RSS/PSS) | resíduo |
| --- | --- | --- | --- | --- |
| padrão | 206 124 / 196 894 kB | 976 128 kB | 353 456 / 344 095 kB | +147 MB |
| `MALLOC_ARENA_MAX=2` | 198 336 / 189 127 kB | 855 656 kB | **295 712 / 286 372 kB** | **+97 MB** |

Ciclo **com cartão** (clip japonês em tela cheia, leituras com correspondência — o mesmo protocolo
com que o D98 foi medido; não houve execução com `MALLOC_ARENA_MAX=2` nesse cenário, então ele fica
fora da comparação):

| configuração | repouso (RSS/PSS) | pico no ciclo | desligado +35 s (RSS/PSS) | resíduo |
| --- | --- | --- | --- | --- |
| padrão (D98+D99) | 209 056 / 199 780 kB | 853 556 kB | 395 256 / 382 547 kB | +186 MB |

- **Decisão: não adotado (ainda).** No único par medido no mesmo tipo de ciclo (sem cartão), a
  variável corta **50 MB** do resíduo (147 → 97 MB) e ~120 000 kB do pico
  (976 128 → 855 656 kB). O número maior que aparecia antes neste item vinha de comparar o resíduo
  de um ciclo com cartão (186 MB) com o de um ciclo sem cartão (97 MB). Só o AppImage consegue levá-la (o `AppRun` é um script do
  repositório); o `.deb` usa o launcher nativo do jpackage, que não passa por shell nenhum, e adotar
  num pacote e não no outro divide o comportamento. O custo de contenção de duas arenas num processo
  que aloca em ONNX + Skiko não foi medido, e a política do projeto é adotar com número dos dois
  lados.
- **Decisão: `-Xmx` fica em 1g.** Com `-Xms64m` e o G1 devolvendo commit, `-Xmx` é teto e não
  reserva: baixar para 640m não muda o RSS de um app ocioso e tira margem do cenário que esta VM não
  reproduz (tela 4K com os nove dicionários), onde o harness mediu 295 MB de pico vivo.
- **Arquivos.** `desktop/build.gradle.kts` (sem mudança) · empacotamento Linux
- **Próximo passo, se o Linux voltar à mesa:** medir latência de OCR e de desenho com 2 e 4 arenas e,
  com número dos dois lados, exportar `MALLOC_ARENA_MAX` no `AppRun` do AppImage e explicar no
  `docs/LINUX.md` por que o `.deb` fica de fora.

## D101 — O aviso de atualização na bandeja sobrevivia à atualização  `[x]` (21/11/2026)

**Pedido.** Depois de atualizar os dicionários, o aviso continuava aparecendo ao passar o mouse no ícone
da bandeja.

- **Causa raiz.** Duas coisas no mesmo lugar:
  1. `SystemTrayManager.setStatus(...)` só era chamado quando a checagem achava atualização; nada
     escrevia o tooltip de volta. Instalar pelo aviso zerava `pendingDictionaryUpdates` e
     `last_dict_update_notice`, mas não o texto da bandeja, que ficava até fechar o app.
  2. `dictionaryOffersToAnnounce` devolvia lista vazia para «nada instalável» **e** para «o mesmo
     conjunto já anunciado», e o `Main.kt` zerava `last_dict_update_notice` nos dois casos. O mesmo
     conjunto voltava a ser anunciado na checagem seguinte, e a distinção que faltava era justamente a
     que decide limpar o tooltip.
- **Correção.**
  - `dictionaryAnnouncement(result, alreadyShownKey)` substitui `dictionaryOffersToAnnounce` e devolve
    `Clear` (nada instalável), `Keep` (o mesmo conjunto) ou `Show(offers)`. A regra de deduplicação
    continua uma só.
  - No `Clear` saem a chave, `pendingDictionaryUpdates` e o tooltip.
  - `onDictionaryUpdatesInstalled` limpa o tooltip junto do resto, sem esperar a próxima checagem.
- **Correção da revisão (mesmo dia).** O tooltip é uma linha só para os dois avisos, e o do app
  («✨ Nova versão vX disponível!») é escrito na inicialização: `setStatus("")` no fim do aviso de
  dicionário apagava também ele. Os dois pontos passam por `trayTooltipAfterDictionaryNotice(appUpdate)`,
  que devolve o aviso de versão quando ele ainda vale e vazio quando não há nenhum.
- **Arquivos.** `desktop/.../DictionaryUpdateMonitor.kt` · `desktop/.../Main.kt` ·
  `desktop/src/test/.../DictionaryUpdateOffersTest.kt` ·
  `desktop/src/test/.../TrayDictionaryNoticeTest.kt`

Testes novos: 3 em `TrayDictionaryNoticeTest` (o último, da revisão acima, incluído) e 1 em
`DictionaryUpdateOffersTest`; a suíte desktop fechou em **595 testes** com a revisão.
Mutações provadas:

| mutação | teste que acusa |
| --- | --- |
| `onDictionaryUpdatesInstalled` deixa de limpar o tooltip | `installing the announced dictionaries clears the tray tooltip` |
| `trayTooltipAfterDictionaryNotice` devolve sempre vazio | `the version notice survives the end of the dictionary one` |
| `Keep` volta a zerar `last_dict_update_notice` | `only a check with nothing installable forgets the announcement` |

## D102 — O cartão do hover salta para cima ao abrir o dicionário  `[x]` (21/11/2026)

**Pedido.** Com o cartão perto da barra de tarefas, clicar no dicionário do cartão faz o cartão mudar
para uma posição acima; o ponteiro sai de cima dele e o cartão some.

- **Causa raiz.** `planCardPlacement` recalculava o canto do cartão a partir do ponteiro **com a altura
  nova** a cada mudança de altura. Com a lista aberta o cartão ganha algumas centenas de pixels; perto da
  barra de tarefas o cálculo «não cabe embaixo» passava a valer e o cartão inteiro era jogado para cima do
  ponteiro. O ponteiro e o clique do cabeçalho ficavam fora do cartão novo, o `HoverDismissGate` contava
  como saída e o cartão fechava. No cenário do E2E (área útil 1280×672, ponteiro em `y=420`), o cartão
  fechado fica em `y=438`; com a lista aberta o cálculo antigo o jogava para `y=42` (360 px de altura),
  deixando o clique do cabeçalho em `y=453` fora do cartão.
- **Correção.** O cartão guarda o ponteiro para o qual foi posicionado (`placedFor`). Uma mudança de altura
  para o **mesmo** ponteiro é o usuário crescendo este cartão, não uma palavra nova: `planCardPlacement`
  mantém o canto superior (`holdCardLocation`) e só desloca o cartão o mínimo necessário para o fundo não
  sair da área útil. Só uma palavra nova volta a ancorar no ponteiro.
- **Área útil.** `HoverGeometry.workAreaAt` desconta a barra de tarefas (e qualquer borda reservada) na
  colocação do cartão: antes o cartão podia terminar atrás dela, com as últimas linhas da lista
  inalcançáveis. O recorte do OCR continua sendo o monitor inteiro.
- **Resultado no cenário medido.** O cartão cresce a partir do próprio canto; sem espaço abaixo, desliza só
  o excedente (de `y=438` para `y=312`, fundo em 672) e o clique do cabeçalho continua dentro dele — o
  hover não fecha mais. Quando há espaço acima, o canto não se move.
- **Arquivos.** `desktop/.../HoverTooltipWindow.kt` · `desktop/.../HoverGeometry.kt` ·
  `desktop/src/test/.../HoverTooltipHeightTest.kt` · `desktop/src/test/.../HoverCardGrowthTest.kt`

Suíte desktop: **583 testes, 0 falhas, 6 pulados**. Teste E2E com janela AWT real (`HoverCardGrowthTest`, área
útil 1280×672): `location` antes `(382, 438)` com 460×200, depois `(382, 312)` com 460×360 — fundo na borda
da área útil — e o clique do cabeçalho em `(442, 453)` dentro do cartão nos dois momentos. Mutações provadas:

| mutação | teste que acusa |
| --- | --- |
| `planCardPlacement` ignora `sameAnchor` e sempre ancora no ponteiro | 4 testes de posição (unitário + E2E) |
| O cartão passa a dizer sempre `sameAnchor = false` | `the card remembers the word it was placed for` |
| `holdCardLocation` deixa de limitar o fundo à tela | 3 testes de posição |

- **Cuidado.** Qualquer correção precisa manter o cartão inteiro na tela; cortar a lista não é aceitável.

## D103 — A janela de resultado e a do dicionário rápido lembram a posição  `[x]` (21/11/2026)

**Pedido.** A janela de texto reconhecido deve se comportar como a do dicionário rápido: guardar a
última posição usada. Se estiver minimizada, abrir desminimizada — o mesmo vale para o dicionário
rápido.

- **Causa.** A `ResultWindow` abria sempre centrada (`rememberWindowState(width, height)` sem posição) e
  nada gravava o canto: quem arrastava a janela para o canto da tela a via voltar ao centro na captura
  seguinte. O dicionário rápido já gravava `quick_dict_x/y`, mas só era *ativado* uma vez, na criação
  (`LaunchedEffect(Unit)`): com a janela minimizada, apertar o atalho ou clicar na bandeja de novo não
  mudava `showQuickDict` (já `true`), então nada a trazia de volta. Faltava ainda validar a posição
  gravada: um monitor desconectado deixava o canto salvo fora de qualquer tela, e a janela abria onde não
  dá para clicar nem alcançar a barra de título.
- **Correção.** `DesktopSettings` ganhou `resultWindowX/Y` (chaves `result_window_x` / `result_window_y`),
  gravadas a cada movimento da janela. `WindowPlacement.kt` (novo) tem `rememberedWindowCorner(x, y,
  screens)`: devolve o canto só enquanto um monitor atual o contém, e `null` — «centre-se» — quando o
  monitor sumiu ou nunca houve canto. `screenBoundsNow()` lista os monitores em coordenadas AWT (vazio
  em máquina headless). As duas janelas passam por esse helper e ambas chamam `activateOnOpen` a cada
  pedido, não só na criação: a de resultado com `LaunchedEffect(croppedImage, initialText)`, a do
  dicionário com um contador `quickDictRequest` que a bandeja e o atalho incrementam
  (`QuickDictWindow(activation = ...)`).
- **Arquivos.** `desktop/.../WindowPlacement.kt` · `desktop/.../DesktopSettings.kt` · `desktop/.../ResultWindow.kt`
  · `desktop/.../QuickDictWindow.kt` · `desktop/.../Main.kt` · `desktop/src/test/.../WindowRestoreTest.kt`
  · `desktop/src/test/.../DesktopSettingsTest.kt`

Suíte desktop: **591 testes, 0 falhas, 6 pulados**. Regressão em `WindowRestoreTest` (7): o canto é
honrado enquanto um monitor o contém e descartado quando o monitor sumiu (inclusive em lista vazia); um
`JFrame` real abre no canto gravado; um `JFrame` realmente minimizado volta com `activateOnOpen`; e a
leitura do código confirma que as duas janelas usam o helper, que a de resultado grava o canto e que as
duas ativações são por pedido (a do dicionário, com o contador alimentado por bandeja e atalho).
`DesktopSettingsTest` ganhou o round-trip de `result_window_x/y`. Mutações provadas:

| mutação | teste que acusa |
| --- | --- |
| `rememberedWindowCorner` devolve o canto sem validar contra os monitores | `a corner left on a monitor that is gone is dropped` e `with no screens there is nowhere to place it` |
| `activateOnOpen` deixa de limpar `ICONIFIED` | `a minimised window is restored by asking for it` |
| A do dicionário volta a ativar só com `LaunchedEffect(Unit)` | `both windows save and restore their corner` |

- **E2E medido.** Nesta máquina (primário 1280×720), o `JFrame` de teste nasce em `(60, 40)` — o canto
  pedido — e um `JFrame` minimizado (`ICONIFIED`) volta com `extendedState and ICONIFIED == 0` depois de
  `activateOnOpen`. O `settings.properties` do usuário não foi tocado: os testes usam arquivo temporário.
- **Limite.** A janela Compose em si (abrir, arrastar, minimizar e reabrir pelo atalho) exige o app
  rodando com display; o que foi provado é o helper, as duas janelas AWT de verdade e a ligação no
  código. Não houve sessão manual com o app real.

## 26. Ronda 47 — pedidos de 23/11/2026

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R47a | `[x]` | **Câmera como a captura de tela inteira**: a foto vira os balões azuis de trechos, com página própria, zoom e «Tirar outra foto» | média |
| R47b | `[x]` | **«Remover» dos disponíveis em vermelho**, como o dos instalados | fácil |
| R47c | `[x]` | **Setas da prioridade dos motores separadas**, com o mesmo vão da lista de dicionários | trivial |
| R47d | `[x]` | **«Reinstalar» um dicionário de formato antigo** quando a verificação não oferece atualização | média |
| R47e | `[x]` | **Cartão do hover alinhado ao canto da janela que encosta no mouse**, para a folga da estimativa não ficar entre os dois | média |
| R47f | `[x]` | **Ordem dos dicionários no hover** (e em todos os seletores) igual à prioridade configurada | fácil |
| R47g | `[x]` | **«Fechar» do resultado em vermelho e sempre no canto inferior esquerdo** das ações | fácil |

### R47a — Câmera como a captura de tela inteira  `[x]` (23/11/2026)

**Pedido.** Depois da foto, a leitura deve abrir os mesmos balões azuis da captura de tela inteira, e
o usuário precisa de um caminho para tirar outra foto.

- **Causa.** O R46d lia a foto e ia direto ao painel: `recogniseImage` chamava `showResultFor` com
  `canRepick = false`. A foto não passava por `placeBlocks` nem pelo `TextPickerOverlay`, então não
  havia balão nenhum — e o único jeito de tirar outra era voltar à bolha e abrir a câmera de novo.
  O caminho da tela não podia ser copiado como estava: seus retângulos são convertidos para os pixels
  da tela, e a foto tem tamanho e proporção próprios.
- **Correção.** A foto ganhou uma página do tamanho da tela: o bitmap é desenhado no centro, sem
  distorção, sobre preto, e os retângulos do motor são levados para essa página — preservando
  `cropRect` nos pixels da foto para o recorte do painel. A geometria saiu do serviço para
  `PhotoPageGeometry` (R47a, revisão): `fit` calcula a escala uniforme e a centralização, `place`
  move qualquer retângulo e `page` aloca e pinta o bitmap da página. Com a página pronta, o
  `TextPickerOverlay` existente desenha os balões, aceita pinça e devolve o índice; `pendingPage`
  guarda a página para «◀ Trechos» sem pagar outro OCR. «Tirar outra foto» aparece no picker
  (`retake = true`) e no painel do resultado (`canRetakePhoto`), e os dois voltam à `CameraActivity`
  pelo mesmo `onCameraRequested`. O caminho de tela continua com `retake = false`.
- **Arquivos.** `app/.../service/FloatingOcrService.kt` · `capture/PhotoPage.kt` (novo) ·
  `overlay/TextPickerOverlay.kt` · `overlay/OverlayController.kt` · `overlay/ResultOverlay.kt` ·
  `values/strings.xml` · `values-en/strings.xml` · `app/src/test/.../CameraCaptureTest.kt` ·
  `app/src/test/.../PhotoPageGeometryTest.kt` (novo)

Suíte do app: **902 testes (451 em debug e 451 em release), 0 falhas**. Regressão em
`CameraCaptureTest` (+3): o passo da foto usa `PickerDecision.offersPicker`, `placePhotoBlocks` e
`showTextPicker(..., retake = true)`; o retake só fica visível quando a página veio da câmera; e o
painel do resultado só oferece «Tirar outra foto» para uma foto. `PhotoPageGeometryTest` (5) mede o
encaixe de uma foto deitada e de uma em pé, o deslocamento de uma caixa para a página, a alocação
(bitmap no tamanho da tela, foto na posição do encaixe e barras pretas em volta) e a recusa de
imagem/página sem área. Mutações provadas:

| mutação | teste que acusa |
| --- | --- |
| `recogniseImage` volta a abrir o picker com `retake = false` | `the photo opens the picker over the picture` |
| `fit` usa `max` em vez de `min` | `a landscape photo is letterboxed above and below` |
| `place` ignora `left`/`top` | `a box of the photo lands on the same characters of the page` |
| `page` desenha a foto na página inteira | `the page is allocated at screen size with the photo fitted on it` |

- **Limite.** Sem aparelho: o Robolectric mede o picker e o painel, não uma foto real decodificada e
desenhada na tela. A geometria e a alocação da página são medidas com o desenho nativo do Robolectric
(`GraphicsMode.NATIVE`); a pintura final do `TextPickerOverlay` numa tela não foi vista.

### R47b — «Remover» dos disponíveis em vermelho  `[x]` (23/11/2026)

- **Causa.** `availableRow` trocava o rótulo para «Remover» quando o dicionário já estava instalado,
  mas continuava com o fundo padrão de `actionButton` — o azul do app. O cartão da lista de
  instalados já usava `warn`; a mesma ação aparecia com duas cores.
- **Correção.** O botão da linha de «Disponíveis» passa a receber `R.color.warn` quando está no
  estado «Remover»; «Baixar» e «Cancelar» seguem no azul.
- **Arquivos.** `app/.../DictionaryActivity.kt` · `app/src/test/.../ActionButtonWiringTest.kt`

Regressão: `every remove button is the danger fill` instala `jmdict-en` (que também aparece em
«Disponíveis») e exige **todos** os «Remover» da tela com o fundo `warn`; antes eram dois e só um era
vermelho. Mutação provada: voltar `availableRow` ao fundo padrão derruba esse teste.

### R47c — Setas da prioridade dos motores separadas  `[x]` (23/11/2026)

- **Causa.** Em `renderEnginePriorityList` a seta para baixo era adicionada com `row.addView(downBtn)`,
  sem `marginStart`, enquanto a lista de dicionários usava 6 dp. Os dois botões ficavam colados e
  liam como um controle único «↑↓».
- **Correção.** A seta para baixo recebe `LinearLayout.LayoutParams` com `marginStart = dp(6)`.
- **Arquivos.** `app/.../MainActivity.kt` · `app/src/test/.../ActionButtonWiringTest.kt`

Regressão: `the engine priority arrows are two separated buttons` exige margem à esquerda em cada
«↓». Mutação provada: remover o `marginStart` derruba o teste.

### R47d — Reinstalar um dicionário de formato antigo  `[x]` (23/11/2026)

**Pedido.** O aviso «Compilados por uma versão antiga do app» manda atualizar, mas alguns dicionários
não aparecem para atualizar nem depois de «Verificar atualizações». Nesse caso deve haver uma opção de
reinstalar com a versão/índice atual.

- **Causa.** «Atualizar» só existia quando a última verificação dizia `AVAILABLE` (`result.canRefresh`).
  Um dicionário cujo publisher não anuncia revisão nova — ou que não tem publisher — ficava com o
  formato velho e sem nenhum botão, apesar de o catálogo ainda ter o arquivo atual.
- **Correção.** O cartão instalado calcula `reinstallOffered` quando `info.outdated` e existe entrada no
  catálogo, e mostra «Reinstalar» quando «Atualizar» não é oferecido. A ação é a mesma `startDownload`
  do R43: baixa o arquivo do catálogo e compila com o app atual, preservando id, ativação e prioridade.
  O banner e a linha «formato antigo» passam a citar «Reinstalar» (pt/en).
- **Arquivos.** `app/.../DictionaryActivity.kt` · `values/strings.xml` · `values-en/strings.xml` ·
  `app/src/test/.../DictionaryUpdatesScreenTest.kt`

Regressão: `an old-format dictionary can be reinstalled even with nothing to update` instala um
`jmdict-en` com `compiledWith = FORMAT_VERSION - 1`, exige o botão «Reinstalar» sem nenhuma
atualização armazenada, clica e espera o dicionário recompilado — revisão nova e definição nova no
índice. Mutação provada: desligar `reinstallOffered` derruba o teste.

- **Limite.** A troca de arquivo é a mesma já coberta pelos testes de download; não houve download real
de 15–39 MB nesta sessão.

### R47e — O cartão do hover encosta no mouse pelo canto certo  `[x]` (23/11/2026)

**Pedido.** O cartão às vezes aparece muito longe do mouse; ele deve nascer da posição atual do
ponteiro e respeitar os cantos, com o canto mais próximo encostando no mouse.

- **Causa.** A janela é dimensionada por `estimateHeight`, que quebra as linhas a 32 caracteres —
  bem menos do que o Compose desenha. A sobra virava altura transparente embaixo do `Surface`, que
  ficava alinhado ao topo: o canto inferior da **janela** ficava a 18 px do ponteiro, mas o canto
  visível do cartão ficava a centenas de pixels, porque o conteúdo não ocupava a janela.
  `placeTooltip` estava correto; o que faltava era dizer **onde dentro da janela** o cartão é
  desenhado.
- **Correção.** O conteúdo passa a ser alinhado ao canto da janela mais próximo do ponteiro:
  `cardContentCorner` diz se o cartão abriu acima (`verticalBottom`) e/ou à esquerda
  (`horizontalEnd`) do ponteiro, e o `Box` que envolve o `Surface` usa esse canto
  (`BiasAlignment`). A sobra da estimativa fica no lado oposto ao mouse, fora do caminho. A altura,
  o posicionamento e `sameAnchor` (D102) não mudam — o cartão que cresce para o mesmo ponteiro
  continua mantendo o canto.
- **Arquivos.** `desktop/.../HoverTooltipWindow.kt` · `desktop/src/test/.../HoverTooltipHeightTest.kt`

Suíte desktop: **599 testes, 0 falhas, 6 pulados**. Regressão (+2): `cardContentCorner` devolve
bottom-end para um cartão acima/à esquerda, top-start para um abaixo/à direita e só o lado que
virou quando só um virou; a leitura do código confirma que o `Box` usa o canto e o `BiasAlignment`.
Mutação provada: `cardContentCorner` devolver sempre top-start derruba
`the card is drawn against the window corner nearest the pointer`.

- **Correção da revisão (mesmo dia).** O canto era escolhido pela colocação *hipotética* para o
  novo tamanho. Ao abrir a lista para o mesmo ponteiro, o D102 mantém o canto antigo; a hipótese
  podia dizer «acima» enquanto a janela continuava «abaixo», e o cartão visível ia para o lado
  errado. O alinhamento passa a usar os bounds que a janela realmente reporta (`placedBounds`, o
  mesmo retângulo que o hover usa), caindo no `anchor` só antes do primeiro posicionamento.
- **Limite.** A posição final numa tela real não foi medida nesta sessão; a prova é a geometria
  (`placeTooltip`) mais o alinhamento do conteúdo. O ajuste fino de 1–2 px do canto fica para uma
  conferência visual.

### R47f — Ordem dos dicionários igual à prioridade configurada  `[x]` (23/11/2026)

**Pedido.** No cartão do hover, a lista de dicionários aparece em ordem diferente da aba de
instalados; conferir todas as abas que oferecem escolher um dicionário.

- **Causa.** `HoverOcrManager.getInstalledDictionaryTitles()` lia `installedTitles(directory)`, que
  devolve a ordem do sistema de arquivos (alfabética). A janela de resultado e o dicionário rápido já
  usavam `installedTitles(settings)`, a ordem de prioridade; o cartão do hover e os dois filtros dos
  Ajustes ficavam na ordem da pasta. O seletor da aba de busca do gerenciador também lia a pasta.
- **Correção.** O hover, os dois filtros dos Ajustes e a busca do gerenciador passam por
  `DesktopDictLoader.installedTitles(settings)`. A contagem dos Ajustes continua sobre a pasta inteira
  (o texto é «encontrados na pasta atual»); só as listas de escolha mudam.
- **Arquivos.** `desktop/.../HoverOcrManager.kt` · `SettingsWindow.kt` · `DictionaryManagerWindow.kt` ·
  `desktop/src/test/.../DictionaryTitlesTest.kt`

Regressão (+2): uma pasta temporária com `a/b/c.yabai` e ordem `b,a,c` exige `[b,a,c]` pela
configuração, não pela pasta; e a leitura confirma que hover, gerenciador, resultado, dicionário rápido
e os dois filtros dos Ajustes pedem a ordem de prioridade. Mutação provada: o hover voltar a
`installedTitles(directory)` derruba `every dictionary chooser asks for the priority order`.

- **Limite.** Os dicionários desativados continuam aparecendo nas listas (comportamento anterior de
  `installedTitles(settings)`); filtrar por um deles simplesmente não acha nada. Não foi aberto um app
  com nove dicionários reais para conferir a ordem na pintura.

### R47g — «Fechar» do resultado em vermelho e no canto inferior esquerdo  `[x]` (23/11/2026)

**Pedido.** No painel que mostra o texto reconhecido, «Fechar» deve ser vermelho e ficar sempre na
posição mais à esquerda/inferior em relação aos outros botões.

- **Causa.** O «Fechar» entrava na grade antes de «Imagem», «Trechos» e «Tirar outra foto», com o
  fundo azul padrão. A posição dependia de quantas ações vinham depois dele e a cor não o distinguia
  do resto — a saída do painel mudava de lugar e de peso conforme o tipo de leitura.
- **Correção.** O `ActionGrid` ganhou `addOnOwnRow`, que começa uma linha nova; «Fechar» é sempre o
  último item, sozinho na última linha, ou seja, no canto inferior esquerdo. O fundo passa a ser
  `R.color.warn`, o mesmo vermelho de «Remover». As demais ações mantêm a ordem em que estavam
  (copiar, IA, imagem, trechos, nova foto).
- **Arquivos.** `app/.../overlay/ActionGrid.kt` · `overlay/ResultOverlay.kt` ·
  `app/src/test/.../overlay/ActionGridTest.kt` · `app/src/test/.../ActionButtonWiringTest.kt` ·
  `ImagePanelTest.kt` · `ResultOverlayLayoutTest.kt`

Regressão (+2 e expectativas atualizadas): `a button added on its own row is always the bottom-left
one` usa três ações antes dele (o caso em que «adicionar por último» cairia na direita) e exige
`[2,1,1]` com «Fechar» sozinho no fim; `the result close is red and is the bottom-left action` monta o
painel com imagem, trechos e nova foto, exige a última linha com um único «Fechar» e o fundo `warn`.
As contagens de linhas de `ResultOverlayLayoutTest` e a ordem de `ImagePanelTest` passam a refletir a
linha própria. Mutações provadas:

| mutação | teste que acusa |
| --- | --- |
| `addOnOwnRow` volta a ser `add` | `a button added on its own row is always the bottom-left one` e `the result close is red and is the bottom-left action` |
| «Fechar» volta ao fundo azul padrão | `the result close is red and is the bottom-left action` |

- **Limite.** Sem aparelho: o Robolectric mede a grade e a cor do fundo, não o painel sobre um app de
  verdade.

## 27. Ronda 48 — pedidos de 23/11/2026

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| R48a | `[x]` | **Câmera sem confirmação**: a foto aparece na própria tela da câmera, com os balões por cima, sem «✕/✓» e sem trocar de tela | média |
| R48b | `[x]` | **Botões do seletor acima dos botões do celular**, mantendo a imagem no tamanho dela | fácil |
| R48c | `[x]` | **Câmera sem foto guardada e sem tela travada**: o arquivo vive só no cache, é varrido a cada disparo, e a espera pelo serviço tem limite | fácil |

### R48a — A foto é lida na própria tela da câmera  `[x]` (23/11/2026)

**Pedido.** Tirar a foto e já ver a imagem com os balões identificados, sem confirmar nada; não sair
da câmera ao reconhecer, deixando a foto parada de fundo enquanto carrega.

- **Causa.** A `CameraActivity` era um portal para o app da câmera (`ActivityResultContracts.TakePicture`,
  arquivo entregue por `FileProvider`). Quem tirava a foto, portanto, era o app da câmera: o «✕» e o
  «✓» da confirmação eram dele, e só depois do «✓» o portal fechava e o serviço abria outra tela —
  os balões do picker — já sem nada por baixo. Nenhuma parte do passo entre o disparo e a leitura
  estava nas mãos deste app.
- **Correção.** A `CameraActivity` virou uma tela de câmera com CameraX (`camera-camera2`,
  `camera-lifecycle`, `camera-view`): `PreviewView` em tela cheia, botão de disparo e «✕» próprios, e
  o disparo entrega o JPEG direto no `ImageCapture`. Salva a foto, libera a câmera, desenha a própria
  foto (`CameraPhoto.preview`, decodificada com amostragem **sem apagar o arquivo**, que o serviço
  ainda vai ler) e mostra o indicador «Reconhecendo o texto…». Não há passo de confirmação e a tela não
  muda; o serviço lê o arquivo como antes (`ACTION_PHOTO` + `EXTRA_PHOTO_PATH`) e, quando os balões
  estão na tela, avisa por `ACTION_PHOTO_READY` — a câmera só então sai, e como a página do picker é a
  mesma foto o handover não aparece.
- **Uma espera a menos.** O painel flutuante deixou de abrir o cartão de carregamento para uma foto
  (`recogniseImage(..., loadingPanel = false)`): a tela da câmera já é o indicador, e dois spinners
  sobre a mesma foto só cobriam a imagem.
- **Arquivos.** `app/.../CameraActivity.kt` · `capture/CameraPhoto.kt` · `service/FloatingOcrService.kt`
  · `AndroidManifest.xml` · `values/themes.xml` (`Theme.YabaiOcr.Camera`) · `values/strings.xml` ·
  `values-en/strings.xml` · `app/src/test/.../CameraCaptureTest.kt` · `gradle/libs.versions.toml` ·
  `app/build.gradle.kts`

O `FileProvider` da câmera saiu do manifesto junto com o `res/xml/camera_paths.xml`: o `content://`
Uri existia para entregar o arquivo ao app da câmera, e agora o arquivo não sai do app.

Regressão (+2 em `CameraCaptureTest`, e o teste do manifesto deixou de exigir o `FileProvider`):
`the picture is taken in place, without a confirmation step` exige `ImageCapture.Builder()` e
`capture.takePicture(` na tela, `CameraPhoto.preview(` + a foto desenhada + `camera_reading`, a espera
por `ACTION_PHOTO_READY` do lado da tela e o `sendBroadcast(Intent(ACTION_PHOTO_READY)` mais
`recogniseImage(photo, loadingPanel = false)` do lado do serviço, e recusa
`ActivityResultContracts.TakePicture`; `the photo shown behind the spinner is read without taking the file from the reader` decodifica um JPEG e exige que o arquivo continue lá.

Mutações a provar:

| mutação | teste que acusa |
| --- | --- |
| A tela volta a abrir o app da câmera (`TakePicture`) | `the picture is taken in place, without a confirmation step` |
| `preview` apaga o arquivo como `loadUpright` | `the photo shown behind the spinner is read without taking the file from the reader` |
| O serviço deixa de mandar `ACTION_PHOTO_READY` | `the picture is taken in place, without a confirmation step` |

- **Limite.** A câmera de verdade não foi aberta: esta máquina não tem Android SDK, então a suíte do
  app não rodou nesta rodada (os números de AGENTS/ROADMAP continuam os da última execução, 902). O que
  está provado é o que o Robolectric alcança (decodificação da foto sem apagar o arquivo) e o que os
  testes de fonte exigem do caminho. Falta instalar o APK e conferir, num aparelho, o enquadramento do
  `PreviewView`, o giro do JPEG e o tempo entre o disparo e os balões.

### R48b — Os botões do seletor acima dos botões do celular  `[x]` (23/11/2026)

**Pedido.** Com o celular em pé, a parte de baixo fica cortada onde estão os botões do sistema
(voltar, home, aplicativos), na leitura da tela inteira e na da câmera; manter a imagem no mesmo
tamanho e pôr «Cancelar», «Texto todo» e «Tirar outra foto» logo acima deles, sem sobreposição.

- **Causa.** A janela do picker é a tela inteira — a página precisa manter o tamanho em que foi
  capturada, senão as caixas saem do texto — e a barra de navegação é pintada por cima do último
  pedaço dela. A barra de ações ficava a 40 dp do fundo da janela: menos que a altura da barra do
  sistema, então metade dela caía atrás dos botões do celular.
- **Correção.** A barra de ações passou a ficar a `16 dp + altura da barra de navegação`
  (`Context.systemBarHeight("navigation_bar_height", 48)`, novo em `util/Dimensions.kt`). A página e o
  `BlockCanvasView` não mudaram de tamanho: só a barra sobe, e a imagem continua exatamente onde
  estava, com a mesma escala.
- **Arquivos.** `app/.../overlay/TextPickerOverlay.kt` · `util/Dimensions.kt` ·
  `app/src/test/.../overlay/TextPickerOverlayTest.kt`

Regressão (+1): `the actions are drawn above the system navigation buttons` mede o picker em
320×640 dp e exige que o fundo da barra fique acima da faixa da barra de navegação (640 px de tela −
48 px da barra) **e** que a área em que a página é desenhada (`TextPickerOverlay.pageView`, o
`BlockCanvasView`) continue inteira: 320×640 no canto (0,0). A primeira versão media
`overlay.measuredHeight`, que é 640 px mesmo que o `BlockCanvasView` encolha — uma asserção sempre
verdadeira, que não pegava a mutação.

| mutação | teste que acusa |
| --- | --- |
| `bottomMargin` volta a `dp(40)` | `the actions are drawn above the system navigation buttons` |
| A barra sobe encolhendo o `BlockCanvasView` | o mesmo teste (`pageView` mediria menos que 640 px, ou desceria) |

- **Limite.** A altura vem do dimen do framework, não de um inset real (uma janela flutuante que
  cobre a tela inteira não recebe insets). Em navegação por gestos o dimen pode ser maior que a barra
  desenhada, o que deixa uma folga a mais, nunca uma sobreposição. Sem aparelho nesta rodada, a
  posição na tela não foi vista.

### R48c — A foto não fica guardada e a câmera não fica travada  `[x]` (23/11/2026)

**Pedido.** A imagem da câmera fica salva no celular? O padrão correto é não ficar. E, se o serviço
morrer ou o aviso da leitura se perder, a tela da câmera não pode ficar presa na foto com o spinner
para sempre.

- **Causa (foto).** O arquivo já nascia em `cacheDir/photos` — armazenamento privado do app, fora do
  `MediaStore` e sem nenhuma escrita na galeria — e o serviço o apagava em `finally`, ao decodificar.
  O que faltava era o caso em que a foto **nunca chega a ser lida**: um disparo cujo serviço não
  veio, ou uma tela fechada antes da entrega, deixava um JPEG no cache até o próprio Android
  limpá-lo.
- **Causa (tela).** O único jeito de a `CameraActivity` sair era `ACTION_PHOTO_READY`. Serviço morto,
  broadcast perdido ou motor que não volta deixavam a foto congelada e o spinner sem fim — sair só
  pelo ✕, sem nenhuma pista do que houve.
- **Correção.** (1) `CameraPhoto.clearLeftovers(directory)` limpa a pasta e é chamado no começo de
  **cada** disparo: nenhuma foto sobrevive entre usos, e a que o serviço lê continua sendo apagada
  por ele. (2) `capture/PhotoWait.kt` é o relógio da espera: armado na entrega do arquivo, desarmado
  pela resposta (e na saída da tela), e aos 30 s devolve o controle — o spinner some, o `ImageView`
  da foto dá lugar à pré-visualização de novo, o disparo volta a ficar habilitado e um aviso explica
  o que aconteceu; o ✕ continua ali. Uma leitura que chegue atrasada não quebra nada: o picker abre
  por cima do que estiver na tela.
- **Arquivos.** `app/.../CameraActivity.kt` · `capture/PhotoWait.kt` (novo) · `capture/CameraPhoto.kt` ·
  `values/strings.xml` · `values-en/strings.xml` (`camera_no_answer`) ·
  `app/src/test/.../capture/PhotoWaitTest.kt` (novo) · `app/src/test/.../CameraCaptureTest.kt`

Regressão (+5): `PhotoWaitTest` (3) prova com o relógio de verdade que a espera não dispara antes do
limite e dispara depois dele, que a resposta a desarma (a tela não é fechada duas vezes) e que o
limite padrão não é curto demais para um motor responder; em `CameraCaptureTest`,
`the photo stays in the app's cache and never reaches the gallery` exige `File(cacheDir, "photos")` e
recusa `MediaStore`, `getExternalFilesDir`, `Environment.getExternalStorage` e `DCIM`, e
`a picture left behind by an earlier shot is swept before the next one` enche a pasta e exige que
`clearLeftovers` a esvazie.

| mutação | teste que acusa |
| --- | --- |
| `PhotoWait.arm` deixa de agendar o `Runnable` | `a reading that never answers hands the screen back after the limit` |
| `PhotoWait.settle` deixa de remover o `Runnable` | `the answer stops the wait, so the screen is not closed twice` |
| `clearLeftovers` vira `Unit` | `a picture left behind by an earlier shot is swept before the next one` |
| O disparo grava em `getExternalFilesDir`/`MediaStore` | `the photo stays in the app's cache and never reaches the gallery` |

- **Limite.** Sem aparelho e sem Android SDK nesta rodada: a suíte do app **não foi executada** e as
  mutações acima continuam por provar. O que a leitura do código garante é que o arquivo é privado
  (`cacheDir` é limpo pelo sistema, não aparece na galeria nem no gerenciador de arquivos do
  usuário) e que nada no caminho da câmera escreve no armazenamento compartilhado. Falta conferir no
  celular: tirar uma foto, ver que ela não aparece na galeria; matar o serviço depois do disparo e ver
  a câmera voltar em 30 s.
