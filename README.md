# Yabai OCR

Bolha flutuante que lê **japonês** em qualquer app: você toca, escolhe a área (ou a tela inteira),
o texto é reconhecido e copiado para a área de transferência.

O motor padrão é o **Google Lens**, o mesmo usado pelo app Lens / pelo Google Tradutor — inclui
texto vertical de mangá e kanji difícil. Quando não há internet, o **ML Kit** (offline, embutido no
APK) assume como reserva.

---

## Como instalar

1. Copie o APK para o celular (ou use `adb install`).
2. Abra o APK e permita a instalação de fontes desconhecidas, se o Android pedir.
3. Abra **Yabai OCR**.

APK pronto:

| Arquivo | Uso |
| --- | --- |
| `app/build/outputs/apk/release/app-release.apk` | **Use este.** Assinado com a chave de debug, instala direto. ~35 MB. |
| `app/build/outputs/apk/debug/app-debug.apk` | Versão de desenvolvimento (pacote `dev.yabai.ocr.debug`, pode conviver com a outra). |

São 35 MB porque o APK traz, embutidos, o motor de OCR japonês do ML Kit (funciona sem internet) e o
motor de tradução neural do ML Kit (os *modelos* de idioma, esses sim, são baixados depois). As
bibliotecas nativas para `arm64-v8a` respondem por quase tudo isso, e o bloco
`ndk { abiFilters … }` em `app/build.gradle.kts` deixa só essa ABI — todo celular de 2016 para cá é
64 bits. Se precisar instalar num aparelho 32 bits, acrescente `"armeabi-v7a"` à lista e o APK sobe
para ~52 MB.

---

## Como usar

1. Abra o app e conceda **Sobrepor outros apps** (a bolha precisa disso).
2. (Opcional) Conceda **Notificações** — o Android exige uma notificação enquanto a captura está ativa.
3. Toque em **Iniciar OCR flutuante** e aceite o aviso de captura de tela do sistema.
4. Abra o app onde está o texto (mangá, jogo, site…).
5. Toque na bolha: os **seis ícones** do menu aparecem **numa faixa logo abaixo dela**.
   - **Tela inteira** (cantos de tela) — reconhece tudo que está na tela e **desenha uma caixa sobre
     cada trecho** encontrado;
   - **Selecionar área** (marcas de corte) — arraste para marcar a região e toque em **Reconhecer**.
   - **Ler kanji** (漢) — congela a tela e espera você tocar num **kanji**: o cartão mostra a palavra
     que o lê com a leitura dela e **o significado da palavra** (剣技 → esgrima, não só 技 → técnica),
     a leitura de **cada kanji** da palavra quando os verbetes deixam dividir (同時に → 同 どう, 時 じ),
     as leituras do dicionário de kanji (音/訓) e **de qual dicionário** veio o verbete. O retângulo
     cobre os **kanji da palavra**, não só o que você tocou. A página também amplia com a pinça, como
     na tela de trechos.
     Sem dicionário de kanji instalado — ou com ele desativado — o cartão diz **o que fazer** em vez
     de ficar vazio.
   - **Dicionário** (livro) — abre o painel como um **dicionário de consulta**: digite `日本` e veja a
     definição; abaixo do campo vêm os outros verbetes que começam assim (`日本語`, `日本人`, …). A busca
     usa todos os dicionários ativos ou só o escolhido, como no painel.
   - **Ajustes** (controles) e **Encerrar** (✕ vermelho) completam a faixa; os nomes aparecem na
     descrição de cada ícone, para leitores de tela.
6. Na tela inteira, **toque no trecho** que interessa: só ele vai para o painel (e para a área de
   transferência). A tela **congela** na captura que acabou de ser lida: **pinça para ampliar** (de
   1× a 5×) e arraste com um dedo para andar pela página — caixas, rubi e tarjas ampliam junto, então
   a marcação nunca sai de cima do texto. **Texto todo** manda a tela inteira, **Cancelar** desfaz.
   Quando o painel está aberto, **◀ Trechos** — como o **VOLTAR** do telefone ou um toque fora do
   cartão — traz as caixas de volta sem gastar outra consulta ao Google. Num recorte, o mesmo gesto
   **fecha** o painel.
7. O texto aparece no painel. Se **Copiar automaticamente** estiver ligado, já foi para a área de
   transferência; senão toque em **Copiar**.

Detalhes úteis:

- O botão **🌙 Escuro / ☀️ Claro** no topo troca o tema do app inteiro. Tema claro não é só a
  janela principal: painéis, tela de dicionários e botões seguem o mesmo `values`/`values-night`, com
  o rótulo sempre contrastando com o fundo (o mesmo botão é escuro sobre claro e claro sobre escuro).
- A bolha pode ser **arrastada** para onde você quiser: de fábrica ela **fica onde o dedo solta** e só
  não sai da tela. Para quem prefere a bolha sempre fora do caminho, **«Prender a bolha nas bordas»**,
  nos ajustes, traz de volta o encaixe na lateral mais próxima.
- Para **encerrar**, arraste a bolha até o ✕ que aparece no centro de baixo: o puxão começa a 192 dp
  do ✕ (três vezes o tamanho dele), cresce depressa — na metade do caminho a bolha já andou metade do
  que o dedo andou — e a bolha **encolhe** até 70 % como se fosse sugada; com o dedo em cima do ✕ ela
  fica exatamente no centro dele, que fica vermelho, e ao soltar o app pergunta antes de desligar.
  Soltar em qualquer outro lugar devolve a bolha ao tamanho normal e a deixa onde o dedo soltou.
- Toque na bolha **para abrir os ícones**; com o menu aberto, toque em qualquer outro lugar da tela
  para fechá-lo.
- Com o **painel de resultado** aberto, a tela pertence ao painel: **VOLTAR** ou um toque fora do
  cartão dá um passo atrás — se o texto veio de «Tela inteira», voltam as caixas; se veio de um
  recorte, o painel fecha. Para chegar à bolha, saia primeiro do painel.
- **HOME** e a tela de **apps recentes** fecham o painel: ele não pertence a tarefa nenhuma e ficaria
  flutuando sobre a tela inicial. Quem avisa é o foco da janela, sem permissão nova — a mesma que o
  painel já usa para o texto editável. A bolha continua onde estava.
- A **barra de espaço** do teclado nunca fecha o painel — nem o do dicionário, nem o da tradução: ela
  troca de língua (a lista de línguas aparece normalmente) ou insere um espaço no campo. O teclado
  abre janelas próprias e devolve o foco ao fechar; só uma perda de foco que **não volta** conta
  como ter saído do app.
- A faixa de ícones fica centrada sob a bolha; perto da borda ela encosta na tela, e quando não há
  espaço abaixo ela aparece **em cima** da bolha.
- Na faixa, **Ajustes** reabre a tela de configuração e **Encerrar** desliga o serviço.
- A **notificação** do OCR tem dois botões: **Pausar/Retomar** e **Parar**. Pausar não encerra nada —
  a sessão de captura continua viva, mas a bolha e o painel saem da tela e a notificação passa a dizer
  «Yabai OCR pausado» com o botão **Retomar**, que devolve a bolha exatamente ao lugar onde estava,
  sem pedir a captura de novo. **Parar** continua encerrando de verdade. Nos ajustes o status mostra
  **Pausado** e o botão principal também retoma.
- **Selecionar texto** no painel (texto reconhecido, tradução, definição, ficha de kanji, busca do
  dicionário): toque longo numa palavra seleciona ela e abre uma barrinha com **Copiar · Recortar ·
  Colar · Selecionar tudo**, dentro do próprio painel. O popup do sistema não aparece em janela
  flutuante — ele pertence à janela de um app comum —, então o painel desenha o seu: a barra fica acima
  da linha selecionada (abaixo, quando não há espaço), um toque fora dela **desmarca e fecha** — o mesmo
  gesto do sistema, sem precisar do botão de fechar — e o **VOLTAR** fecha só ela antes de sair do
  painel. As ações valem sobre a palavra marcada mesmo que o teclado mexa na seleção entre o toque
  longo e o toque no botão. **Recortar** copia e apaga, como num campo normal: a palavra vai para a área
  de transferência antes de sair da tela.
