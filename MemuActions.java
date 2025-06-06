package newgame;

import javax.swing.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class MemuActions {
    // Standard resolution settings
    private static final int TARGET_WIDTH = 400;
    private static final int TARGET_HEIGHT = 652;
    private static final int TARGET_DPI = 133;
    
    public static void startInstance(JFrame parent, int index, Runnable onSuccess) {
        executeCommand(parent, "start", index, onSuccess);
    }

    public static void stopInstance(JFrame parent, int index, Runnable onSuccess) {
        executeCommand(parent, "stop", index, onSuccess);
    }

    public static void optimizeInstanceInBackground(int index, Runnable onComplete) {
        new Thread(() -> {
            try {
                System.out.println("Auto-optimizing instance " + index + " in background...");
                
                // Check if instance is already stopped to avoid unnecessary restart
                boolean wasRunning = isInstanceRunning(index);
                
                // Stop instance first to change resolution (only if running)
                if (wasRunning) {
                    System.out.println("Stopping running instance " + index + " for optimization...");
                    executeMemuCommand("stop", "-i", String.valueOf(index));
                    Thread.sleep(3000); // Wait for stop
                } else {
                    System.out.println("Instance " + index + " already stopped, proceeding with optimization...");
                }
                
                // Set resolution to 400x652 with 133 DPI
                executeMemuCommand("setconfigex", "-i", String.valueOf(index), 
                    "resolution", TARGET_WIDTH + "," + TARGET_HEIGHT + "," + TARGET_DPI);
                Thread.sleep(1000);
                
                // Set other optimization settings
                executeMemuCommand("setconfigex", "-i", String.valueOf(index), "cpus", "2");
                Thread.sleep(500);
                executeMemuCommand("setconfigex", "-i", String.valueOf(index), "memory", "3000");
                Thread.sleep(500);
                executeMemuCommand("setconfigex", "-i", String.valueOf(index), "fps", "30");
                Thread.sleep(1000);
                
                System.out.println("Instance " + index + " optimized to " + TARGET_WIDTH + "x" + TARGET_HEIGHT + " @ " + TARGET_DPI + " DPI");
                System.out.println("Note: Instance will be started separately after optimization");
                
                if (onComplete != null) {
                    SwingUtilities.invokeLater(onComplete);
                }
                
            } catch (Exception e) {
                System.err.println("Background optimization failed for instance " + index + ": " + e.getMessage());
                if (onComplete != null) {
                    SwingUtilities.invokeLater(onComplete);
                }
            }
        }).start();
    }
    
    /**
     * Check and ensure instance has correct resolution
     */
    public static void ensureCorrectResolution(JFrame parent, int index, Runnable onComplete) {
        new SwingWorker<Boolean, Void>() {
            protected Boolean doInBackground() throws Exception {
                // Check current resolution by taking a screenshot
                if (BotUtils.takeMenuScreenshotLegacy(index, "screenshots/resolution_check_" + index + ".png")) {
                    // Load image to check dimensions
                    if (BotUtils.isOpenCvLoaded()) {
                        org.opencv.core.Mat screen = org.opencv.imgcodecs.Imgcodecs.imread(
                            "screenshots/resolution_check_" + index + ".png");
                        
                        if (!screen.empty()) {
                            int currentWidth = screen.cols();
                            int currentHeight = screen.rows();
                            screen.release();
                            
                            System.out.println("Instance " + index + " current resolution: " + 
                                             currentWidth + "x" + currentHeight);
                            
                            // Check if resolution matches target
                            if (currentWidth != TARGET_WIDTH || currentHeight != TARGET_HEIGHT) {
                                System.out.println("Resolution mismatch! Correcting to " + 
                                                 TARGET_WIDTH + "x" + TARGET_HEIGHT);
                                
                                // Stop, fix resolution, start
                                executeMemuCommand("stop", "-i", String.valueOf(index));
                                Thread.sleep(3000);
                                
                                executeMemuCommand("setconfigex", "-i", String.valueOf(index), 
                                    "resolution", TARGET_WIDTH + "," + TARGET_HEIGHT + "," + TARGET_DPI);
                                Thread.sleep(1000);
                                
                                executeMemuCommand("start", "-i", String.valueOf(index));
                                Thread.sleep(5000);
                                
                                return true; // Resolution was corrected
                            } else {
                                System.out.println("Resolution is correct: " + 
                                                 currentWidth + "x" + currentHeight);
                                return false; // No correction needed
                            }
                        }
                    }
                }
                return false;
            }
            
            protected void done() {
                try {
                    Boolean corrected = get();
                    if (corrected) {
                        System.out.println("Resolution corrected for instance " + index);
                    }
                    if (onComplete != null) {
                        SwingUtilities.invokeLater(onComplete);
                    }
                } catch (Exception e) {
                    System.err.println("Resolution check failed: " + e.getMessage());
                    if (onComplete != null) {
                        SwingUtilities.invokeLater(onComplete);
                    }
                }
            }
        }.execute();
    }
    
    /**
     * Force set resolution to target dimensions
     */
    public static void forceSetResolution(JFrame parent, int index, Runnable onComplete) {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                System.out.println("Force setting resolution for instance " + index + 
                                 " to " + TARGET_WIDTH + "x" + TARGET_HEIGHT);
                
                executeMemuCommand("stop", "-i", String.valueOf(index));
                Thread.sleep(3000);
                
                executeMemuCommand("setconfigex", "-i", String.valueOf(index), 
                    "resolution", TARGET_WIDTH + "," + TARGET_HEIGHT + "," + TARGET_DPI);
                Thread.sleep(1000);
                
                executeMemuCommand("start", "-i", String.valueOf(index));
                Thread.sleep(5000);
                
                return null;
            }
            
            protected void done() {
                try {
                    get();
                    System.out.println("Resolution force-set completed for instance " + index);
                    if (onComplete != null) {
                        SwingUtilities.invokeLater(onComplete);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(parent, 
                        "Force resolution failed: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Check if an instance is currently running
     */
    private static boolean isInstanceRunning(int index) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                Main.MEMUC_PATH, "isvmrunning", "-i", String.valueOf(index)
            });
            
            boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String output = reader.readLine();
                if (output != null) {
                    String trimmed = output.trim();
                    return trimmed.equals("1") || trimmed.equalsIgnoreCase("Running");
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking if instance " + index + " is running: " + e.getMessage());
        }
        return false;
    }

    private static void executeCommand(JFrame parent, String command, int index, Runnable onSuccess) {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                executeMemuCommand(command, "-i", String.valueOf(index));
                return null;
            }
            
            protected void done() {
                try {
                    get();
                    if (onSuccess != null) {
                        SwingUtilities.invokeLater(onSuccess);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(parent, 
                        "Command failed: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    
    private static void executeMemuCommand(String... args) throws Exception {
        String[] fullCommand = new String[args.length + 1];
        fullCommand[0] = Main.MEMUC_PATH;
        System.arraycopy(args, 0, fullCommand, 1, args.length);
        
        Process p = Runtime.getRuntime().exec(fullCommand);
        int exitCode = p.waitFor();
        
        if (exitCode != 0) {
            throw new Exception("MEmu command failed with exit code: " + exitCode);
        }
    }
    
    // Getter methods for the target resolution
    public static int getTargetWidth() { return TARGET_WIDTH; }
    public static int getTargetHeight() { return TARGET_HEIGHT; }
    public static int getTargetDPI() { return TARGET_DPI; }
}