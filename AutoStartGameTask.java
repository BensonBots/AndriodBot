package newgame;

import java.awt.Point;
import java.nio.file.Paths;
import java.io.File;

public class AutoStartGameTask {
    private final MemuInstance instance;
    private final int attempts;
    private final Runnable onComplete;
    private volatile boolean shouldStop = false;

    public AutoStartGameTask(MemuInstance instance, int attempts, Runnable onComplete) {
        this.instance = instance;
        this.attempts = attempts;
        this.onComplete = onComplete;
    }

    public void execute() {
        if (instance == null) {
            System.err.println("Cannot start game loop: instance is null");
            return;
        }
        
        if (instance.isAutoStartGameRunning()) {
            System.out.println("Auto start game already running for instance " + instance.index);
            return;
        }
        
        instance.setAutoStartGameRunning(true);
        instance.setState("Starting game...");
        
        Thread gameThread = new Thread(() -> {
            try {
                // Use the EXACT SAME path that works perfectly for resolution checks
                String screenPath = Paths.get(BotUtils.SCREENSHOTS_DIR, "resolution_check_" + instance.index + ".png").toString();
                
                BotUtils.createDirectoryIfNeeded(BotUtils.SCREENSHOTS_DIR);
                
                for (int i = 0; i < attempts && !shouldStop && !Thread.currentThread().isInterrupted(); i++) {
                    System.out.println("Game start attempt " + (i+1) + "/" + attempts + " for instance " + instance.index);
                    
                    // Try to get a good screenshot - use same method that works for resolution check
                    boolean screenshotSuccess = false;
                    for (int retry = 0; retry < 5; retry++) { // Increased retries
                        System.out.println("Screenshot attempt " + (retry + 1) + "/5 for game start...");
                        System.out.println("Using working screenshot path: " + screenPath);
                        
                        // Delete existing file to get fresh screenshot
                        File existingFile = new File(screenPath);
                        if (existingFile.exists()) {
                            existingFile.delete();
                            System.out.println("Deleted existing resolution_check file");
                            try {
                                Thread.sleep(500); // Give file system time
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        
                        if (BotUtils.takeMenuScreenshotLegacy(instance.index, screenPath)) {
                            File screenFile = new File(screenPath);
                            if (screenFile.exists() && screenFile.length() > 15000) {
                                screenshotSuccess = true;
                                System.out.println("✅ Screenshot successful: " + screenFile.length() + " bytes (using resolution_check path)");
                                break;
                            } else {
                                System.err.println("❌ Screenshot too small (" + 
                                    (screenFile.exists() ? screenFile.length() + " bytes" : "doesn't exist") + 
                                    "), retrying...");
                                
                                // Wait longer between retries
                                BotUtils.delay(2000);
                            }
                        } else {
                            System.err.println("❌ Screenshot command failed, retrying...");
                            BotUtils.delay(2000);
                        }
                    }
                    
                    if (!screenshotSuccess) {
                        instance.setState("[ERROR] Screenshot failed after 5 retries (" + (i+1) + "/" + attempts + ")");
                        System.err.println("All screenshot attempts failed, skipping this game start attempt");
                        if (!BotUtils.delay(5000)) break; // Wait longer before next attempt
                        continue;
                    }
                    
                    // Verify screenshot can be loaded by OpenCV before proceeding
                    if (BotUtils.isOpenCvLoaded()) {
                        org.opencv.core.Mat testMat = org.opencv.imgcodecs.Imgcodecs.imread(screenPath);
                        if (testMat.empty()) {
                            System.err.println("❌ OpenCV cannot load screenshot, retrying...");
                            testMat.release();
                            if (!BotUtils.delay(2000)) break;
                            continue;
                        } else {
                            System.out.println("✅ OpenCV validated screenshot: " + testMat.cols() + "x" + testMat.rows());
                            testMat.release();
                        }
                    }
                    
                    // Use the new method with retry capability
                    Point gameIcon = BotUtils.findImageOnScreenGrayWithRetry(screenPath, "game_icon.png", 0.8, instance.index);
                    if (gameIcon != null) {
                        instance.setState("Game already running");
                        System.out.println("Game already detected running for instance " + instance.index);
                        break;
                    }
                    
                    boolean closedPopup = false;
                    for (String closeBtn : new String[]{"close_x.png", "close_x2.png", "close_x3.png"}) {
                        Point closeBtnLoc = BotUtils.findImageOnScreenGrayWithRetry(screenPath, closeBtn, 0.8, instance.index);
                        if (closeBtnLoc != null) {
                            if (BotUtils.clickMenu(instance.index, closeBtnLoc)) {
                                instance.setState("Closed popup (" + (i+1) + "/" + attempts + ")");
                                System.out.println("Closed popup for instance " + instance.index);
                                closedPopup = true;
                                if (!BotUtils.delay(1000)) break;
                                
                                // Retake screenshot after closing popup with same robust logic
                                for (int popupRetry = 0; popupRetry < 3; popupRetry++) {
                                    if (BotUtils.takeMenuScreenshotLegacy(instance.index, screenPath)) {
                                        File screenFile = new File(screenPath);
                                        if (screenFile.exists() && screenFile.length() > 15000) {
                                            System.out.println("✅ Post-popup screenshot: " + screenFile.length() + " bytes");
                                            break;
                                        }
                                    }
                                    BotUtils.delay(1000);
                                }
                                break;
                            }
                        }
                    }
                    
                    if (closedPopup) {
                        if (!BotUtils.delay(1000)) break;
                        continue;
                    }
                    
                    Point launcher = BotUtils.findImageOnScreenGrayWithRetry(screenPath, "game_launcher.png", 0.8, instance.index);
                    if (launcher != null) {
                        if (BotUtils.clickMenu(instance.index, launcher)) {
                            instance.setState("Launched game (" + (i+1) + "/" + attempts + ")");
                            System.out.println("Clicked game launcher for instance " + instance.index);
                        } else {
                            instance.setState("[ERROR] Click failed (" + (i+1) + "/" + attempts + ")");
                        }
                    } else {
                        instance.setState("[ERROR] Launcher not found (" + (i+1) + "/" + attempts + ")");
                        System.out.println("Game launcher not found for instance " + instance.index);
                    }
                    
                    if (i < attempts - 1 && !BotUtils.delay(5000)) {
                        break;
                    }
                }
                
                if (!shouldStop && !Thread.currentThread().isInterrupted()) {
                    // Final verification with robust screenshot
                    boolean finalScreenshotSuccess = false;
                    for (int retry = 0; retry < 3; retry++) {
                        if (BotUtils.takeMenuScreenshotLegacy(instance.index, screenPath)) {
                            File screenFile = new File(screenPath);
                            if (screenFile.exists() && screenFile.length() > 15000) {
                                finalScreenshotSuccess = true;
                                break;
                            }
                        }
                        BotUtils.delay(1000);
                    }
                    
                    if (finalScreenshotSuccess) {
                        Point gameIcon = BotUtils.findImageOnScreenGrayWithRetry(screenPath, "game_icon.png", 0.8, instance.index);
                        if (gameIcon != null) {
                            instance.setState("Game running successfully");
                            System.out.println("Game confirmed running for instance " + instance.index);
                        } else {
                            instance.setState("Game status uncertain");
                        }
                    } else {
                        instance.setState("Final verification failed");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in auto start game loop: " + e.getMessage());
                e.printStackTrace();
                instance.setState("[ERROR] " + e.getMessage());
            } finally {
                instance.setAutoStartGameRunning(false);
                String finalState = instance.isAutoGatherRunning() ? "Gathering resources" : "Idle";
                instance.setState(finalState);
                System.out.println("Auto start game loop completed for instance " + instance.index);
                
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        }, "GameStarter-" + instance.index);
        
        gameThread.setDaemon(true);
        gameThread.start();
    }

    public void stop() {
        shouldStop = true;
        System.out.println("Stop requested for auto start game task on instance " + instance.index);
    }
}