- **Estender a seleção** funciona como num campo comum: depois do toque longo aparecem duas **alças**
  redondas nas pontas da seleção, e arrastar uma delas estica a marcação letra a letra enquanto o dedo
  anda. A barra se esconde durante o arrasto, para não cobrir justamente o texto que você está pegando,
  e volta com as ações quando o dedo solta.
- As duas **permissões** também se removem pelo app: com a permissão concedida o botão deixa de dizer
  «Conceder» e passa a dizer **Remover**. Ele pergunta antes («Remover a permissão de sobreposição?»,
  com a consequência escrita) e só então abre a tela do sistema no ponto certo — o Android não deixa
  um app revogar a própria permissão, quem desliga o interruptor é você. **Cancelar** não abre nada.
- O botão principal cresce quando precisa: com o OCR ativo o texto vira «OCR flutuante ativo — toque
  para reabrir os ajustes», que ocupa duas ou três linhas, e a altura acompanha o texto em vez de
  cortar a última linha.
- **A seção dobra sozinha:** toque no título «Tradução», «Sugestões», «Kanji» ou «Dicionário» (ou no
  chevron ao lado) e só aquela seção se recolhe. O chevron do cabeçalho do painel dobra o painel
  **inteiro** numa linha só, que passa a mostrar a tradução.
- **Imagem** no painel põe o recorte capturado ao lado do texto reconhecido, para conferir o kanji
  que o OCR trocou. A escolha fica guardada nos ajustes.
- Os botões do painel ficam numa **grade de duas colunas de largura igual**, em vez de uma linha que
  quebrava em pedaços de tamanhos diferentes. A ordem é a do uso: primeiro o que sai do painel (as
  duas cópias, a pergunta à IA e **Fechar**), depois o que muda a captura (imagem, escolher trecho,
  tentar offline). O botão que sobra sozinho na última linha ocupa a linha inteira.
- **Perguntar à IA** copia o texto e abre a app de IA que você escolheu nos ajustes, com a pergunta
  já escrita. Não é uma integração com serviço nenhum: o texto é compartilhado como qualquer outra
  coisa, e quem responde é a app que já está no telefone.
- O nome do dicionário no cabeçalho da seção «Dicionário» abre a lista de dicionários ativos: dá para
  consultar só um deles (ou **Todos**) sem sair do painel. A escolha também fica guardada. Com a lista
  aberta, o **VOLTAR** (ou um toque fora do cartão) fecha só a lista e deixa o painel onde estava; um
  segundo VOLTAR é que sai do painel — antes o primeiro já jogava a captura fora.
- As **leituras** (furigana) que o Lens devolve encostadas nos kanji são reconhecidas e tiradas da frase —
  é o que impede o `しょくいん職員さん` de chegar ao dicionário. Nos ajustes dá para mantê-las onde foram
  impressas: `職員（しょくいん）さん`.
- Os kanji cuja leitura **não** está impressa ganham uma: o dicionário já conhece a leitura e o painel
  escreve-a **no próprio texto reconhecido**, ao lado dos caracteres (`聞（き）いた`).
- Desligue **Escolher o trecho da tela** nos ajustes se preferir que a tela inteira vá direto para o
  painel.

---

## Ler a palavra: dicionário offline e tradução

O painel de resultado não mostra só o texto. Abaixo dele vêm duas seções que se preenchem sozinhas
enquanto você lê:

- **Dicionário** — o texto reconhecido é segmentado em palavras (japonês não tem espaço, então isso é
  feito por busca no próprio dicionário) e cada palavra ganha leitura, classe e definição. Verbos
  conjugados são devolvidos à forma de dicionário: `食べました → 食べる 【polite past】`.
- **Tradução** — a frase inteira, pelo modelo **offline** do ML Kit (japonês → português ou inglês).

### O texto é editável

OCR erra kanji, e quando erra você normalmente sabe qual era. O texto reconhecido é um campo de
**edição**: corrija à mão e **0,8 s depois da última tecla** a consulta ao dicionário e a tradução são
refeitas sozinhas, com o texto corrigido. Não é preciso capturar a tela de novo — e as seções mostram
«Traduzindo…»/«Consultando…» enquanto trabalham, para não parecer que nada aconteceu.

**A leitura na tela não é o que sai dela.** As leituras que o painel escreve no texto (`職員（しょくいん）
さん`) são para ler, não para enviar: **Copiar original** e **Perguntar à IA** mandam a frase
**reconhecida**, sem os parênteses — `職員さん`. Se você editou o texto, o seu texto é que sai: a
correção é a única cópia que a tem.

### Consultar o dicionário sem capturar

O ícone **Dicionário** (livro) da bolha abre o painel como um dicionário de consulta: o campo do topo é
para **digitar**, não para corrigir um OCR. Digite `日本` e a resposta aparece como em qualquer captura —
definições, leituras e o bloco de kanji quando a consulta é um kanji só —, com uma diferença: abaixo do
campo vêm **sugestões** dos verbetes que começam pela consulta (`日本語`, `日本人`, …). Elas saem dos
mesmos dicionários ativos e obedecem ao mesmo seletor do cabeçalho («Todos» ou um só).

- A consulta inteira é tentada **primeiro como palavra**: `にほん` acha `日本` e `食べました` acha
  `食べる`, sem passar pela segmentação de frase.
- **Procurar pelo sentido:** ao lado do campo, o botão «🔤 Palavra» alterna para «🔄 Tradução». Nesse
  modo a consulta é procurada **dentro das definições**, não nos verbetes: `espada` acha 剣, 刀,
  サーベル e o que mais for definido assim, com a mesma ordem de importância dos dicionários. Todas
  as palavras digitadas precisam aparecer na definição (`espada longa` só traz o que tem as duas).
  Acentuação não atrapalha: `agua` acha «água» e vice-versa. O modo fica guardado e vale para as
  próximas buscas.
- **O primeiro uso da tradução prepara um índice.** Ele é gravado **ao lado de cada dicionário**
  (uns 10 a 20 MB no maior) e leva de segundos a alguns minutos, uma vez só — o painel avisa
  «Pesquisando pelo sentido… o primeiro uso de cada dicionário prepara um índice». Depois disso a
  busca responde na hora, e o índice é refeito sozinho se o dicionário for atualizado. Quem nunca
  liga o modo «Tradução» não paga nada por ele.
- **Romaji também funciona:** digite `nihon` ou `toukyou` e o app converte para hiragana
  (`にほん`, `とうきょう`) antes de procurar. Vogais longas com acento (`tōkyō`) também são aceitas.
  Romaji sem acento de vogal longa (`tokyo`) é ambíguo — prefira `toukyou` ou `tōkyō`.
- Tocar numa sugestão preenche o campo e consulta na hora; dá para continuar editando o que ela escreveu.
- O botão ao lado do campo muda com o que há nele: **×** limpa a consulta inteira; com o campo
  **vazio** ele vira **Colar** e traz o texto que está na área de transferência (o mesmo de um Ctrl+C),
  já consultando. Sem nada copiado, um aviso curto explica em vez de não fazer nada.
- Texto copiado de um leitor costuma vir com a leitura impressa — `職員（しょくいん）`. Em **Gerenciar
  dicionários**, ligue **«Ignorar as leituras entre parênteses na busca»**: a consulta descarta só os
  grupos que são **somente kana** (`職員（しょくいん）` → `職員`, `双(そう)眸(ぼう)` → `双眸`), nos dois tipos
  de parêntese. O campo continua mostrando o texto inteiro, e `(笑)`, `(2024)` e um parêntese sem par
  **não** são tocados. Vem desligada: sem a opção, a consulta é exatamente a digitada.
- **VOLTAR** ou um toque fora do cartão fecham a busca — não há captura atrás dela. Imagem e trechos
  não aparecem: a busca não reconheceu texto nenhum. A consulta em si, porém, pode sair do painel:
  com algo no campo aparecem **Copiar original** e **Perguntar à IA**, os mesmos botões da captura,
  com a palavra do campo no lugar da frase reconhecida. Com o campo vazio os dois somem — não há o que
  copiar nem sobre o que perguntar —, e não existe «Copiar tradução» aqui: o dicionário responde com
  definições, não com uma tradução.
