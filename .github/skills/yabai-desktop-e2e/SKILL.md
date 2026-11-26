---
name: yabai-desktop-e2e
description: Verificar o Yabai OCR Desktop de verdade no Windows — iniciar com --minimized, capturar telas com java.awt.Robot, comparar pixels antes/depois e validar picker, recorte, hover e HiDPI. Use quando a mudança for visual, de captura de tela, atalhos, bandeja ou janelas Compose.
---

# E2E do desktop

Teste de unidade não vê reamostragem, véu, janela no tamanho errado nem atalho roubado. Mudança
visual no `:desktop` só está pronta com o app real e evidência numérica.

## Regras de segurança (não negociáveis)

1. **Backup do `settings.properties`** antes de tudo; restaurar o arquivo exato no fim.
2. Rodar o app com **`--minimized`**. Sem isso a sonda pode clicar na janela de Ajustes e regravar
   os atalhos ao "capturar" uma tecla.
3. Matar **somente** `dev.yabai.ocr.desktop.MainKt`. O Gradle sai com código `-1`; é esperado.
4. Usar o caminho explícito `C:\Users\Eric PC\AppData\Local\Temp\opencode\...`. `$env:TEMP` pode
   resolver para `C:\Users\ERICPC~1` e quebrar gravação/remoção.
5. Não deixar app nem sonda rodando. Não usar a máquina do usuário como tela de teste sem restaurar
   o estado.

## Checklist

### 1. Estado inicial

```powershell
$probe = "C:\Users\Eric PC\AppData\Local\Temp\opencode\yabai-e2e"
New-Item -ItemType Directory -Force -Path $probe | Out-Null
$settings = Join-Path $env:USERPROFILE ".yabai_ocr\settings.properties"
Copy-Item -LiteralPath $settings -Destination (Join-Path $probe "settings.properties.bak") -Force
(Get-FileHash -LiteralPath $settings -Algorithm SHA256).Hash |
    Set-Content (Join-Path $probe "settings.sha256")
Get-Content $settings | Select-String "hotkey|picker_reading"
```

Anote os atalhos atuais. Nesta máquina o usuário usa `global_hotkey=Insert` e
`region_hotkey=Ctrl + Shift + A`; não presuma os padrões do README.

### 2. Subir o app

```powershell
$log = Join-Path $probe "app.log"
Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/c", ".\gradlew.bat :desktop:run --console=plain --args=`"--minimized`" > `"$log`" 2>&1" `
    -WindowStyle Hidden

$deadline = (Get-Date).AddSeconds(180)
while ((Get-Date) -lt $deadline -and
       -not (Select-String -Path $log -Pattern "gancho global de teclado" -Quiet -ErrorAction SilentlyContinue)) {
    Start-Sleep -Seconds 2
}
Get-Content $log -Tail 5
```

Espere a linha `[Hotkey] gancho global de teclado ativo.`.

### 3. Sonda

Copie `.github/skills/yabai-desktop-e2e/references/RobotProbe.java` para `$probe`, ajuste os
atalhos e o ponto de clique, e rode:

```powershell
cd "C:\Users\Eric PC\AppData\Local\Temp\opencode\yabai-e2e"
javac -encoding UTF-8 RobotProbe.java
java "-Dfile.encoding=UTF-8" "-Dout=$probe" RobotProbe
```

A sonda tira `0-antes`, abre a tela inteira (`1-seletor`), clica num parágrafo (`2-resultado`) e
abre o recorte (`3-recorte`). Ela estaciona o ponteiro numa área vazia antes das capturas porque o
picker realça a caixa sob o cursor.

### 4. Comparar os pixels

```powershell
& .\.github\skills\yabai-desktop-e2e\scripts\compare-regions.ps1 `
    -Before (Join-Path $probe "0-antes.png") `
    -After  (Join-Path $probe "1-seletor.png") `
    -Region "pagina branca=850,210,140,180", "xadrez 1px=520,435,460,20", `
            "fundo preto=100,540,200,80", "area vazia=1100,450,150,150" `
    -FailOnDiff
