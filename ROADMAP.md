# Roadmap — Yabai OCR

Ordem de execução: **bugs primeiro**, depois o que for mais fácil. Cada item é implementado sozinho,
marcado aqui e só então o próximo começa.

Legenda: `[ ]` pendente · `[x]` pronto · `[~]` parcial

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
  «Encerrar» do menu (`ic_menu_stop`) sobre um disco escuro translúcido — a cruz vermelha lê-se sobre
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
  quem decide é um ajuste na tela principal («Manter o furigana entre parênteses»), que viaja até o
  `LensResultBuilder` pelo `FuriganaMode` do motor. O modo entrou na chave do cache do `LensOcrEngine`
  (`LensKey`) junto de idioma/dimensão/ampliação: ele não muda a *requisição*, mas mudar o ajuste e a
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
  palavra seriam uma palavra que o dicionário não tem, exactamente o que não se inventa. As leituras
  vêm do `KanjiEntry`, que agora **carrega** o on'yomi e o kun'yomi — o blob compacto já os tinha e o
  leitor deitava-os fora. Ordem: **kun primeiro** (um kanji sozinho num texto japonês lê-se à japonesa
  primeiro), ordenados até três, o `た.べる` do KANJIDIC reduzido ao `た` (o `べる` está na frase) e
  separados por `・`. O que é palpite sai dentro dos parênteses com `?` e é **contado**
  (`Annotated.uncertain`), e o painel mostra a nota que explica o `?` só quando há algum.
- **O texto reconhecido não é tocado.** O bloco é uma leitura *do* texto, não o texto: escrever dentro
  do campo editável envenenaria o dicionário e a tradução (é esse campo que alimenta os dois) e brigaria
  com as correcções do usuário. Se a leitura já está impressa na frase — o rubi do mangá ou o
  `職員（しょくいん）` que o D1 deixa — ela **não é escrita segunda vez**.
- **O bloco é mais um item dobravel** do painel, acima da lista de palavras (quem não sabe ler um kanji
  quer a leitura ao lado dele, não três secções abaixo) e o seu texto é **seleccionável**, como todo o
  resto do painel.
- **Saída em texto (`職員（しょくいん）`), não em rubi por cima do kanji.** O rubi de verdade precisa de
  `Spannable` num `TextView` próprio, não é copiável como texto e não é o que o painel faz em lugar
  nenhum; a convenção de parênteses é a que o D1 já tinha introduzido. Fica registado como refinamento
  possível, não como pendência.
- **A escolha do dicionário vale também aqui**: o bloco recebe o mesmo `only` que a consulta e o quadro
  de kanji, então nunca é respondido por um dicionário que a secção de palavras foi mandada ignorar —
  a mesma regra que o M1 prende do outro lado.
- **Ajuste próprio** («Mostrar a leitura dos kanji»), **ligado** por omissão: é uma ajuda de leitura e
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
| `the reading of the sentence is written beside the kanji` (painel) | o bloco mostra a leitura, seleccionável |
| `nothing to read means no block at all` (painel) | sem leitura não há bloco — e a frase não muda |
| `a guessed reading is called out under the sentence` (painel) | a nota do `?` só aparece quando há palpite |
| `the block folds, and stays folded when the text is looked up again` (painel) | o bloco dobra e não se desdobra sozinho |
| `the block sits above the dictionary` (painel) | a ordem dos blocos é a decidida |

Conferido contra o código quebrado, **dezasseis** quebras, cada uma derrubando um teste de verdade:
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
`expected:<𠮟（しか）る> but was:<null>`; bloco fora do painel → `NullPointerException` na secção; nota do
palpite a desaparecer → `expected:<[? — o dicionário não tem este kanji como palavra…]> but was:<[null]>`;
leitura não seleccionável → `the reading has to be selectable to be copied`; dicionário antes do bloco →
`expected the reading before the words, got [dictionary, furigana, dictionary]`. Não são testes vazios.

---

### Registo — a carga que vai para o Lens  (18/10/2026)

A última pendência aberta do roadmap era o `DEFAULT_MAX_DIMENSION`, que estava em 1600 a aguentar uma
captura de tela de 1080×2400 reduzida a 720×1600. Se a redução custava alguma coisa não se sabia; agora
sabe-se, porque foi medido contra o endpoint real (a sonda usada foi o `LensLiveTest` com uma lista de
cargas; a sonda foi apagada a seguir, as cargas eram ficheiros temporários):

