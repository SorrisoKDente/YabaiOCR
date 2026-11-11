package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings
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
    private var statusNotifier: StatusNotifierTray? = null
    private var activeTrayMenu: JPopupMenu? = null

    /** Returns true only when the icon was really added to the system tray. */
    fun setup(): Boolean {
        // KDE Wayland does not deliver XEmbed clicks to AWT (the tray icon is visible but inert).
        // Publish the standard StatusNotifierItem instead; ContextMenu(x,y) opens the same Swing
        // menu, so right-click keeps working. X11 and Windows keep the historical AWT path.
        if (trayBackendFor(DesktopPlatform.family, DesktopPlatform.session) == TrayBackend.SNI) {
            val notifier = StatusNotifierTray(
                onActivate = { SwingUtilities.invokeLater { onFullScreenCaptureRequested() } },
                onSecondaryActivate = { SwingUtilities.invokeLater { onRegionCaptureRequested() } },
                onContextMenu = { x, y -> showCustomTrayMenu(Point(x, y)) },
            )
            if (notifier.start()) {
                statusNotifier = notifier
                return true
            }
            println("StatusNotifierItem indisponível: ${notifier.lastError}")
        }

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
                        if (trayMenuOpensOn(e.button, e.isPopupTrigger, showMenuOnLeftClick)) {
                            showCustomTrayMenu(MouseInfo.getPointerInfo()?.location ?: Point(e.x, e.y))
                        }
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

        add(DesktopStrings.trayFullScreen) { onFullScreenCaptureRequested() }
        add(DesktopStrings.trayRegion) { onRegionCaptureRequested() }
        add(DesktopStrings.trayQuickDict) { onQuickDictRequested() }

        nativeHoverItem = MenuItem(hoverMenuItemLabel()).apply {
            addActionListener { onHoverRequested() }
        }
        nativeHoverItem?.let { menu.add(it) }

        menu.addSeparator()
        add(DesktopStrings.trayManageDicts) { onFullDictRequested() }
        add(DesktopStrings.traySettings) { onSettingsRequested() }
        menu.addSeparator()
        add(DesktopStrings.trayExit) { onExitRequested() }
        return menu
    }

    private fun hoverMenuItemLabel(): String =
        DesktopStrings.trayHover(isHoverActive())

    /** Keeps the native menu label truthful after the hover is toggled anywhere. */
    fun updateHover(active: Boolean) {
        SwingUtilities.invokeLater {
            nativeHoverItem?.label = DesktopStrings.trayHover(active)
        }
    }

    private fun showCustomTrayMenu(anchor: Point?) {
        SwingUtilities.invokeLater {
            // A second click on the SNI icon must replace the previous menu instead of leaving an
            // orphan popup that no click can dismiss.
            activeTrayMenu?.isVisible = false

            val popup = JPopupMenu().apply {
                background = Color(0x12, 0x14, 0x1A)
                border = BorderFactory.createLineBorder(Color(0x2E, 0x33, 0x44), 1)
                isOpaque = true
            }
            activeTrayMenu = popup

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

            panel.add(createTrayButton(DesktopStrings.trayFullScreen) { onFullScreenCaptureRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 2)))
            panel.add(createTrayButton(DesktopStrings.trayRegion) { onRegionCaptureRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 2)))
            panel.add(createTrayButton(DesktopStrings.trayQuickDict) { onQuickDictRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 2)))

            val hoverStatus = DesktopStrings.trayHover(isHoverActive())
            panel.add(createTrayButton(hoverStatus) { onHoverRequested() })

            panel.add(Box.createRigidArea(Dimension(0, 2)))
            panel.add(createTrayButton(DesktopStrings.trayManageDicts) { onFullDictRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 2)))
            panel.add(createTrayButton(DesktopStrings.traySettings) { onSettingsRequested() })
            panel.add(Box.createRigidArea(Dimension(0, 4)))

            val sep = JSeparator()
            sep.maximumSize = Dimension(230, 1)
            sep.foreground = Color(0x2E, 0x33, 0x44)
            sep.background = Color(0x12, 0x14, 0x1A)
            panel.add(sep)
            panel.add(Box.createRigidArea(Dimension(0, 4)))

            panel.add(createTrayButton(DesktopStrings.trayExit) { onExitRequested() })

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

            // Temporary dark invoker dialog. It stays 1x1 under the popup, but it must be focusable
            // and focused: on Wayland the JDK dismisses the popup on the invoker's focus loss only.
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
                    if (activeTrayMenu === popup) activeTrayMenu = null
                    invoker.dispose()
                }
                override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent?) {
                    if (activeTrayMenu === popup) activeTrayMenu = null
                    invoker.dispose()
                }
            })

            if (DesktopPlatform.session == SessionType.WAYLAND) {
                showTrayMenuInvoker(
                    invoker = JDialogTrayMenuInvoker(invoker),
                    showPopup = { popup.show(invoker, 0, 0) },
                    isMenuVisible = { popup.isVisible },
                    closeMenu = { popup.isVisible = false },
                )
            } else {
                popup.show(invoker, 0, 0)
            }
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
        statusNotifier?.updateStatus(text)
        SwingUtilities.invokeLater {
            trayIcon?.toolTip = if (text.isEmpty()) {
                "Yabai OCR Desktop"
            } else {
                "Yabai OCR — ${text.take(120)}"
            }
        }
    }

    fun remove() {
        statusNotifier?.stop()
        statusNotifier = null
        trayIcon?.let {
            SystemTray.getSystemTray().remove(it)
        }
        trayIcon = null
        nativeHoverItem = null
    }
}