```

As coordenadas acima são de uma página de teste 1280×720; ajuste à sua. O que importa:

- **0 diferenças** em regiões sem desenho do app.
- **Xadrez de 1 px é o teste decisivo**: qualquer reamostragem o transforma em cinza. Antes da
  correção de HiDPI, 6.900 dos 9.200 px mudavam; depois, 0.
- **Não inclua** a área do texto (o picker desenha caixas e rubi por cima, de propósito) nem a barra
  de tarefas (o acrílico do Windows muda de tom). Elas não medem reamostragem.
- Se houver diferença, o script mostra o primeiro pixel e as cores. Investigue antes de culpar o
  teste.

### 5. Inspeção visual

Abra `1-seletor.png`, `2-resultado.png` e `3-recorte.png` (ferramenta de imagem do harness). Confirme:

- o picker cobre a tela inteira, sem tarja faltando;
- as caixas estão alinhadas ao texto e o rubi/faixa está legível;
- o resultado abre com o cabeçalho do seletor e o texto do parágrafo;
- o recorte mostra «Arraste para selecionar» e só escurece depois do arrasto.

Números provam a resolução; a imagem prova a intenção.

### 6. Encerrar e restaurar

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like "*dev.yabai.ocr.desktop.MainKt*" } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 2

Copy-Item -LiteralPath (Join-Path $probe "settings.properties.bak") -Destination $settings -Force
$expected = Get-Content (Join-Path $probe "settings.sha256")
$actual = (Get-FileHash -LiteralPath $settings -Algorithm SHA256).Hash
if ($actual -ne $expected) { throw "settings.properties NÃO foi restaurado" }
Get-Content $settings | Select-String "hotkey|picker_reading"
```

Mate o app **antes** de restaurar: ele grava ajustes enquanto roda. Confira também que não sobrou
nenhum processo:

```powershell
(Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like "*dev.yabai.ocr.desktop.MainKt*" } |
    Measure-Object).Count
```

## Armadilhas e o que cada sintoma significa

| sintoma | causa provável | onde olhar |
| --- | --- | --- |
| picker com 2/3 da tela | `toDp()` aplicado sobre bounds AWT | `SegmentPickerWindow.kt` / `RegionSelectionWindow.kt` |
| texto borrado, xadrez virou cinza | variante de usuário ampliada ou `FilterQuality` padrão | `ScreenCapture.kt`, `drawImage` |
| tela inteira escurecida antes de arrastar | véu desenhado sempre | `RegionSelectionWindow.kt` |
| barra de tarefas fora da janela | maximizou para a work area | `screenBounds` vs. `maximumWindowBounds` |
| atalhos trocados depois da sonda | clique caiu na janela de Ajustes | rodar com `--minimized` |
| `gradlew` terminou `-1` | app foi morto | esperado |
| região de texto difere | caixas/rubi desenhados por cima | escolha região sem desenho |
| barra de tarefas difere | acrílico do Windows | não use essa região |
| `Remove-Item` falha em `C:\Users\ERICPC~1` | `$env:TEMP` curto | use caminho explícito |

## HiDPI (o erro que já custou caro)

- `Robot.createScreenCapture(bounds)` devolve a variante de **usuário** (1280×720 a 150%). O
  framebuffer nativo é 1920×1080.
- Use `createMultiResolutionScreenCapture(bounds)` e peça `bounds × escala`; o teste
  `ScreenCaptureTest` cobre esse cálculo.
- A janela usa os bounds AWT **diretos** em `.dp`; o canvas é 1:1 com a imagem nativa.
- A captura do seletor é do **monitor principal**; o hover usa o monitor sob o ponteiro. Não
  confunda os dois ao testar multimonitor.

## Evidência para o relatório

Tabela de regiões com `N de TOTAL` (e o primeiro pixel, se houver), mais o resultado visual. Diga
quais áreas ficaram de fora e por quê (texto, barra de tarefas). Sem isso, "ficou nítido" não vale.
