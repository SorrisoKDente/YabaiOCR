# SteamOS / Steam Deck — requisitos e instalação

Guia para instalar, testar e remover o **Yabai OCR Desktop** no **SteamOS**, com foco no
**Desktop Mode (KDE Plasma + Wayland)**, que é o alvo principal do suporte Linux. O plano técnico
completo está em [LINUX.md](LINUX.md).

> **Game Mode (Gamescope) é melhor esforço.** A janela, o OCR e os dicionários funcionam, mas não há
> KWin scripting nem bandeja: hover e atalhos globais ficam indisponíveis por limitação da sessão,
> não do app.

---

## 1. Requisitos

### 1.1 Para rodar o app (usuário final)

| item | requisito | observação |
| --- | --- | --- |
| SteamOS | 3.x atual, x86_64 | Steam Deck LCD/OLED e outros PCs com SteamOS |
| sessão | **Desktop Mode**, `XDG_SESSION_TYPE=wayland`, KDE Plasma | X11 também funciona, com o caminho legado |
| espaço | ~1 GB para o app + dados | dicionários podem ocupar mais |
| FUSE | `libfuse.so.2` | só é necessário se você usar o `.AppImage`; o pacote `.tar.gz` não usa FUSE |
| portal | `xdg-desktop-portal` + `xdg-desktop-portal-kde` | captura de tela e atalhos globais |
| PipeWire | `pipewire` + `wireplumber` | usado pelo `ScreenCast` de outros desktops; o KDE captura pelo `ScreenShot2` (portal `Screenshot` como fallback) |
| fontes | Noto CJK | o SteamOS já traz; validar com `fc-list :lang=ja` |
| Java | **não precisa** | o runtime JDK 21 vai embutido no pacote |

O app **não escreve em `/usr`**, não pede root e não instala dependências do sistema. Tudo que ele
grava fica em `$HOME` (ver §8).

### 1.2 Para compilar no SteamOS (opcional)

| item | requisito |
| --- | --- |
| JDK | **21+** (`jdk21-openjdk`) — o runtime empacotado já inclui o necessário |
| Gradle | o wrapper `./gradlew` baixa sozinho |
| empacotamento | `fakeroot` para `.deb`; `appimagetool` é opcional, só para o `.AppImage` de arquivo único |
| rede | para baixar Gradle e dependências na primeira compilação |

> **Recomendação:** compile no Ubuntu/Debian ou em um container e copie só o pacote para o Deck.
> Instalar JDK/pacotes no SteamOS exige desbloquear a raiz e pode ser desfeito por atualizações do
> sistema. O passo a passo de compilação no SteamOS está em §6, mas é o caminho menos estável.

---

## 2. Antes de instalar: conferir o ambiente

No Desktop Mode, abra o **Konsole** e rode:

```bash
echo "$XDG_SESSION_TYPE"        # esperado: wayland
echo "$XDG_CURRENT_DESKTOP"     # esperado: KDE
plasmashell --version
```

Confira o portal, o PipeWire e o FUSE:

```bash
busctl --user introspect org.freedesktop.portal.Desktop /org/freedesktop/portal/desktop \
  | grep -E 'Screenshot|GlobalShortcuts' || true
busctl --user introspect org.kde.KWin.ScreenShot2 /org/kde/KWin/ScreenShot2 2>/dev/null || true

pactl info | grep -i "server name" || true
ldconfig -p | grep libfuse.so.2 || true
fc-list :lang=ja | head -n 3
```

O ideal é ver `ScreenShot2` (captura direta), `Screenshot` (fallback) e `GlobalShortcuts`, um servidor
PipeWire e `libfuse.so.2`. Se algo faltar,
veja §5.

---

## 3. Caminho A — instalar o AppImage (recomendado)

### 3.1 Obter o AppImage

Duas opções:

1. **Baixar de um release** do GitHub, quando houver um asset `*.AppImage`.
2. **Compilar em outro Linux** (Ubuntu 22.04, por exemplo):

