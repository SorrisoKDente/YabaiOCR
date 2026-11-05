import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/**
 * Template de sonda ponta a ponta do Yabai OCR Desktop.
 *
 * Copy to a temp folder, adjust the hotkeys/click target for the machine, then:
 *   javac -encoding UTF-8 RobotProbe.java
 *   java -Dfile.encoding=UTF-8 -Dout="C:\...\probe" RobotProbe
 *
 * The app must already be running with --minimized (otherwise a stray click can land on the
 * Settings window and rebind the global hotkeys). Screenshots use user-space coordinates, the
 * same ones Robot uses for mouse/keyboard. Park the pointer on an empty area before each shot:
 * the picker highlights the box under the cursor, which changes pixels on purpose.
 *
 * Adjust:
 *  - HOTKEY_FULL_SCREEN / HOTKEY_REGION to the values in %USERPROFILE%\.yabai_ocr\settings.properties
 *  - CLICK_X / CLICK_Y to a point inside a recognized paragraph box
 *  - SLEEP_* to give OCR/network time (Lens can take several seconds)
 */
public final class RobotProbe {

    // Defaults documented in README. This machine may use VK_INSERT for the full-screen capture.
    private static final int[] HOTKEY_FULL_SCREEN = { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_O };
    private static final int[] HOTKEY_REGION = { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_A };

    private static final int PARK_X = 1150;
    private static final int PARK_Y = 500;
    private static final int CLICK_X = 600;
    private static final int CLICK_Y = 300;

    private static final long SLEEP_AFTER_HOTKEY_MS = 7000;
    private static final long SLEEP_AFTER_CLICK_MS = 5000;
    private static final long SLEEP_AFTER_REGION_MS = 2500;
    private static final long SLEEP_PARK_MS = 900;

    private static Robot robot;
    private static Path outDir;

    private RobotProbe() {
    }

    public static void main(String[] args) throws Exception {
        String configured = System.getProperty("out");
        if (configured == null || configured.isEmpty()) {
            configured = System.getProperty("java.io.tmpdir") + File.separator + "yabai-probe";
        }
        outDir = Paths.get(configured).toAbsolutePath();
        Files.createDirectories(outDir);

        robot = new Robot();
        robot.setAutoDelay(40);

        parkPointer();
        sleep(600);
        shot("0-antes.png");

        keys(HOTKEY_FULL_SCREEN);
        System.out.println("atalho da tela inteira enviado; aguardando OCR");
        sleep(SLEEP_AFTER_HOTKEY_MS);
        parkPointer();
        sleep(SLEEP_PARK_MS);
        shot("1-seletor.png");

        click(CLICK_X, CLICK_Y);
        sleep(SLEEP_AFTER_CLICK_MS);
        shot("2-resultado.png");

        keys(KeyEvent.VK_ESCAPE);
        sleep(1200);

        keys(HOTKEY_REGION);
        System.out.println("atalho do recorte enviado; aguardando overlay");
        sleep(SLEEP_AFTER_REGION_MS);
        shot("3-recorte.png");

        keys(KeyEvent.VK_ESCAPE);
        sleep(800);
        System.out.println("capturas em " + outDir);
    }

    private static void parkPointer() {
        robot.mouseMove(PARK_X, PARK_Y);
    }

    private static void shot(String name) throws Exception {
        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage image = robot.createScreenCapture(screen);
        Path file = outDir.resolve(name);
        ImageIO.write(image, "png", file.toFile());
        System.out.println("screenshot=" + file + " (" + image.getWidth() + "x" + image.getHeight() + ")");
    }

    private static void keys(int... codes) {
        for (int code : codes) {
            robot.keyPress(code);
        }
        sleep(60);
        for (int i = codes.length - 1; i >= 0; i--) {
            robot.keyRelease(codes[i]);
        }
    }

    private static void click(int x, int y) {
        robot.mouseMove(x, y);
        sleep(150);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        sleep(80);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