| carga | bytes | tempo | HTTP | trechos | leitura miúda |
| --- | --- | --- | --- | --- | --- |
| tela 1080×2400 reduzida (limite 1600 → 720×1600) | 104 702 | 1,2 s | 200 | 4–5 | **não** |
| tela 1080×2400 nativa (limite 2400) | 177 897 | 0,9–1,3 s | 200 | 5 | **sim** (`よど` como trecho próprio) |
| página fotografada 1080×2400 | 2 393 184 | 5,1 s | 200 | 5 | — |
| recorte 1350×3000 (extremo do ajuste) | 3 718 338 | 6,9 s | 200 | 6 | — |

As duas primeiras linhas são **a mesma página**, desenhada nítida nas duas densidades (texto a 30 px
com leitura a 15 px, e texto a 45 px com leitura a 22 px). O resultado repetiu-se nas duas corridas:
com a leitura a 15 px o Lens não a reconhece de todo; a 22 px, vem como trecho próprio. E é essa leitura
que o D1 tira da frase e que o D2 escreve — a redução não custava bytes, custava a funcionalidade.

- `DEFAULT_MAX_DIMENSION` passou de **1600** para **2400**: a tela de um telefone comum entra inteira,
  sem reamostragem; uma tela maior continua a ser reduzida, mas só até 2400. *Superado no mesmo dia —
  ver a nota no fim da secção.*
- O que se paga: numa captura de tela +73 KB e o mesmo tempo; numa página fotografada 2,4 MB e ~5 s —
  dentro do `callTimeout` de 20 s, e o endpoint aceita (3,7 MB, ~7 s, também aceite). O tecto não é o
  endpoint.
- Testes: `app/.../ocr/ImagePreparerTest.kt` (7) — a tela de telefone desce a 720×1600 no limite por
  omissão (era «passa intacta» quando o limite era 2400), uma tela maior é reduzida mantendo as
  proporções, o recorte é ampliado mas só até duas vezes e nunca além do limite, e a imagem vai em
  **PNG** com bytes dentro. Conferido contra o código quebrado: subir o limite para 2400 →
  `expected:<720> but was:<1080>`; ampliação sem limite → `expected:<600> but was:<800>`; envio em JPEG
  → `expected a PNG signature, got [-1, -40, -1, -32]`.

**19/10/2026 — o limite voltou a 1600, e esta medição ficou superada.** A conclusão acima foi tirada
com uma página **desenhada**: fundo limpo, letra de imprensa, sem arte por baixo e sem a letra grande
estilizada que abre um título de mangá. Numa página real (ver «N1 — A precisão do OCR caiu») o tamanho
maior faz o Lens perder exactamente esse kanji. Como a palavra perdida não se recupera e a leitura
impressa que o maior ganhava é redundante (o D2 escreve-a do dicionário, e um recorte é ampliado),
ficou o kanji. O resto desta secção — o protocolo, os bytes, o tempo — continua válido; o que mudou é
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
qualquer app instalada (`ACTION_SEND`), e qualquer app de IA aceita texto partilhado. O botão novo,
**«Perguntar à IA»**, faz três coisas e nenhuma delas passa por este app:

1. copia o **texto reconhecido** (o que está no painel, com as correcções do usuário) para a área de
   transferência;
2. monta a pergunta: um **prompt editável** nos ajustes, de fábrica «Traduza para português e
   explique: », seguido do texto — prompt vazio manda só o texto;
3. abre a **app escolhida** com a pergunta já escrita, ou o **seletor do Android** para quem preferir
   decidir de cada vez.

Por que assim e não com uma API: não há chave, conta nem custo; a app que responde é a que o usuário
já escolheu para si (`ChatGPT`, `Gemini`, `Copilot`, `Claude`, o que estiver instalado); e continua a
funcionar se ele trocar de app, porque não há lista de apps «suportadas» — a lista é a do aparelho.
O que se paga é que a app do outro lado não sabe nada desta, e por isso vai uma pergunta escrita.

Abriu outra app a partir da bolha é o mesmo caminho que o botão **«Ajustes»** da bolha já usava: o app
tem uma janela visível, a que o usuário acabou de tocar.

O que mudou:

