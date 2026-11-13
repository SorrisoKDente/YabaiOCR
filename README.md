# Yabai OCR

**Português** | [English](README.en.md)

Leitor de japonês com OCR para **desktop** e **Android**: selecione qualquer texto japonês na tela —
um balão de mangá, uma legenda, um jogo — e veja na hora a leitura, o significado no dicionário e a
tradução, sem tirar print nem abrir outra janela.

O reconhecimento pode ser **online** (Google Lens) ou **offline** (ML Kit no Android, PP-OCRv6 no
desktop e no Android), então o app continua útil sem conexão.

---

## O que ele faz

- **Recorte à mão** de uma área da tela e leitura imediata do texto.
- **Tela inteira com seletor**: o app marca cada balão/parágrafo e você toca no que quer ler, em vez
  de receber a página toda.
- **Hover no desktop**: pare o mouse sobre um texto japonês e o cartão aparece com a palavra e o
  verbete.
- **Dicionários offline** no formato do Yomitan (Jitendex, JMdict, KANJIDIC e outros), com busca por
  termo, leitura ou definição, filtro por dicionário e ordem de preferência.
- **Furigana**: leitura impressa preservada, leitura gerada pelo dicionário, ou as duas.
- **Card de kanji** com traços, leituras e compostos.
- **Tradução offline** da frase reconhecida e botão para perguntar a uma IA externa.
- **Motores de OCR à escolha**, com modo automático e ordem de prioridade.

## Plataformas

| plataforma | requisitos |
| --- | --- |
| **Windows** 10/11 | JDK 17+ para compilar; existe pacote pronto em [`apk-output/`](apk-output) para Android |
| **Linux** (X11 e Wayland/KDE) | JDK 17+; no Wayland/KDE a captura usa o `KWin.ScreenShot2` (portal `Screenshot` como fallback) e os atalhos usam o portal `GlobalShortcuts`; o hover no Plasma lê a tela sem diálogo de permissão — ver [LINUX.md](LINUX.md) |
| **Steam Deck** | Desktop Mode é o alvo; Game Mode é melhor esforço — ver [STEAMOS.md](STEAMOS.md) |
| **Android** 8.0+ (API 26) | APK `arm64-v8a`; overlay e captura de tela exigem permissão |

## Como usar

**Desktop**

```powershell
.\gradlew.bat :desktop:run
```

O app abre nos Ajustes e fica na bandeja. A interface existe em **português e inglês**, escolhido nos
Ajustes. Atalhos padrão (todos configuráveis):

| atalho | ação |
| --- | --- |
| `Ctrl + Shift + O` | captura de tela inteira com seletor de trechos |
| `Ctrl + Shift + A` | recorte à mão |
| `Ctrl + Shift + H` | liga/desliga o hover |
| `Ctrl + Shift + D` | busca rápida no dicionário |
| `Ctrl + Shift + X` | alterna entre as leituras do hover |

Em telas múltiplas, os Ajustes deixam escolher qual monitor a captura de tela inteira e o recorte à
mão usam: o principal, um monitor específico ou aquele em que o ponteiro está no momento da captura.

**Android**: instale o APK, conceda **sobreposição** e **captura de tela** (MediaProjection), escolha
o motor e toque em iniciar. A bolha flutuante dispara a captura.

## Motores de OCR

| motor | onde roda | internet | observações |
| --- | --- | --- | --- |
| **Google Lens** | desktop e Android | sim | melhor precisão geral; sujeito a limite de uso do Google |
| **ML Kit (japonês)** | Android | não | modelo embutido no APK, rápido, bom em texto impresso |
| **PP-OCRv6_manga** | desktop e Android | não | detector de balões + leitor de linhas; ~23 MB baixados nos Ajustes |

No modo **Automático** o app percorre a ordem de prioridade escolhida e só passa para o próximo motor
quando o anterior não devolve texto — é assim que o offline assume quando não há conexão.

## Dicionários

Os dicionários são locais, no formato do [Yomitan](https://github.com/yomidevs/yomitan): instale pelo
catálogo do app ou importe um ZIP. Nada do que você pesquisa sai do aparelho.

## Compilar

```powershell
.\gradlew.bat :desktop:run          # roda o desktop
.\gradlew.bat :desktop:test         # suíte do desktop
.\gradlew.bat :core:test            # núcleo (JVM puro)
.\gradlew.bat :app:assembleDebug    # APK de debug
.\gradlew.bat :app:testDebugUnitTest
```

Os pacotes do desktop saem de `:desktop:packagePortableTarGz` e `:desktop:packageLinuxAppImage`
(Linux) ou do instalador gerado pelo Compose Desktop no Windows.

## Estrutura do projeto

| módulo | papel |
| --- | --- |
| `:core` | Kotlin/JVM puro: OCR (Lens, PP-OCRv6), protobuf, dicionários, japonês, geometria |
| `:desktop` | Compose Desktop: captura, seletor, hover, atalhos, bandeja, ajustes |
| `:app` | Android: serviço de captura, overlays, dicionários, tradução |

O mapa detalhado — arquivo por arquivo, fluxos e armadilhas — está em
[ARCHITECTURE.md](ARCHITECTURE.md).

## Documentação

| documento | conteúdo |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | estrutura completa do código, fluxos e onde mexer |
| [ROADMAP.md](ROADMAP.md) | histórico técnico: causa raiz, correção e evidências de cada mudança |
| [LANGUAGES.md](LANGUAGES.md) | textos de interface em português e inglês |
| [LINUX.md](LINUX.md) | suporte a Linux (X11, Wayland, portais) |
| [STEAMOS.md](STEAMOS.md) | instalação e uso no Steam Deck |
| [AGENTS.md](AGENTS.md) | convenções de trabalho no repositório |
| [CONTRIBUTING.md](CONTRIBUTING.md) | como compilar, testar e enviar uma mudança |

Os documentos públicos existem em português e em inglês (`README.en.md`, `ARCHITECTURE.en.md`,
`CONTRIBUTING.en.md`); o `ROADMAP.md` é o histórico técnico interno, em português.

## Estado e contribuições

O projeto está em desenvolvimento ativo. Bugs e sugestões são bem-vindos pelas *issues*; antes de
abrir um *pull request*, leia [AGENTS.md](AGENTS.md) — as regras de teste de regressão e de prova de
mutação valem para qualquer mudança de comportamento.

## Licença

[MIT](LICENSE): use, copie, modifique e redistribua livremente, inclusive em projetos comerciais,
bastando manter o aviso de copyright. O software é fornecido sem garantias.
