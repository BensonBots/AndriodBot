package newgame;

import javax.swing.SwingWorker;
import java.util.List;

/**
 * New AutoGatherResources system using OCR-based march detection
 */
public class GatherResourcesTask extends SwingWorker<Void, String> {
    private final MemuInstance instance;
    private volatile boolean shouldStop = false;

    public GatherResourcesTask(MemuInstance instance) {
        this.instance = instance;
    }

    @Override
    protected Void doInBackground() throws Exception {
        try {
            instance.setAutoGatherRunning(true);
            instance.setState("Starting resource gathering...");
            
            System.out.println("🚀 Starting new GatherResourcesTask for instance " + instance.index);
            
            // Main gathering loop
            while (!shouldStop && !isCancelled()) {
                try {
                    // Step 1: Setup march view (open left panel + wilderness)
                    publish("🔧 Setting up march view...");
                    if (!MarchDetector.setupMarchView(instance.index)) {
                        publish("❌ Failed to setup march view, retrying in 30 seconds...");
                        Thread.sleep(30000);
                        continue;
                    }
                    
                    // Step 2: Read march queue statuses
                    publish("📋 Reading march queues...");
                    List<MarchDetector.MarchInfo> allQueues = MarchDetector.readMarchQueues(instance.index);
                    
                    if (allQueues.isEmpty()) {
                        publish("⚠️ No march queues detected, retrying in 30 seconds...");
                        Thread.sleep(30000);
                        continue;
                    }
                    
                    // Step 3: Check for available queues
                    List<MarchDetector.MarchInfo> availableQueues = MarchDetector.getAvailableQueues(allQueues);
                    
                    publish("📊 March Queue Status:");
                    for (MarchDetector.MarchInfo queue : allQueues) {
                        String icon = getStatusIcon(queue.status);
                        publish("   " + icon + " " + queue);
                    }
                    
                    // Step 4: Start marches if queues are available
                    if (!availableQueues.isEmpty()) {
                        publish("✅ Found " + availableQueues.size() + " available march queues");
                        
                        for (MarchDetector.MarchInfo queue : availableQueues) {
                            if (shouldStop) break;
                            
                            publish("🎯 Starting march on Queue " + queue.queueNumber);
                            
                            // Here you would implement the actual march starting logic
                            // For now, we'll just simulate it
                            if (startMarchOnQueue(queue.queueNumber)) {
                                publish("✅ Successfully started march on Queue " + queue.queueNumber);
                            } else {
                                publish("❌ Failed to start march on Queue " + queue.queueNumber);
                            }
                            
                            Thread.sleep(3000); // Wait between march starts
                        }
                    } else {
                        publish("⏳ No available march queues, waiting 60 seconds...");
                        
                        // Show current queue status when waiting
                        int gatheringCount = 0;
                        int unlockCount = 0;
                        int cannotUseCount = 0;
                        
                        for (MarchDetector.MarchInfo queue : allQueues) {
                            switch (queue.status) {
                                case GATHERING: gatheringCount++; break;
                                case UNLOCK: unlockCount++; break;
                                case CANNOT_USE: cannotUseCount++; break;
                            }
                        }
                        
                        publish("📈 Summary: " + gatheringCount + " gathering, " + 
                               unlockCount + " unlockable, " + cannotUseCount + " unusable");
                    }
                    
                    // Step 5: Wait before next check
                    if (!shouldStop) {
                        publish("💤 Waiting 60 seconds before next check...");
                        Thread.sleep(60000); // Check every minute
                    }
                    
                } catch (InterruptedException e) {
                    System.out.println("GatherResourcesTask interrupted");
                    break;
                } catch (Exception e) {
                    System.err.println("Error in gather resources loop: " + e.getMessage());
                    publish("❌ Error: " + e.getMessage());
                    Thread.sleep(30000); // Wait 30 seconds on error
                }
            }
            
        } finally {
            instance.setAutoGatherRunning(false);
            instance.setState("Resource gathering stopped");
            System.out.println("🛑 GatherResourcesTask stopped for instance " + instance.index);
        }
        
        return null;
    }
    
    /**
     * Start a march on a specific queue (placeholder implementation)
     */
    private boolean startMarchOnQueue(int queueNumber) {
        try {
            // TODO: Implement actual march starting logic here
            // This would involve:
            // 1. Clicking on the queue
            // 2. Selecting resource type and level
            // 3. Searching for resources
            // 4. Clicking gather button
            // 5. Deploying the march
            
            System.out.println("🎯 Starting march on queue " + queueNumber + " (placeholder)");
            Thread.sleep(2000); // Simulate march starting time
            
            return true; // Placeholder success
            
        } catch (Exception e) {
            System.err.println("Error starting march on queue " + queueNumber + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get status icon for display
     */
    private String getStatusIcon(MarchDetector.MarchStatus status) {
        switch (status) {
            case IDLE:
                return "✅";
            case GATHERING:
                return "🔄";
            case UNLOCK:
                return "🔒";
            case CANNOT_USE:
                return "❌";
            default:
                return "❓";
        }
    }
    
    @Override
    protected void process(List<String> chunks) {
        // Update instance state with latest message
        if (!chunks.isEmpty()) {
            String latestMessage = chunks.get(chunks.size() - 1);
            instance.setState(latestMessage);
            System.out.println("[Instance " + instance.index + "] " + latestMessage);
        }
    }
    
    @Override
    protected void done() {
        try {
            get(); // Check for exceptions
            instance.setState("Resource gathering completed");
            System.out.println("✅ GatherResourcesTask completed successfully for instance " + instance.index);
        } catch (Exception e) {
            System.err.println("GatherResourcesTask failed: " + e.getMessage());
            instance.setState("Resource gathering failed: " + e.getMessage());
        }
    }
    
    /**
     * Stop the gathering task
     */
    public void stopGathering() {
        shouldStop = true;
        cancel(true);
        System.out.println("🛑 Gathering task stop requested for instance " + instance.index);
    }
}