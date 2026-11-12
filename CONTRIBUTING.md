# Contribuindo com o Yabai OCR

Obrigado pelo interesse. O projeto é aberto e *pull requests* são bem-vindos.

## Antes de começar

Leia [AGENTS.md](AGENTS.md): são as regras que valem para qualquer mudança, humana ou automatizada.
Para achar o arquivo certo, use [ARCHITECTURE.md](ARCHITECTURE.md).

## Compilar e testar

Windows/PowerShell, na raiz:

```powershell
.\gradlew.bat :core:test              # núcleo em Kotlin/JVM puro
.\gradlew.bat :desktop:test           # desktop (Compose + JUnit)
.\gradlew.bat :app:testDebugUnitTest  # Android com Robolectric, sem aparelho
.\gradlew.bat :desktop:run            # abre o app
```

Linux e Steam Deck: veja [LINUX.md](LINUX.md) e [STEAMOS.md](STEAMOS.md).

## Regras que não são negociáveis

- **Precisão de OCR:** o texto reconhecido é o dado. Nunca "corrija" um caractere reconhecido sem
  que isso seja o comportamento pedido e testado.
- **Teste de regressão + prova de mutação:** toda correção traz um teste que falha sem a correção e
  passa com ela, e a causa raiz precisa derrubar esse teste quando reintroduzida.
- **Menor mudança possível**, sem reformatar arquivos alheios.
- **UI em pt-BR**, comentários de código em inglês.
- **Documentação na mesma tarefa:** mudou comportamento → README/ROADMAP; mudou a estrutura de
  arquivos → ARCHITECTURE.md.

## Enviando a mudança

1. Um assunto por *pull request*, com uma frase explicando a causa raiz.
2. Descreva no PR os comandos rodados e os números das suítes.
3. Se a mudança for visual (captura, picker, recorte, hover), diga o que foi validado no app real e o
   que não foi.

## Licença

Ao contribuir você concorda que sua contribuição é distribuída sob a licença [MIT](LICENSE) do
projeto.
