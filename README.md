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
5. Toque na bolha: os quatro ícones do menu aparecem **numa faixa logo abaixo dela**.
   - **Tela inteira** (cantos de tela) — reconhece tudo que está na tela e **desenha uma caixa sobre
     cada trecho** encontrado;
   - **Selecionar área** (marcas de corte) — arraste para marcar a região e toque em **Reconhecer**.
   - **Ajustes** (controles) e **Encerrar** (✕ vermelho) completam a faixa; os nomes aparecem na
     descrição de cada ícone, para leitores de tela.
6. Na tela inteira, **toque no trecho** que interessa: só ele vai para o painel (e para a área de
   transferência). **Texto todo** manda a tela inteira, **Cancelar** desfaz. Quando o painel está
   aberto, **◀ Trechos** — como o **VOLTAR** do telefone ou um toque fora do cartão — traz as caixas
   de volta sem gastar outra consulta ao Google. Num recorte, o mesmo gesto **fecha** o painel.
7. O texto aparece no painel. Se **Copiar automaticamente** estiver ligado, já foi para a área de
   transferência; senão toque em **Copiar**.

Detalhes úteis:

- A bolha pode ser **arrastada** para qualquer canto e "gruda" na borda mais próxima.
- Para **encerrar**, arraste a bolha até o ✕ que aparece no centro de baixo: perto dele a bolha é
  **puxada para o ✕**, ele fica vermelho quando ela está em cima e, ao soltar, o app pergunta antes de
  desligar. Soltar em qualquer outro lugar devolve a bolha para a borda mais próxima.
- Toque na bolha **para abrir os ícones**; com o menu aberto, toque em qualquer outro lugar da tela
  para fechá-lo.
- Com o **painel de resultado** aberto, a tela pertence ao painel: **VOLTAR** ou um toque fora do
  cartão dá um passo atrás — se o texto veio de «Tela inteira», voltam as caixas; se veio de um
  recorte, o painel fecha. Para chegar à bolha, saia primeiro do painel.
- A faixa de ícones fica centrada sob a bolha; perto da borda ela encosta na tela, e quando não há
  espaço abaixo ela aparece **em cima** da bolha.
- Na faixa, **Ajustes** reabre a tela de configuração e **Encerrar** desliga o serviço.
- Toque longo no texto seleciona, como em qualquer TextView.
- **A seção dobra sozinha:** toque no título «Tradução», «Kanji» ou «Dicionário» (ou no chevron ao
  lado) e só aquela seção se recolhe. O chevron do cabeçalho do painel dobra o painel **inteiro**
  numa linha só, que passa a mostrar a tradução.
- **Imagem** no painel põe o recorte capturado ao lado do texto reconhecido, para conferir o kanji
  que o OCR trocou. A escolha fica guardada nos ajustes.
- **Perguntar à IA** copia o texto e abre a app de IA que você escolheu nos ajustes, com a pergunta
  já escrita. Não é uma integração com serviço nenhum: o texto é compartilhado como qualquer outra
  coisa, e quem responde é a app que já está no telefone.
- O nome do dicionário no cabeçalho da seção «Dicionário» abre a lista de dicionários ativos: dá para
  consultar só um deles (ou **Todos**) sem sair do painel. A escolha também fica guardada.
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

| Ajuste «Manter o furigana entre parênteses» | Resultado |
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
- Liga e desliga em **«Mostrar a leitura dos kanji»** (ligado de fábrica). O texto reconhecido é
  selecionável e copiável, como sempre.

#### A leitura sobre a página, antes de escolher o trecho

O painel escreve a leitura no texto; a **tela de trechos** — aquela em que as caixas aparecem sobre a
página, antes de você tocar em nada — não tinha leitura nenhuma. Agora tem, e você escolhe a forma em
**«Leitura sobre a página, na tela de trechos»**:

| Ajuste | O que aparece |
| --- | --- |
| **Rubi** | a leitura desenhada ao lado de cada kanji: uma coluna de kana à direita de uma coluna vertical, uma linha pequena acima de uma linha horizontal |
| **Tarja** | uma etiqueta ao lado de cada balão que tenha leitura, com a frase e as leituras entre parênteses, `職員（しょくいん）さんに…` |
| **Não mostrar** (padrão) | só as caixas, como antes |

O rubi é fiel ao que o mangá faz, mas numa página inteira a letra fica do tamanho do rubi impresso:
para ler de perto, use a tarja, o painel ou o zoom do sistema. As duas formas ficam disponíveis para
você decidir qual prefere. As leituras vêm do dicionário e aparecem só para o que ele conhece — um
kanji sem palavra e sem entrada de kanji fica sem leitura, como no painel. O rubi custa uma consulta
por coluna, feita depois do OCR e em segundo plano; se o dicionário não abrir, as caixas aparecem do
mesmo jeito, só sem leitura.

### Ver a imagem capturada

Quando o OCR troca um kanji, quem tira a dúvida é a imagem: o painel diz **淀** e a página diz qual
era. Por isso o painel pode mostrar, **ao lado do texto reconhecido**, o pedaço de tela de onde
aquele texto saiu:

| O que você toca | O que acontece |
| --- | --- |
| **Imagem** (na linha de ações do painel) | O recorte aparece à esquerda do texto reconhecido e o botão fica destacado. Toque de novo para esconder. |

- A escolha é **lembrada** e vale para as próximas capturas; a mesma caixa existe nos ajustes
  («Mostrar a imagem capturada ao lado do texto»). Vem **desligada** de fábrica: o que perde largura
  com a imagem é o texto reconhecido — justamente o campo que você edita.
- Numa **captura de área** a imagem é o recorte inteiro. Quando o texto vem de um trecho escolhido no
  «tela inteira», a imagem é **aquele trecho**, não a tela toda: uma página em miniatura não ajudaria
  a conferir kanji nenhum.
- O tamanho quem manda é a própria imagem (nunca ampliada, no máximo 120×200 dp), e o texto fica com
  o que sobra; uma coluna estreita de mangá quase não tira largura do texto. Se o painel estiver
  dobrado, a imagem some junto com o resto.

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
- O bloco **Kanji** segue a mesma escolha, para uma seção não contradizer a outra.

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

### Dicionários

Nenhum dicionário vem dentro do APK (o menor útil tem 300 KB e o maior 37 MB, e cada um tem sua
licença). Você baixa ou importa em **Gerenciar dicionários**:

| Caminho | Como funciona |
| --- | --- |
| Lista do app | Baixa o `.zip` oficial mais recente, converte uma vez e instala. Tem barra de progresso. |
| **Importar arquivo** | Escolhe qualquer `.zip` no formato **Yomitan/Yomichan** pelo seletor do sistema e converte igual. |

O que está na lista:

| Dicionário | Idiomas | Tamanho |
| --- | --- | --- |
| JMdict | inglês | 15 MB |
| Jitendex | inglês | 37 MB |
| JMnedict (nomes próprios) | inglês | 11 MB |
| Wikcionário japonês → português | português | 0,4 MB |
| Wikcionário japonês (monolíngue) | japonês | 12 MB |
| Wikcionário japonês → inglês | inglês | 21 MB |
| KANJIDIC | inglês | 0,7 MB |
| KANJIDIC | português | 0,3 MB |

Vários podem ficar ativos ao mesmo tempo. Quando duas palavras batem, a ordem da lista decide: o de
cima aparece primeiro, e as demais opções vêm empilhadas abaixo — como no Yomichan. Dá para ativar,
desativar, reordenar e remover. Para consultar só um deles **sem mexer nesta lista**, use o seletor do
cabeçalho do painel («Escolher o dicionário sem sair do painel»).

Duas ressalvas honestas:

- **Não existe JMdict em português.** As definições em português vêm do Wikcionário lusófono (8 mil
  verbetes) e do KANJIDIC em português (só os ~2 mil kanji usuais). Um kanji raro como **淀** só tem
  significado em inglês — lá está (`pool (in river)`, 音 テン/デン, 訓 よど.む). Para o sentido em
  português, conte com a **tradução** da frase.
