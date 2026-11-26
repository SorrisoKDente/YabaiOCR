---
name: yabai-ocr-quality
description: Mexer no reconhecimento e no texto japonês do Yabai OCR — Lens/protobuf, blocks e caixas, ordem de leitura, furigana, normalização CJK e dicionários. Use ao mudar OCR, parsing, leituras, segmentação, lookup ou qualquer coisa que possa alterar o texto reconhecido.
---

# Qualidade de OCR e dicionários

## Regra de ouro

O texto reconhecido é o dado. Não "conserte" kanji, não complete conjugações, não remova espaços
por estética e não troque uma leitura incerta por uma plausível. Se mudar o que o usuário vê,
prove com teste e evidência.

Normalização existente (`JapaneseText.normalize`) remove espaços **somente entre dois CJK**, para
desfazer o espaçamento que reconhecedores inserem entre tokens. Ela é intencional e testada; não a
substitua por `replace(" ", "")`, que destruiria espaços entre palavras latinas.

## Módulo e fluxo

Tudo de OCR compartilhado vive em `:core`, Kotlin/JVM puro — **nada de Android**. O Android usa
`:core` via `LensOcrEngine`/`OcrDispatcher`; o desktop usa `LensClient` direto.

```
imagem (PNG + width/height reais)
  → LensProto.buildRequest            protobuf escrito à mão, sem protoc
  → LensClient.recognize/Suspend      OkHttp, cookie jar, retries
  → LensProto.parseResponse           geometria normalizada (0..1)
  → LensResultBuilder.build           pixels, segments, blocks
  → ReadingOrder / RubyGeometry       ordem de leitura e furigana
  → LookupEngine / Furigana           dicionário e leituras
```

Arquivos-chave:

| arquivo | responsabilidade |
| --- | --- |
| `core/.../lens/LensProto.kt` | esquema do endpoint, request e parsing |
| `core/.../lens/LensClient.kt` | HTTP, retries, `recognize`/`recognizeSuspend` |
| `core/.../lens/LensResultBuilder.kt` | words → lines → blocks, caixas em pixels |
| `core/.../lens/RubyGeometry.kt` | qual linha é leitura de qual |
| `core/.../lens/FuriganaMode.kt` | `REMOVE` ou `PARENTHESES` |
| `core/.../layout/ReadingOrder.kt` | vertical/horizontal, ordem humana |
| `core/.../jp/JapaneseText.kt` | normalização CJK-aware |
| `core/.../dict/LookupEngine.kt` | maior correspondência, exata antes de conjugada |
| `core/.../dict/Furigana.kt` | leitura da palavra ao lado dos kanji |
| `core/.../dict/Deinflector.kt` | conjugação → forma de dicionário |
| `core/.../dict/CompactDictionary.kt` | índice na RAM, verbete no disco |

## Pontos que já causaram bug

- **Coordenadas normalizadas.** O Lens devolve geometria 0..1; `toPixels(imageWidth, imageHeight)`
  usa as dimensões da imagem **submetida**. Se `ImagePreparer` reescalou, a caixa é relativa à nova
  imagem — nunca à tela nem à imagem original.
- **`text_separator`.** Quando o campo existe (mesmo vazio, caso do japonês), o separador é anexado
  literalmente; só quando está ausente entra um espaço. Não "conserte" isso com um espaço fixo.
- **Furigana impressa.** A leitura chega como uma linha separada (`しょくいん` ao lado de `職員`).
  `RubyGeometry` decide por geometria: mais fina (≤ 0,6), encostada (≤ 0,5 da espessura), cobrindo
  o kanji e do lado do rubi. As regras são conservadoras de propósito: na dúvida, a linha **fica**
  no texto como veio. Não afrouxe um limite sem medir um caso real e sem teste.
- **Furigana de dicionário.** `Furigana.split` só anota quando a leitura alinha com os kanji; se não
  alinhar, deixa a palavra intacta. Kanji isolado pode gerar até 3 leituras e é marcado como
  incerto. Nunca apresente uma leitura adivinhada como certa.
- **Ordem de leitura.** Vertical lê colunas da direita para a esquerda; horizontal, linhas de cima
  para baixo. Um item sem caixa mantém a ordem original em vez de meia-ordenação.
- **Lookup.** `lookup` tenta a superfície exata antes da deinflectida, para `食べて` não virar
  `食べる` no meio de outra palavra. O `Deinflector` usa bitmask de classe gramatical; aceitar
  candidato sem a máscara correta produz verbete errado.
- **Consulta digitada.** `DictionaryQuery.stripReadings` remove só parênteses cujo conteúdo inteiro
  é kana (`職員（しょくいん）`). Não generalize para qualquer parêntese.
- **Dicionário em cache.** No desktop, `DesktopDictLoader.cachedEngine` reusa o `LookupEngine`
  enquanto pasta, filtro, ordem e nome/tamanho/data dos `.yabai` não mudam; ao mudar, fecha o antigo.
  Não segure arquivos abertos depois de trocar a pasta.

## Testes

```powershell
.\gradlew.bat :core:test                                   # padrão, sem rede
.\gradlew.bat :core:test -Plive=1                           # Lens de verdade (rede, pode falhar por cota)
.\gradlew.bat :core:test -Pdicts="E:\Documentos\DicionariosJapones"  # ZIPs reais do Yomitan
```

- `LensProtoParsingTest`, `FuriganaTest` (dict e lens), `ReadingOrderTest`, `DictionaryQueryTest`,
  `JapaneseTextTest` e `DictionaryCompileTest` são a rede de segurança. Rode `:core:test` depois de
  qualquer mudança no core.
- O teste `live` depende do endpoint público do Lens (sem chave, sem conta) e pode ser limitado por
  cota. Falha de rede não é bug de parsing: separe as duas coisas no relatório.
- Os testes de dicionário real só rodam com `-Pdicts`; deixe o teste padrão hermético. `maxHeapSize`
  é 2 g porque compilar dicionário grande segura uma chave por leitura.
- Teste de OCR afirma o **texto exato** e as caixas relevantes. Se o esperado precisa ser arredondado
  ou "corrigido" para passar, o teste está errado — ou o código está.

## Checklist de mudança

- [ ] A causa está no parsing/geometria/lookup, não no sintoma visual?
- [ ] O texto reconhecido continua idêntico, ou a mudança é intencional e documentada?
- [ ] Leituras incertas continuam marcadas (não promovidas a certas)?
- [ ] Caixas continuam relativas à imagem submetida (teste com dimensões diferentes de 1:1)?
- [ ] `:core:test` verde; `-Plive=1`/`-Pdicts` quando o caso exigir, com o resultado relatado?
- [ ] Teste de regressão novo + mutação provada (`yabai-regression`)?
- [ ] README/ROADMAP atualizados se o comportamento visível mudou?

## O que não fazer

- Não "corrija" o texto com lista de substituições de kanji ou de conjugação.
- Não descarte uma linha só porque parece lixo; primeiro prove que é leitura e que o modo pede
  removê-la.
- Não use `System.currentTimeMillis`/rede/locale do sistema em teste unitário.
- Não mexa em `:core` importando Android, `android.graphics` ou `Context`.