- A **barra de espaço** do teclado nunca fecha o dicionário: ela muda de língua ou insere espaço no
  campo.
- A escolha de dicionário vale para a busca e fica guardada, como no painel de uma captura.
- Nos ajustes, ligue **«Manter a última busca aberta no dicionário»** para que o campo comece sempre
  com a última coisa que você pesquisou — útil quando consulta várias palavras seguidas.
- **Outros apps podem mandar a palavra:** selecione um texto no leitor (ReadEra, por exemplo) e escolha
  **Yabai OCR** em «Aplicativos disponíveis» / «Compartilhar». O **painel do dicionário aparece por
  cima do leitor**, como o da bolha, com a palavra já no campo — a página que você estava lendo fica
  atrás, sem captura e sem a bolha ligada. O app responde às duas portas padrão do Android —
  **«Processar texto»** (`ACTION_PROCESS_TEXT`) e o **compartilhamento de texto simples**
  (`ACTION_SEND`, `text/plain`) —, que é como os leitores montam a lista de dicionários/tradutores
  instalados. Sem a permissão de sobreposição, a mesma busca abre como uma tela do app. O filtro de
  dicionários e a limpeza de parênteses valem do mesmo jeito, e o painel sobre o leitor traz as mesmas
  **Copiar original** e **Perguntar à IA** do painel da bolha — a palavra vai para a área de
  transferência e, se você já escolheu uma app de IA, ela abre com a pergunta escrita.

### Furigana (a leitura ao lado do kanji)

Mangá imprime a leitura dos kanji em kana pequenas encostadas neles — e o Lens reporta isso como **texto**,
não como anotação. Sem tratamento, a frase sai com a leitura **colada na palavra que ela explica**:

```
しょくいん職員さんに聞いた          ← o que o OCR devolve
職員さんに聞いた                   ← o que a frase é
```

Não é só feiúra: `しょくいん職員` não existe em dicionário nenhum, e a tradução recebe uma palavra
inventada no meio da frase. O app reconhece a leitura **pela posição dela na página** (kana finas,
encostadas num kanji, cobrindo só os caracteres que leem) e faz uma das duas coisas — a sua escolha:

| Ajuste «Manter o furigana que a página imprime» | Resultado |
| --- | --- |
| **desligado** (padrão) | `職員さんに聞いた` — a leitura sai da frase, que é o que o dicionário e a tradução querem |
| ligado | `職員（しょくいん）さんに聞いた` — a leitura fica **depois dos kanji que ela lê**, para ler em voz alta |

Numa captura real a leitura veio assim: `しょくいんネオの職員さんに聞いたら外出したって`. O certo é o mesmo
princípio — tirar a leitura de dentro da frase —, e é isso que o app faz, seja a frase curta ou longa.
Nesse mesmo balão (cinco colunas de texto vertical, cada leitura impressa na sarjeta à direita dos kanji
que ela lê), com o ajuste ligado, era assim que a leitura voltava e é assim que ela volta:

```
ネオ（き）の職員さ（しょくいん）んに聞い（ヒドー）たら非童（がいしゅつ）帝くん…   ← antes: cada leitura depois da coluna errada
ネオの職員（しょくいん）さんに聞（き）いたら非童帝（ヒドー）くん外出（がいしゅつ）したって…   ← agora: depois dos kanji que ela lê
```

- Vale só para o **Lens**, que é quem reporta as leituras com posição. O ML Kit offline não tem amostra
  medida e não é tocado: na dúvida, o app não mexe — linha não reconhecida fica como estava.
- A leitura é detectada comparando **tamanho e distância** com a linha vizinha, e só se ela for **só
  kana** ao lado de uma linha **com kanji**. Duas colunas de balão, mesmo que uma seja bem menor que a
  outra, continuam sendo dois textos. Num balão de colunas coladas, a leitura vai para a coluna de que
  ela está **do lado certo** — o rubi é impresso à direita de uma coluna (acima, numa linha horizontal)
  —, e não para a coluna anterior da frase, que é para onde ela caía.
- A leitura é escrita **depois dos caracteres que ela lê**, e só deles. O rubi é composto ajustado aos
  kanji que lê, então sobra um fio além deles; esse fio não conta como outro caractere.
- O **texto cru do Lens** (o painel em «texto linha por linha») continua mostrando a leitura: é ali que se
  confere o que o OCR viu, e escondê-la seria falsear o resultado do OCR.

Uma nota sobre o **tamanho**: numa tela de telefone a imagem vai em 720×1600 (ver **Precisão**, abaixo),
e a leitura impressa muito pequena — uns 15 px de altura — fica abaixo do que o Lens reporta nesse
tamanho. Quando isso acontece não há o que tirar: a frase já sai limpa. A leitura continua disponível
**no texto reconhecido** (escrita pelo dicionário) e num **recorte**, que é ampliado antes de ir.

#### E quando o mangá não imprime a leitura

Aí o Lens não tem o que reportar — mas o dicionário sabe. Ele já dividiu a frase em palavras, e cada
palavra que ele achou traz a própria leitura. O painel escreve essa leitura **no próprio texto
reconhecido**, ao lado de cada kanji — não num bloco à parte, que ficava longe dos caracteres que ele
explica:

```
職員さんに聞いた   →   職員（しょくいん）さんに聞（き）いた
```

Numa frase de verdade, com o JMdict:

```
私は昨日、友達と一緒に寿司を食べました。
  →  私（わたし）は昨日（さくじつ）、友達（ともだち）と一緒（いっしょ）に寿司（すし）を食（た）べました。
```

- A leitura vai **só em cima do kanji**: `食（た）べました`, e não `食（たべる）べました` — o `べました`
  já está impresso na frase. Uma leitura que não encaixa nos caracteres é **deixada de fora**: leitura
  errada ensina o caracter errado.
- Kanji que **nenhuma palavra** cobre e que está **sozinho** recebe as leituras do KANJIDIC, da japonesa
  para a chinesa (`職員（しょくいん）` é palavra; `滉（こう・たか?）` é palpite). O `?` diz que ali são as
  leituras do **kanji**, não de uma palavra; a nota que explica o `?` aparece acima da lista de palavras
  e só quando há algum palpite. Dois kanji colados nunca são lidos como se fossem um só.
- A leitura entra **depois** da consulta, sobre o texto que o OCR devolveu: escrevê-la não dispara uma
  segunda consulta, e o dicionário e a tradução continuam a receber o texto limpo.
- **Se você corrigir o texto, a correção manda.** Uma consulta que chega depois de uma edição não
  sobrescreve o que você escreveu; a leitura nova só entra enquanto o campo ainda é o que o painel
  escreveu.
- Vale para a **página toda**: com «Texto todo» selecionado, a leitura sai ao lado de cada kanji do
  texto inteiro — não é preciso escolher trecho por trecho.
- Se a leitura **já estiver na frase** (o rubi que o app manteve entre parênteses, ou o que o mangá
  imprimiu), ela não é escrita uma segunda vez.
- Liga e desliga em **«Escrever no painel a leitura do dicionário»** (ligado de fábrica). O texto
  reconhecido é selecionável e copiável, como sempre.

#### A leitura sobre a página, antes de escolher o trecho

O painel escreve a leitura no texto; a **tela de trechos** — aquela em que as caixas aparecem sobre a
página, antes de você tocar em nada — não tinha leitura nenhuma. Agora tem, e você escolhe a forma em
**«Leitura sobre a página, na tela de trechos»**:

| Ajuste | O que aparece |
| --- | --- |
| **Rubi** | a leitura desenhada ao lado de cada kanji: uma coluna de kana à direita de uma coluna vertical, uma linha pequena acima de uma linha horizontal |
| **Tarja** | uma etiqueta ao lado de cada balão que tenha leitura, com a frase e as leituras entre parênteses, `職員（しょくいん）さんに…`; quando a faixa ao lado já está ocupada por outra etiqueta, ela desce uma posição e continua colada ao próprio balão |
| **Não mostrar** (padrão) | só as caixas, como antes |

