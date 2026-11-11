# LANGUAGES.md — Tradução da interface (localização da UI)

Esse documento é o plano de localização da interface do Yabai OCR, não de OCR/dicionário/leitura de
sinais japoneses. O app já nasce pensado para japonês e, por enquanto, essa não é uma tarefa de
“acessibilidade a mais idiomas” no sentido de OCR multilingue: a interface é só o que vamos mudar.

## O que esse documento vai e não vai

- Vai: explicar o que é preciso fazer para traduzir a interface para outros idiomas (começando por
  inglês, que é o primeiro candidato real), com o escopo detectado no código e com cruzamento com o
  que já foi feito no port Linux (`LINUX.md`).
- Não vai (aqui): detalhar como adicionar OCR, leitura ou dicionário de outro idioma via OCR/Lens/MlKit
  ou dicionários do Yomitan. Isso é “futuro, se fizer sentido”. Se chegar a ser feito, ele tem seu
  próprio rascunho/hoje disperso; não estou documentando linha a linha de OCR/dicionário neste arquivo.

## Dois “idiomas”: não confundir

Antes de qualquer coisa, separar dois eixos diferentes:

1. **Idioma do texto reconhecido (OCR + lookup + dicionário).**
   Por padrão o app lê japonês. Até o momento ela depende de:
   - OCR/Lens configurado para `ja`;
   - lookup/dicionários feitos para japonês (jmdict, jitendex, JMnedict, surasura, wikcionário-ja-*,
     etc.);
   - normalizações e fuzzy/detalhes de blocos que presumem leitura de japonês.

   Isso é o núcleo de produto. Traduzir a interface não muda isso. O app continua lendo japonês e
   consultando dicionários de japonês.

2. **Idioma da interface (UI).**
   O que o usuário vê: títulos de janelas, labels, botões, mensagens, placeholders, erros, diálogos.
   Essa é a peça que se traduz.

Atenção: não confundir “traduzir a interface para inglês” com “ler inglês via OCR/dicionário”. São
coisas diferentes e eu não misturo. Se no futuro quiser que o app reconheça ou consulte outro idioma,
isso é outro trabalho, com seu próprio escopo (OCR/Lens para outro idioma, outro dicionário e provavelmente
outras fontes), e não se resume a trocar strings.

## Por que começar por inglês

- Inglês é o idioma natural de referência para um app voltado a estudantes de japonês.
- Onde a interface tem vocabulário bom em português, a versão inglesa costuma ser a mais útil como
  ponto inicial se o objetivo for ampliar quem usa.
- Se o objetivo for só “traduzir a interface”, inglês é um caso concreto e mensurável.

Não está decidido que inglês seja o único idioma futuro. Se quiser começar por outro, o plano é o
mesmo; só troca o idioma alvo. Neste momento, inglês é o primeiro exemplo concreto.

## O que vai ser alterado para uma interface em outro idioma

A UI está espalhada em dois mundos distintos: **desktop (Kotlin/JVM + Compose)** e **Android
(Kotlin + strings.xml)**. Cada um precisa de seu próprio trabalho, e eles não se importam automaticamente
um ao outro.

### 1) Inventário e agrupamento da interface

Antes de traduzir, a primeira passo prático é saber o que existe e onde está. Na raiz do projeto eu
detectei dois grandes grupos:

- **Mobile**: strings em XML de recursos (`app/src/main/res/values/strings.xml` e, se forem criados,
  namespaces `values-<lang>/strings.xml`).
- **Desktop**: texto do sistema e da UI em Kotlin dentro de `desktop/src/main/kotlin/dev/yabai/ocr/desktop/`.

Eu não confiei em números exatos depois dos problemas de sintaxe/timeout no terminal, mas a leitura
feita por arquivo deu um inventário razoável para descrever escopo. O documento vai se basear nele.

