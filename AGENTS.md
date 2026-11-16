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
| não sabe onde fica algo | `ARCHITECTURE.md` (árvore + papel de cada arquivo + fluxos) |

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
- A interface existe em **todos os idiomas disponíveis** — hoje pt-BR e en — e o usuário escolhe o
  idioma nos Ajustes. String nova entra nos dois de uma vez (`DesktopStrings` no desktop;
  `values/strings.xml` + `values-en/strings.xml` no Android). Nunca uma string só em um idioma, nunca
  texto fixo numa tela. Comentários de código seguem o inglês já usado no repositório.

## Comandos

Windows/PowerShell, na raiz:

```powershell
.\gradlew.bat :desktop:test --console=plain       # suíte desktop (404 testes em 15/11/2026)
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

A suíte desktop fechou em **456 testes**, o `:core` padrão (sem `-Plive`/`-Pdicts`) em **197** e o
app, com Robolectric, em **836** na última verificação (16/11/2026). Se os números mudarem, confirme
que os testes novos são intencionais e atualize ROADMAP/AGENTS.

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

- `README.md` (inglês, original) e `README.pt-BR.md` são para quem usa: o que é, requisitos e
  instalação por sistema, atalhos, como compilar e limitações. É vitrine pública — detalhe de
  implementação não entra aqui.
- `ARCHITECTURE.md` é para quem mexe no código: árvore de diretórios, papel de cada arquivo, fluxos e
  onde procurar cada tipo de mudança.
- `docs/ROADMAP.md` é para quem mantém: itens `B*`/`F*`/`M*`/`D*`/`I*`/`N*`/`R*`, estado `[x]`, data
  `(dd/MM/yyyy)`, causa raiz, o que mudou, testes/mutações e evidência ponta a ponta.
- Mudou comportamento? Atualize README/ROADMAP na mesma tarefa. Não deixe o README prometer algo que
  o código não faz.
- Use a skill `yabai-docs` e o template
  `.opencode/skills/yabai-docs/references/entry-template.md`.

**Documento público é bilíngue.** O original é em inglês: `README.md`, `ARCHITECTURE.en.md`,
`CONTRIBUTING.en.md`. A versão em pt-BR vem ao lado (`README.pt-BR.md`, `ARCHITECTURE.md`,
`CONTRIBUTING.md`), com o seletor de idioma no topo. Mudou um, atualize o par na mesma tarefa.
O `docs/ROADMAP.md` é histórico técnico interno e fica só em português.

**Estrutura mudou? Atualize o `ARCHITECTURE.md` na mesma tarefa.** Isso vale para arquivo, pasta ou
módulo novo, removido, renomeado ou que passou a ter outro papel, e para fluxo novo (motor, captura,
ajuste, tela). O documento é a referência de busca do projeto: se ele não bate com o código, a próxima
mudança começa no lugar errado. As seções afetadas são a árvore (item 2), a tabela arquivo a arquivo
do módulo (item 3), os fluxos (item 5) e a tabela «onde mexer para cada pedido».

## Skills do projeto

As skills vivem em `.opencode/skills/<nome>/SKILL.md` e são carregadas sob demanda. Não existe skill
para tarefa de rotina: se a mudança é pequena e o AGENTS já responde, trabalhe direto.

| skill | quando usar |
| --- | --- |
| `yabai-regression` | qualquer correção de bug ou mudança de comportamento: escrever o teste que falha sem a correção e provar a mutação |
| `yabai-desktop-e2e` | quando o resultado só se vê rodando o app: captura, picker, recorte, hover, HiDPI, bandeja, atalhos |
| `yabai-ocr-quality` | qualquer coisa que possa mudar o texto reconhecido: Lens, blocks, ordem de leitura, furigana, normalização CJK, dicionários, lookup |
| `yabai-android-app` | mudanças no `:app`: overlays, `FloatingOcrService`, MediaProjection, permissões, painéis, Robolectric |
| `yabai-docs` | fechar uma tarefa que mudou comportamento: entrada de ROADMAP no formato do projeto e ajuste do README |
| `systematic-debugging` | bug cuja causa não é óbvia: reproduzir, reduzir, formular hipótese e testá-la antes de propor correção |

**Que skill carregar.** Uma correção de comportamento normalmente pede `yabai-regression`; se ela for
visual/desktop, também `yabai-desktop-e2e`; se mexer em texto japonês, também `yabai-ocr-quality`; e
`yabai-docs` no fim. Skills não se substituem: cada uma cobre uma parte da definição de pronto.

## Checklist para Nova Versão do App

Sempre que for lançar uma nova versão (ex.: `1.0.7`), os seguintes arquivos precisam ser atualizados:

1. **`app/build.gradle.kts`**: Atualizar `versionName = "1.0.7"` (o `versionCode` é calculado automaticamente pela função `calculateVersionCode`).
2. **`desktop/build.gradle.kts`**: Atualizar o fallback de `appVersion` (`?: "1.0.7"`).
3. **`desktop/src/main/kotlin/dev/yabai/ocr/desktop/DesktopAppUpdateChecker.kt`**: Atualizar a constante `FALLBACK_VERSION = "1.0.7"`.

## Definição de pronto

- [ ] Causa raiz identificada (não só o sintoma).
- [ ] Menor mudança possível; sem reformatar arquivos alheios.
- [ ] Teste de regressão novo passa e a mutação correspondente o derruba.
- [ ] Suíte do(s) módulo(s) afetado(s) verde.
- [ ] E2E feito quando o comportamento é visual/desktop, com evidência numérica.
- [ ] `settings.properties` restaurado e nenhum processo deixado aberto.
- [ ] README/ROADMAP atualizados se o comportamento mudou.
- [ ] `ARCHITECTURE.md` atualizado se algum arquivo, pasta, módulo ou fluxo entrou, saiu ou mudou de papel.
- [ ] Toda string nova de UI existe em pt-BR e en; documento público atualizado nas duas línguas.
- [ ] Relatório final em pt-BR, com comandos, números e limitações.
