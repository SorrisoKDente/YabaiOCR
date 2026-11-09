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
| PipeWire | `pipewire` + `wireplumber` | usado pelo portal `ScreenCast` |
| fontes | Noto CJK | o SteamOS já traz; validar com `fc-list :lang=ja` |
| Java | **não precisa** | o runtime JDK 21 vai embutido no pacote |

O app **não escreve em `/usr`**, não pede root e não instala dependências do sistema. Tudo que ele
grava fica em `$HOME` (ver §8).

### 1.2 Para compilar no SteamOS (opcional)

| item | requisito |
| --- | --- |
| JDK | **21+** (`jdk21-openjdk`) — o runtime empacotado precisa de 21 para ScreenCast |
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
  | grep -E 'ScreenCast|GlobalShortcuts' || true

pactl info | grep -i "server name" || true
ldconfig -p | grep libfuse.so.2 || true
fc-list :lang=ja | head -n 3
```

O ideal é ver `ScreenCast` e `GlobalShortcuts`, um servidor PipeWire e `libfuse.so.2`. Se algo faltar,
veja §5.

---

## 3. Caminho A — instalar o AppImage (recomendado)

### 3.1 Obter o AppImage

Duas opções:

1. **Baixar de um release** do GitHub, quando houver um asset `*.AppImage`.
2. **Compilar em outro Linux** (Ubuntu 22.04, por exemplo):

```bash
./gradlew :desktop:packageAppImage --console=plain
find desktop/build/distributions -maxdepth 2 -name '*.AppImage'
```

Copie o arquivo para o Deck por USB, `scp` ou pasta compartilhada. O AppImage é autocontido; não é
preciso copiar o projeto nem instalar Java no Deck.

### 3.2 Colocar em `~/Applications`

O SteamOS usa o sistema de arquivos raiz imutável; instalação de usuário vai em `$HOME`.

```bash
mkdir -p ~/Applications
mv ~/Downloads/yabai-ocr-*.AppImage ~/Applications/
chmod +x ~/Applications/yabai-ocr-*.AppImage
```

> Se o arquivo veio de um pendrive exFAT/FAT32, o bit de execução pode se perder. O `chmod +x` acima
> resolve.

### 3.3 Primeira execução

```bash
~/Applications/yabai-ocr-*.AppImage
```

Na primeira captura de tela, o KDE abre o diálogo do portal. Escolha **a tela inteira** ou o monitor
e autorize. O JDK guarda um *restore token* em `~/.java/robot/screencast-tokens.properties`, então o
diálogo não deve reaparecer a cada captura.

Para testar sem misturar seus dados:

```bash
YABAI_HOME=~/yabai-teste ~/Applications/yabai-ocr-*.AppImage
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
~/Applications/yabai-ocr-*.AppImage --minimized
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
~/Applications/yabai-ocr-*.AppImage --appimage-extract-and-run
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
  | grep -E 'ScreenCast|GlobalShortcuts' || true
```

Sem `ScreenCast`, a captura não funciona. Sem `GlobalShortcuts`, o app mostra “atalhos indisponíveis”
e usa o fallback do KWin (no KDE) ou mantém as ações no menu da bandeja.

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
chmod +x gradlew

./gradlew :desktop:test --console=plain
./gradlew :desktop:packageAppImage --console=plain

find desktop/build/distributions -maxdepth 2 -name '*.AppImage'
```

Depois:

```bash
chmod +x desktop/build/distributions/*.AppImage
cp desktop/build/distributions/*.AppImage ~/Applications/
```

> Se `/usr/lib/jvm` tiver outro nome, ajuste o `JAVA_HOME`. Se você atualizar o SteamOS e o JDK
> sumir, repita a instalação ou volte para o caminho A.

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
| `~/Applications/yabai-ocr-*.AppImage` | executável | apague o arquivo |
| `~/.yabai_ocr/` | ajustes, dicionários, cache | apague a pasta |
| `~/.local/share/applications/yabai-ocr.desktop` | atalho do menu | “Integrar” ou apague |
| `~/.local/share/icons/hicolor/256x256/apps/yabai-ocr.png` | ícone | apague |
| `~/.config/autostart/yabai-ocr.desktop` | início automático | desligue o toggle ou apague |
| `~/.local/share/kwin/scripts/yabai-ocr/` | script KWin do hover | apague; o app reinstala |
| `~/.java/robot/screencast-tokens.properties` | token de captura do JDK | opcional; força nova autorização |

Nada é gravado em `/usr`, `/etc` ou `/var` pelo app.

---

## 9. Desinstalar

```bash
# atalho e ícone
rm -f ~/.local/share/applications/yabai-ocr.desktop
rm -f ~/.local/share/icons/hicolor/256x256/apps/yabai-ocr.png

# autostart
rm -f ~/.config/autostart/yabai-ocr.desktop

# script do hover
rm -rf ~/.local/share/kwin/scripts/yabai-ocr

# dados e cache (opcional: apaga dicionários e ajustes)
rm -rf ~/.yabai_ocr

# token de captura (opcional: o portal pedirá autorização de novo)
rm -f ~/.java/robot/screencast-tokens.properties

# executável
rm -f ~/Applications/yabai-ocr-*.AppImage
```

---

## 10. Solução de problemas

| sintoma | causa provável | ação |
| --- | --- | --- |
| `Permission denied` ao abrir | AppImage sem bit de execução | `chmod +x` no arquivo |
| `error loading libfuse.so.2` | FUSE 2 ausente | `--appimage-extract-and-run` ou instale `fuse2` (§5.4) |
| captura preta ou sem diálogo | portal `ScreenCast`/PipeWire ausente | §5.2; confirme `XDG_SESSION_TYPE=wayland` e o backend KDE |
| hover “indisponível” | sessão não é KDE Wayland, ou script KWin não carregou | use Desktop Mode/Plasma; veja o status nos Ajustes; confira `~/.local/share/kwin/scripts/yabai-ocr/` |
| atalhos “indisponíveis” | portal `GlobalShortcuts` ausente | no KDE o fallback KWin assume; fora do KDE use a bandeja |
| bandeja não aparece | StatusNotifier ausente (Game Mode/GNOME) | abra sem `--minimized`; o app mostra a janela |
| app “invisível” após `--minimized` | a bandeja falhou | abra sem a flag; o fallback abre a janela |
| quadrados no lugar do japonês | fonte CJK ausente | instale `noto-fonts-cjk` (§5.4) |
| “Baixar Atualização” abre a página do release | o release não publicou AppImage | comportamento esperado; baixe manualmente |
| atualização do SteamOS quebrou o script KWin | KWin/Plasma mudou de versão | abra o app de novo; ele reinstala o script se o conteúdo mudou |

---

## 11. Referências

- [LINUX.md](LINUX.md) — plano, decisões e checklist de aceite por fase.
- [README.md](README.md) — uso do app, atalhos e ajustes.
- [ROADMAP.md](ROADMAP.md) — decisões D1–D34 com causa raiz e evidência.