O rubi é fiel ao que o mangá faz, mas numa página inteira a letra fica do tamanho do rubi impresso:
para ler de perto, use a tarja, o painel ou **a pinça na própria tela de trechos** — a tela congela na
captura e um `Canvas.scale` move página, caixas, rubi e tarjas juntos, de 1× a 5×. As duas formas ficam
disponíveis para você decidir qual prefere. As leituras vêm do dicionário e aparecem só para o que ele
conhece — um kanji sem palavra e sem entrada de kanji fica sem leitura, como no painel. O rubi custa uma
consulta por coluna, feita depois do OCR e em segundo plano; se o dicionário não abrir, as caixas
aparecem do mesmo jeito, só sem leitura.

### Ver a imagem capturada

Quando o OCR troca um kanji, quem tira a dúvida é a imagem: o painel diz **淀** e a página diz qual
era. Por isso o painel pode mostrar, **ao lado do texto reconhecido**, o pedaço de tela de onde
aquele texto saiu:

| O que você toca | O que acontece |
| --- | --- |
| **Imagem** (na grade de ações do painel) | O recorte aparece à esquerda do texto reconhecido e o botão fica destacado. Toque de novo para esconder. |

- A escolha é **lembrada** e vale para as próximas capturas; a mesma caixa existe nos ajustes
  («Mostrar a imagem capturada ao lado do texto»). Vem **desligada** de fábrica: o que perde largura
  com a imagem é o texto reconhecido — justamente o campo que você edita.
- Numa **captura de área** a imagem é o recorte inteiro. Quando o texto vem de um trecho escolhido no
  «tela inteira», a imagem é **aquele trecho**, não a tela toda: uma página em miniatura não ajudaria
  a conferir kanji nenhum.
- O tamanho quem manda é a própria imagem (nunca ampliada, no máximo 120×200 dp), e o texto fica com
  o que sobra; uma coluna estreita de mangá quase não tira largura do texto. Numa tela baixa a imagem
  encolhe antes que os botões do painel passem da borda de baixo: ela é a única parte do cartão que
  pode ceder sem deixar nada fora de alcance. Se o painel estiver dobrado, a imagem some junto com o
  resto.

### Escolher o dicionário sem sair do painel

Com vários dicionários ativos, a definição que aparece é a do **primeiro da lista** que conhece a
palavra — e às vezes a que interessa é a do outro. O cabeçalho da seção «Dicionário» diz qual está
respondendo e abre a lista com um toque:

| O que você toca | O que acontece |
| --- | --- |
| O nome do dicionário ao lado de «Dicionário» | Abre a lista: **Todos** e cada dicionário ativo. O que está em uso vem destacado. |
| Um nome da lista | A consulta é refeita na hora, só com aquele dicionário. O seletor fecha. |
| **Todos** | Volta a consultar todos, na ordem de prioridade. |

- A escolha é **lembrada** e vale para as próximas capturas — e a **tradução da frase não depende dela**,
  então só o bloco do dicionário muda de conteúdo.
- O seletor só aparece com **dois ou mais** dicionários ativos: com um só não há o que escolher. Quem
  habilita e ordena os dicionários é **Gerenciar dicionários**, não o painel.
- Se o dicionário escolhido for desativado ou removido depois, o filtro é **descartado** e o painel volta
  a consultar todos — em vez de não achar palavra nenhuma para sempre.
- O bloco **Kanji** segue a mesma escolha, para uma seção não contradizer a outra — com uma exceção: um
  dicionário de **palavras** não tem verbetes de kanji, e escolhê-lo não pode deixar o cartão do kanji
  mudo. Nesse caso o bloco do kanji consulta os dicionários que têm kanji; a escolha continua valendo
  quando ela inclui um deles.

**Dicionário padrão do cartão de kanji.** Em **Gerenciar dicionários**, o cartão «Dicionário de kanji»
escolhe qual dicionário de kanji responde ao cartão do 漢 e à leitura de cada kanji do composto —
«Todos os dicionários de kanji» é o padrão. A escolha é **independente** do seletor do painel (aquele
fala do texto reconhecido; este, de um kanji) e é guardada pelo **id** do dicionário instalado, então
continua valendo depois de uma atualização que mude a data no título. Se o escolhido for removido,
desativado ou for de palavras, o cartão volta a consultar todos em vez de ficar mudo.

O **significado do composto** (剣技 → esgrima) segue outra escolha: a do **painel**, não a do cartão de
kanji — quem tem o composto é um dicionário de palavras, e o KANJIDIC só conhece o 技 sozinho. Com o
Jitendex em português ativo, o significado sai em português; com o JMdict, em inglês.

### Recolher o painel

O painel cresce de cima para baixo e, num mangá com um kanji diferente por quadro, a seção do
dicionário fica maior do que a tela — e o que interessa (a tradução) sai de vista, ou o texto que você
quer ler fica escondido atrás do painel. Por isso o painel dobra:

| O que você toca | O que acontece |
| --- | --- |
| O título de uma seção («Tradução», «Kanji», «Dicionário») | Só aquela seção se recolhe, virando uma linha. As outras ficam como estão. |
| O chevron ao lado do título do painel | O painel inteiro vira **uma linha**, mostrando a tradução numa tira — o mangá embaixo continua à vista. Toque de novo para abrir. |

A escolha de cada seção é lembrada enquanto o painel está aberto, inclusive nas re-análises que a
edição do texto dispara: você recolhe o dicionário uma vez e ele continua recolhido. **Uma captura
nova abre tudo aberto** — um painel que nasce recolhido esconderia justamente a resposta que você
pediu.

### Sair do app fecha o painel

Apertar **Início** ou o **botão de apps recentes** (o quadrado, ou o gesto equivalente) fecha o
painel: o de tradução, o dicionário aberto pela bolha, o que o ReadEra abre com a palavra
selecionada e o leitor de kanji (as tiras do «Ler kanji»). O painel não é uma tela do app — é uma
janela flutuante —, e sem isso ela ficava por cima do launcher.

O aviso vem do sistema quando esses botões são apertados, e o Yabai OCR o lê por **duas vias**: o
broadcast `ACTION_CLOSE_SYSTEM_DIALOGS` e a chamada `closeSystemDialogs` que **cada janela** recebe.
As duas juntas cobrem o botão, o gesto e as ROMs que mexem no launcher. O menu de energia
(`globalactions`) e os outros avisos do sistema **não** fecham nada — só `homekey` e `recentapps`.
Tirar o foco da janela (quando outro app vem para a frente) continua fechando o painel como antes.

### Dicionários

Nenhum dicionário vem dentro do APK (o menor útil tem 100 KB e o maior 37 MB, e cada um tem sua
licença). Você baixa ou importa em **Gerenciar dicionários**:

| Caminho | Como funciona |
| --- | --- |
| Lista do app | Baixa o `.zip` oficial mais recente, converte uma vez e instala. Tem barra de progresso, e enquanto baixa o próprio botão vira **Cancelar** (o download para de verdade, sem esperar o arquivo terminar). |
| **Importar arquivo** | Escolhe qualquer `.zip` no formato **Yomitan/Yomichan** pelo seletor do sistema e converte igual. |

O que está na lista:

| Dicionário | Idiomas | Tamanho |
| --- | --- | --- |
| JMdict | inglês | 15 MB |
| Jitendex | inglês | 37 MB |
| Jitendex em português (ggjz.org) | português | 39 MB |
| JMnedict (nomes próprios) | inglês | 11 MB |
| Wikcionário japonês → português | português | 0,4 MB |
| Wikcionário japonês (monolíngue) | japonês | 12 MB |
| Wikcionário japonês → inglês | inglês | 21 MB |
| Onomatopeias (surasura) | japonês | 0,1 MB |
| KANJIDIC | inglês | 0,7 MB |
| KANJIDIC | português | 0,3 MB |

Vários podem ficar ativos ao mesmo tempo. Quando duas palavras batem, a ordem da lista decide: o de
cima aparece primeiro, e as demais opções vêm empilhadas abaixo — como no Yomichan. Dá para ativar,
desativar, reordenar e remover. Para consultar só um deles **sem mexer nesta lista**, use o seletor do
cabeçalho do painel («Escolher o dicionário sem sair do painel»).

Duas ressalvas honestas:

