package dev.yabai.ocr.desktop

import java.awt.Color
import java.awt.Font
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JDialog
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.SwingUtilities

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
        val bi = AppIconGenerator.createTrayImage(16)

        val popupMenu = createDarkPopupMenu()

        trayIcon = TrayIcon(bi, "Yabai OCR Desktop").apply {
            isImageAutoSize = true
            addActionListener { onCaptureRequested() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseReleased(e: MouseEvent) {
                    if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                        showPopupMenu(popupMenu, e.x, e.y)
                    }
                }
                override fun mousePressed(e: MouseEvent) {
                    if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                        showPopupMenu(popupMenu, e.x, e.y)
                    }
                }
            })
        }

        try {
            tray.add(trayIcon)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createDarkPopupMenu(): JPopupMenu {
        val popup = JPopupMenu().apply {
            background = Color(0x1B, 0x1E, 0x28)
            border = BorderFactory.createLineBorder(Color(0x2E, 0x33, 0x44), 1)
        }

        val font = Font("SansSerif", Font.PLAIN, 12)

        fun createItem(text: String, action: () -> Unit): JMenuItem {
            return JMenuItem(text).apply {
                setFont(font)
                foreground = Color.WHITE
                background = Color(0x1B, 0x1E, 0x28)
                border = BorderFactory.createEmptyBorder(6, 12, 6, 12)
                isOpaque = true
                addActionListener { action() }
            }
        }

        popup.add(createItem("✂️ Capturar Tela (OCR)") { onCaptureRequested() })
        popup.add(createItem("🔍 Busca Rápida de Dicionário") { onQuickDictRequested() })
        popup.add(createItem("📚 Gerenciar Dicionários") { onFullDictRequested() })
        popup.add(createItem("⚙️ Ajustes e Configurações") { onSettingsRequested() })

        popup.add(JSeparator().apply {
            foreground = Color(0x2E, 0x33, 0x44)
            background = Color(0x1B, 0x1E, 0x28)
        })

        popup.add(createItem("✕ Sair") { onExitRequested() })

        return popup
    }

    private fun showPopupMenu(popup: JPopupMenu, x: Int, y: Int) {
        SwingUtilities.invokeLater {
            val dialog = JDialog().apply {
                isUndecorated = true
                setSize(1, 1)
                location = java.awt.Point(x, y)
                isAlwaysOnTop = true
                isVisible = true
            }
            popup.show(dialog, 0, 0)
            popup.addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
                override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent?) {}
                override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent?) {
                    dialog.dispose()
                }
                override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent?) {
                    dialog.dispose()
                }
            })
        }
    }

    fun remove() {
        trayIcon?.let {
            SystemTray.getSystemTray().remove(it)
        }
    }
}
