package newgame;

import java.awt.Point;
import java.nio.file.Paths;

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
                String screenPath = Paths.get(BotUtils.SCREENSHOTS_DIR, "current_screen_" + instance.index + ".png").toString();
                
                BotUtils.createDirectoryIfNeeded(BotUtils.SCREENSHOTS_DIR);
                
                for (int i = 0; i < attempts && !shouldStop && !Thread.currentThread().isInterrupted(); i++) {
                    System.out.println("Game start attempt " + (i+1) + "/" + attempts + " for instance " + instance.index);
                    
                    if (!BotUtils.takeMenuScreenshotLegacy(instance.index, screenPath)) {
                        instance.setState("[ERROR] Screenshot failed (" + (i+1) + "/" + attempts + ")");
                        if (!BotUtils.delay(2000)) break;
                        continue;
                    }
                    
                    Point gameIcon = BotUtils.findImageOnScreenGray(screenPath, "game_icon.png", 0.8);
                    if (gameIcon != null) {
                        instance.setState("Game already running");
                        System.out.println("Game already detected running for instance " + instance.index);
                        break;
                    }
                    
                    boolean closedPopup = false;
                    for (String closeBtn : new String[]{"close_x.png", "close_x2.png", "close_x3.png"}) {
                        Point closeBtnLoc = BotUtils.findImageOnScreenGray(screenPath, closeBtn, 0.8);
                        if (closeBtnLoc != null) {
                            if (BotUtils.clickMenu(instance.index, closeBtnLoc)) {
                                instance.setState("Closed popup (" + (i+1) + "/" + attempts + ")");
                                System.out.println("Closed popup for instance " + instance.index);
                                closedPopup = true;
                                if (!BotUtils.delay(1000)) break;
                                if (!BotUtils.takeMenuScreenshotLegacy(instance.index, screenPath)) {
                                    instance.setState("[ERROR] Screenshot failed after popup");
                                    break;
                                }
                                break;
                            }
                        }
                    }
                    
                    if (closedPopup) {
                        if (!BotUtils.delay(1000)) break;
                        continue;
                    }
                    
                    Point launcher = BotUtils.findImageOnScreenGray(screenPath, "game_launcher.png", 0.8);
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
                    if (BotUtils.takeMenuScreenshotLegacy(instance.index, screenPath)) {
                        Point gameIcon = BotUtils.findImageOnScreenGray(screenPath, "game_icon.png", 0.8);
                        if (gameIcon != null) {
                            instance.setState("Game running successfully");
                            System.out.println("Game confirmed running for instance " + instance.index);
                        }
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