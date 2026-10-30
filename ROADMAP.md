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
| R35f | `[-]` | **Pesquisar pela tradução** no dicionário — medido: não vale a pena | estudo + alta |

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

### R35f — Pesquisar pela tradução  `[-]` (28/10/2026 — medido, não vale a pena)

**Pedido:** no dicionário, poder procurar pela **tradução** — digitar «espada» e achar 剣 / 刀 — com um
botão que escolha entre procurar «o original em japonês» e «a tradução».

**O que o código de hoje permite:** nada disso de graça. O dicionário compilado é indexado por
**headword e leitura** (`CompactDictionary.keysStartingWith`); as definições ficam em blobs lidos um a
um, e não há índice invertido. Procurar «espada» nas definições hoje significaria ler as ~200 mil
entradas de um JMdict/Jitendex **a cada consulta** — segundos de CPU e bateria, e a cada tecla.

**Medição (feita e depois removida):** um teste temporário compilou o **Jitendex PT-BR** e percorreu
todas as entradas uma vez, contando o que um índice invertido teria de guardar. Números do PC de
desenvolvimento, com o arquivo `ggjz-jitendex-ptbr.zip` (39,5 MB):

| medida | valor |
| --- | --- |
| compilação | 9,2 s |
| varredura das definições | 3,3 s |
| entradas | 428.496 |
| caracteres de definição | 32,65 milhões |
| palavras (tokens) | 4,71 milhões |
| palavras distintas | 351.861 |
| `terms.dat` atual | 55 MB |
| índice invertido, piso | **≈51 MB** (4 bytes por ocorrência + chaves + sobrecarga do mapa) |
| índice sem stopwords e palavras de 1–2 letras | **≈40 MB** |

O piso é generoso: conta 4 bytes por ocorrência e uma chave de 40 bytes, sem dicionário de sinônimos,
sem stemming e sem posição no verbete — o índice real seria maior. E é **por dicionário**: quem tem
Jitendex PT + JMdict + KANJIDIC pagaria isso três vezes. No celular, a varredura inicial seria bem mais
lenta que os 3,3 s do PC e o índice ficaria na RAM (ou seria lido do disco a cada consulta), no mesmo
aparelho que já mantém o índice de chaves dos dicionários ativos.

**Decisão: fechar sem código.** O custo medido — quase dobrar o espaço ocupado pelos dicionários, pagando
uma varredura longa na primeira vez, por dicionário — não se justifica para uma busca que o usuário faz
ocasionalmente e que hoje tem alternativa: ele já pode procurar em japonês (inclusive **romaji**, que o
R31 converte) e ler a definição. Dois caminhos foram descartados junto com o índice:

| caminho | por que não |
| --- | --- |
| índice no import | mudaria o formato: **todos** os dicionários teriam de ser reimportados, e o arquivo cresceria os mesmos ~40–50 MB por dicionário; o aviso de formato antigo existe, mas a cura seria pior que a doença |
| traduzir a consulta | ML Kit traduz «espada» → `剣` e consulta: devolve **uma** palavra, não os verbetes que contêm o sentido — não é busca por significado, é busca por uma tradução escolhida por um modelo |

**O que fica registrado para o futuro:** se o item voltar, o caminho é o **índice no import**, com
aumento de `FORMAT_VERSION` e o aviso de reimportação já existente, e com o tamanho medido **antes** de
prometer a função. O teste de medição não fica no repositório: era um instrumento, não uma garantia, e
um teste que só imprime números passaria por construção. Não há teste de regressão para esta rodada
porque **nada foi entregue** — o que fecha o item são os números acima.

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
