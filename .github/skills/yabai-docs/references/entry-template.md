# <ID> — <título curto>  `[x]` (<dd/MM/yyyy>)

<Contexto: o que existia, o que o usuário via, desde quando. Se uma correção anterior falhou,
explique por que ela não bastou — sem reescrever a história, só o suficiente para entender.>

- **Causa raiz.** <Uma frase concreta. Onde o código fazia a coisa errada e por quê.>
- **Correção.** <O que mudou, em bullets. Cite arquivo/função quando ajudar a manter.>
  - <...>
- **Arquivos.** `<caminho>` · `<caminho>` (opcional)

Testes: **<N> no total**, com <M> novos — `<ClasseTest>` (<n>), `<ClasseTest>` (<n>). As regras que
mais doem foram conferidas por mutação, cada uma falhando e voltando a passar ao restaurar:

| mutação | teste que acusa |
| --- | --- |
| <descrição da mutação> | `<nome exato do teste>` |
| <descrição da mutação> | `<nome exato do teste>` |

Ponta a ponta, com o app rodando: <passos>. <Números: «0 diferenças em 25.200 px (página branca);
0 em 9.200 px (xadrez de 1 px)», coordenadas clicadas, tempos.> <Se não houve E2E, diga o motivo.>

Limitação conhecida: <o que continua fora do escopo. Remova a linha se não houver.>
