package dev.yabai.ocr.desktop

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

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

        trayIcon = TrayIcon(bi, "Yabai OCR Desktop").apply {
            isImageAutoSize = true
            addActionListener { onCaptureRequested() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseReleased(e: MouseEvent) {
                    if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3 || e.button == MouseEvent.BUTTON1) {
                        showCustomTrayMenu(e.x, e.y)
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

    private fun showCustomTrayMenu(x: Int, y: Int) {
        SwingUtilities.invokeLater {
            val window = JWindow()
            window.isAlwaysOnTop = true

            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.background = Color(0x12, 0x14, 0x1A)
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0x2E, 0x33, 0x44), 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            )

            fun createTrayButton(text: String, action: () -> Unit): JButton {
                val btn = JButton(text)
                btn.font = Font("SansSerif", Font.PLAIN, 13)
                btn.foreground = Color.WHITE
                btn.background = Color(0x1B, 0x1E, 0x28)
                btn.isFocusPainted = false
                btn.isBorderPainted = false
                btn.isContentAreaFilled = true
                btn.alignmentX = Component.LEFT_ALIGNMENT
                btn.maximumSize = Dimension(220, 36)
                btn.horizontalAlignment = SwingConstants.LEFT
                btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

                btn.addMouseListener(object : MouseAdapter() {
                    override fun mouseEntered(e: MouseEvent) {
                        btn.background = Color(0x2E, 0x33, 0x44)
                    }
                    override fun mouseExited(e: MouseEvent) {
                        btn.background = Color(0x1B, 0x1E, 0x28)
                    }
                })

                btn.addActionListener {
                    window.dispose()
                    action()
                }
                return btn
            }

            panel.add(createTrayButton(" ✂️  Capturar Tela (OCR)") { onCaptureRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 4)))
            panel.add(createTrayButton(" 🔍  Busca Rápida de Dicionário") { onQuickDictRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 4)))
            panel.add(createTrayButton(" 📚  Gerenciar Dicionários") { onFullDictRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 4)))
            panel.add(createTrayButton(" ⚙️  Ajustes e Configurações") { onSettingsRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 6)))

            val sep = JSeparator()
            sep.maximumSize = Dimension(220, 1)
            sep.foreground = Color(0x2E, 0x33, 0x44)
            sep.background = Color(0x12, 0x14, 0x1A)
            panel.add(sep)
            panel.add(Box.createRigidArea(Dimension(0, 6)))

            panel.add(createTrayButton(" ✕  Sair") { onExitRequested() })

            window.contentPane = panel
            window.pack()

            // Position menu near tray icon (adjusting for screen edges)
            val screenSize = Toolkit.getDefaultToolkit().screenSize
            val winWidth = window.width
            val winHeight = window.height

            val posX = if (x + winWidth > screenSize.width) screenSize.width - winWidth - 10 else x
            val posY = if (y + winHeight > screenSize.height) y - winHeight else y

            window.location = Point(posX, posY)

            window.addWindowFocusListener(object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent?) {
                    window.dispose()
                }
            })

            window.isVisible = true
        }
    }

    fun remove() {
        trayIcon?.let {
            SystemTray.getSystemTray().remove(it)
        }
    }
}