Cuidado: “quantos caracteres” ou “quantas strings” não é a mesma coisa que “o que traduzir”. Muitos
trechos são técnicos, ids, URLs, pequenos separadores, ids de atalho, placeholders de formatação, textos
de log, textos de recursos, ou textos que servem de chave que não devem virar string traduzida literalmente.
O inventário deve distinguir essas categorias.

### 2) Desktop: identificar o que é UI e o que é não-UI

No desktop, o texto está muito misturado. Parte é interface, parte é configuração/internals, parte é
recursos tecnicos. Para traduzir com segurança, preciso separar, em ordem, o que importa:

- Textos que aparecem para o usuário (labels, botões, títulos, mensagens, tooltips).
- Textos que aparecem em diálogos/janelas, inclusive os que são relativamente estáticos (ex.: título da
  janela, labels do seletor de tela, labels dos dicionários, labels do catálogo).
- Mensagens de erro/pending para o usuário.
- Placeholders de formatação e expressões interpoladas, que não se traduzem em si, mas que determinam
  a forma como a string será montada.
- Textos que são ids ou constantes técnicas (`"en"`, `"pt"`, `"ja"`, nomes de extensão, nomes de
  variáveis de ambiente, nomes de arquivos, caminhos, nomes de provedor) e que, em geral, não devem
  ser traduzidos.
- Texto que pode ser só log/debug e não precisa de localização imediatamente.

Um trabalho de interface aqui não é só “traduzir tudo que tem aspas”. É preciso reestruturar as
strings reais em um esquema de localização, ou pelo menos em constantes/arquivo de recurso, separando
a exibição da lógica.

### 3) Mobile: já segue o padrão Android de recursos

O mobile já usa um arquivo de recursos (`strings.xml`), o que é a base correta para localização. A
mudança para outro idioma, quando feita, segue o caminho idiomático do Android:

- manter o idioma base;
- criar recursos por idioma (`values-en`, `values-pt`, etc.) conforme necessário.

O ponto de atenção aqui é:

- strings não extraídas para o arquivo de recursos (literal no Kotlin) precisam ser tratadas;
- mensagens que dependem de interpolação podem precisar de novas keys ou de posições/ordens diferentes
  em outro idioma;
- qualquer texto misturado no layout/tema/overlay, se houver, precisa ser checado.

O XML de origem já é chamado de `strings.xml` e está em português (BR). Enfim, a estrutura de localização
móvel é mais direta que a do desktop, mas ainda exige revisão do que está inline e do que já está
centralizado.

### 4) Temas, cores, ícones e textos constitutivos

Alguns textos não são “labels” simples: vêm de temas, de regras visuais, ou de dados que são parte
do catálogo/dicionário. Ex.: nomes dos dicionários no catálogo, descrições, atribuições, tags de idioma
dos dicionários, labels de tamanho, labels de status de atualização.

Esses textos podem ser UI, mas podem ter conteúdo ligado a dados (catálogo de dicionários) e não
apenas ao display. Traduzir interface é, em parte, traduzir o que vem “parado” no app, mas precisa
definir onde fica a responsabilidade: se um nome de dicionário forma parte do catálogo ou se é só
display interno.

### 5) Atalhos, chaves de configuração e textos de log

Atalhos e chaves de configuração aparecem na UI, mas também vivem em `settings.properties` e em código
de configuração. Aqui a distinção importante:

- O que o usuário vê sobre o atalho pode ser traduzido.
- O código interno que manipula/fecha o atalho não deve ser confundido com texto de interface.
- Logs e mensagens de console podem ou não precisar de localização; dependendo do objetivo, um log em
  inglês pode ser mais útil. Não estou generalizando: entram no inventário e se decidem.

## O plano passo a passo para inglês

A ordem prática, pelo menos para o primeiro idioma, é:

1. **Escolher escopo real.** Decidir o que vai ser traduzido e o que não vai. Por padrão sugiro
   começar pela interface principal (menus, janelas de ajuste/busca/hover/resultado/gerenciador de
   dicionários, mensagens de erro e sucesso, labels dos dicionários). Depois eventualmente cobrir mais.

2. **Far o inventário por plataforma.** Desktop e mobile separados, com lista de arquivos de origem. O
   importante é saber quais textos são candidatos reais e quais não são.

3. **Separar UI de informação técnica.** Identificar ids, URLs, nomes de extensão, nomes de arquivos,
   nomes de provedor, keys de configuração etc. e não tratá-los como strings comuns.

4. **Escolher o mecanismo de localização para cada plataforma.**
   - Mobile: Android resource strings por idioma (já existe estrutura base).
   - Desktop: aqui o caminho mais seguro é trazer as strings de UI para um lugar centralizado ou para
     recursos estruturados por idioma, e não deixar todo texto espalhado nos literais. Isso é mais
     chato, mas faz a tradução não virar um espaguete.

5. **Preparar o idioma alvo.**
   - Para inglês: traduzir com atenção a termos já em português, para não trocar o que já é bom sem
     necessidade e sem perder clareza.
   - Para qualquer outro: mesmo processo, só outro idioma.

6. **Preservar exatidão e não corrigir o que não é UI.** Tags, leituras, nomes de dicionários etc. não
   são texto solto; se forem traduzidos no display, que seja com critério explicado. Se não fizerem
   sentido, não traduzir.

7. **Teste/visualizar.** Aqui a janela de ajustes e as janelas de OCR/resultado/dicionário/teste de
   atalhos são os lugares reais de conferência.

8. **Documentar o que ficou de fora** e por quê, para que a próxima iteração (outros idiomas, mais
   strings, catálogo, etc.) saiba o que está pendente.

## Riscos e armadilhas documentadas

- Traduzir textos que são ids/chaves/urls/constantes de código é um risco real de quebra.
- Misturar OCR/dicionário com tradução da interface gera confusão de escopo. Separar deixou tudo mais
  claro.
- Traduzir como “trocar string” pode quebrar interpolação, pluralização, orientação de texto.

Não sendo a interface, seguir os riscos que já conheço do app (em especial: nunca “corrigir” texto
reconhecido sem ser o comportamento pedido e testado; UI em pt-BR quando for display; comentários do
código em inglês).

## LINUX.md: impacto sobre o port Linux

`LINUX.md` documenta um port Linux feito pelo projeto. Posso cruzar com ele e dizer se ele precisa de
ajuste relacionado a interface. No momento, LINUX.md parece estar separado do que é “traduzir a interface”;
no entanto, quando for traduzir o desktop, vale revisar:

- labels/mentions de Linux que aparecem na interface desktop (se houver);
- textos técnicos de instalação/entrada/sessão que estejam relacionados;
- se o Linux então tiver suas próprias strings de UI (ou parte delas) fora do estado atual.

Se algo relevante aparecer em LINUX.md sobre a interface em si, eu trago aqui como nota ou atualizo este
documento para deixar isso explícito na seção de impacto.

## Futuro, se for útil

- Outros idiomas de interface (além de inglês), se fizer sentido.
- OCR/dicionário para outro idioma de origem: não objeto deste documento; se for feito, será outro plano
  e não o de localização da interface.

## Limitações deste documento

- É um plano, não implementação. Não vou codificar a tradução aqui.
- Inventário e contagens baseados em leitura de arquivos, não em teste de UI executável.
- Números exatos de strings podem variar se outro arquivos forem encontrados ou se houver limpeza/organização;
  para fins do plano, o importante é separar UI de não-UI e separar desktop de mobile.

Se quiser que eu avance para a etapa concreta (inventário lista por arquivo, seleção de strings, ou
começar a preparar o idioma alvo), me avise. Se quiser focar primeiro em um dos dois: interface de
desktop, ou interface mobile, posso particionar.
