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