- `app/.../ai/AskAi.kt` (novo) — a pergunta montada, o intent de partilha, a lista de apps que aceitam
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
| `the prompt and the text are never glued together` | exactamente um espaço entre os dois, como o usuário escreveu («:» acabado de escrever) |
| `the text keeps its own line breaks` | as linhas do texto são do texto, não se achatam |
| `an empty text is nothing to send` | sem texto não há pergunta |
| `the question leaves as a share of plain text` | `ACTION_SEND`, `text/plain` e o texto no extra |
| `without a chosen app the text goes to the chooser` | sem escolha, o intent não tem destinatário e vai embrulhado no seletor |
| `a chosen app is tried first, and the chooser is still there behind it` | a ordem das tentativas, e o seletor sempre atrás |
| `with no choice there is still something to open` | o seletor sozinho é uma lista válida |
| `the list is the phone's, with our own app left out` | a lista vem do `PackageManager` (e não do próprio app) |
| `an app appears once however many activities it offers` | duas actividades da mesma app são uma escolha |
| `the list is sorted by name` | a lista não muda de ordem entre aberturas |
| `the chosen app is named the way the phone names it` | o nome vem do aparelho, não de uma cópia guardada |
| `a package that is gone reads as no choice` | app desinstalada não deixa um nome a fingir |
| `no installed app at all leaves nothing to choose` | sem apps que aceitem texto, a lista é vazia e assumida |
| `the panel offers the question when the setting allows it` | o botão segue o ajuste (e não aparece sozinho) |
| `the question is offered next to the copy buttons` | o lugar do botão: a fila das cópias, primeiro |
| `the question is asked about the text on screen, corrections included` | pergunta sobre o texto **como está**, não como foi reconhecido |
| `an error panel does not offer the question` | onde não há texto não há pergunta |
| `the question reaches the app through the controller` | a ponte painel → app (é daqui que sairia um botão que não faz nada) |

A lista de acções que o painel oferece foi actualizada nos dois testes que a fixam
(`ResultOverlayLayoutTest`, `ImagePanelTest`) — é a rede que prende «um botão a mais» sem querer.

Não-vacuidade: 20 mutações em `AskAi.kt`, `ResultOverlay.kt` e `OverlayController.kt`, **20/20
apanhadas** (`C:\Users\Eric PC\AppData\Local\Temp\opencode\ai_nonvacuous.ps1`). Duas valem a pena
contar: tirar o seletor de trás da app escolhida falha `a chosen app is tried first…`; e trocar o
texto da pergunta pelo texto tal como foi reconhecido falha `the question is asked about the text on
screen…`. A prova apanhou também um teste **vazio**: `the question leaves as a share of plain text`
comparava o tipo MIME com a própria constante do código, e por isso passava mesmo com a constante
trocada para `text/html`; o teste passou a escrever `text/plain` por extenso (é um contrato com a app
do outro lado, não um nome nosso).

O que fica **de fora**, e sabe-se:

- o serviço (copiar, tentar, esquecer o nome morto) não tem teste: o `FloatingOcrService` não tem
  nenhum, porque precisa de uma `MediaProjection` de verdade. O que dava para separar sem mentir — a
  ordem das tentativas — foi para o `AskAi`, e é isso que os testes prendem.
- o `<queries>` do manifesto não tem teste: é uma declaração, e nenhum teste a lê. O sintoma de ela
  faltar (lista vazia no Android 11+) aparece no telefone, não aqui.
- a ponte dos ajustes (`MainActivity`: gravar o prompt a cada tecla, escolher a app na lista) não tem
  teste pela mesma razão que o resto dos ajustes não tem: o projecto não tem testes de `MainActivity`.

---

## 6. Segunda ronda  (19/10/2026)

Seis pontos relatados depois do I1, na ordem em que vão ser feitos: **bugs primeiro**, depois o que for
mais fácil. O foco declarado do app é a **precisão** — o resto é secundário — e é por isso que o N1
não espera por mais nada que não seja o print.

| # | Estado | Item | Dificuldade |
| --- | --- | --- | --- |
| N1 | `[x]` | **A precisão caiu**: um kanji que era reconhecido (淀) já não é | bug (a investigar) |
| N2 | `[x]` | **«Perguntar à IA» não abre app nenhuma** | bug (trivial) |
| N3 | `[ ]` | **VOLTAR / tocar fora** = «Trechos»; «Fechar» quando não há trechos | fácil |
| N4 | `[ ]` | **Ordem do furigana entre parênteses** saiu errada | fácil |
| N5 | `[ ]` | **Furigana na página toda**, sem clicar num trecho | fácil |
| N6 | `[ ]` | A bolha é **puxada para o ✕** quando chega perto | fácil |

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
por isso o teste a 1080×2400 foi feito sobre uma **cópia ampliada** — não é exactamente o que o telefone
viu, e é provável que a leitura `まで` seja artefacto dessa ampliação. O que o print **do aparelho** prova,
e que a medição reproduz, é o que interessa: a coluna volta **sem o 淀** e a caixa começa onde a do print
começa. Não é a segmentação a mudar — o texto é que perdeu o kanji.

