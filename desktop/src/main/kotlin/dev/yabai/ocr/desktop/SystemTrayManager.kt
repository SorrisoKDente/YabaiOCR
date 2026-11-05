package dev.yabai.ocr.desktop

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class SystemTrayManager(
    private val onFullScreenCaptureRequested: () -> Unit,
    private val onRegionCaptureRequested: () -> Unit,
    private val onQuickDictRequested: () -> Unit,
    private val onFullDictRequested: () -> Unit,
    private val onSettingsRequested: () -> Unit,
    private val onHoverRequested: () -> Unit,
    private val isHoverActive: () -> Boolean,
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
            addActionListener { onFullScreenCaptureRequested() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseReleased(e: MouseEvent) {
                    if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3 || e.button == MouseEvent.BUTTON1) {
                        // TrayIcon mouse coordinates are icon-relative on Windows; the pointer is
                        // what the user sees, and it is already in screen coordinates.
                        val anchor = MouseInfo.getPointerInfo()?.location ?: Point(e.x, e.y)
                        showCustomTrayMenu(anchor)
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

    private fun showCustomTrayMenu(anchor: Point) {
        SwingUtilities.invokeLater {
            val popup = JPopupMenu().apply {
                background = Color(0x12, 0x14, 0x1A)
                border = BorderFactory.createLineBorder(Color(0x2E, 0x33, 0x44), 1)
                isOpaque = true
            }

            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.background = Color(0x12, 0x14, 0x1A)
            panel.border = BorderFactory.createEmptyBorder(6, 6, 6, 6)

            fun createTrayButton(text: String, action: () -> Unit): JButton {
                val btn = JButton(text)
                btn.font = Font("SansSerif", Font.PLAIN, 13)
                btn.foreground = Color.WHITE
                btn.background = Color(0x1B, 0x1E, 0x28)
                btn.isFocusPainted = false
                btn.isBorderPainted = false
                btn.isContentAreaFilled = true
                btn.alignmentX = Component.LEFT_ALIGNMENT
                btn.maximumSize = Dimension(230, 36)
                btn.horizontalAlignment = SwingConstants.LEFT
                btn.margin = Insets(0, 0, 0, 0)
                btn.border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
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
                    popup.isVisible = false
                    action()
                }
                return btn
            }

            panel.add(createTrayButton("🖥️  Capturar Tela Inteira (OCR)") { onFullScreenCaptureRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 2)))
            panel.add(createTrayButton("✂️  Capturar Área (OCR)") { onRegionCaptureRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 2)))
            panel.add(createTrayButton("🔍  Busca Rápida de Dicionário") { onQuickDictRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 2)))

            val hoverStatus = if (isHoverActive()) "🌐  Modo Hover [Ativo]" else "🌐  Modo Hover [Inativo]"
            panel.add(createTrayButton(hoverStatus) { onHoverRequested() })

            panel.add(Box.createRigidArea(Dimension(0, 2)))
            panel.add(createTrayButton("📚  Gerenciar Dicionários") { onFullDictRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 2)))
            panel.add(createTrayButton("⚙️  Ajustes e Configurações") { onSettingsRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 4)))

            val sep = JSeparator()
            sep.maximumSize = Dimension(230, 1)
            sep.foreground = Color(0x2E, 0x33, 0x44)
            sep.background = Color(0x12, 0x14, 0x1A)
            panel.add(sep)
            panel.add(Box.createRigidArea(Dimension(0, 4)))

            panel.add(createTrayButton("✕  Sair") { onExitRequested() })

            popup.add(panel)

            // Measure menu preferred size
            val popSize = popup.preferredSize
            val winWidth = popSize.width
            val winHeight = popSize.height

            // Work area of the monitor under the cursor, taskbar excluded.
            val gc = screenConfigurationAt(anchor)
            val screenBounds = gc.bounds
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
            val workArea = Rectangle(
                screenBounds.x + insets.left,
                screenBounds.y + insets.top,
                screenBounds.width - insets.left - insets.right,
                screenBounds.height - insets.top - insets.bottom,
            )

            val position = placeTrayMenu(anchor, winWidth, winHeight, workArea)

            // Temporary dark invisible invoker dialog
            val invoker = JDialog().apply {
                isUndecorated = true
                isAlwaysOnTop = true
                setSize(1, 1)
                location = position
                contentPane.background = Color(0x12, 0x14, 0x1A)
                background = Color(0x12, 0x14, 0x1A)
                isVisible = true
            }

            popup.addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
                override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent?) {}
                override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent?) {
                    invoker.dispose()
                }
                override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent?) {
                    invoker.dispose()
                }
            })

            popup.show(invoker, 0, 0)
        }
    }

    /**
     * The monitor under [point], so a tray menu on a secondary taskbar stays on that monitor.
     * Falls back to the default screen when the point is outside every device.
     */
    private fun screenConfigurationAt(point: Point): GraphicsConfiguration {
        val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        return environment.screenDevices
            .firstOrNull { it.defaultConfiguration.bounds.contains(point) }
            ?.defaultConfiguration
            ?: environment.defaultScreenDevice.defaultConfiguration
    }

    /** Updates the tooltip of the tray icon, which is the only always-visible hover feedback. */
    fun setStatus(message: String) {
        val text = message.trim()
        SwingUtilities.invokeLater {
            trayIcon?.toolTip = if (text.isEmpty()) {
                "Yabai OCR Desktop"
            } else {
                "Yabai OCR — ${text.take(120)}"
            }
        }
    }

    fun remove() {
        trayIcon?.let {
            SystemTray.getSystemTray().remove(it)
        }
        trayIcon = null
    }
}