```bash
./gradlew :desktop:packageLinuxAppImage --console=plain
find desktop/build/distributions/appimage -maxdepth 1 -name '*.AppImage'
```

> `:desktop:packageAppImage` do Compose 1.7 **não** gera um AppImage de arquivo único: ele cria a
> mesma pasta do `:desktop:createDistributable`. Use `:desktop:packageLinuxAppImage`, que chama o
> `appimagetool`. Se você não tiver o `appimagetool`, use o `.tar.gz` abaixo.

Sem `appimagetool`, o pacote portátil é:

```bash
./gradlew :desktop:packagePortableTarGz --console=plain
# desktop/build/distributions/portable/Yabai_OCR_Linux_x86_64_1.0.0.tar.gz
```

Copie o arquivo para o Deck por USB, `scp` ou pasta compartilhada. O pacote é autocontido; não é
preciso copiar o projeto nem instalar Java no Deck.

### 3.2 Colocar em `~/Applications`

O SteamOS usa o sistema de arquivos raiz imutável; instalação de usuário vai em `$HOME`.

Com AppImage:

```bash
mkdir -p ~/Applications
mv ~/Downloads/Yabai_OCR-*.AppImage ~/Applications/
chmod +x ~/Applications/Yabai_OCR-*.AppImage
```

Com o `.tar.gz` (sem FUSE):

```bash
mkdir -p ~/Applications/yabai-ocr
tar -xzf ~/Downloads/Yabai_OCR_Linux_x86_64_1.0.0.tar.gz -C ~/Applications/yabai-ocr
chmod +x ~/Applications/yabai-ocr/bin/yabai-ocr
```

> Se o arquivo veio de um pendrive exFAT/FAT32, o bit de execução pode se perder. O `chmod +x` acima
> resolve.

### 3.3 Primeira execução

```bash
~/Applications/Yabai_OCR-*.AppImage
# ou, sem FUSE:
~/Applications/yabai-ocr/bin/yabai-ocr
```

**No AppImage a captura cai no portal `Screenshot`, e isso é estrutural.** O KWin só autoriza
`org.kde.KWin.ScreenShot2` a quem tem `X-KDE-DBUS-Restricted-Interfaces=org.kde.KWin.ScreenShot2`
numa entrada `.desktop` **que viva em diretório privilegiado** (`/usr/share/applications`) e cujo
`Exec` seja o próprio `/proc/<pid>/exe`. Dentro de um AppImage o exe é
`/tmp/.mount_Yabai_XXXX/opt/yabai-ocr/bin/yabai-ocr`, com sufixo novo a cada abertura: nenhuma
entrada fixa casa. A entrada oculta `~/.local/share/applications/yabai-ocr-screenshot.desktop` que o
app grava **não** autoriza nada (diretório de usuário é recusado) — ela é inofensiva e só registra o
caminho em execução.

Nesse caminho de portal o KDE mostra **«Permitir que aplicativos tirem capturas de tela?»** a cada
chamada, e é isso que deixa o hover lento. Para saber qual dos dois caminhos está em uso, veja a
linha `[Captura] …` no log (ou o fim da linha de status do hover nos Ajustes): `KWin ScreenShot2` é
a captura direta; `portal Screenshot` é o caminho lento. A saída é **Ajustes → 🖼 Captura silenciosa no KDE**: grava
`~/.config/environment.d/yabai-ocr.conf` com `KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1`, que desliga a
checagem dentro do KWin. A sessão só lê esse arquivo no login, então **saia e entre uma vez** depois
de ligar — não é preciso repetir depois, e não é preciso reiniciar a cada abertura do app. A opção
vale para a sessão inteira (qualquer programa passa a capturar sem pedir), por isso é opt-in.

Para testar sem misturar seus dados:

```bash
YABAI_HOME=~/yabai-teste ~/Applications/Yabai_OCR-*.AppImage
```

### 3.4 Integrar ao menu de aplicativos

Com o app aberto, vá em **Ajustes → Integração com o Linux → “Integrar ao sistema”**. Ele grava:

- `~/.local/share/applications/yabai-ocr.desktop`
- `~/.local/share/icons/hicolor/256x256/apps/yabai-ocr.png`

Se o menu não atualizar na hora, saia e entre na sessão ou rode:

```bash
update-desktop-database ~/.local/share/applications 2>/dev/null || true
```

O botão só funciona quando o app consegue identificar o executável real (o AppImage em execução). Se
ele recusar, confira se o AppImage está em `~/Applications` e se foi aberto por esse caminho.

### 3.5 Iniciar junto com a sessão

Em **Ajustes → Inicialização e Comportamento da Janela**, ligue “Iniciar na bandeja ao entrar na
sessão”. O app cria:

```bash
~/.config/autostart/yabai-ocr.desktop
```

O entry usa `--minimized`. Se a bandeja não estiver disponível, o app abre a janela em vez de rodar
invisível.

### 3.6 Teste rápido no Desktop Mode

1. Captura de tela inteira pelo ícone da bandeja.
2. Recorte à mão (**✂️ Capturar Área**).
3. Hover: `Ctrl + Shift + H` e passe o mouse sobre um texto japonês.
4. Atalhos globais: `Ctrl + Shift + O` (tela inteira) e `Ctrl + Shift + A` (área).
5. Bandeja: clique esquerdo captura; menu tem “Modo Hover [Ativo/Inativo]”.
6. “Integrar ao sistema” e abrir pelo menu de aplicativos.
7. Ajustes mostram o estado de captura, atalhos e hover — se algo estiver indisponível, o motivo
   aparece na tela.

---

## 4. Rodar com `--minimized`

```bash
~/Applications/Yabai_OCR-*.AppImage --minimized
```

Só use depois de confirmar que a bandeja aparece no Plasma. Se a bandeja falhar, o app abre a janela
normalmente — esse fallback é intencional.

---

## 5. Dependências que podem faltar no SteamOS

O SteamOS já vem preparado para o Desktop Mode, mas estas peças são as que costumam faltar em imagens
mais antigas ou em instalações mínimas.

### 5.1 FUSE (para executar o AppImage)

```bash
ldconfig -p | grep libfuse.so.2
```

Se não aparecer nada, duas saídas:

```bash
# sem tocar no sistema: extrai e executa
~/Applications/Yabai_OCR-*.AppImage --appimage-extract-and-run
```

ou instale `fuse2` (§5.4).

### 5.2 Portal e PipeWire (captura e atalhos)

```bash
pacman -Q xdg-desktop-portal xdg-desktop-portal-kde pipewire wireplumber
```

Esses pacotes normalmente já estão instalados no Desktop Mode. Se o diálogo de captura não abrir,
verifique se o backend KDE está ativo:

```bash
busctl --user introspect org.freedesktop.portal.Desktop /org/freedesktop/portal/desktop \
  | grep -E 'Screenshot|GlobalShortcuts' || true
busctl --user introspect org.kde.KWin.ScreenShot2 /org/kde/KWin/ScreenShot2 2>/dev/null || true
```

Sem `ScreenShot2` (ou o portal `Screenshot` como fallback), a captura não funciona. Sem
`GlobalShortcuts`, o app mostra “atalhos indisponíveis” e usa o fallback do KWin (no KDE) ou mantém
as ações no menu da bandeja.

### 5.3 Fontes japonesas

```bash
fc-list :lang=ja | head
```

Se a lista estiver vazia, instale `noto-fonts-cjk` (§5.4). Sem isso, a UI pode mostrar caixas vazias.

### 5.4 Instalar pacotes no SteamOS (com aviso)

O SteamOS tem a raiz somente leitura. Para instalar pacotes do sistema:

```bash
sudo steamos-readonly disable

sudo pacman -S --needed fuse2 \
  xdg-desktop-portal xdg-desktop-portal-kde \
  pipewire wireplumber \
  noto-fonts-cjk

sudo steamos-readonly enable
```

Regras:

