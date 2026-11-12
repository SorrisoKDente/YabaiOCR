# Contributing to Yabai OCR

[Português](CONTRIBUTING.md) | **English**

Thanks for your interest. The project is open and *pull requests* are welcome.

## Before you start

Read [AGENTS.md](AGENTS.md): those are the rules that apply to any change, human or automated. To
find the right file, use [ARCHITECTURE.en.md](ARCHITECTURE.en.md).

## Building and testing

Windows/PowerShell, from the repository root:

```powershell
.\gradlew.bat :core:test              # pure Kotlin/JVM core
.\gradlew.bat :desktop:test           # desktop (Compose + JUnit)
.\gradlew.bat :app:testDebugUnitTest  # Android with Robolectric, no device
.\gradlew.bat :desktop:run            # opens the app
```

Linux and Steam Deck: see [LINUX.md](LINUX.md) and [STEAMOS.md](STEAMOS.md).

## Non-negotiable rules

- **OCR accuracy:** the recognized text is the data. Never "fix" a recognized character unless that
  is the requested, tested behavior.
- **Regression test + mutation proof:** every fix ships a test that fails without the fix and passes
  with it, and reintroducing the root cause must break that test.
- **Smallest possible change**, without reformatting unrelated files.
- **Every UI string exists in all available languages** (today pt-BR and en) and the user picks the
  language in Settings. Comments are in English.
- **Documentation in the same task:** behavior changed → README/ROADMAP; file structure changed →
  ARCHITECTURE.md (in both languages).

## Sending the change

1. One subject per *pull request*, with a sentence explaining the root cause.
2. Describe in the PR the commands you ran and the suite numbers.
3. If the change is visual (capture, picker, crop, hover), say what you validated in the real app and
   what you did not.

## License

By contributing you agree that your contribution is distributed under the project's [MIT](LICENSE)
license.