- **Não existe JMdict em português, mas o Jitendex em português chega perto.** É uma adaptação do
  Jitendex inglês feita pelo ggjz.org: cerca de **211 mil verbetes** traduzidos, com definições completas
  em português (日本 → «Japão», 日本語 → «japonês»). O Wikcionário lusófono (8 mil verbetes) e o KANJIDIC
  em português (só os ~2 mil kanji usuais) continuam menores, para consultas pontuais. Um kanji raro
  como **淀** só tem significado em inglês — lá está (`pool (in river)`, 音 テン/デン, 訓 よど.む). Para o
  sentido em português, comece pelo Jitendex PT-BR; a **tradução** da frase cobre o que faltar.
- **Cada dicionário ativo custa memória.** Só o índice de chaves fica na RAM (o resto é lido do
  arquivo sob demanda): cerca de 17 MB para o JMdict, 18 MB para o Jitendex, 21 MB para o JMnedict,
  29 MB para o Wikcionário inglês. Todos juntos passam de 80 MB. Se o celular tiver pouca memória,
  deixe ativos só os que você usa.

**Katakana e onomatopeia.** As palavras em katakana (コンビニ, アルバイト, サラリーマン) já estão no
JMdict, no Jitendex e no Jitendex PT-BR como verbete comum, com a marca de «geralmente escrito em
katakana» — não existe (nem faz falta) um dicionário separado só de katakana. Onomatopeia é onde
falta alguma coisa: as miméticas de mangá que o JMdict não lista. Para elas a lista traz
**Onomatopeias (surasura)** — 1.421 entradas, definições em japonês com exemplos, 0,1 MB, e é o que
faz ぴくりぴくり responder. Existe também um dicionário japonês → inglês de onomatopeias, o
«Onomatoproject» (12,2 MB), mas ele só é distribuído numa pasta do Google Drive, sem endereço fixo
por arquivo: **Importar arquivo** aceita o `.zip` dele, e a lista curada não o oferece porque o link
poderia morrer de uma atualização para outra.

A definição é convertida **na importação**. Um dicionário importado antes de uma mudança no formato
continua com o texto antigo e a lista de dicionários avisa («Compilado com uma versão antiga do
app…»). Se ele veio da **lista do app**, use **Atualizar** na seção «Atualizações»; se veio de um
**arquivo**, importe de novo — o app não guarda o `.zip` de origem de uma importação manual, então não
há como reconvertê-la sozinho.

**Atualizar sem reinstalar.** A seção **«Atualizações»**, no topo de *Gerenciar dicionários*, tem
**Verificar atualizações**: o app pergunta a cada editor qual é a versão publicada e marca as linhas
que têm algo novo. Não é um download — são um descritor de ~1 KB (Jitendex PT-BR e Wikcionários) ou um
`HEAD` (JMdict, Jitendex, JMnedict, KANJIDIC, surasura) por dicionário. A linha marcada ganha
**Atualizar**, e **Atualizar tudo** faz o mesmo para todas, uma de cada vez, mostrando o **percentual**
de cada download e a conversão. Como não existe patch — as definições são gravadas no arquivo na
importação —, atualizar baixa o `.zip` novo, converte e troca a cópia **no mesmo lugar**: o id, a
posição na lista e o estado ativo/desativado continuam, e a versão nova já responde na consulta
seguinte. Não é preciso desinstalar nem reimportar, e ao terminar a tela continua utilizável (o
próximo **Verificar** pode ser disparado na hora).

As revisões são comparadas **como números, não como texto**: o descritor do ggjz/Wikcionário pode
ficar para trás do arquivo que ele aponta (o `kty-ja-pt` anuncia `2025.08.12` enquanto o `.zip` já é
`2026.03.05`), e uma revisão mais antiga ou igual à instalada conta como **em dia** — o app nunca
oferece voltar de versão. Quando o editor muda o rótulo sem números (`2026-03-05` → `2026.03.05`), a
comparação entende que é a mesma versão.

Em **«Verificar atualizações automaticamente»** escolha **Desligado** (padrão), **Todos os dias**,
**Toda semana** ou **Todo mês**. A verificação periódica usa o WorkManager: sobrevive a reinícios, só
roda com internet e **apenas notifica** — nunca baixa 39 MB por conta própria. No Android 13+ ela pede
a permissão de notificação; sem ela a verificação continua e o resultado aparece só na tela. O sistema
pode atrasar a execução para economizar bateria, então «toda semana» é uma referência, não um relógio.
Dicionários vindos de **Importar arquivo** não têm editor para consultar: a linha deles diz isso e não
oferece botão. Uma cópia baixada antes desta versão não tem assinatura guardada; a primeira verificação
responde «não foi possível comparar» e oferece **Atualizar** uma vez, que passa a registrar a versão.

O aviso aparece em **dois lugares**, porque um só não bastava: num **aviso no topo da lista de
dicionários**, com o nome de cada dicionário antigo, e numa **nota no painel** logo que uma consulta
usa um deles — é a tela onde a diferença é lida. Cada linha mostra ainda **de onde o dicionário veio**
(«Origem: JMdict (inglês)»): o título vem de dentro do arquivo e se repete entre as variantes — o
JMdict em inglês e o português, os três Wikcionários —, então sem essa linha duas entradas ficavam
idênticas na tela.

**Como a definição é desenhada.** Sentidos numerados um por linha (`1.`, `2.`), e as definições do
mesmo sentido juntas, separadas por `;`. Notas (`ℹ`) e exemplos (`例`, com a tradução em `→`) vêm logo
abaixo do sentido a que pertencem, em letra menor, cinza e recuada. A classe gramatical
(`1-dan・transitive`) fica na linha de apoio, sob a palavra, junto do nome do dicionário. É o mesmo
texto que o Jitendex e o JMdict trazem — só separado em vez de grudado.

**Classes e campos saem da tradução; «Ver também» não aparece.** O Jitendex em português marca cada
sentido com etiquetas próprias — «substantivo», «suru», «transitivo», «linguística» — e uma versão
antiga do app as lia como se fossem a definição: 経験 virava `1. substantivo` / `suru` / `transitivo` /
`experiência`, tudo no mesmo corpo de letra. Agora essas etiquetas vão para a **linha de apoio** (sob a
palavra, junto do nome do dicionário, em letra menor e cinza), e a definição começa direto no
significado: `1. experiência`. O bloco «Ver também» — um link para outro verbete, que o painel não tem
como abrir — é removido inteiro, com o rótulo, a palavra ligada e a glosa dela. As duas mudanças
alteram o texto gravado na importação, então um Jitendex importado antes desta versão continua com o
texto velho (a lista avisa): **baixe ou importe de novo** para ver as etiquetas separadas.

### Tradução offline

A primeira tradução baixa o modelo do idioma (cerca de **30 MB**), via Google Play Services; depois
disso funciona em modo avião. Por isso o primeiro uso precisa de internet e de Play Services. Se o
modelo não puder ser baixado, o painel diz exatamente isso em vez de ficar mudo.

### Perguntar a uma IA que você já tem

A tradução do ML Kit resolve a frase; o que ela não resolve é «por que esta palavra está escrita
assim», «e num mangá, que tom tem isso», «esta gíria existe mesmo». Para isso existe o botão
**Perguntar à IA**, logo abaixo das duas cópias na grade do painel:

| O que ele faz | Detalhe |
| --- | --- |
| Copia o texto reconhecido | Para a área de transferência, com as suas correções — o mesmo texto do painel. |
| Monta a pergunta | O que estiver em «O que perguntar» nos ajustes, seguido do texto. De fábrica: *«Traduza para português e explique:»*. |
| Abre a app de IA | A que você escolheu nos ajustes, com a pergunta já escrita. Sem escolha — ou se a app escolhida já não existe — aparece uma janela com os apps de IA e tradutores do telefone, cada um com ícone e nome; o toque abre o app. |
| Fecha o painel | Só quando um app abriu. Ele é uma janela flutuante: deixado na tela, ficaria por cima de quem vai responder. Se nada abriu, o painel fica e diz o que aconteceu. |

A lista de apps é desenhada **pelo Yabai OCR**, numa **janela própria** — não pelo seletor do
Android: o seletor do sistema devolvia o controle sem abrir o app escolhido no aparelho em que isto
foi testado (fechava e a tela ficava como estava). A janela mostra cada app com o **ícone** e o nome,
rola quando a lista não cabe na tela, e fecha pelo ✕, por um toque fora do cartão ou pelo BACK — sem
fechar a tradução atrás. Abrir o app daqui, com o nome do pacote, é o mesmo caminho de quando há uma
app fixa, que sempre funcionou.