- Instale **somente o que faltar**. Não rode `pacman -Syu` para “atualizar tudo”: o SteamOS é uma
  imagem versionada e uma atualização parcial pode quebrar o sistema. Atualize o SteamOS pelo próprio
  Steam/`steamos-update`.
- Se o `pacman` reclamar de banco de dados ausente, sincronize apenas o necessário com
  `sudo pacman -Sy --needed ...` e reative a raiz imediatamente.
- Alterações em `/usr` podem ser desfeitas por uma atualização do SteamOS. O AppImage e os dados do
  usuário em `$HOME` sobrevivem.
- Se você não quiser mexer na raiz, use `--appimage-extract-and-run` e aceite a limitação de captura
  sem o portal.

---

## 6. Caminho B — compilar no SteamOS (opcional, menos recomendado)

Só faça isso se quiser compilar dentro do Deck. O caminho normal é compilar em outro Linux e copiar o
AppImage.

```bash
sudo steamos-readonly disable
sudo pacman -S --needed git jdk21-openjdk fakeroot fuse2
sudo steamos-readonly enable

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
java -version    # precisa dizer 21

cd ~/yabai_ocr
chmod +x gradlew desktop/package/linux/build-appimage.sh

./gradlew :desktop:test --console=plain
./gradlew :desktop:packagePortableTarGz --console=plain
```

