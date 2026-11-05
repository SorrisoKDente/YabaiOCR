# Yabai OCR — instruções do projeto

Leia antes de mexer no código. Vale para todo o repositório.

## O que é

Leitor de japonês com OCR. O módulo `:desktop` lê a tela do Windows (hover, captura de tela inteira,
recorte à mão, dicionários); o módulo `:app` é o app Android com OCR offline (ML Kit) ou Lens,
overlays e dicionários. O `:core` é Kotlin/JVM puro e compartilha OCR, protobuf, dicionários e
geometria entre os dois.

## Módulos

| módulo | papel | regra |
| --- | --- | --- |
| `:core` | OCR/Lens, protobuf, dicionários, furigana, layout | **nada de Android** aqui |
| `:desktop` | Compose Desktop, bandeja, atalhos globais, hover, seletor de tela | Windows é a plataforma alvo |
| `:app` | Android, `minSdk 26`, `compileSdk/targetSdk 36` | overlays em Views puras, testados com Robolectric |

## Mapa rápido

| área | comece por |
| --- | --- |
| captura/picker | `ScreenCapture.kt`, `SegmentPickerWindow.kt`, `RegionSelectionWindow.kt` |
| geometria do picker | `PickerBoxes.kt`, `PickerReadings.kt` |
| atalhos/ajustes | `GlobalHotkeyManager.kt`, `HotkeyMatcher.kt`, `DesktopSettings.kt`, `SettingsWindow.kt` |
| hover | `HoverOcrManager.kt`, `HoverGeometry.kt`, `HoverTextSelector.kt`, `HoverTooltipWindow.kt` |
| resultado/dicionários | `ResultWindow.kt`, `DesktopDictLoader.kt`, `core/.../dict/LookupEngine.kt` |
| Android | `service/FloatingOcrService.kt`, `capture/ScreenCaptureSession.kt`, `overlay/` |
| testes | `desktop/src/test/...`, `core/src/test/...`, `app/src/test/...` |

## Regra de ouro: precisão de OCR

O texto reconhecido é o dado. **Nunca** "corrija", normalize ou substitua um caractere reconhecido
sem que isso seja o comportamento pedido e testado.

- Não troque kanji por outro "parecido", não complete conjugações, não remova espaços CJK por
  estética. Normalização existe para um fim específico e testado (`JapaneseText.normalize` remove
  espaço só entre dois CJK), não para "corrigir" o que foi reconhecido.
- Incerto continua incerto: preserve a leitura duvidosa e sinalize (âmbar, status, motivo) em vez de
  adivinhar.
- Uma mudança de prompt, de pós-processamento ou de dicionário pode alterar o texto final. Trate
  como mudança de comportamento: teste de regressão + evidência.
- O que o usuário vê na UI é pt-BR. Comentários de código seguem o inglês já usado no repositório.

## Comandos

Windows/PowerShell, na raiz:

```powershell
.\gradlew.bat :desktop:test --console=plain       # suíte desktop (74 testes em 05/11/2026)
.\gradlew.bat :desktop:compileKotlin              # compilação rápida do desktop
.\gradlew.bat :desktop:run --args="--minimized"   # app na bandeja, sem abrir Ajustes
.\gradlew.bat :core:test                          # core (JVM puro)
.\gradlew.bat :app:testDebugUnitTest              # Android com Robolectric, sem aparelho
.\gradlew.bat :app:assembleDebug                  # APK de debug (sufixo .debug)
```

Opções do `:core`:

```powershell
.\gradlew.bat :core:test -Plive=1                                  # fala com o Lens de verdade (rede)
.\gradlew.bat :core:test -Pdicts="E:\Documentos\DicionariosJapones" # ZIPs reais do Yomitan
```

A suíte desktop fechou em **74 testes** e o `:core` padrão (sem `-Plive`/`-Pdicts`) em **143** na
última verificação. Se os números mudarem, confirme que os testes novos são intencionais e atualize
README/ROADMAP.

## Teste de regressão e prova de mutação

Toda correção de bug ou mudança de comportamento precisa de:

1. um teste de regressão **não vazio** que falha sem a correção;
2. prova de mutação: reintroduzir a causa raiz, ver o teste falhar, restaurar e ver passar;
3. a suíte inteira verde depois da restauração.

