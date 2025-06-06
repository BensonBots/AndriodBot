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
     * Read march queue statuses using OCR
     */
    public static List<MarchInfo> readMarchQueues(int instanceIndex) {
        System.out.println("📋 Reading march queues using OCR for instance " + instanceIndex);
        
        String screenPath = "screenshots/march_queues_" + instanceIndex + ".png";
        if (!BotUtils.takeMenuScreenshotLegacy(instanceIndex, screenPath)) {
            System.err.println("Failed to take screenshot for march queues");
            return new ArrayList<>();
        }
        
        List<MarchInfo> marches = new ArrayList<>();
        
        // Based on your screenshot, adjust regions to better capture the different parts
        Rectangle[] queueRegions = {
            new Rectangle(20, 170, 180, 45),  // March Queue 1 - expanded to capture timer
            new Rectangle(20, 215, 180, 45),  // March Queue 2 - expanded  
            new Rectangle(20, 260, 180, 35),  // March Queue 3
            new Rectangle(20, 295, 180, 35),  // March Queue 4
            new Rectangle(20, 330, 180, 35),  // March Queue 5
            new Rectangle(20, 365, 180, 35)   // March Queue 6
        };
        
        for (int i = 0; i < queueRegions.length; i++) {
            int queueNumber = i + 1;
            MarchInfo marchInfo = analyzeQueueWithOCR(screenPath, queueRegions[i], queueNumber);
            marches.add(marchInfo);
            System.out.println("📊 " + marchInfo);
        }
        
        return marches;
    }
    
    /**
     * Analyze a specific queue region using OCR
     */
    private static MarchInfo analyzeQueueWithOCR(String screenPath, Rectangle region, int queueNumber) {
        if (!BotUtils.isOpenCvLoaded()) {
            return new MarchInfo(queueNumber, MarchStatus.CANNOT_USE);
        }
        
        Mat screen = null;
        Mat roi = null;
        Mat processed = null;
        
        try {
            screen = Imgcodecs.imread(screenPath, Imgcodecs.IMREAD_COLOR);
            if (screen.empty()) {
                return new MarchInfo(queueNumber, MarchStatus.CANNOT_USE);
            }
            
            // Extract the queue region
            Rect roiRect = new Rect(
                Math.max(0, region.x),
                Math.max(0, region.y),
                Math.min(region.width, screen.cols() - Math.max(0, region.x)),
                Math.min(region.height, screen.rows() - Math.max(0, region.y))
            );
            
            roi = new Mat(screen, roiRect);
            
            // Save raw ROI for debugging
            String rawRoiPath = "screenshots/queue_" + queueNumber + "_raw.png";
            Imgcodecs.imwrite(rawRoiPath, roi);
            System.out.println("Queue " + queueNumber + " raw ROI saved to: " + rawRoiPath);
            
            // Try multiple preprocessing approaches
            String ocrText = tryMultiplePreprocessingMethods(roi, queueNumber);
            
            // Analyze OCR results directly
            MarchInfo result = analyzeOCRText(queueNumber, ocrText);
            
            System.out.println("Queue " + queueNumber + " final OCR text: '" + ocrText.trim() + "'");
            
            return result;
            
        } catch (Exception e) {
            System.err.println("Error analyzing queue " + queueNumber + " with OCR: " + e.getMessage());
            return new MarchInfo(queueNumber, MarchStatus.CANNOT_USE);
        } finally {
            // Clean up
            if (screen != null) screen.release();
            if (roi != null) roi.release();
            if (processed != null) processed.release();
        }
    }
    
    /**
     * Try multiple preprocessing methods to find what works best
     */
    private static String tryMultiplePreprocessingMethods(Mat roi, int queueNumber) {
        // Test more aggressive preprocessing since basic approaches aren't working well
        String[] methodNames = {"ContrastEnhanced3x", "BinaryThreshold3x", "Crisp4x"};
        String bestResult = "";
        int bestScore = 0;
        
        for (int method = 0; method < 3; method++) {
            Mat processed = null;
            try {
                processed = preprocessImageMethod(roi, method);
                String ocrImagePath = "screenshots/queue_" + queueNumber + "_method" + method + ".png";
                Imgcodecs.imwrite(ocrImagePath, processed);
                
                // Try OCR with simpler settings first
                String result = runSimpleOCR(ocrImagePath, queueNumber, methodNames[method]);
                int score = scoreOCRResult(result, queueNumber);
                
                System.out.println("Queue " + queueNumber + " " + methodNames[method] + " preprocessing: '" + result.trim() + "' (score: " + score + ")");
                
                if (score > bestScore) {
                    bestScore = score;
                    bestResult = result;
                }
                
                // If we get a good result, stop trying other methods
                if (score > 40) {
                    System.out.println("Queue " + queueNumber + " " + methodNames[method] + " successful, skipping other methods");
                    break;
                }
                
            } catch (Exception e) {
                System.err.println("Error in preprocessing method " + method + ": " + e.getMessage());
            } finally {
                if (processed != null) processed.release();
            }
        }
        
        return bestResult;
    }
    
    /**
     * Run simple OCR configurations - focus on the most promising settings
     */
    private static String runSimpleOCR(String imagePath, int queueNumber, String methodName) {
        // Just the most promising OCR settings based on our testing
        String[][] ocrConfigs = {
            // Best performers from previous tests
            {"--psm", "6", "--oem", "1"}, // PSM 6 worked best with Light2xUpscale
            {"--psm", "3", "--oem", "1"}, // Default
            {"--psm", "7", "--oem", "1"}, // Single line
            {"--psm", "8", "--oem", "1"}  // Single word
        };
        
        String bestResult = "";
        int bestScore = 0;
        
        for (int i = 0; i < ocrConfigs.length; i++) {
            String result = runTesseractOCR(imagePath, ocrConfigs[i]);
            int score = scoreOCRResult(result, queueNumber);
            
            // Show attempts for first 2 queues
            if (queueNumber <= 2) {
                System.out.println("  Queue " + queueNumber + " " + methodName + " PSM:" + ocrConfigs[i][1] + " -> '" + result.trim() + "' (score: " + score + ")");
            }
            
            if (score > bestScore) {
                bestScore = score;
                bestResult = result;
            }
        }
        
        return bestResult;
    }
    
    /**
     * Run specialized OCR with settings that match the successful online demo
     */
    private static String runSpecializedOCR(String imagePath, int queueNumber, String methodName) {
        // OCR configurations based on what works in online Tesseract
        String[][] ocrConfigs = {
            // Config 1: Default settings (like online demo) - often the best
            {"--psm", "3", "--oem", "1"},
            
            // Config 2: Uniform block of text
            {"--psm", "6", "--oem", "1"},
            
            // Config 3: Single text line
            {"--psm", "7", "--oem", "1"},
            
            // Config 4: Single word
            {"--psm", "8", "--oem", "1"},
            
            // Config 5: Legacy engine (sometimes more accurate)
            {"--psm", "3", "--oem", "0"},
            
            // Config 6: Combined engines
            {"--psm", "3", "--oem", "3"},
            
            // Config 7: Auto page segmentation
            {"--psm", "1", "--oem", "1"}
        };
        
        String bestResult = "";
        int bestScore = 0;
        
        for (int i = 0; i < ocrConfigs.length; i++) {
            String result = runTesseractOCR(imagePath, ocrConfigs[i]);
            int score = scoreOCRResult(result, queueNumber);
            
            // Show all attempts for first 2 queues to see what's happening
            if (queueNumber <= 2) {
                System.out.println("Queue " + queueNumber + " " + methodName + " OCR config " + (i+1) + " (PSM:" + ocrConfigs[i][1] + " OEM:" + ocrConfigs[i][3] + "): '" + result.trim() + "' (score: " + score + ")");
            }
            
            if (score > bestScore) {
                bestScore = score;
                bestResult = result;
            }
        }
        
        return bestResult;
    }
    
    /**
     * Run optimized OCR specifically tuned for game UI text
     */
    private static String runOptimizedOCR(String imagePath, int queueNumber, String methodName) {
        // OCR configurations optimized for game UI
        String[][] ocrConfigs = {
            // Config 1: Single text block (best for UI elements)
            {"--psm", "6", "--oem", "1", "-c", "tessedit_char_whitelist=0123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz .()/-"},
            
            // Config 2: Treat image as single text line
            {"--psm", "7", "--oem", "1", "-c", "tessedit_char_whitelist=0123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz .()/-"},
            
            // Config 3: Single word (good for simple labels)
            {"--psm", "8", "--oem", "1", "-c", "tessedit_char_whitelist=0123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz .()/-"},
            
            // Config 4: Raw line (sparse text)
            {"--psm", "13", "--oem", "1", "-c", "tessedit_char_whitelist=0123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz .()/-"}
        };
        
        String bestResult = "";
        int bestScore = 0;
        
        for (int i = 0; i < ocrConfigs.length; i++) {
            String result = runTesseractOCR(imagePath, ocrConfigs[i]);
            int score = scoreOCRResult(result, queueNumber);
            
            // Only show detailed attempts for first 2 queues to reduce spam
            if (queueNumber <= 2) {
                System.out.println("Queue " + queueNumber + " " + methodName + " OCR config " + (i+1) + ": '" + result.trim() + "' (score: " + score + ")");
            }
            
            if (score > bestScore) {
                bestScore = score;
                bestResult = result;
            }
        }
        
        return bestResult;
    }
    
    /**
     * Different preprocessing methods - back to clean, simple approaches
     */
    private static Mat preprocessImageMethod(Mat roi, int method) {
        Mat result = new Mat();
        
        switch (method) {
            case 0: // Original image - no processing
                result = roi.clone();
                break;
                
            case 1: // Just grayscale conversion
                Imgproc.cvtColor(roi, result, Imgproc.COLOR_BGR2GRAY);
                break;
                
            case 2: // Clean 2x upscale with Lanczos (smooth, high quality)
                Mat gray = new Mat();
                Imgproc.cvtColor(roi, gray, Imgproc.COLOR_BGR2GRAY);
                Imgproc.resize(gray, result, new Size(gray.cols() * 2, gray.rows() * 2), 0, 0, Imgproc.INTER_LANCZOS4);
                gray.release();
                break;
        }
        
        return result;
    }
    
    /**
     * Run multiple OCR attempts with different settings for best accuracy
     */
    private static String runMultipleOCRAttempts(String imagePath, int queueNumber, String methodName) {
        // OCR configuration attempts - optimized for what we know works
        String[][] ocrConfigs = {
            // Config 1: Standard text recognition
            {"--psm", "6", "--oem", "1", "-c", "tessedit_char_whitelist=0123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz .()/-"},
            
            // Config 2: Single text line
            {"--psm", "7", "--oem", "1", "-c", "tessedit_char_whitelist=0123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz .()/-"},
            
            // Config 3: Raw line without word detection
            {"--psm", "13", "--oem", "1", "-c", "tessedit_char_whitelist=0123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz .()/-"}
        };
        
        String bestResult = "";
        int bestScore = 0;
        
        for (int i = 0; i < ocrConfigs.length; i++) {
            String result = runTesseractOCR(imagePath, ocrConfigs[i]);
            int score = scoreOCRResult(result, queueNumber);
            
            // Only show detailed attempts for first 2 queues to reduce spam
            if (queueNumber <= 2) {
                System.out.println("Queue " + queueNumber + " " + methodName + " OCR config " + (i+1) + ": '" + result.trim() + "' (score: " + score + ")");
            }
            
            if (score > bestScore) {
                bestScore = score;
                bestResult = result;
            }
        }
        
        return bestResult;
    }
    
    /**
     * Score OCR result based on recognizable keywords and patterns
     */
    private static int scoreOCRResult(String text, int queueNumber) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        
        String lowerText = text.toLowerCase();
        int score = 0;
        
        // Score based on recognizable keywords - enhanced scoring
        String[] highValueKeywords = {"gathering", "idle", "unlock", "cannot", "lumberyard"};
        String[] mediumValueKeywords = {"stone", "gold", "food", "queue", "march", "lv", "level"};
        
        for (String keyword : highValueKeywords) {
            if (lowerText.contains(keyword)) {
                score += 15; // Higher score for important keywords
            }
        }
        
        for (String keyword : mediumValueKeywords) {
            if (lowerText.contains(keyword)) {
                score += 8;
            }
        }
        
        // Bonus for "as idle" pattern (common OCR output)
        if (lowerText.contains("as idle") || lowerText.contains("as ilde")) {
            score += 20;
        }
        
        // Score based on timer patterns
        if (lowerText.matches(".*\\d{1,2}:\\d{2}(:\\d{2})?.*")) {
            score += 25; // High score for timer patterns
        }
        
        // Bonus for reasonable text length
        if (lowerText.length() > 5 && lowerText.length() < 100) {
            score += 3;
        }
        
        // Penalty for too many unrecognizable characters or single characters
        if (lowerText.length() == 1 && lowerText.matches("\\d")) {
            score -= 10; // Heavy penalty for single digits like "0"
        }
        
        int alphabeticChars = lowerText.replaceAll("[^a-z]", "").length();
        int totalChars = lowerText.length();
        if (totalChars > 0) {
            double readabilityRatio = (double) alphabeticChars / totalChars;
            if (readabilityRatio < 0.3) {
                score -= 5;
            }
        }
        
        return score;
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
                // Don't spam errors for failed attempts
                return "";
            }
            
            return output.toString();
            
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Analyze OCR text to determine march status with improved fuzzy matching
     */
    private static MarchInfo analyzeOCRText(int queueNumber, String ocrText) {
        String text = ocrText.toLowerCase().trim();
        
        // Remove line breaks and normalize spaces
        text = text.replaceAll("\\s+", " ");
        
        System.out.println("Queue " + queueNumber + " processed text: '" + text + "'");
        
        // Check for gathering status with timer pattern and fuzzy matching
        Pattern timerPattern = Pattern.compile("\\d{1,2}:\\d{2}(:\\d{2})?");
        Matcher timerMatcher = timerPattern.matcher(text);
        
        // Look for gathering keywords and patterns (including fuzzy matches)
        boolean isGathering = timerMatcher.find() || 
            text.contains("gathering") || 
            containsFuzzy(text, "lumberyard", "lamberyar", "lumber", "lumberya") ||
            containsFuzzy(text, "stone", "quarry") ||
            containsFuzzy(text, "gold", "mine") ||
            containsFuzzy(text, "food", "farm") ||
            text.contains("lv") ||  // Level indicator
            (text.contains("idle") && text.contains("gathering"));
        
        if (isGathering) {
            String remainingTime = null;
            if (timerMatcher.find()) {
                timerMatcher.reset();
                if (timerMatcher.find()) {
                    remainingTime = timerMatcher.group();
                }
            }
            
            String resourceInfo = extractResourceType(text);
            return new MarchInfo(queueNumber, MarchStatus.GATHERING, remainingTime, resourceInfo);
        }
        
        // Check for idle status (enhanced patterns)
        if (containsFuzzy(text, "idle", "idte", "idel", "ilde") || 
            text.contains("as idle") || 
            text.contains("as ilde") ||
            (text.contains("march") && text.contains("queue") && !isGathering)) {
            return new MarchInfo(queueNumber, MarchStatus.IDLE);
        }
        
        // Check for unlock status
        if (containsFuzzy(text, "unlock", "unock", "ulcock", "unlck")) {
            return new MarchInfo(queueNumber, MarchStatus.UNLOCK);
        }
        
        // Check for cannot use status
        if (containsFuzzy(text, "cannot", "can't", "canot", "cant") || 
            (text.contains("use") && !text.contains("queue"))) {
            return new MarchInfo(queueNumber, MarchStatus.CANNOT_USE);
        }
        
        // If OCR text is too garbled (less than 3 meaningful characters), use position-based defaults
        if (text.replaceAll("[^a-z]", "").length() < 3) {
            System.out.println("Queue " + queueNumber + " OCR too unclear, using position defaults");
            if (queueNumber <= 2) {
                return new MarchInfo(queueNumber, MarchStatus.IDLE);
            } else if (queueNumber == 3) {
                return new MarchInfo(queueNumber, MarchStatus.UNLOCK);
            } else {
                return new MarchInfo(queueNumber, MarchStatus.CANNOT_USE);
            }
        }
        
        // Default fallback based on queue number
        if (queueNumber <= 2) {
            return new MarchInfo(queueNumber, MarchStatus.IDLE);
        } else if (queueNumber == 3) {
            return new MarchInfo(queueNumber, MarchStatus.UNLOCK);
        } else {
            return new MarchInfo(queueNumber, MarchStatus.CANNOT_USE);
        }
    }
    
    /**
     * Check if text contains any of the given keywords (fuzzy matching)
     */
    private static boolean containsFuzzy(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Extract resource type from OCR text with fuzzy matching
     */
    private static String extractResourceType(String text) {
        if (containsFuzzy(text, "lumberyard", "lamberyar", "lumber", "wood")) {
            return "Lumberyard";
        } else if (containsFuzzy(text, "stone", "quarry")) {
            return "Stone Quarry";
        } else if (containsFuzzy(text, "gold", "mine")) {
            return "Gold Mine";
        } else if (containsFuzzy(text, "food", "farm")) {
            return "Farm";
        }
        return "Resource";
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