As outras três hipóteses ficam refutadas por construção:

- **D1** só retira linhas cujo texto é kana puro; `淀` não é kana, não podia ser tomado por leitura.
- **`placeBlocks`** descarta caixas minúsculas ou sem geometria; a 1080 não veio segmento nenhum com o
  淀, não havia caixa para descartar.
- **`upscaleSmall`** só toca em recortes abaixo de 420 px; a tela inteira nunca passa por lá.

**A troca, e porque se escolheu o kanji.** O 2400 existia por uma medição anterior (ver «Registo — a
carga que vai para o Lens»): numa **página desenhada**, a leitura miúda impressa (15 px a 720×1600
contra 22 px a 1080×2400) só é reconhecida no tamanho maior. Numa página de mangá real o mesmo tamanho
maior perde um kanji grande estilizado. Não há um tamanho que dê os dois. Ficou o kanji: uma palavra
perdida não se recupera, e a leitura impressa que o maior ganhava é redundante — o **D2** escreve a
leitura a partir do dicionário, e um **recorte** (onde a leitura miúda é olhada de perto) é ampliado e
continua a ser lido. O usuário escolheu esta opção depois de ver a medição.

**Correção:** `DEFAULT_MAX_DIMENSION` volta a **1600** — uma tela de 1080×2400 viaja como 720×1600. Só o
número mudou; nenhuma outra decisão do caminho de OCR foi tocada.

**Teste de regressão:** `ocr/ImagePreparerTest`:

| teste | o que ele prende |
| --- | --- |
| `a phone screen is brought down at the default` | a tela de 1080×2400 chega ao Lens como 720×1600, e não nativa |

Conferido contra o código quebrado: **2/2 mutações apanhadas** — voltar a constante para 2400 (a
regressão exacta) e anular a conta da redução; cada uma falha exactamente o teste acima
(`n1_nonvacuous.ps1`).

**O que fica em aberto, e sabe-se porquê:**

- O teste prende a **decisão** (o tamanho), não o comportamento do Lens — não há teste offline que prove
  o que o endpoint devolve. A prova é a medição acima, com dois pedidos reais e datada.
- Um bloco com texto mas **sem geometria** continua a não ser oferecido no «Tela inteira» (o
  `placeBlocks` descarta-o). Não foi a causa aqui — o texto do 淀 nem chegou — mas é o mesmo sintoma e
  continua por tratar.

### N2 — «Perguntar à IA» não abre app nenhuma  `[x]` (19/10/2026)

Relato: com uma app escolhida (DeepSeek) ou sem app nenhuma, o botão mostra sempre «Texto copiado, mas
nenhuma app instalada aceita texto», não abre seletor nenhum, e a mensagem ainda por cima aparece
atrás da janela do app que está aberto.

Causa, que se vê no código sem precisar do aparelho: a partilha é lançada **a partir do serviço**, e um
`Context` que não é uma `Activity` só pode lançar um intent com `FLAG_ACTIVITY_NEW_TASK`. Sem a flag, o
`startActivity` lança uma excepção de runtime e o `runCatching` à volta transforma-a em «não abriu».
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

Conferido contra o código antigo: **4/4 mutações apanhadas** —
tirar a flag de todos os intents, tirá-la só do seletor, remover a deduplicação do aviso e fazer o
`showResultNote` não fazer nada; cada uma falha exactamente o teste acima (`n2_nonvacuous.ps1`).
O teste do seletor é separado de propósito: a flag no intent embrulhado não chega.

Fica sem teste, e sabe-se porquê: o `FloatingOcrService.askAi` em si — o `startActivity` verdadeiro
precisa de um aparelho. O que ele decide (que intents tentar) está em `AskAi`, testado; a ponte
painel→serviço (`onAskAiRequested`) é a mesma do I1.

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
  saída em texto é copiável, seleccionável e é a convenção que o D1 já tinha, e o painel não usa
  `Spannable` em lugar nenhum. Fica como refinamento, não como pendência.
- **A leitura vinda da geometria do D1 alimentando o bloco**: desnecessário — se o mangá imprimiu o
  rubi, a frase já o tem entre parênteses e o D2 não escreve por cima.
- **A carga que vai para o Lens**: era a última dúvida aberta e está fechada com medições — ver
  «Registo — a carga que vai para o Lens», acima.

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
