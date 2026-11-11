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
- `desktop/build.gradle.kts`: dependências `dbus-java-core` e `dbus-java-transport-native-unixsocket`
  5.1.1 (mesma biblioteca que a P4 usa para o serviço de hover).

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
