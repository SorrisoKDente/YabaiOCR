---
name: yabai-docs
description: Atualizar o README do usuário e o ROADMAP de decisões do Yabai OCR com causa raiz, tabela de mutações e evidência E2E no formato que o projeto já usa. Use ao concluir uma correção, mudar comportamento visível, adicionar ajuste/atalho ou registrar uma decisão.
---

# Documentação do Yabai OCR

## Dois documentos, dois leitores

| arquivo | leitor | conteúdo |
| --- | --- | --- |
| `README.md` | quem usa | instalar, usar, atalhos, ajustes, limitações, compilar/testar |
| `docs/ROADMAP.md` | quem mantém | por que mudou, causa raiz, testes, mutações, evidência, decisões |

Mudou comportamento visível? Atualize os dois **na mesma tarefa**. O README não pode prometer o que
o código não faz; o ROADMAP não pode esconder uma regressão atrás de "melhorias".

## README

- Escrito em pt-BR, direto, na segunda pessoa («aperte», «clique»).
- Use os rótulos reais da UI entre «aspas»: «🖥️ Capturar Tela Inteira (OCR)», «✂️ Capturar Área
  (OCR)», «Ajustes → Atalhos Globais».
- Toda tecla/atalho em `código`. Distinga **padrão** do que está configurado na máquina do usuário
  quando isso importar (ex.: `global_hotkey` é `Insert` nesta máquina, mas o padrão é
  `Ctrl + Shift + O`).
- Comandos em bloco `powershell`, com `.\gradlew.bat` no Windows.
- Documente a limitação junto do recurso: ex. «o seletor captura o monitor principal; o hover usa o
  monitor sob o ponteiro».
- Não despeje detalhes internos (nomes de função, protobuf) no README; isso é ROADMAP.
- Se o total de testes mudou, atualize os números citados em README/AGENTS.

## ROADMAP

### Estrutura real

O arquivo tem seções numeradas por dificuldade/tema e itens com prefixo próprio:

| prefixo | seção |
| --- | --- |
| `B` | 1. Correções de bug |
| `F` | 2. Fácil |
| `M` | 3. Médio |
| `D` | 4. Difícil (pesquisa) |
| `I` | 5. Pedidos novos |
| `N` | 6. Segunda ronda |
| `R` | 7+ rondas seguintes |

**Não invente um `D` para tudo.** Use o próximo número livre da seção que corresponde ao item e
nunca renumere os existentes. Cada seção tem uma tabela no topo (`| # | Estado | Item | Dificuldade |`)
e um detalhamento `### <ID> — <título>  `[x]` (dd/MM/yyyy)`. Adicione nos dois lugares.

Estados: `[ ]` pendente, `[x]` pronto, `[~]` parcial, `[-]` descartado. Data no formato
`dd/MM/yyyy`.

### O que uma entrada precisa provar

1. **Contexto** — o que existia, o que doía, por que a tentativa anterior não bastou.
2. **Causa raiz** — uma frase concreta (ex.: «`createScreenCapture` devolvia a variante de usuário;
   desenhá-la num canvas 1,5× reamostrava cada glifo»).
3. **Correção** — bullets do que mudou, com nomes de arquivo/função quando ajudam a manter.
4. **Testes** — total e quantos são novos, com as classes. Use o número real do Gradle.
5. **Tabela de mutações** — cada regra que dói: mutação → teste que acusa.
6. **Evidência E2E** — passos e números: regiões comparadas, `0 de N px`, coordenadas, tempos.
   Se não houve E2E, diga isso e por quê; não invente.
7. **Limitação conhecida** — o que continua fora do escopo.

Não registre logs transitórios, caminhos de backup, `settings.properties` do usuário nem nomes de
sondas temporárias. O ROADMAP é permanente.

### Exemplo de tabela de mutação

```markdown
| mutação | teste que acusa |
| --- | --- |
| `toCanvas` com escala 1:1 | `a box is scaled from the image to the canvas` |
| `hit` devolve a primeira caixa, não a menor | `the smallest containing box wins…` |
```

Cada linha precisa ter sido **executada**: mutou, o teste nomeado falhou, restaurou, passou.

## Fechamento

- [ ] README descreve o comportamento atual (atalhos, ajustes, limitações).
- [ ] ROADMAP tem item na tabela e detalhamento com data.
- [ ] Causa raiz explícita, não só «corrigido».
- [ ] Total de testes e tabela de mutação conferem com a execução real.
- [ ] Evidência E2E com números, ou ausência justificada.
- [ ] Nada de credenciais, caminhos pessoais ou lixo de depuração.
- [ ] Linhas quebradas em ~100 colunas, como o resto do arquivo.

Use
`.github/skills/yabai-docs/references/entry-template.md` como ponto de partida.