O `.tar.gz` fica em `desktop/build/distributions/portable/` e não precisa de FUSE. Para gerar o
AppImage de arquivo único, instale o `appimagetool` (o AppImage oficial de
<https://github.com/AppImage/appimagetool/releases>) e rode:

```bash
export APPIMAGETOOL=/caminho/para/appimagetool-x86_64.AppImage
# sem FUSE no sistema:
export APPIMAGE_EXTRACT_AND_RUN=1
./gradlew :desktop:packageLinuxAppImage --console=plain
```

O resultado fica em `desktop/build/distributions/appimage/Yabai_OCR-1.0.0-x86_64.AppImage`.

> O `gradle.properties` não guarda mais o caminho absoluto do JDK do Windows. O Gradle usa o
> `JAVA_HOME` que você exportou. Se você atualizar o SteamOS e o JDK sumir, repita a instalação ou
> volte para o caminho A.

---

## 7. Game Mode

| recurso | Game Mode |
| --- | --- |
| UI, OCR, dicionários, tradução | ✅ abre como janela normal |
| captura de tela | ⚠️ depende do portal disponível na sessão Gamescope |
| atalhos globais | ❌ sem KWin/portal de atalhos |
| hover | ❌ sem ponteiro global do KWin |
| bandeja | ❌ |
| autostart | ⚠️ iniciar junto com o Steam |

Para adicionar ao Game Mode: **Biblioteca → Adicionar jogo não Steam** e aponte para o AppImage.
Não prometa hover/atalhos nessa sessão.

---

## 8. O que o app grava no SteamOS

| caminho | conteúdo | como remover |
| --- | --- | --- |
| `~/Applications/Yabai_OCR-*.AppImage` | executável | apague o arquivo |
| `~/.yabai_ocr/` | ajustes, dicionários, cache | apague a pasta |
| `~/.local/share/applications/yabai-ocr.desktop` | atalho do menu (declara a interface do KWin) | “Integrar” ou apague |
| `~/.local/share/applications/yabai-ocr-screenshot.desktop` | entrada oculta com o caminho do binário em execução (não autoriza `ScreenShot2`: usuário não é diretório privilegiado) | apague; o app regrava no próximo início |
| `~/.config/environment.d/yabai-ocr.conf` | `KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1`, só se você ligar «Captura silenciosa no KDE» | desligue a opção ou apague o arquivo (vale no próximo login) |
| `~/.local/share/icons/hicolor/256x256/apps/yabai-ocr.png` | ícone | apague |
| `~/.config/autostart/yabai-ocr.desktop` | início automático | desligue o toggle ou apague |
| `~/.local/share/kwin/scripts/yabai-ocr/` | script KWin do hover | apague; o app reinstala |
| `~/.java/robot/screencast-tokens.properties` | token legado do `ScreenCast` do JDK | não é usado no KDE Wayland; pode apagar |

Nada é gravado em `/usr`, `/etc` ou `/var` pelo app.

---

## 9. Desinstalar

```bash
# atalho e ícone
rm -f ~/.local/share/applications/yabai-ocr.desktop
rm -f ~/.local/share/icons/hicolor/256x256/apps/yabai-ocr.png

# autostart
rm -f ~/.config/autostart/yabai-ocr.desktop

# entrada oculta de autorização da captura
rm -f ~/.local/share/applications/yabai-ocr-screenshot.desktop

# script do hover
rm -rf ~/.local/share/kwin/scripts/yabai-ocr

# dados e cache (opcional: apaga dicionários e ajustes)
rm -rf ~/.yabai_ocr

# token legado do ScreenCast do JDK (não usado no KDE Wayland)
rm -f ~/.java/robot/screencast-tokens.properties

# executável
rm -f ~/Applications/Yabai_OCR-*.AppImage
```

---

## 10. Solução de problemas

| sintoma | causa provável | ação |
| --- | --- | --- |
| `Permission denied` ao abrir | AppImage sem bit de execução | `chmod +x` no arquivo |
| `error loading libfuse.so.2` | FUSE 2 ausente | `--appimage-extract-and-run` ou instale `fuse2` (§5.4) |
| “Não foi possível publicar o serviço D-Bus dev.yabai.Ocr” | runtime empacotado sem `jdk.security.auth` ou sem as bibliotecas nativas do `junixsocket` (build antigo) | use o pacote atual; confira `jdk.security.auth` em `lib/runtime/release` e os jars `junixsocket-*` em `lib/app` |
| atalhos aparecem como ativos, mas não disparam | portal devolve `session_handle`/`Activated` como object path; build antigo lia como texto | use o pacote atual (correção D35); confira o status em Ajustes |
| clique direito na bandeja não abre o menu | build antigo usava `TrayIcon` AWT/XEmbed; o Plasma Wayland não entrega o clique ao processo Java | use o build atual (StatusNotifierItem, correção D36); confirme `org.kde.StatusNotifierItem-<pid>-1` em `busctl --user list` |
| menu da bandeja não fecha ao clicar fora | build antigo desenhava o menu Swing num invólucro sem foco | use o build atual (DBusMenu, correção D44); o Plasma desenha e fecha o menu nativamente |
| bandeja não aparece no KDE | `org.kde.StatusNotifierWatcher` ausente ou app iniciado antes do Plasma | reinicie o app no Desktop Mode; se persistir, abra sem `--minimized` e veja o log |
| `packageAppImage` não gera `.AppImage` | o Compose 1.7 mapeia esse alvo para uma pasta | use `:desktop:packageLinuxAppImage` ou `:desktop:packagePortableTarGz` |
| captura preta ou “permissão negada” | `ScreenShot2` e portal `Screenshot` indisponíveis, ou fallback negado | §5.2; confirme `XDG_SESSION_TYPE=wayland`, o backend KDE e, se o fallback for usado, permita a captura |
| hover “indisponível” | sessão não é KDE Wayland, ou script KWin não carregou | use Desktop Mode/Plasma; veja o status nos Ajustes; confira `~/.local/share/kwin/scripts/yabai-ocr/` |
| hover lento mesmo com a captura direta (`ScreenShot2 · ~75 ms`) | a espera é do OCR: o motor `Lens` é uma chamada de rede (~1,2 s por leitura neste hardware) e o `PP-OCRv6` offline mediu 2,3 s na mesma imagem | é o custo do motor escolhido, não da captura; veja o tempo de captura e o motor na linha de status/Ajustes. Dos ~1,4 s, ~250 ms são o **repouso do ponteiro** (padrão desde o D82), ajustável em **Ajustes → Modo Hover → «Espera para abrir o card»** (150/250/420 ms) |
| o cartão «Renderizador gráfico» mostra só «Software» | no Linux o Skiko desenha com OpenGL e «Automático» **já é** OpenGL; oferecer os dois seria a mesma escolha com dois nomes, e Direct3D não existe ali (D82) | é o esperado. «Software» é o caminho de volta quando a placa de vídeo não desenha a janela (VM sem GL, desktop remoto, driver recusando o contexto) |
| depois de fechar um AppImage à força, a próxima cópia diz «já existe uma instância aberta» | matar o **runtime** do AppImage não mata o app filho, que continua vivo segurando `~/.yabai_ocr/instance.lock` | encerre pelo app ou mate o processo `yabai-ocr` em si; confira com `ps -eo pid=,comm= \| awk '$2=="yabai-ocr"'` |
| janela «Portal» aparece e o hover demora a cada leitura | o `ScreenShot2` recusou o app (`Error.NoAuthorized`) e a captura caiu no portal `Screenshot`. Em AppImage é o comportamento normal: o exe vive em `/tmp/.mount_*` e nunca casa com a entrada fixa exigida pelo KWin (D61) | ligue **Ajustes → 🖼 Captura silenciosa no KDE** e saia/entre na sessão uma vez; o log do app mostra `captura direta pelo KWin recusada (…)` |
| não se sabe se a captura está no `ScreenShot2` ou no portal | o caminho realmente usado não aparecia em lugar nenhum (só a recusa era registrada) | o log imprime, no arranque, `[Captura] caminho esperado nesta sessão: KWin ScreenShot2 (KDE Wayland)` e, na primeira captura de cada transporte, `[Captura] captura: KWin ScreenShot2 · 1280×800 · 47 ms` (D81). A linha de status do hover nos Ajustes termina com a mesma medida. `ScreenShot2` = direto e rápido; `portal Screenshot` = diálogo e segundos por leitura |
| atalho novo (ex.: busca rápida) não dispara, o antigo continua | o KGlobalAccel mantinha a combinação anterior daquele fallback do KWin | use o build atual (D45); se persistir, rode `kbuildsycoca6` uma vez ou reinicie o app para reaplicar o fallback |
| “o KWin bridge recusou carregar o script” ao reabrir o app | o script da execução anterior continuou carregado no KWin | use o build atual (D45), que descarrega o script obsoleto antes de carregar; se aparecer mesmo assim, reinicie a sessão do Plasma |
| atalhos “indisponíveis” | portal `GlobalShortcuts` ausente | no KDE o fallback KWin assume; fora do KDE use a bandeja |
| bandeja não aparece | StatusNotifier ausente (Game Mode/GNOME) | abra sem `--minimized`; o app mostra a janela |
| app “invisível” após `--minimized` | a bandeja falhou | abra sem a flag; o fallback abre a janela |
| quadrados no lugar do japonês | fonte CJK ausente | instale `noto-fonts-cjk` (§5.4) |
| o botão de IA do resultado não abre nada | `Desktop.browse` do JDK falha dentro do AppImage (precisa do `gio`/peer X11 do desktop) e a falha era engolida | use o build atual (D46): ele tenta `xdg-open`, `gio open`, `kde-open5` antes do AWT; se nenhum abrir, o link vai para a área de transferência e uma aviso aparece |
| a captura sempre pega a tela errada (Steam Deck com monitor externo) | a captura usava o monitor principal | Ajustes → “Captura de Tela — Qual Monitor”: escolha um monitor ou “Monitor onde está o ponteiro”; o hover continua seguindo o ponteiro |
| “Baixar Atualização” abre a página do release | o release não publicou AppImage | comportamento esperado; baixe manualmente |
| atualização do SteamOS quebrou o script KWin | KWin/Plasma mudou de versão | abra o app de novo; ele reinstala o script se o conteúdo mudou |

---

## 11. Referências

- [LINUX.md](LINUX.md) — plano, decisões e checklist de aceite por fase.
- [README.pt-BR.md](../README.pt-BR.md) — requisitos, instalação, uso e atalhos (em português).
- [ROADMAP.md](ROADMAP.md) — decisões D1–D35 com causa raiz e evidência.
