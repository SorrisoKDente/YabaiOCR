---
name: yabai-android-app
description: Mexer no app Android do Yabai OCR — overlays, FloatingOcrService, MediaProjection, captura de tela, dicionários, tradução offline e testes com Robolectric sem aparelho. Use ao alterar o módulo :app, permissões, serviço em primeiro plano, painéis ou telas.
---

# App Android (`:app`)

## Configuração real

| item | valor |
| --- | --- |
| `namespace` / `applicationId` | `dev.yabai.ocr` |
| `minSdk` | 26 |
| `compileSdk` / `targetSdk` | 36 |
| Java/Kotlin | 17 |
| ABI | somente `arm64-v8a` (x86 só em emulador; `armeabi-v7a` só para aparelho 32-bit) |
| debug | sufixo `.debug` (`applicationIdSuffix`) |
| release | assinado com a keystore de debug; `minify` desligado |

Bibliotecas centrais: ML Kit Text Recognition japonês (modelo embutido, offline), ML Kit Translate,
WorkManager para atualização de dicionários, OkHttp para Lens, `:core` para OCR/dicionários.

## Comandos

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:installDebug          # com aparelho/emulador conectado
```

`lint` não bloqueia (`abortOnError = false`) — não use "passou no lint" como prova.

## Robolectric (sem aparelho)

Os painéis são construídos em código, sem XML revisado pelo compilador. Os testes medem as views de
verdade:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class MeuPainelTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    // measure + layout + assert de altura/linhas/cliques
}
```

- Ficam em `app/src/test/java/dev/yabai/ocr/...`; `unitTests.isIncludeAndroidResources = true`
  resolve strings/cores/temas reais.
- O padrão do projeto é `@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")`; mantenha a mesma
  densidade/width nos testes de layout para as comparações continuarem válidas.
- Robolectric mede texto como ~1 px por caractere; **não** afirme largura de glifo. Compare um botão
  com ele mesmo, uma linha com o espaço disponível, o painel com a tela.
- Teste o comportamento que já quebrou: altura do painel vs. tela, botão que não corta o rótulo,
  duas linhas que quebram, VOLTAR que não fecha cedo, foco que não vaza.

## Captura de tela (MediaProjection)

Regras que o Android 14+ impõe e que já causaram bug:

- **Um `VirtualDisplay` por `MediaProjection`.** O segundo é recusado. Rotação se resolve com
  `resize()` + `setSurface()`, não recriando a captura.
- **`startForeground` antes de `getMediaProjection`**, com o tipo `mediaProjection`. O Android 14
  exige o serviço em primeiro plano antes da permissão.
- **Captura orientada a evento.** Uma tela espelhada só entrega frame quando o conteúdo muda; um
  print de tela estática esperaria para sempre. `armCapture()` é chamado **antes** de esconder os
  overlays: o frame gerado pelo desaparecimento das janelas é o frame limpo desejado.
- A notificação é obrigatória enquanto a captura está ativa; não a remova para "limpar" a UI.
- `ImagePreparer` reescala/codifica; as dimensões passadas ao `EncodedImage` têm de ser as da imagem
  **depois** da preparação. Caixa de OCR é relativa a essa imagem (ver `yabai-ocr-quality`).

## Overlays e serviço

- **Janelas são destruídas, não escondidas.** O painel de resultado rouba foco de propósito (seleção
  de texto e VOLTAR). Uma janela focusable invisível engoliria toques do app de baixo.
- **O painel não pode passar da tela.** O teto do scroller é medido; não volte a reservar uma
  constante em dp para as partes fixas.
- **Botões quebram linha.** Use `FlowLayout`; `LinearLayout` nunca quebra e espreme.
- **VOLTAR:** o primeiro VOLTAR sai de um estado interno (ex.: edição/seleção); o segundo fecha o
  painel. Não jogue a captura fora no primeiro.
- **Dobra do painel** sobrevive entre capturas; uma captura nova abre desdobrada (ver ROADMAP).
- **Nada de janela órfã:** ao parar o serviço, remova overlay, bolha e notificação; o teste
  `PauseTest`/`BackAndOutsideTest` cobre parte disso.
- Toques fora, arrastar para fechar e perda de foco têm testes próprios; mexa neles quando mudar a
  hierarquia de views.

## OCR, dicionários e tradução

- `OcrDispatcher` escolhe o motor (`AUTO`, Lens ou ML Kit) e contabiliza o uso do Lens; o Lens é o
  endpoint público não oficial (sem chave/conta) e pode ser limitado. ML Kit é offline.
- `UsageTracker` é local; o Google não expõe cota. Não prometa cota no README.
- Dicionários `.yabai` compilados ficam em disco; `DictionaryRepository` mantém um `LookupEngine` em
  cache. Ao trocar pasta/ordem/conjunto, feche o antigo.
- `AskAi` não usa API nem chave: monta a pergunta e compartilha com um app de IA instalado. A lista
  filtra o que não é IA (R11).
- Precisão de OCR vale aqui também: não "conserte" o texto do ML Kit/Lens. Divergência entre motores
  é dado, não bug a esconder.

## Armadilhas

- Não importe Android no `:core`; o core roda em JVM pura nos testes e no desktop.
- Não recrie `MediaProjection`/`VirtualDisplay` na rotação.
- Não chame `getMediaProjection` antes de `startForeground`.
- Não deixe overlay focusable invisível.
- Não use dimensões fixas de tela; use `dp`/medição real.
- Não adicione permissão sem checar `PermissionRemovalTest` e o README (o app evita permissões que
  não usa).
- Mudança de layout sem teste de medição é aposta; adicione um caso com tela estreita
  (`w320dp-h640dp`) e texto longo.

## Fechamento

- [ ] `:app:testDebugUnitTest` verde; total informado.
- [ ] Novo teste mede o caso real (tela estreita, texto longo, VOLTAR, foco).
- [ ] Mutação provada quando o teste protege uma causa raiz.
- [ ] `:core:test` se o `:core` foi tocado.
- [ ] README/ROADMAP atualizados se o comportamento visível mudou.
- [ ] Teste em aparelho quando a mudança depende de MediaProjection/overlay real; se não foi
  possível, diga explicitamente que a verificação foi só Robolectric.