- **Cada dicionário ativo custa memória.** Só o índice de chaves fica na RAM (o resto é lido do
  arquivo sob demanda): cerca de 17 MB para o JMdict, 18 MB para o Jitendex, 21 MB para o JMnedict,
  29 MB para o Wikcionário inglês. Todos juntos passam de 80 MB. Se o celular tiver pouca memória,
  deixe ativos só os que você usa.

### Tradução offline

A primeira tradução baixa o modelo do idioma (cerca de **30 MB**), via Google Play Services; depois
disso funciona em modo avião. Por isso o primeiro uso precisa de internet e de Play Services. Se o
modelo não puder ser baixado, o painel diz exatamente isso em vez de ficar mudo.

### Perguntar a uma IA que você já tem

A tradução do ML Kit resolve a frase; o que ela não resolve é «por que esta palavra está escrita
assim», «e num mangá, que tom tem isso», «esta gíria existe mesmo». Para isso existe o botão
**Perguntar à IA**, ao lado das cópias:

| O que ele faz | Detalhe |
| --- | --- |
| Copia o texto reconhecido | Para a área de transferência, com as suas correções — o mesmo texto do painel. |
| Monta a pergunta | O que estiver em «O que perguntar» nos ajustes, seguido do texto. De fábrica: *«Traduza para português e explique:»*. |
| Abre a app de IA | A que você escolheu nos ajustes, com a pergunta já escrita. Sem escolha, ou se a app escolhida já não existe, quem aparece é o **seletor do Android**. |

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
apagado, ele manda só o texto — e a app que responde, escolhida de uma lista lida do aparelho. O nome
da app é lido do telefone, não guardado: se você desinstalar a que tinha escolhido, o ajuste volta
sozinho a «Perguntar sempre», em vez de apontar para uma app que não está lá.

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
de `肉塊に潰されてる`), mesmo com todas as letras certas.

Além disso, a imagem vai com o lado maior em **1600 px** (`AppSettings.DEFAULT_MAX_DIMENSION`) e o
idioma anunciado é `ja` — uma tela de 1080×2400 viaja, portanto, como 720×1600.

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
  dict/LookupEngine.kt        segmentação por maior correspondência + quebra de kanji
  dict/Furigana.kt           a leitura da palavra escrita ao lado dos kanji que ela lê
  dict/Deinflector.kt         conjugações → forma de dicionário (tabela do Yomichan)
  dict/GlossaryRenderer.kt    structured-content dos dicionários modernos → texto
  resources/deinflect.json    matriz de regras (Yomichan, MIT)

app/    o app Android
  MainActivity.kt           permissões + ajustes + painel de uso
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
  dict/DictionaryRepository.kt instalação no disco + um LookupEngine em cache
  dict/DictionaryDownloader.kt download com progresso (e o descritor do Wikcionário)
  translate/OfflineTranslator.kt  ML Kit tradutor offline
  overlay/…                 bolha, menu, seletor de área, caixas para escolher o trecho e painel de
                            resultado (Views puras)
  overlay/FlowLayout.kt     linha de botões que quebra em vez de espremer (painel de resultado)
  overlay/ResultOverlay.kt  o painel; o teto do scroller é medido, não um número fixo
  service/FloatingOcrService.kt     o serviço que costura tudo

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
KANJIDIC, Wikcionário), mede quanta memória cada índice ocupa e confere que uma busca responde. Ela é
opcional e aponta para uma pasta com os `.zip` baixados:

```powershell
gradle :core:test -Pdicts="C:\caminho\para\os\dicionarios"
```

Os nomes de arquivo esperados são os mesmos da lista do app (`JMdict_english.zip`,
`jitendex-yomitan.zip`, `JMnedict.zip`, `KANJIDIC_english.zip`, `KANJIDIC_portuguese.zip`,
`kty-ja-pt.zip`, `kty-ja-ja.zip`, `kty-ja-en.zip`); o que faltar é simplesmente ignorado.

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
