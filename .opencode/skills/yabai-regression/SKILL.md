---
name: Yabai Regression
description: Escrever e provar testes de regressão no Yabai OCR, incluindo a prova de mutação (mutar a causa, ver o teste falhar, restaurar e ver passar). Use ao corrigir bug, mudar comportamento de OCR/desktop/Android ou revisar se um teste realmente cobre a causa.
---

# Teste de regressão e prova de mutação

## Regra

Um teste de regressão só vale se **falhar sem a correção**. Teste que passa nos dois estados não
protege nada. Antes de dizer "pronto", prove por mutação.

## Onde o teste mora

| módulo | diretório | runner |
| --- | --- | --- |
| `:core` | `core/src/test/kotlin/dev/yabai/ocr/core/...` | JUnit 4, `:core:test` |
| `:desktop` | `desktop/src/test/kotlin/dev/yabai/ocr/desktop/...` | JUnit 4, `:desktop:test` |
| `:app` | `app/src/test/java/dev/yabai/ocr/...` | JUnit 4 + Robolectric, `:app:testDebugUnitTest` |

Prefira extrair a regra para uma função pura (geometria, casamento de atalho, parsing, seleção de
texto) e testá-la sem tela. Foi assim que `PickerBoxes`, `PickerReadings`, `HotkeyMatcher`,
`HoverGeometry` e `ScreenCaptureTest` ficaram testáveis.

## Anatomia de um bom teste

1. Nome em frase de comportamento: `` `a display scaled at 150 percent asks for the native device size` ``.
2. Um comportamento por teste; sem loop vazio, sem `assertTrue(true)`, sem "não lançou exceção".
3. Valores exatos quando o dado é exato: texto reconhecido, coordenadas, pixels, chave lida.
4. Entrada que representa o caso real (ex.: 1280×720 a 150% → 1920×1080; xadrez de 1 px).
5. Sem depender da máquina: nada de `~/.yabai_ocr/settings.properties` real, rede, hora, locale ou
   fonte instalada. Use arquivo temporário e valores injetados.

Exemplo de teste de causa raiz (HiDPI):

```kotlin
@Test
fun `a display scaled at 150 percent asks for the native device size`() {
    val size = nativeCaptureSize(Rectangle(0, 0, 1280, 720), 1.5, 1.5)
    assertEquals(1920, size.width)
    assertEquals(1080, size.height)
}
```

## Prova de mutação

Use o script (backup, mutação literal, teste filtrado, restauração em `finally`, hash conferido):

O caminho abaixo é relativo à raiz do repositório (o `SKILL.md` fica em
`.opencode/skills/yabai-regression/`):

```powershell
# mutação que deve derrubar o teste
.\.opencode\skills\yabai-regression\scripts\mutation-proof.ps1 `
  -File "desktop\src\main\kotlin\dev\yabai\ocr\desktop\ScreenCapture.kt" `
  -Find "(bounds.width * scaleX).roundToInt()," `
  -Replace "bounds.width," `
  -Test "dev.yabai.ocr.desktop.ScreenCaptureTest" `
  -Task ":desktop:test"

# o mesmo para o core
.\.opencode\skills\yabai-regression\scripts\mutation-proof.ps1 `
  -File "core\src\main\kotlin\dev\yabai\ocr\core\jp\JapaneseText.kt" `
  -Find "previous != null && next != null && isCjk(previous) && isCjk(next)" `
  -Replace "false" `
  -Test "dev.yabai.ocr.core.jp.JapaneseTextTest" `
  -Task ":core:test"
```

Saída esperada: `MUTAÇÃO PROVADA`, com pelo menos um `FAILED` do teste filtrado. Códigos:

| código | significado |
| --- | --- |
| `0` | mutação derrubou o teste e o arquivo voltou ao hash original |
| `1` | erro de preparação (arquivo/trecho não encontrado, ocorrência ambígua) |
| `2` | **mutação não detectada** — o teste não cobre a causa |
| `3` | restauração/verificação falhou (pare tudo e confira o arquivo) |

O script restaura o arquivo no `finally` mesmo se o Gradle falhar. Se você interromper o processo,
confira `git diff` antes de continuar.

### Mutação manual (quando o script não serve)

1. `git diff` limpo para o arquivo.
2. Edite a causa raiz de forma literal e mínima (não o teste).
3. Rode **só** o teste: `.\gradlew.bat :desktop:test --tests "dev.yabai.ocr.desktop.ScreenCaptureTest" --console=plain`.
4. Confirme `FAILED` e a mensagem certa (não erro de compilação).
5. Restaure com `git checkout -- <arquivo>` ou desfaça a edição.
6. Rode de novo e confirme verde.

## Cuidados

- **Compilação falhando não é mutação provada.** O erro tem de ser do assert.
- Se o Gradle disser `UP-TO-DATE` e o teste não rodar, use `--rerun-tasks` ou mude o arquivo de
  teste de verdade.
- Não deixe a mutação no código. Ao final, `git diff` deve mostrar só a correção intencional.
- Teste de OCR precisa afirmar o **texto exato** e a geometria relevante (caixa/linha). Arredondar
  ou "corrigir" o esperado para o teste passar é proibido.
- Para mudança visual, o teste de unidade não substitui o E2E: use `yabai-desktop-e2e`.

## Evidência para o relatório

Inclua uma tabela curta:

| mutação | teste que acusa | resultado |
| --- | --- | --- |
| escala ignorada na captura | `a display scaled at 150 percent…` | falhou e voltou a passar |

E o total da suíte depois da restauração. Sem isso, a correção não está comprovada.
