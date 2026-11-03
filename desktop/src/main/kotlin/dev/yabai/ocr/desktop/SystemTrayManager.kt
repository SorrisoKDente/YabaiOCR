package dev.yabai.ocr.desktop

import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon

class SystemTrayManager(
    private val onCaptureRequested: () -> Unit,
    private val onQuickDictRequested: () -> Unit,
    private val onFullDictRequested: () -> Unit,
    private val onSettingsRequested: () -> Unit,
    private val onExitRequested: () -> Unit,
) {
    private var trayIcon: TrayIcon? = null

    fun setup() {
        if (!SystemTray.isSupported()) {
            println("SystemTray não é suportado no sistema.")
            return
        }

        val tray = SystemTray.getSystemTray()
        val popup = PopupMenu()

        val itemCapture = MenuItem("Capturar Tela (OCR)")
        itemCapture.addActionListener { onCaptureRequested() }
        popup.add(itemCapture)

        val itemQuickDict = MenuItem("Busca Rapida de Dicionario")
        itemQuickDict.addActionListener { onQuickDictRequested() }
        popup.add(itemQuickDict)

        val itemFullDict = MenuItem("Gerenciar Dicionarios")
        itemFullDict.addActionListener { onFullDictRequested() }
        popup.add(itemFullDict)

        val itemSettings = MenuItem("Ajustes e Configuracoes")
        itemSettings.addActionListener { onSettingsRequested() }
        popup.add(itemSettings)

        popup.addSeparator()

        val itemExit = MenuItem("Sair")
        itemExit.addActionListener { onExitRequested() }
        popup.add(itemExit)

        val bi = AppIconGenerator.createTrayImage(16)

        trayIcon = TrayIcon(bi, "Yabai OCR Desktop", popup).apply {
            isImageAutoSize = true
            addActionListener { onCaptureRequested() }
        }

        try {
            tray.add(trayIcon)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun remove() {
        trayIcon?.let {
            SystemTray.getSystemTray().remove(it)
        }
    }
}
