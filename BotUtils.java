package newgame;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.awt.Point;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class BotUtils {
    public static final String MEMUC_PATH = "C:\\Program Files\\Microvirt\\MEmu\\memuc.exe";
    public static final String SCREENSHOTS_DIR = "screenshots";
    public static boolean openCvLoaded = false;

    static {
        try {
            // Try to load OpenCV using the standard method
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            openCvLoaded = true;
            System.out.println("OpenCV loaded successfully");
        } catch (Exception | UnsatisfiedLinkError e) {
            System.err.println("Failed to load OpenCV: " + e.getMessage());
            System.err.println("OpenCV features will be disabled. Image matching will not work.");
            System.err.println("To enable OpenCV: Install OpenCV for Java and add it to your path");
            openCvLoaded = false;
        }
    }

    public static void init() {
        System.out.println("=== MEmu Instance Manager Starting ===");
        System.out.println("=== Cleaning up corrupted screenshots ===");
        
        // Clean up any corrupted screenshot files
        cleanupCorruptedScreenshots();
        
        System.out.println("=== Cleanup complete ===");
        
        // Setup image directory
        setupImageDirectory();
    }

    private static void cleanupCorruptedScreenshots() {
        try {
            File screenshotsDir = new File(SCREENSHOTS_DIR);
            if (screenshotsDir.exists()) {
                File[] files = screenshotsDir.listFiles((dir, name) -> name.endsWith(".png"));
                if (files != null) {
                    for (File file : files) {
                        if (file.length() < 1000) { // Files smaller than 1KB are likely corrupted
                            file.delete();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    private static void setupImageDirectory() {
        System.out.println("=== Image Directory Setup ===");
        File workingDir = new File(System.getProperty("user.dir"));
        System.out.println("Working directory: " + workingDir.getAbsolutePath());
        
        // Look for images in src/images
        File imagesDir = new File(workingDir, "src/images");
        System.out.println("PRIORITY: Looking for images in " + imagesDir.getPath());
        
        if (imagesDir.exists() && imagesDir.isDirectory()) {
            System.out.println("✅ Found src/images directory:");
            File[] imageFiles = imagesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
            if (imageFiles != null) {
                for (File imageFile : imageFiles) {
                    System.out.println("  - " + imageFile.getName() + " (" + imageFile.length() + " bytes)");
                    
                    // Test if OpenCV can load the image
                    if (openCvLoaded) {
                        try {
                            Mat testMat = Imgcodecs.imread(imageFile.getAbsolutePath(), Imgcodecs.IMREAD_GRAYSCALE);
                            if (!testMat.empty()) {
                                System.out.println("    ✅ OpenCV can load: " + imageFile.getName() + " (" + testMat.cols() + "x" + testMat.rows() + ")");
                                testMat.release();
                            } else {
                                System.out.println("    ❌ OpenCV cannot load: " + imageFile.getName());
                            }
                        } catch (Exception e) {
                            System.out.println("    ❌ Error loading " + imageFile.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }
        } else {
            System.out.println("❌ src/images directory not found");
        }
        
        System.out.println("============================");
    }

    public static boolean isOpenCvLoaded() {
        return openCvLoaded;
    }

    public static void createDirectoryIfNeeded(String dirPath) {
        try {
            Path path = Paths.get(dirPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            System.err.println("Failed to create directory: " + dirPath + " - " + e.getMessage());
        }
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

    public static Point findImageOnScreenGrayWithRetry(String screenshotPath, String templateName, double threshold, int instanceIndex) {
        if (!openCvLoaded) {
            System.err.println("OpenCV not loaded, cannot perform image matching");
            return null;
        }

        try {
            // Find the template file
            File templateFile = findImageFile(templateName);
            if (templateFile == null) {
                System.err.println("Template not found: " + templateName);
                return null;
            }

            System.out.println("Found image at: " + templateFile.getAbsolutePath());
            System.out.println("Loading template: " + templateFile.getAbsolutePath() + " (size: " + templateFile.length() + " bytes)");
            System.out.println("Loading screen: " + screenshotPath + " (size: " + new File(screenshotPath).length() + " bytes)");

            // Load template and screen images
            Mat template = Imgcodecs.imread(templateFile.getAbsolutePath(), Imgcodecs.IMREAD_GRAYSCALE);
            Mat screen = Imgcodecs.imread(screenshotPath, Imgcodecs.IMREAD_GRAYSCALE);

            if (template.empty()) {
                System.err.println("Failed to load template: " + templateName);
                return null;
            }

            if (screen.empty()) {
                System.err.println("Failed to load screenshot: " + screenshotPath);
                template.release();
                return null;
            }

            System.out.println("Screen dimensions: " + screen.cols() + "x" + screen.rows());
            System.out.println("Template dimensions: " + template.cols() + "x" + template.rows());

            // Perform template matching
            Mat result = new Mat();
            Imgproc.matchTemplate(screen, template, result, Imgproc.TM_CCOEFF_NORMED);

            // Find the best match
            Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
            double confidence = mmr.maxVal;

            System.out.println("Template matching confidence: " + String.format("%.3f", confidence) + " (threshold: " + threshold + ") for " + templateName);

            // Clean up
            template.release();
            screen.release();
            result.release();

            if (confidence >= threshold) {
                Point matchPoint = new Point((int)mmr.maxLoc.x, (int)mmr.maxLoc.y);
                System.out.println("Found template at: (" + matchPoint.x + ", " + matchPoint.y + ") for " + templateName);
                return matchPoint;
            } else {
                System.out.println("Template not found - confidence too low for " + templateName);
                return null;
            }

        } catch (Exception e) {
            System.err.println("Error in image matching: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static File findImageFile(String imageName) {
        // Look in src/images first
        File srcImagesFile = new File("src/images/" + imageName);
        if (srcImagesFile.exists()) {
            return srcImagesFile;
        }

        // Look in current directory
        File currentDirFile = new File(imageName);
        if (currentDirFile.exists()) {
            return currentDirFile;
        }

        // Look in images subdirectory
        File imagesSubdirFile = new File("images/" + imageName);
        if (imagesSubdirFile.exists()) {
            return imagesSubdirFile;
        }

        return null;
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

    public static boolean delay(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static boolean isInstanceRunning(int index) {
        try {
            ProcessBuilder builder = new ProcessBuilder(MEMUC_PATH, "isvmrunning", "-i", String.valueOf(index));
            Process process = builder.start();
            
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String output = reader.readLine();
                if (output != null) {
                    String trimmedOutput = output.trim();
                    return trimmedOutput.equals("1") || trimmedOutput.equalsIgnoreCase("Running");
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking if instance " + index + " is running: " + e.getMessage());
        }
        return false;
    }

    public static void enableAutoStart(int index) {
        System.out.println("Auto Start Game is enabled for instance " + index);
        // This method can be expanded to perform additional setup if needed
    }

    // Alias method for compatibility with other classes
    public static Point findImageOnScreenGray(String screenshotPath, String templateName, double threshold) {
        return findImageOnScreenGrayWithRetry(screenshotPath, templateName, threshold, 0);
    }
}