A janela abre na lista curta: **só apps de IA e tradutores**, reconhecidos pelo pacote (ChatGPT, Gemini,
Claude, Copilot, Perplexity, DeepSeek, Grok, os tradutores do Google, Microsoft, DeepL, Yandex e os de
fábrica) ou por uma palavra do nome (`gpt`, `gemini`, `assistente`, `tradutor`, `tradução`, `translate`…).
Gmail, Bluetooth e «Converter para JPEG» aceitam texto mas não respondem a uma pergunta, e saem da
frente. Como isso é um palpite sobre nomes, nada é tirado: o rodapé **«Mostrar todos (N)»** abre a
lista completa do telefone e **«Só os de IA»** volta. Se nenhum app parecer de IA, a lista aparece
inteira — melhor do que dizer que não há nenhum.

Aqui **não há integração com nenhum serviço de IA**: nada de chave, conta, cota ou custo, e o texto
não passa por este app — ele é copiado e compartilhado como qualquer outra coisa que se compartilha
(`ACTION_SEND`), que é o que qualquer app de IA (ChatGPT, Gemini, Copilot, Claude…) já entende. A
consequência agradável é que a lista de apps «suportadas» **é a lista do seu telefone**: instalar uma
app nova é o suficiente, e trocar de app preferida é uma escolha nos ajustes, não uma atualização do
Yabai OCR.

Se **nenhum** app do telefone aceitar texto, o painel diz isso mesmo — logo abaixo do cabeçalho, onde
o aviso não pode ficar escondido atrás do app que está aberto — e o texto já está na área de
transferência.

Nos ajustes, a seção **«6. Perguntar a uma IA»** tem o botão (ligado/desligado), o campo da pergunta —
apagado, ele manda só o texto — e a app que responde, escolhida de uma lista lida do aparelho. A lista é
a **mesma** da janela de escolha do painel: **«Automático (só apps de IA)»** por omissão, os apps de IA
e tradutores, e o rodapé **«Mostrar todos (N)»** para o resto do telefone. O nome da app é lido do
telefone, não guardado: se você desinstalar a que tinha escolhido, o ajuste volta sozinho a
«Automático», em vez de apontar para uma app que não está lá.

---

## Precisão

Três ajustes controlam o resultado:

| Ajuste | Padrão | O que faz |
| --- | --- | --- |
| Motor | **Automático** | Usa Lens e cai para o ML Kit se o Lens falhar ou não devolver texto. |
| Ampliar recortes pequenos | ligado | Recortes com menos de 420 px no lado maior são ampliados até 2×. Ajuda bastante em furigana e letras pequenas. |
| Escolher o trecho da tela | ligado | Depois de reconhecer a tela inteira, desenha uma caixa sobre cada trecho e espera você escolher. Desligado, o texto todo vai direto para o painel. |
| Listar o texto linha por linha | desligado | Mostra o resultado exatamente como o motor o segmentou, sem juntar em um parágrafo. |

O Lens entrega o texto em **colunas soltas**: um balão de três colunas chega como três textos
separados. O `core` junta de volta as colunas que formam o mesmo bloco (mesma direção, faixa em comum
e vão menor que um caractere), o que é o que faz um balão virar uma frase traduzível em vez de três
fragmentos — e é também o que define o tamanho das caixas da tela inteira.

A ordem das colunas também vem dos retângulos, não da resposta: o Lens devolve as colunas de um balão na
ordem que quer (`ネオの職員さん`, depois `非童帝くん`, depois `に聞いたら`), e o `core` as lê **da direita
para a esquerda**, de cima para baixo dentro de cada coluna — e, num texto horizontal, de cima para baixo
e da esquerda para a direita. Sem isso, a frase saía com os pedaços trocados (`てる潰され内塊に` em vez
de `肉塊に潰されてる`), mesmo com todas as letras certas. O motor offline (ML Kit) usa a mesma regra: ele
entrega a página de cima para baixo e da esquerda para a direita, o que num balão vertical põe a última
coluna primeiro; os retângulos que ele mesmo devolve são a única coisa que sabe a ordem certa.

Além disso, a imagem vai com o lado maior em **1600 px** (`AppSettings.DEFAULT_MAX_DIMENSION`) e o
idioma anunciado é `ja` — uma tela de 1080×2400 viaja, portanto, como 720×1600.

As caixas da tela de trechos são desenhadas **na escala da página**, não na da janela. Quando a janela
fica menor que a captura — a barra de status volta a aparecer, por exemplo —, a página é esticada para
caber e as caixas esticam junto, no desenho e no toque. Antes elas ficavam nos pixels da captura: numa
página com **tarja preta em cima e embaixo** a caixa errava o alvo na mesma proporção das barras.

Esse número foi medido duas vezes, e a segunda corrigiu a primeira:

- Numa **página desenhada** (fundo limpo, letra de imprensa), 1080×2400 lia a leitura pequena encostada ao
  kanji (22 px de altura) que 720×1600 não lia (15 px).
- Numa **página de mangá de verdade**, o contrário: a 1080×2400 o Lens **perde o kanji grande
  estilizado** — o `淀` do título, desenhado à mão por cima da arte — e a coluna volta sem ele
  (`みなきまで。` na cópia ampliada que foi medida); a 720×1600 a mesma coluna volta inteira,
  `淀みなき拳で。`. Medido no endpoint real mudando só o tamanho, e o aparelho confirmou o essencial:
  a caixa do trecho deixou de cobrir o 淀.

A palavra que se perde não volta; a leitura impressa que o tamanho maior ganhava é redundante — o painel
escreve a leitura a partir do dicionário **no texto reconhecido**, e um **recorte**, onde a leitura pequena
é olhada de perto, é ampliado e continua sendo lido. Por isso o limite é 1600.

Dicas para acertar de primeira:

- **Recorte justo > tela inteira.** Marque só o balão/bloco de texto: menos ruído, resultado melhor.
- Em mangá vertical, com **Tela inteira** basta tocar no balão: as colunas dele já foram juntadas em um
  trecho só. Com **Selecionar área**, marque o balão inteiro, não uma coluna solta.
- Se o resultado vier vazio, **Tentar offline** aparece no painel e roda o ML Kit na mesma área.

### Guia anônima e telas que se escondem

Pedido: uma permissão para ler a guia anônima do Google. **Não existe** — e vale explicar por quê,
porque o sintoma (`Não foi possível reconhecer o texto.` com a página certa na frente) parece defeito.

O Android tem um sinalizador, o `FLAG_SECURE`, com que **o próprio app** pede que a janela dele não
apareça em capturas de tela: é o que banco usa no extrato e o que app de vídeo usa para não deixar
gravar o filme. A captura por `MediaProjection` — a mesma permissão que este app recebe quando você
aceita «Iniciar agora» — entrega a janela marcada **em branco**. Não há permissão de usuário que mude
isso: quem marca é o app exibido, e só ele pode desmarcar. É bom que seja assim.

O que varia de app para app é se ele marca a guia anônima. Navegador em modo anônimo normalmente
**não** marca (a captura funciona, é o mesmo mecanismo de sempre); quando a captura volta em branco,
foi o app que marcou. A outra via que o Android oferece é um **serviço de acessibilidade**, que lê a
árvore de textos da janela em vez de fotografá-la — mas é outra arquitetura, pede uma permissão bem
mais invasiva, e não serve ao caso deste app: texto desenhado **dentro de uma imagem** (mangá, scan,
print) não existe como texto nessa árvore, e é justamente esse texto que o OCR existe para ler. Por
isso não há botão novo: a limitação é do sistema, não desta app.

---

## Uso do Google Lens (e a verdade sobre a cota)

A tela do app tem uma seção **“7. Uso do Google Lens”** com:

- quantas requisições ao Lens foram feitas **hoje** e no **total**;
- a data da primeira requisição e o resultado da última;
- um aviso vermelho quando o Google **recusa** a requisição (HTTP 429/403), com a contagem
  regressiva do `Retry-After` quando o servidor manda esse campo.

O que **não** existe, e por isso não está lá:

