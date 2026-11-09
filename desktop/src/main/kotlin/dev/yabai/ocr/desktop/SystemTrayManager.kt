package dev.yabai.ocr.desktop

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

internal class SystemTrayManager(
    private val onFullScreenCaptureRequested: () -> Unit,
    private val onRegionCaptureRequested: () -> Unit,
    private val onQuickDictRequested: () -> Unit,
    private val onFullDictRequested: () -> Unit,
    private val onSettingsRequested: () -> Unit,
    private val onHoverRequested: () -> Unit,
    private val isHoverActive: () -> Boolean,
    private val onExitRequested: () -> Unit,
    private val menuKind: TrayMenuKind = trayMenuKindFor(DesktopPlatform.family, DesktopPlatform.session),
    private val showMenuOnLeftClick: Boolean = !DesktopPlatform.isLinux,
) {
    private var trayIcon: TrayIcon? = null
    private var nativeHoverItem: MenuItem? = null

    /** Returns true only when the icon was really added to the system tray. */
    fun setup(): Boolean {
        if (!SystemTray.isSupported()) {
            println("SystemTray não é suportado no sistema.")
            return false
        }

        val tray = SystemTray.getSystemTray()
        val bi = AppIconGenerator.createTrayImage(16)

        val icon = TrayIcon(bi, "Yabai OCR Desktop").apply {
            isImageAutoSize = true
            addActionListener { onFullScreenCaptureRequested() }
            if (menuKind == TrayMenuKind.CUSTOM) {
                addMouseListener(object : MouseAdapter() {
                    override fun mouseReleased(e: MouseEvent) {
                        if (!trayMenuOpensOn(e.button, e.isPopupTrigger, showMenuOnLeftClick)) return
                        // On Wayland AWT runs through XWayland and may or may not report a global
                        // pointer; when it does not, the menu opens in the work-area corner instead
                        // of using the icon-relative event coordinates, which are not screen ones.
                        showCustomTrayMenu(MouseInfo.getPointerInfo()?.location)
                    }
                })
            }
        }

        if (menuKind == TrayMenuKind.NATIVE) {
            icon.popupMenu = createNativeMenu()
        }

        return try {
            tray.add(icon)
            trayIcon = icon
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Native menu for Linux: the desktop positions it at the icon, so it works without a global
     * pointer. The left click still performs the full-screen capture, as on Windows.
     */
    private fun createNativeMenu(): PopupMenu {
        val menu = PopupMenu()

        fun add(label: String, action: () -> Unit) {
            menu.add(MenuItem(label).apply { addActionListener { action() } })
        }

        add("🖥️  Capturar Tela Inteira (OCR)") { onFullScreenCaptureRequested() }
        add("✂️  Capturar Área (OCR)") { onRegionCaptureRequested() }
        add("🔍  Busca Rápida de Dicionário") { onQuickDictRequested() }

        nativeHoverItem = MenuItem(hoverMenuItemLabel()).apply {
            addActionListener { onHoverRequested() }
        }
        nativeHoverItem?.let { menu.add(it) }

        menu.addSeparator()
        add("📚  Gerenciar Dicionários") { onFullDictRequested() }
        add("⚙️  Ajustes e Configurações") { onSettingsRequested() }
        menu.addSeparator()
        add("✕  Sair") { onExitRequested() }
        return menu
    }

    private fun hoverMenuItemLabel(): String =
        if (isHoverActive()) "🌐  Modo Hover [Ativo]" else "🌐  Modo Hover [Inativo]"

    /** Keeps the native menu label truthful after the hover is toggled anywhere. */
    fun updateHover(active: Boolean) {
        SwingUtilities.invokeLater {
            nativeHoverItem?.label =
                if (active) "🌐  Modo Hover [Ativo]" else "🌐  Modo Hover [Inativo]"
        }
    }

    private fun showCustomTrayMenu(anchor: Point?) {
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

            // Work area of the monitor under the cursor, taskbar excluded. Without a global pointer
            // (native Wayland) the menu falls back to the bottom-right of the default monitor.
            val gc = anchor?.let { screenConfigurationAt(it) }
                ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
            val screenBounds = gc.bounds
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
            val workArea = Rectangle(
                screenBounds.x + insets.left,
                screenBounds.y + insets.top,
                screenBounds.width - insets.left - insets.right,
                screenBounds.height - insets.top - insets.bottom,
            )

            val anchorPoint = anchor ?: trayMenuFallbackAnchor(workArea)
            val position = placeTrayMenu(anchorPoint, winWidth, winHeight, workArea)

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
        nativeHoverItem = null
    }
}
