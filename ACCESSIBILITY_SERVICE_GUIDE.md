# Guia de Implementação do Serviço de Acessibilidade (Yabai Accessibility Service)

Este documento descreve detalhadamente como o **Serviço de Acessibilidade** foi implementado no Yabai OCR e o que é necessário para reativá-lo caso decida implementá-lo novamente no futuro.

---

### Por que foi removido?
Quando um aplicativo que solicita `BIND_ACCESSIBILITY_SERVICE` é instalado manualmente via APK (sideloaded) sem estar na Google Play Store, o **Google Play Protect** exibe um alerta bloqueando a instalação ("O app foi bloqueado para proteger seu dispositivo"). Embora seja seguro para uso pessoal, isso dificulta a distribuição e atualização local do APK.

---

### Passo a Passo para Reimplementar no Futuro

Se desejar reativar o botão de acessibilidade na barra de navegação do sistema para iniciar o OCR de qualquer tela, siga os passos abaixo:

#### 1. Criar o arquivo de configuração XML
Crie o arquivo `app/src/main/res/xml/accessibility_service_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRequestAccessibilityButton"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100" />
```

#### 2. Criar a classe do Serviço
Crie `app/src/main/java/dev/yabai/ocr/service/YabaiAccessibilityService.kt`:
```kotlin
package dev.yabai.ocr.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityButtonController
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dev.yabai.ocr.MainActivity

class YabaiAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Não utilizado para rastreamento de eventos
    }

    override fun onInterrupt() {
        // Obrigatório pela classe AccessibilityService
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        accessibilityButtonController.registerAccessibilityButtonCallback(
            object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) {
                    launchMainActivity()
                }
            }
        )
    }

    private fun launchMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }
}
```

#### 3. Declarar no AndroidManifest.xml
Adicione a declaração do serviço dentro da tag `<application>` em `app/src/main/AndroidManifest.xml`:
```xml
        <!-- Accessibility Service for floating OCR button shortcut -->
        <service
            android:name=".service.YabaiAccessibilityService"
            android:exported="true"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
```

#### 4. Adicionar as Strings em `strings.xml`
Em `app/src/main/res/values/strings.xml`:
```xml
    <string name="accessibility_service_description">Permite iniciar o Yabai OCR rapidamente usando o botão de acessibilidade na barra de navegação do sistema.</string>
    <string name="shortcut_accessibility_title">Botão de Acessibilidade</string>
    <string name="shortcut_accessibility_desc">Habilite o serviço de acessibilidade para adicionar um botão na barra de navegação do celular.</string>
```

#### 5. Adicionar a UI em `activity_main.xml` e lógica em `MainActivity.kt`
- Adicione na tela de ajustes um card/botão para abrir `Settings.ACTION_ACCESSIBILITY_SETTINGS`.
- Implemente a verificação se o serviço está ativo usando `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
