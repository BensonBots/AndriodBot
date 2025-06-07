package newgame;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.*;

public class MarchDetector {
    
    public enum MarchStatus {
        IDLE, UNLOCK, CANNOT_USE, GATHERING
    }
    
    public static class MarchInfo {
        public final int queueNumber;
        public final MarchStatus status;
        public final String remainingTime;
        public final String resourceInfo;
        
        public MarchInfo(int queueNumber, MarchStatus status) {
            this(queueNumber, status, null, null);
        }
        
        public MarchInfo(int queueNumber, MarchStatus status, String remainingTime, String resourceInfo) {
            this.queueNumber = queueNumber;
            this.status = status;
            this.remainingTime = remainingTime;
            this.resourceInfo = resourceInfo;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("March Queue ").append(queueNumber).append(": ").append(status);
            if (remainingTime != null) {
                sb.append(" (").append(remainingTime).append(")");
            }
            if (resourceInfo != null) {
                sb.append(" - ").append(resourceInfo);
            }
            return sb.toString();
        }
    }
    
    // Auto Gather Settings Classes
    public static class AutoGatherSettings {
        public int numberOfMarches;
        public List<MarchSetting> marchSettings;
        
        public AutoGatherSettings() {
            this.numberOfMarches = 2;
            this.marchSettings = new ArrayList<>();
            // Default settings
            marchSettings.add(new MarchSetting(1, "Food", 1));
            marchSettings.add(new MarchSetting(2, "Wood", 1));
        }
    }
    
    public static class MarchSetting {
        public int marchNumber;
        public String resourceType; // Food, Wood, Stone, Iron
        public int level; // 1-8
        
        public MarchSetting(int marchNumber, String resourceType, int level) {
            this.marchNumber = marchNumber;
            this.resourceType = resourceType;
            this.level = level;
        }
        
        @Override
        public String toString() {
            return "March " + marchNumber + ": " + resourceType + " Lv." + level;
        }
    }
    
    // Tesseract OCR path - adjust this to your installation
    private static final String TESSERACT_PATH = "C:\\Program Files\\Tesseract-OCR\\tesseract.exe";
    
    /**
     * Open the left march panel by clicking open_left.png
     */
    public static boolean openLeftPanel(int instanceIndex) {
        System.out.println("🔍 Opening left march panel for instance " + instanceIndex);
        
        String screenPath = "screenshots/open_left_" + instanceIndex + ".png";
        if (!BotUtils.takeMenuScreenshotLegacy(instanceIndex, screenPath)) {
            System.err.println("Failed to take screenshot for opening left panel");
            return false;
        }
        
        Point openLeftButton = BotUtils.findImageOnScreenGray(screenPath, "open_left.png", 0.6);
        if (openLeftButton != null) {
            if (BotUtils.clickMenu(instanceIndex, openLeftButton)) {
                System.out.println("✅ Clicked open left panel button");
                BotUtils.delay(2000); // Wait for panel to open
                return true;
            }
        }
        
        System.err.println("❌ Could not find or click open_left.png");
        return false;
    }
    
    /**
     * Click wilderness button to access march queues
     */
    public static boolean clickWildernessButton(int instanceIndex) {
        System.out.println("🏔️ Clicking wilderness button for instance " + instanceIndex);
        
        String screenPath = "screenshots/wilderness_" + instanceIndex + ".png";
        if (!BotUtils.takeMenuScreenshotLegacy(instanceIndex, screenPath)) {
            System.err.println("Failed to take screenshot for wilderness button");
            return false;
        }
        
        Point wildernessButton = BotUtils.findImageOnScreenGray(screenPath, "wilderness_button.png", 0.6);
        if (wildernessButton != null) {
            if (BotUtils.clickMenu(instanceIndex, wildernessButton)) {
                System.out.println("✅ Clicked wilderness button");
                BotUtils.delay(3000); // Wait for wilderness view to load
                return true;
            }
        }
        
        System.err.println("❌ Could not find or click wilderness_button.png");
        return false;
    }
    
