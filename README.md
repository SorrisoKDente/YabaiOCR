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
| `app/build/outputs/apk/release/app-release.apk` | **Use este.** Assinado com a chave de debug, instala direto. |
| `app/build/outputs/apk/debug/app-debug.apk` | Versão de desenvolvimento (pacote `dev.yabai.ocr.debug`, pode conviver com a outra). |

---

## Como usar

1. Abra o app e conceda **Sobrepor outros apps** (a bolha precisa disso).
2. (Opcional) Conceda **Notificações** — o Android exige uma notificação enquanto a captura está ativa.
3. Toque em **Iniciar OCR flutuante** e aceite o aviso de captura de tela do sistema.
4. Abra o app onde está o texto (mangá, jogo, site…).
5. Toque na bolha e escolha:
   - **Tela inteira** — reconhece tudo que está na tela;
   - **Selecionar área** — arraste para marcar a região e toque em **Reconhecer**.
6. O texto aparece no painel. Se **Copiar automaticamente** estiver ligado, já foi para a área de
   transferência; senão toque em **Copiar**.

Detalhes úteis:

- A bolha pode ser **arrastada** para qualquer canto e "gruda" na borda mais próxima.
- Toque na bolha **enquanto o painel de resultado está aberto** para abrir o menu, ou toque fora do
  painel para fechá-lo.
- No menu da bolha, **Ajustes** reabre a tela de configuração e **Encerrar** desliga o serviço.
- Toque longo no texto seleciona, como em qualquer TextView.

---

## Precisão

Três ajustes controlam o resultado:

| Ajuste | Padrão | O que faz |
| --- | --- | --- |
| Motor | **Automático** | Usa Lens e cai para o ML Kit se o Lens falhar ou não devolver texto. |
| Ampliar recortes pequenos | ligado | Recortes com menos de 420 px no lado maior são ampliados até 2×. Ajuda bastante em furigana e letras miúdas. |
| Listar o texto linha por linha | desligado | Mostra o resultado exatamente como o motor o segmentou, sem juntar em um parágrafo. |

Além disso, a imagem é enviada com o lado maior em **1600 px** (`AppSettings.DEFAULT_MAX_DIMENSION`)
e o idioma anunciado é `ja`. Diminuir para ~1200 deixa o upload mais leve; aumentar ajuda em telas
grandes com texto fino.

Dicas para acertar de primeira:

- **Recorte justo > tela inteira.** Marque só o balão/bloco de texto: menos ruído, resultado melhor.
- Em mangá vertical, selecione a coluna inteira (ou o balão todo), não uma linha solta.
- Se o resultado vier vazio, **Tentar offline** aparece no painel e roda o ML Kit na mesma área.

---

## Uso do Google Lens (e a verdade sobre a cota)

A tela do app tem uma seção **“5. Uso do Google Lens”** com:

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
  jp/JapaneseText.kt        normalização de espaço CJK-aware

app/    o app Android
  MainActivity.kt           permissões + ajustes + painel de uso
  AppSettings.kt            preferências compartilhadas
  UsageTracker.kt           contador local de requisições ao Lens (o Google não expõe cota)
  capture/ScreenCaptureSession.kt   um VirtualDisplay de longa duração + ImageReader
  ocr/OcrDispatcher.kt      escolhe o motor, implementa o modo automático e contabiliza o uso
  ocr/LensOcrEngine.kt      Lens via core
  ocr/MlKitOcrEngine.kt     ML Kit japonês offline
  ocr/ImagePreparer.kt      reescala e codifica em PNG
  overlay/…                 bolha, menu, seletor de área e painel de resultado (Views puras)
  service/FloatingOcrService.kt     o serviço que costura tudo
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

Existe um teste que fala com o endpoint de verdade, desligado por padrão:

```powershell
gradle :core:test -Plive=1
```

Para reduzir o tamanho do APK, `app/build.gradle.kts` limita as ABIs a `arm64-v8a` e `armeabi-v7a`
(x86 só serve para emulador). Remova o bloco `ndk { … }` se precisar rodar em emulador x86.

---

## Aviso

O `core` fala com `https://lensfrontend-pa.googleapis.com/v1/crupload` usando a mesma chave de API
que o Chrome carrega internamente — o mesmo caminho que a biblioteca `chrome-lens-ocr` usa. **Isso é
uma API não oficial do Google**: não há contrato, ela pode mudar ou ser bloqueada sem aviso, e uso
intenso pode esbarrar em limite de requisições. Para uso casual e pessoal funciona bem; para algo
distribuído em escala, troque por um serviço oficial (Cloud Vision) ou fique só no ML Kit offline.