Use a skill `yabai-regression` e o script
`.opencode/skills/yabai-regression/scripts/mutation-proof.ps1`. Mutação que não derruba
nenhum teste significa que o teste não cobre a causa — reescreva o teste, não aceite o verde.

## Segurança de ambiente e configurações

- `~/.yabai_ocr/settings.properties` é do usuário. Faça backup antes de qualquer E2E, restaure o
  arquivo exato no fim e confira as chaves de atalho (`global_hotkey`, `region_hotkey`,
  `hover_hotkey`, `quick_dict_hotkey`) e `picker_reading`.
- Rode o desktop com `--minimized` em provas automatizadas. Sem isso, um clique da sonda pode cair
  na janela de Ajustes e **regravar os atalhos** ao capturar uma tecla.
- Mate só o processo `dev.yabai.ocr.desktop.MainKt`; o Gradle sai com código `-1` e isso é esperado.
- Não deixe app, sonda ou processo Java rodando ao terminar.
- Em PowerShell, prefira o caminho explícito
  `C:\Users\Eric PC\AppData\Local\Temp\opencode`; `$env:TEMP` pode resolver para o caminho curto
  `C:\Users\ERICPC~1` e quebrar `Remove-Item`/gravação.
- Não faça `git push`, `git reset --hard` nem `git clean` sem pedir confirmação. Commit só quando o
  usuário pedir.

## Armadilhas conhecidas (desktop)

- **HiDPI:** `createScreenCapture` devolve a variante de usuário. Use
  `createMultiResolutionScreenCapture` e peça o tamanho nativo (`bounds × escala`); desenhar a
  variante de usuário num canvas maior reamostra e borra cada glifo.
- **Janela:** use os bounds AWT **diretos** em `.dp`. Converter com `toDp()` aplica a densidade duas
  vezes e encolhe a janela para 2/3 num display a 150%. Desenhe 1:1 com `FilterQuality.None`.
- **Sem véu:** o picker é opaco e sem escurecimento; no recorte à mão o véu só aparece depois do
  arrasto.
- **Atalhos:** os códigos `VC_*` do JNativeHook **não** são os do AWT. O `GlobalHotkeyManager` é
  criado com `remember` e `updateHotkeys` vale na hora; não volte a recriá-lo só no início.
- **Bandeja:** clique esquerdo = tela inteira; «✂️ Capturar Área» = recorte à mão; não recrie um
  único atalho que decide os dois.
- **Monitor:** o seletor captura o monitor principal; o hover usa o monitor sob o ponteiro.

## Documentação

- `README.md` é para quem usa: comportamento, atalhos, ajustes, comandos e limitações.
- `ROADMAP.md` é para quem mantém: itens `B*`/`F*`/`M*`/`D*`/`I*`/`N*`/`R*`, estado `[x]`, data
  `(dd/MM/yyyy)`, causa raiz, o que mudou, testes/mutações e evidência ponta a ponta.
- Mudou comportamento? Atualize os dois na mesma tarefa. Não deixe o README prometer algo que o
  código não faz.
- Use a skill `yabai-docs` e o template
  `.opencode/skills/yabai-docs/references/entry-template.md`.

## Skills do projeto

Carregue a skill certa antes de trabalhar:

| skill | quando |
| --- | --- |
| `yabai-regression` | escrever/provar teste de regressão e mutação |
| `yabai-desktop-e2e` | validar visualmente o app real (captura, picker, recorte, hover) |
| `yabai-ocr-quality` | mexer em Lens, blocks, leituras, dicionários, texto japonês |
| `yabai-docs` | atualizar README/ROADMAP com evidência |
| `yabai-android-app` | mexer no app Android, overlays, MediaProjection, Robolectric |

## Definição de pronto

- [ ] Causa raiz identificada (não só o sintoma).
- [ ] Menor mudança possível; sem reformatar arquivos alheios.
- [ ] Teste de regressão novo passa e a mutação correspondente o derruba.
- [ ] Suíte do(s) módulo(s) afetado(s) verde.
- [ ] E2E feito quando o comportamento é visual/desktop, com evidência numérica.
- [ ] `settings.properties` restaurado e nenhum processo deixado aberto.
- [ ] README/ROADMAP atualizados se o comportamento mudou.
- [ ] Relatório final em pt-BR, com comandos, números e limitações.