    /**
     * Read march queue statuses using simplified OCR on left panel only
     */
    public static List<MarchInfo> readMarchQueues(int instanceIndex) {
        System.out.println("[Instance " + instanceIndex + "] 📋 Reading march queues...");
        
        // Take screenshot of the whole screen first
        String fullScreenPath = "screenshots/march_full_" + instanceIndex + ".png";
        if (!BotUtils.takeMenuScreenshotLegacy(instanceIndex, fullScreenPath)) {
            System.err.println("❌ Failed to take full screenshot");
            return new ArrayList<>();
        }
        
        // Extract only the left march queue panel (based on your image)
        String leftPanelPath = extractLeftPanel(fullScreenPath, instanceIndex);
        if (leftPanelPath == null) {
            System.err.println("❌ Failed to extract left panel");
            return new ArrayList<>();
        }
        
        // OCR only the left panel - much cleaner text
        String fullText = performSimpleOCR(leftPanelPath);
        if (fullText == null || fullText.trim().isEmpty()) {
            System.err.println("❌ OCR returned empty text");
            return new ArrayList<>();
        }
        
        System.out.println("📋 Left panel OCR text:");
        System.out.println("--- Start OCR Text ---");
        System.out.println(fullText);
        System.out.println("--- End OCR Text ---");
        
        // Parse the text to extract march queue information
        return parseMarchQueues(fullText);
    }
    
    /**
     * Extract just the text area from the left march queue panel (no flag icons)
     */
    private static String extractLeftPanel(String fullScreenPath, int instanceIndex) {
        if (!BotUtils.isOpenCvLoaded()) {
            return null;
        }
        
        Mat fullScreen = null;
        Mat leftPanel = null;
        
        try {
            fullScreen = Imgcodecs.imread(fullScreenPath, Imgcodecs.IMREAD_COLOR);
            if (fullScreen.empty()) {
                return null;
            }
            
            // Based on your image, we need to avoid the flag icons on the left
            // The text area starts after the flags (around x=50) and goes to about x=300
            // Y coordinates remain the same: 190-500
            int panelX = 50;     // Start after the flag icons (was 20)
            int panelY = 190; 
            int panelWidth = 230; // Narrower to avoid flags (was 280)
            int panelHeight = 310;
            
            // Make sure coordinates are within bounds
            panelX = Math.max(0, panelX);
            panelY = Math.max(0, panelY);
            panelWidth = Math.min(panelWidth, fullScreen.cols() - panelX);
            panelHeight = Math.min(panelHeight, fullScreen.rows() - panelY);
            
            // Extract the text-only region (no flags)
            Rect panelRect = new Rect(panelX, panelY, panelWidth, panelHeight);
            leftPanel = new Mat(fullScreen, panelRect);
            
            // Save the extracted text panel
            String leftPanelPath = "screenshots/march_text_panel_" + instanceIndex + ".png";
            Imgcodecs.imwrite(leftPanelPath, leftPanel);
            
            // Print file size for debugging
            File panelFile = new File(leftPanelPath);
            if (panelFile.exists()) {
                System.out.println("Text panel extracted (no flags): " + leftPanelPath + " (" + panelFile.length() + " bytes)");
            }
            
            return leftPanelPath;
            
        } catch (Exception e) {
            System.err.println("❌ Error extracting text panel: " + e.getMessage());
            return null;
        } finally {
            if (fullScreen != null) fullScreen.release();
            if (leftPanel != null) leftPanel.release();
        }
    }
    