> **Não há como ver “quanto resta” da cota.** O endpoint responde com os cabeçalhos genéricos do
> frontend do Google — nenhum `X-RateLimit-*`, nenhum `Remaining`, nenhum `Reset`. Também não há
> documentação: é uma API interna do Chromium, não um produto com contrato. Verifiquei isso numa
> requisição real (HTTP 200, resposta de 2169 bytes) e o único cabeçalho relevante era
> `Server-Timing`. A própria biblioteca de referência (`chrome-lens-ocr`) não documenta limite algum.

Ou seja: os números da tela são **medidos aqui no aparelho**, não lidos do Google. Na prática eles
bastam, porque o único sinal real de limite é o Google começar a recusar — e isso o app mostra na
hora, tanto na tela de uso quanto no painel de resultado (que passa a dizer *“Limite de uso do Google
Lens atingido (HTTP 429)”* em vez de um vago “Lens indisponível”).

Não há um “reset diário” observável: nenhuma resposta informa quando a cota volta. O contador
“hoje” zera à meia-noite apenas para você acompanhar o seu próprio ritmo. O botão **Zerar contador**
limpa tudo.

---

## Estrutura do projeto

```
core/   Kotlin JVM puro — nada de Android aqui
  proto/Proto.kt            codec protobuf escrito à mão (sem protoc)
  lens/LensProto.kt         esquema do endpoint do Lens + montagem da requisição e parsing
  lens/LensClient.kt        cliente OkHttp (cookie jar, retries) + recognizeSuspend
  lens/LensResultBuilder.kt objetos/parágrafos/linhas → segmentos com caixas em pixels
  lens/RubyGeometry.kt      geometria: que linha é leitura (furigana) de qual outra
  jp/JapaneseText.kt        normalização de espaço CJK-aware
  dict/DictionaryCompiler.kt  .zip do Yomitan → formato compacto em disco
  dict/CompactDictionary.kt   leitura do formato compacto (índice na RAM, verbete no disco)
  dict/LookupEngine.kt        segmentação por maior correspondência, consulta inteira e sugestões
  dict/DictionaryQuery.kt     limpa a consulta digitada (tira as leituras entre parênteses)
  dict/Furigana.kt           a leitura da palavra escrita ao lado dos kanji que ela lê
  dict/Deinflector.kt         conjugações → forma de dicionário (tabela do Yomichan)
  dict/GlossaryRenderer.kt    structured-content dos dicionários modernos → texto
  resources/deinflect.json    matriz de regras (Yomichan, MIT)

app/    o app Android
  MainActivity.kt           permissões + ajustes + painel de uso
  LookupActivity.kt         a palavra que outro app manda (ReadEra, compartilhar) abre a busca
  DictionaryActivity.kt     baixar/importar/ativar/reordenar/remover dicionários
  AppSettings.kt            preferências compartilhadas
  UsageTracker.kt           contador local de requisições ao Lens (o Google não expõe cota)
  ai/AskAi.kt               a pergunta e o compartilhamento para uma app de IA (nenhuma API, nenhuma chave)
  capture/ScreenCaptureSession.kt   um VirtualDisplay de longa duração + ImageReader
  ocr/OcrDispatcher.kt      escolhe o motor, implementa o modo automático e contabiliza o uso
  ocr/LensOcrEngine.kt      Lens via core
  ocr/MlKitOcrEngine.kt     ML Kit japonês offline
  ocr/ImagePreparer.kt      reescala e codifica em PNG
  dict/DictionaryCatalog.kt    o que o app oferece para baixar
  dict/KanjiDefault.kt         qual dicionário instalado responde ao cartão de kanji
  dict/DictionaryRepository.kt instalação no disco + um LookupEngine em cache
  dict/DictionarySearch.kt  a resposta de uma consulta digitada (bolha, porta do ReadEra, tela de busca)
  dict/DictionaryDownloader.kt download com progresso (e o descritor do Wikcionário)
  translate/OfflineTranslator.kt  ML Kit tradutor offline
  overlay/…                 bolha, menu, seletor de área, caixas para escolher o trecho e painel de
                            resultado (Views puras)
  overlay/FlowLayout.kt     linha de botões que quebra em vez de espremer (painel de resultado)
  overlay/ResultOverlay.kt  o painel; o teto do scroller é medido, não um número fixo; e a busca digitada
  overlay/KanjiOverlay.kt   «Ler kanji»: a página congelada, o toque num kanji e o cartão de leituras
                            (a grade das células vem de core/layout/KanjiGrid.kt)
  service/FloatingOcrService.kt     o serviço que costura tudo
  service/LookupOverlayService.kt   a palavra vinda de outro app, num painel de sobreposição (ReadEra)

  app/src/test/…            testes de layout das telas, com Robolectric (sem aparelho)
```

Notas de implementação que valem saber antes de mexer:

- **Um `VirtualDisplay` por `MediaProjection`.** O Android 14+ recusa o segundo, então a rotação é
  tratada com `resize()` + `setSurface()` em vez de recriar a captura.
- **Captura é orientada a evento.** Uma tela espelhada só entrega frame quando o conteúdo muda, e um
  print de tela estática ficaria esperando para sempre. Por isso `armCapture()` é chamado *antes* de
  esconder os overlays: o frame gerado pelo desaparecimento das nossas janelas é justamente o frame
  limpo que queremos.
- **Janelas são destruídas, não apenas escondidas.** O painel de resultado rouba foco (de propósito,
  para permitir seleção de texto e o botão VOLTAR); uma janela focusable invisível engoliria toques
  destinados ao app de baixo.
- **`startForeground` antes de `getMediaProjection`.** É o que o Android 14 exige para o tipo
  `mediaProjection`.

---

## Versão Desktop (Windows)

O módulo `:desktop` lê japonês na tela do PC — navegador, leitor de mangá, PDF, jogo — sem tirar print
nem abrir outra janela. Rode com:

```powershell
gradlew :desktop:run
```

### Aparência: tema claro e escuro

Em **Ajustes → Aparência e Tema** escolha «Tema Escuro» ou «Tema Claro». A troca vale na hora, sem
reiniciar. Todas as janelas do app — Ajustes, dicionário rápido e gerenciador de dicionários — seguem
o tema, inclusive os botões: o rótulo de «Padrão» é escuro sobre branco no tema claro e branco sobre
o fundo escuro no tema escuro, sempre acima de 4,5:1 de contraste (WCAG AA). O seletor de tela
inteira, o recorte à mão, o cartão do hover e a janela de resultado são escuros por desenho e não
mudam com o tema.

### Menu da bandeja

O menu abre **junto ao ponteiro**, como o da Steam: o canto inferior esquerdo do menu fica na altura
do cursor e ele cresce para cima e para a direita. Se não couber à direita, ele inverte e cresce para
a esquerda; se não couber acima, abre abaixo. O menu fica sempre dentro da área útil do monitor sob o
cursor, fora da barra de tarefas — inclusive num segundo monitor. Clique esquerdo no ícone abre a
captura de tela inteira; clique direito abre o menu.

### Hover em tempo real (estilo Yomitan/Rikaichan)

1. Abra **Ajustes → Modo Hover → Ativar** (ou aperte o atalho, padrão **`Ctrl + Shift + H`**; o mesmo
   atalho desliga).
2. Deixe o ponteiro **parado por um instante sobre a palavra japonesa**. Não precisa clicar nem
   apertar tecla: depois de ~0,4 s o cartão aparece ao lado do cursor com leitura, classe da palavra,
   definição e o dicionário que respondeu.
3. Para ler a definição com calma, leve o ponteiro até o cartão — ele não fecha. Sair da palavra e do
   cartão fecha em ~0,2 s.
4. O menu da bandeja mostra `Modo Hover [Ativo]`; o tooltip do ícone repete o último resultado, o que
   ajuda a ver o que o OCR leu quando o verbete não vem.

O recorte tem 640×360 px em volta do cursor (mais contexto da linha, onde está a conjugação) e é
limitado à área dos monitores, então funciona com o ponteiro junto das bordas e em mais de uma tela.
A palavra é escolhida pela caixa de cada linha devolvida pelo Google Lens — em texto horizontal e
vertical —, e não pela frase inteira.

