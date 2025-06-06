package newgame;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import javax.swing.*;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class BotUtils {
    public static final String MEMUC_PATH = "C:\\Program Files\\Microvirt\\MEmu\\memuc.exe";
    public static final String IMAGES_DIR = "images";
    public static final String SCREENSHOTS_DIR = "screenshots";
    private static boolean openCvLoaded = false;

    static {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            openCvLoaded = true;
            System.out.println("OpenCV loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load OpenCV: " + e.getMessage());
            showError("OpenCV Error", "Failed to load OpenCV library. Image recognition will not work.\n" + e.getMessage());
        }
    }

    public static boolean isOpenCvLoaded() {
        return openCvLoaded;
    }

    public static Point findImageOnScreenGray(String screenPath, String templatePath, double threshold) {
        if (!openCvLoaded) {
            System.err.println("OpenCV not loaded, cannot perform image matching");
            return null;
        }

        Mat screen = null;
        Mat template = null;
        Mat result = null;
        
        try {
            String fullTemplatePath = getImagePath(templatePath);
            if (!Files.exists(Paths.get(fullTemplatePath))) {
                System.err.println("Template image not found: " + fullTemplatePath);
                return null;
            }

            if (!Files.exists(Paths.get(screenPath))) {
                System.err.println("Screen capture not found: " + screenPath);
                return null;
            }

            screen = Imgcodecs.imread(screenPath, Imgcodecs.IMREAD_GRAYSCALE);
            template = Imgcodecs.imread(fullTemplatePath, Imgcodecs.IMREAD_GRAYSCALE);

            if (screen.empty() || template.empty()) {
                System.err.println("Failed to load images for " + templatePath);
                return null;
            }

            int resultCols = screen.cols() - template.cols() + 1;
            int resultRows = screen.rows() - template.rows() + 1;
            if (resultCols < 1 || resultRows < 1) {
                System.err.println("Template too large for screen: " + templatePath);
                return null;
            }

            result = new Mat(resultRows, resultCols, CvType.CV_32FC1);
            Imgproc.matchTemplate(screen, template, result, Imgproc.TM_CCOEFF_NORMED);

            Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
            
            System.out.println("Template matching confidence: " + String.format("%.3f", mmr.maxVal) + 
                             " (threshold: " + threshold + ") for " + templatePath);
            
            if (mmr.maxVal >= threshold) {
                Point center = new Point(
                    (int)(mmr.maxLoc.x + template.cols()/2.0),
                    (int)(mmr.maxLoc.y + template.rows()/2.0)
                );
                System.out.println("Found template at: " + center + " for " + templatePath);
                return center;
            } else {
                System.out.println("Template not found - confidence too low for " + templatePath);
            }
        } catch (Exception e) {
            System.err.println("Image matching error for " + templatePath + ": " + e.getMessage());
        } finally {
            if (screen != null) screen.release();
            if (template != null) template.release();
            if (result != null) result.release();
        }
        return null;
    }

    private static String getImagePath(String imageName) {
        String imagesPath = Paths.get(IMAGES_DIR, imageName).toString();
        if (Files.exists(Paths.get(imagesPath))) {
            return imagesPath;
        }
        if (Files.exists(Paths.get(imageName))) {
            return imageName;
        }
        return imagesPath;
    }

    public static boolean takeMenuScreenshotLegacy(int index, String savePath) {
        try {
            createDirectoryIfNeeded(SCREENSHOTS_DIR);
            
            ProcessBuilder captureBuilder = new ProcessBuilder(
                MEMUC_PATH, "adb", "-i", String.valueOf(index),
                "shell", "screencap", "-p", "/sdcard/screen.png"
            );
            Process captureProcess = captureBuilder.start();
            
            boolean captureSuccess = captureProcess.waitFor(10, TimeUnit.SECONDS) && 
                                   captureProcess.exitValue() == 0;
            
            if (!captureSuccess) {
                System.err.println("Screenshot capture failed for instance " + index);
                return false;
            }

            Thread.sleep(500);

            ProcessBuilder pullBuilder = new ProcessBuilder(
                MEMUC_PATH, "adb", "-i", String.valueOf(index),
                "pull", "/sdcard/screen.png", savePath
            );
            Process pullProcess = pullBuilder.start();
            
            boolean pullSuccess = pullProcess.waitFor(10, TimeUnit.SECONDS) && 
                                pullProcess.exitValue() == 0;
            
            if (!pullSuccess) {
                System.err.println("Screenshot pull failed for instance " + index);
                return false;
            }

            File screenshotFile = new File(savePath);
            boolean success = screenshotFile.exists() && screenshotFile.length() > 0;
            
            if (success) {
                System.out.println("Screenshot saved: " + savePath + " (" + screenshotFile.length() + " bytes)");
            }
            
            return success;
        } catch (IOException | InterruptedException e) {
            System.err.println("Screenshot error: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    public static boolean clickMenu(int index, Point pt) {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                MEMUC_PATH, "adb", "-i", String.valueOf(index),
                "shell", "input", "tap",
                String.valueOf((int)pt.getX()),
                String.valueOf((int)pt.getY())
            );
            Process process = builder.start();
            
            boolean success = process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
            
            if (success) {
                System.out.println("Clicked at " + pt + " on instance " + index);
            }
            
            return success;
        } catch (IOException | InterruptedException e) {
            System.err.println("Click error: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    public static boolean delay(int millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static void createDirectoryIfNeeded(String dirPath) {
        try {
            Files.createDirectories(Paths.get(dirPath));
        } catch (IOException e) {
            System.err.println("Failed to create directory " + dirPath + ": " + e.getMessage());
        }
    }

    public static boolean isMemucInstalled() {
        return Files.exists(Paths.get(MEMUC_PATH));
    }

    public static void initializeDirectories() {
        createDirectoryIfNeeded(IMAGES_DIR);
        createDirectoryIfNeeded(SCREENSHOTS_DIR);
    }

    private static void showError(String title, String message) {
        SwingUtilities.invokeLater(() -> 
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE)
        );
    }
}