    /**
     * Perform optimized OCR on the clean text panel using better settings
     */
    private static String performSimpleOCR(String imagePath) {
        try {
            // Try multiple OCR configurations to find the best one
            String[] configs = {
                // Config 1: PSM 6 with character whitelist (best for UI text)
                "--psm,6,--oem,1,-c,tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 ",
                
                // Config 2: PSM 4 for single column of text
                "--psm,4,--oem,1,-c,tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 ",
                
                // Config 3: PSM 6 with legacy engine (sometimes more accurate)
                "--psm,6,--oem,0,-c,tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 ",
                
                // Config 4: Default PSM 3 with whitelist
                "--psm,3,--oem,1,-c,tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 "
            };
            
            String bestResult = "";
            int bestScore = 0;
            
            for (String config : configs) {
                String[] configArray = config.split(",");
                String result = runTesseractOCR(imagePath, configArray);
                int score = scoreOCRQuality(result);
                
                System.out.println("🔍 OCR Config " + configArray[1] + ": Score " + score + " - '" + result.replaceAll("\n", " | ").trim() + "'");
                
                if (score > bestScore) {
                    bestScore = score;
                    bestResult = result;
                }
            }
            
            System.out.println("✅ Best OCR result (score: " + bestScore + ")");
            return bestResult;
            
        } catch (Exception e) {
            System.err.println("❌ OCR failed: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Score OCR quality based on expected patterns
     */
    private static int scoreOCRQuality(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        
        String lowerText = text.toLowerCase();
        int score = 0;
        
        // High value keywords
        if (lowerText.contains("march queue")) score += 20;
        if (lowerText.contains("idle")) score += 15;
        if (lowerText.contains("unlock")) score += 15;
        if (lowerText.contains("cannot use")) score += 15;
        
        // Structure patterns
        if (lowerText.matches(".*march queue [1-6].*")) score += 10;
        
        // Penalty for obvious OCR errors
        if (lowerText.contains("] ") || lowerText.contains(") ")) score -= 5;
        if (lowerText.contains("irc") || lowerText.contains("ile")) score -= 3;
        
        // Bonus for clean structure
        String[] lines = text.split("\n");
        int validLines = 0;
        for (String line : lines) {
            String cleanLine = line.trim().toLowerCase();
            if (cleanLine.contains("march queue") || 
                cleanLine.equals("idle") || 
                cleanLine.equals("unlock") || 
                cleanLine.equals("cannot use")) {
                validLines++;
            }
        }
        score += validLines * 5;
        
        return score;
    }
    
    /**
     * Parse the left panel OCR text to extract march queue information
     */
    private static List<MarchInfo> parseMarchQueues(String ocrText) {
        List<MarchInfo> queues = new ArrayList<>();
        
        // Split text into lines and clean up
        String[] lines = ocrText.split("\n");
        List<String> cleanLines = new ArrayList<>();
        
        for (String line : lines) {
            String cleaned = line.trim();
            if (!cleaned.isEmpty() && 
                !cleaned.toLowerCase().contains("ss ee ee") && // Filter out garbage
                !cleaned.toLowerCase().equals("march queue")) { // Filter out header
                cleanLines.add(cleaned);
            }
        }
        
        System.out.println("📋 Cleaned lines: " + cleanLines);
        
        // Track current queue number for gathering detection
        int expectedQueueNumber = 1;
        
        // Parse lines looking for queue patterns
        for (int i = 0; i < cleanLines.size(); i++) {
            String line = cleanLines.get(i).toLowerCase();
            
            // Check for gathering march (appears without "March Queue X" header)
            if (line.contains("gathering") || line.contains("lv") || line.contains("mill") || line.contains("quarry") || line.contains("mine")) {
                MarchInfo gatheringQueue = new MarchInfo(expectedQueueNumber, MarchStatus.GATHERING, null, extractResourceFromGathering(line));
                queues.add(gatheringQueue);
                System.out.println("📊 March Queue " + expectedQueueNumber + ": GATHERING - " + extractResourceFromGathering(line));
                expectedQueueNumber++;
                continue;
            }
            
            // Look for "March Queue X" pattern
            if (line.contains("march") && line.contains("queue")) {
                // Extract queue number
                Pattern numberPattern = Pattern.compile("queue\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
                Matcher numberMatcher = numberPattern.matcher(line);
                
                int queueNumber = expectedQueueNumber; // Use expected number if not found
                if (numberMatcher.find()) {
                    try {
                        queueNumber = Integer.parseInt(numberMatcher.group(1));
                    } catch (NumberFormatException e) {
                        // Keep expected number
                    }
                }
                
                // Look at the next line for status
                MarchStatus status = MarchStatus.IDLE; // default
                if (i + 1 < cleanLines.size()) {
                    String statusLine = cleanLines.get(i + 1).toLowerCase();
                    
                    if (statusLine.contains("idle")) {
                        status = MarchStatus.IDLE;
                    } else if (statusLine.contains("unlock")) {
                        status = MarchStatus.UNLOCK;
                    } else if (statusLine.contains("cannot") || statusLine.contains("use")) {
                        status = MarchStatus.CANNOT_USE;
                    } else if (statusLine.contains("gathering") || 
                               statusLine.contains("lv") || 
                               statusLine.contains("mill") || 
                               statusLine.contains("quarry") || 
                               statusLine.contains("mine") ||
                               statusLine.matches(".*\\d{2}[:\\.]\\d{2}.*")) { // Any timer pattern
                        status = MarchStatus.GATHERING;
                    }
                    
                    // Skip the status line in next iteration
                    i++; // This prevents processing the status line as a queue line
                }
                
                // Check if we already have this queue number (prevent duplicates)
                final int finalQueueNumber = queueNumber; // Make final for lambda
                boolean alreadyExists = queues.stream()
                    .anyMatch(q -> q.queueNumber == finalQueueNumber);
                
                if (!alreadyExists) {
                    MarchInfo queue = new MarchInfo(queueNumber, status);
                    queues.add(queue);
                    System.out.println("📊 March Queue " + queueNumber + ": " + status);
                }
                
                expectedQueueNumber = Math.max(expectedQueueNumber, queueNumber + 1);
            }
        }
        
        // If no queues found, try alternate parsing approach
        if (queues.isEmpty()) {
            queues = parseByLines(cleanLines);
        }
        
        // Sort queues by number to ensure correct order
        queues.sort((a, b) -> Integer.compare(a.queueNumber, b.queueNumber));
        
        return queues;
    }
    
    /**
     * Alternative parsing approach - look for status keywords directly
     */
    private static List<MarchInfo> parseByLines(List<String> lines) {
        List<MarchInfo> queues = new ArrayList<>();
        System.out.println("🔍 Trying alternate parsing approach...");
        
        int queueNumber = 1;
        for (String line : lines) {
            String lowerLine = line.toLowerCase();
            
            // Skip "March Queue" header lines
            if (lowerLine.contains("march") && lowerLine.contains("queue")) {
                continue;
            }
            
            // Look for status keywords
            if (lowerLine.contains("idle")) {
                queues.add(new MarchInfo(queueNumber++, MarchStatus.IDLE));
                System.out.println("📊 Found Queue " + (queueNumber-1) + ": IDLE");
            } else if (lowerLine.contains("unlock")) {
                queues.add(new MarchInfo(queueNumber++, MarchStatus.UNLOCK));
                System.out.println("📊 Found Queue " + (queueNumber-1) + ": UNLOCK");
            } else if (lowerLine.contains("cannot") || lowerLine.contains("use")) {
                queues.add(new MarchInfo(queueNumber++, MarchStatus.CANNOT_USE));
                System.out.println("📊 Found Queue " + (queueNumber-1) + ": CANNOT_USE");
            } else if (lowerLine.contains("gathering") || lowerLine.matches(".*\\d{1,2}:\\d{2}.*")) {
                queues.add(new MarchInfo(queueNumber++, MarchStatus.GATHERING));
                System.out.println("📊 Found Queue " + (queueNumber-1) + ": GATHERING");
            }
        }
        
        return queues;
    }
    
    /**
     * Create default queues when OCR fails to detect patterns
     */
    private static List<MarchInfo> createDefaultQueues(List<String> lines) {
        List<MarchInfo> queues = new ArrayList<>();
        System.out.println("🔧 Creating default queue structure (OCR detection failed)");
        
        // Create 6 default queues based on typical game structure
        for (int i = 1; i <= 6; i++) {
            MarchStatus status;
            if (i <= 2) {
                status = MarchStatus.IDLE; // First 2 queues usually available
            } else if (i == 3) {
                status = MarchStatus.UNLOCK; // Third queue often needs unlock
            } else {
                status = MarchStatus.CANNOT_USE; // Higher queues typically locked
            }
            
            queues.add(new MarchInfo(i, status));
            System.out.println("📊 Default Queue " + i + ": " + status);
        }
        
        return queues;
    }
    
    /**
     * Run Tesseract OCR with specific configuration
     */
    private static String runTesseractOCR(String imagePath, String[] config) {
        try {
            // Check if Tesseract is available
            File tesseractExe = new File(TESSERACT_PATH);
            if (!tesseractExe.exists()) {
                System.err.println("Tesseract not found at: " + TESSERACT_PATH);
                return "";
            }
            
            // Build command with provided configuration
            List<String> command = new ArrayList<>();
            command.add(TESSERACT_PATH);
            command.add(imagePath);
            command.add("stdout");
            
            // Add configuration parameters
            for (String param : config) {
                command.add(param);
            }
            
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            
            // Read output
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            reader.close();
            
            // Wait for completion
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("⚠️ Tesseract OCR failed with exit code: " + exitCode);
                return "";
            }
            
            return output.toString();
            
        } catch (Exception e) {
            System.err.println("❌ Tesseract OCR exception: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Extract resource type from gathering text
     */
    private static String extractResourceFromGathering(String text) {
        if (text.contains("mill")) {
            return "Food";
        } else if (text.contains("lumberyard") || text.contains("lumber")) {
            return "Wood";
        } else if (text.contains("quarry")) {
            return "Stone";
        } else if (text.contains("mine") || text.contains("iron")) {
            return "Iron";
        }
        return "Unknown";
    }
    
    /**
     * Analyze current march status and determine what actions are needed
     */
    public static void analyzeGatheringNeeds(List<MarchInfo> currentQueues, AutoGatherSettings settings) {
        System.out.println("🔍 Analyzing gathering needs with settings:");
        for (MarchSetting setting : settings.marchSettings) {
            System.out.println("  " + setting);
        }
        System.out.println();
        
        // Track which marches are assigned
        boolean[] marchAssigned = new boolean[settings.numberOfMarches + 1]; // 1-based indexing
        
        // First pass: Check what's already gathering
        System.out.println("📊 Current queue status:");
        for (MarchInfo queue : currentQueues) {
            System.out.println("  Queue " + queue.queueNumber + ": " + queue.status + 
                             (queue.resourceInfo != null ? " (" + queue.resourceInfo + ")" : ""));
            
            if (queue.status == MarchStatus.GATHERING && queue.resourceInfo != null) {
                // Try to match this gathering march to a setting
                for (int i = 0; i < settings.marchSettings.size(); i++) {
                    MarchSetting setting = settings.marchSettings.get(i);
                    if (!marchAssigned[setting.marchNumber] && 
                        queue.resourceInfo.equals(setting.resourceType)) {
                        marchAssigned[setting.marchNumber] = true;
                        System.out.println("  ✅ March " + setting.marchNumber + " is active: " + 
                                         queue.resourceInfo + " (Queue " + queue.queueNumber + ")");
                        break;
                    }
                }
            }
        }
        
        System.out.println();
        
        // Second pass: Determine what needs to be started
        System.out.println("🎯 Required actions:");
        for (MarchSetting setting : settings.marchSettings) {
            if (!marchAssigned[setting.marchNumber]) {
                // This march needs to be started
                MarchInfo availableQueue = findAvailableQueue(currentQueues);
                if (availableQueue != null) {
                    System.out.println("  🚀 Start March " + setting.marchNumber + ": " + 
                                     setting.resourceType + " Lv." + setting.level + 
                                     " on Queue " + availableQueue.queueNumber);
                } else {
                    System.out.println("  ⏳ March " + setting.marchNumber + ": " + 
                                     setting.resourceType + " Lv." + setting.level + 
                                     " (waiting for available queue)");
                }
            }
        }
        
        System.out.println();
    }
    
    /**
     * Find the first available (IDLE) queue
     */
    private static MarchInfo findAvailableQueue(List<MarchInfo> queues) {
        for (MarchInfo queue : queues) {
            if (queue.status == MarchStatus.IDLE) {
                return queue;
            }
        }
        return null;
    }
    
    /**
     * Create example auto gather settings for testing
     */
    public static AutoGatherSettings createExampleSettings() {
        AutoGatherSettings settings = new AutoGatherSettings();
        settings.numberOfMarches = 3;
        settings.marchSettings.clear();
        settings.marchSettings.add(new MarchSetting(1, "Food", 2));
        settings.marchSettings.add(new MarchSetting(2, "Iron", 1));
        settings.marchSettings.add(new MarchSetting(3, "Wood", 3));
        return settings;
    }
    
    /**
     * Read march queue statuses and analyze gathering needs
     */
    public static List<MarchInfo> readMarchQueuesWithAnalysis(int instanceIndex, AutoGatherSettings settings) {
        List<MarchInfo> queues = readMarchQueues(instanceIndex);
        
        if (!queues.isEmpty() && settings != null) {
            System.out.println("═══════════════════════════════════════");
            analyzeGatheringNeeds(queues, settings);
            System.out.println("═══════════════════════════════════════");
        }
        
        return queues;
    }
    
    /**
     * Get available march queues (IDLE status)
     */
    public static List<MarchInfo> getAvailableQueues(List<MarchInfo> allQueues) {
        List<MarchInfo> available = new ArrayList<>();
        for (MarchInfo queue : allQueues) {
            if (queue.status == MarchStatus.IDLE) {
                available.add(queue);
            }
        }
        return available;
    }
    
    /**
     * Setup march panel view (open left panel + click wilderness)
     */
    public static boolean setupMarchView(int instanceIndex) {
        System.out.println("🔧 Setting up march view for instance " + instanceIndex);
        
        // Step 1: Open left panel
        if (!openLeftPanel(instanceIndex)) {
            return false;
        }
        
        // Step 2: Click wilderness button
        if (!clickWildernessButton(instanceIndex)) {
            return false;
        }
        
        System.out.println("✅ March view setup complete");
        return true;
    }
}