Se nada aparecer, o **status** (cartão dos Ajustes e tooltip da bandeja) diz o motivo: «Nenhum
dicionário instalado», «Nenhum texto reconhecido perto do cursor» ou «Texto lido, mas sem verbete:
…». Os dicionários ficam na pasta escolhida em **Ajustes → Pasta de Dicionários** (a chave antiga
`custom_dict_directory` continua sendo lida) e são abertos uma vez só, sob demanda.

### Capturar a tela inteira e escolher o trecho

1. Aperte o atalho da **tela inteira** (padrão **`Ctrl + Shift + O`**; se você já usava `Insert`,
   é ele que continua valendo). Em vez de pedir um arrasto, o app captura a tela e mostra o próprio
   screenshot com uma **caixa clicável sobre cada parágrafo**.
2. Clique no trecho que quer ler. A janela de resultado abre com o texto daquele parágrafo já
   reconhecido — o clique é o recorte —, com a imagem do bloco e os verbetes dos dicionários.
3. **Esc**, clique direito, «✕ Cancelar» ou fechar a janela desistem da captura. «✂️ Recortar à mão»
   volta ao overlay de arrasto sobre a mesma imagem capturada, para páginas que o reconhecedor não
   conseguiu separar.
4. **Rubi e faixa:** em **Ajustes → Comportamento do OCR e resultado → Leituras dos kanjis no seletor
   da página** escolha:
   - «Rubi sobre o texto» — a leitura dos kanjis vem dos **dicionários instalados** e é desenhada
     acima do texto horizontal ou na calha à direita do texto vertical;
   - «Faixa indicativa legível» — uma faixa com a frase reconhecida aparece ao lado de cada caixa,
     presa à tela e desviada das outras faixas;
   - «Desativado» — só as caixas, sem leituras.
5. O **recorte à mão** é uma ação separada, não uma preferência escondida: use o atalho próprio
   (padrão **`Ctrl + Shift + A`**) ou **«✂️ Capturar Área (OCR)»** no menu da bandeja. O item
   «🖥️ Capturar Tela Inteira (OCR)» e o clique esquerdo no ícone abrem o seletor de parágrafos.

A captura aparece nas **cores e na resolução reais**: a imagem vem do framebuffer em resolução
nativa, a janela usa a tela inteira como moldura (a área da barra de tarefas incluída; a barra em si
continua por cima, como sempre) e o desenho é 1:1 — sem véu escurecendo nem reamostragem. No recorte
à mão o escurecimento só aparece **depois** de você arrastar, para destacar a seleção.

O seletor captura o **monitor principal**; o modo hover continua escolhendo o monitor sob o ponteiro.

### Busca rápida de dicionário

O atalho **`Ctrl + Shift + D`** (ou «🔍 Busca Rápida de Dicionário» no menu da bandeja) abre uma
janela pequena para consultar os dicionários sem capturar a tela. Ela tem o mesmo seletor de
dicionário do painel e os mesmos dois modos:

- **«🔤 Palavra»** — procura o verbete, o kanji ou a leitura (hiragana/romaji), com sugestões de
  quem começa pela consulta.
- **«🔄 Tradução»** — procura **pelo sentido**: `espada` responde 刀【かたな】e 剣【けん】, com a
  definição completa. É a mesma busca por tradução do Android, com o mesmo custo: o primeiro uso
  prepara um índice ao lado de cada dicionário (o status mostra «Preparando o índice de
  tradução… %») e depois as consultas são quase instantâneas. A escolha fica guardada em
  `search_by_translation`.

A janela guarda posição e tamanho, e prefere o dicionário escolhido no seletor.

### Atalhos globais

| Atalho | Ação |
| --- | --- |
| `Ctrl + Shift + O` (padrão) | Capturar a tela inteira e escolher o trecho |
| `Ctrl + Shift + A` (padrão) | Capturar uma área com arrasto (recorte à mão) |
| `Ctrl + Shift + D` (padrão) | Abrir a busca rápida de dicionário |
| `Ctrl + Shift + H` (padrão) | Ligar/desligar o modo hover |

Todos podem ser trocados em **Ajustes → Atalhos Globais**, inclusive com o app já aberto; o botão
«Capturar» grava a combinação e o gancho global passa a usá-la na hora.

### Testes do desktop

```powershell
gradlew :desktop:test
```

Cobrem o que pode ser quebrado sem tela: casamento dos atalhos (incluindo o fato de os códigos
`VC_*` do JNativeHook não serem os do AWT e de a tela inteira e o recorte terem chaves e padrões
distintos), o tamanho nativo pedido à captura em HiDPI, debounce do hover, carência antes de fechar
o cartão, recorte/HiDPI/multimonitor, escolha da palavra sob o ponteiro, a leitura da pasta de
dicionários (inclusive a chave antiga), a geometria do seletor de tela inteira (escala das caixas,
acerto pela menor caixa, folga presa à imagem, posição do rubi e das faixas), a posição do menu da
bandeja (borda inferior no cursor, inversão à esquerda, work area), o contraste das cores do tema
nos dois modos (4,5:1 para texto e rótulo de botão) e a preferência do modo de busca (palavra ou
tradução, lembrada entre aberturas). Hoje são **96 testes**. A verificação ponta a ponta — Lens de
verdade, mouse parado, clique num parágrafo, cartão na tela — é feita rodando o app.

---

## Compilando

Requisitos: JDK 17+, Android SDK com `platforms;android-36` e `build-tools;36.0.0`.

```powershell
$env:ANDROID_HOME = "I:\Programas\Android\Sdk"
gradle :app:assembleRelease
```

Os testes do `core` (parsing offline, regressão sobre uma resposta real gravada) rodam sem rede:

```powershell
gradle :core:test
```

Há uma bateria de testes que compila **dicionários de verdade** (JMdict, Jitendex, JMnedict,
KANJIDIC, Wikcionário, onomatopeias), mede quanta memória cada índice ocupa e confere que uma busca
responde. Ela é opcional e aponta para uma pasta com os `.zip` baixados:

```powershell
gradle :core:test -Pdicts="C:\caminho\para\os\dicionarios"
```

Os nomes de arquivo esperados são os mesmos da lista do app (`JMdict_english.zip`,
`jitendex-yomitan.zip`, `JMnedict.zip`, `KANJIDIC_english.zip`, `KANJIDIC_portuguese.zip`,
`kty-ja-pt.zip`, `kty-ja-ja.zip`, `kty-ja-en.zip`, `surasura.zip`); o que faltar é simplesmente
ignorado.

O índice da busca por tradução é medido contra um dicionário **já convertido** (o teste copia para
uma pasta temporária antes de gravar o índice, e é ignorado se a pasta não existir):

```powershell
gradle :core:test -PcompiledDicts="C:\caminho\para\os\.yabai"
```

Com o Jitendex de 436.555 verbetes: índice de 310.452 palavras em ~7 s, 12,5 MB gravados e a
reabertura em 11 ms; `sword` devolve 568 verbetes, entre eles 剣.

Existe um teste que fala com o endpoint de verdade, desligado por padrão:

```powershell
gradle :core:test -Plive=1
```

As telas do `app` também têm testes, que **não precisam de aparelho**: o Robolectric monta o painel, mede
no tamanho de uma tela de celular e confere que nada sai cortado nem empurrado para fora.

```powershell
gradle :app:testDebugUnitTest
```

Para reduzir o tamanho do APK, `app/build.gradle.kts` limita as ABIs a `arm64-v8a` (x86 só serve para
emulador, e `armeabi-v7a` só para aparelhos 32 bits). Acrescente as ABIs que faltarem à lista em
`ndk { abiFilters … }` — só saiba que ela responde por quase todo o tamanho do APK.

---

## Aviso

O `core` fala com `https://lensfrontend-pa.googleapis.com/v1/crupload` usando a mesma chave de API
que o Chrome carrega internamente — o mesmo caminho que a biblioteca `chrome-lens-ocr` usa. **Isso é
uma API não oficial do Google**: não há contrato, ela pode mudar ou ser bloqueada sem aviso, e uso
intenso pode esbarrar em limite de requisições. Para uso casual e pessoal funciona bem; para algo
distribuído em escala, troque por um serviço oficial (Cloud Vision) ou fique só no ML Kit offline.
