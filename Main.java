package newgame;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

public class Main extends JFrame {
    public static final String MEMUC_PATH = "C:\\Program Files\\Microvirt\\MEmu\\memuc.exe";
    private DefaultTableModel tableModel;
    private JTable instancesTable;
    public static Map<Integer, Map<String, ModuleState<?>>> instanceModules = new HashMap<>();
    private List<MemuInstance> instances = new ArrayList<>();
    private javax.swing.Timer statusTimer;
    private JButton optimizeAllButton;

    public Main() {
        // Initialize BotUtils
        BotUtils.init();
        
        configureWindow();
        initializeUI();
        loadSettings();
        refreshInstances();
        startStatusUpdater();
    }

    private void configureWindow() {
        setTitle("MEmu Instance Manager");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(null);
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("Failed to set LAF: " + ex);
        }
    }

    private void initializeUI() {
        setLayout(new BorderLayout());

        tableModel = new DefaultTableModel(
            new Object[]{"Index", "Name", "Status", "Serial", "Actions"}, 0) {
            @Override public boolean isCellEditable(int row, int col) {
                return col == 4; // Actions column is now column 4
            }
        };

        instancesTable = new JTable(tableModel);
        configureTable();

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.add(createButton("Refresh", e -> refreshInstances()));
        topPanel.add(createButton("Create", e -> createInstance()));
        
        // Add the Optimize All button
        optimizeAllButton = createButton("Optimize All", e -> optimizeAllInstances());
        optimizeAllButton.setBackground(new Color(34, 139, 34)); // Green background
        optimizeAllButton.setToolTipText("Optimize all stopped instances to 400x652 resolution");
        topPanel.add(optimizeAllButton);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(instancesTable), BorderLayout.CENTER);
    }

    private void configureTable() {
        instancesTable.setRowHeight(40);
        instancesTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        instancesTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        instancesTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        instancesTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        instancesTable.getColumnModel().getColumn(4).setPreferredWidth(350); // Actions column

        TableColumn actionsCol = instancesTable.getColumnModel().getColumn(4);
        actionsCol.setCellRenderer(new ActionCellRenderer(this, instancesTable));
        actionsCol.setCellEditor(new ActionCellRenderer(this, instancesTable));
    }

    private JButton createButton(String text, ActionListener action) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.addActionListener(action);
        return btn;
    }

    private void optimizeAllInstances() {
        // Get all stopped instances
        List<MemuInstance> stoppedInstances = instances.stream()
            .filter(inst -> "Stopped".equals(inst.status))
            .collect(java.util.stream.Collectors.toList());
        
        if (stoppedInstances.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "No stopped instances found to optimize.\nOnly stopped instances can be optimized.", 
                "No Instances to Optimize", 
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // Confirm with user
        int result = JOptionPane.showConfirmDialog(this,
            "This will optimize " + stoppedInstances.size() + " stopped instance(s) to 400x652 resolution.\n" +
            "This process may take several minutes.\n\nContinue?",
            "Optimize All Instances",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        
        // Disable the button during optimization
        optimizeAllButton.setEnabled(false);
        optimizeAllButton.setText("Optimizing...");
        
        // Start the optimization process
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            private int currentInstance = 0;
            private int totalInstances = stoppedInstances.size();
            private int successCount = 0;
            private int failureCount = 0;
            
            @Override
            protected Void doInBackground() throws Exception {
                publish("🚀 Starting batch optimization of " + totalInstances + " instances...");
                
                for (MemuInstance instance : stoppedInstances) {
                    currentInstance++;
                    publish("📋 Processing instance " + currentInstance + "/" + totalInstances + 
                           " (Index: " + instance.index + ", Name: " + instance.name + ")");
                    
                    // Update instance state in UI
                    SwingUtilities.invokeLater(() -> {
                        instance.setState("Optimizing (" + currentInstance + "/" + totalInstances + ")...");
                        refreshInstanceInTable(instance);
                    });
                    
                    boolean success = optimizeSingleInstance(instance.index);
                    
                    if (success) {
                        successCount++;
                        publish("✅ Instance " + instance.index + " optimized successfully");
                        SwingUtilities.invokeLater(() -> {
                            instance.setState("Optimized ✅");
                            refreshInstanceInTable(instance);
                        });
                    } else {
                        failureCount++;
                        publish("❌ Instance " + instance.index + " optimization failed");
                        SwingUtilities.invokeLater(() -> {
                            instance.setState("Optimization failed ❌");
                            refreshInstanceInTable(instance);
                        });
                    }
                    
                    // Small delay between instances
                    Thread.sleep(1000);
                }
                
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    System.out.println(message);
                }
            }
            
            @Override
            protected void done() {
                // Re-enable the button
                optimizeAllButton.setEnabled(true);
                optimizeAllButton.setText("Optimize All");
                
                // Show summary
                String summary = String.format(
                    "Batch optimization completed!\n\n" +
                    "✅ Successful: %d\n" +
                    "❌ Failed: %d\n" +
                    "📊 Total: %d",
                    successCount, failureCount, totalInstances
                );
                
                System.out.println("🎉 " + summary.replace("\n", " "));
                
                JOptionPane.showMessageDialog(Main.this, summary, 
                    "Optimization Complete", 
                    JOptionPane.INFORMATION_MESSAGE);
                
                // Refresh the table
                refreshInstances();
            }
        };
        
        worker.execute();
    }
    
    private boolean optimizeSingleInstance(int index) {
        try {
            System.out.println("🔧 Optimizing instance " + index + "...");
            
            // Step 1: Ensure instance is stopped
            ProcessBuilder stopBuilder = new ProcessBuilder(MEMUC_PATH, "stop", "-i", String.valueOf(index));
            Process stopProcess = stopBuilder.start();
            boolean stopSuccess = stopProcess.waitFor(15, TimeUnit.SECONDS);
            
            if (!stopSuccess) {
                System.err.println("❌ Failed to stop instance " + index);
                return false;
            }
            
            Thread.sleep(3000); // Wait for complete shutdown
            
            // Step 2: Modify resolution
            ProcessBuilder modifyBuilder = new ProcessBuilder(
                MEMUC_PATH, "modify", "-i", String.valueOf(index),
                "-resolution", "400x652", "-dpi", "133"
            );
            
            Process modifyProcess = modifyBuilder.start();
            boolean modifySuccess = modifyProcess.waitFor(30, TimeUnit.SECONDS) && 
                                  modifyProcess.exitValue() == 0;
            
            if (!modifySuccess) {
                System.err.println("❌ Resolution modification failed for instance " + index);
                return false;
            }
            
            Thread.sleep(2000);
            
            // Step 3: Start instance to verify
            ProcessBuilder startBuilder = new ProcessBuilder(MEMUC_PATH, "start", "-i", String.valueOf(index));
            Process startProcess = startBuilder.start();
            boolean startSuccess = startProcess.waitFor(20, TimeUnit.SECONDS) && 
                                 startProcess.exitValue() == 0;
            
            if (!startSuccess) {
                System.err.println("❌ Failed to start instance " + index + " for verification");
                return false;
            }
            
            Thread.sleep(15000); // Wait for full boot
            
            // Step 4: Verify resolution
            String testPath = "screenshots/optimize_verify_" + index + ".png";
            BotUtils.createDirectoryIfNeeded("screenshots");
            
            for (int attempt = 1; attempt <= 3; attempt++) {
                if (BotUtils.takeMenuScreenshotLegacy(index, testPath)) {
                    File testFile = new File(testPath);
                    if (testFile.exists() && BotUtils.openCvLoaded) {
                        org.opencv.core.Mat testMat = org.opencv.imgcodecs.Imgcodecs.imread(testPath, org.opencv.imgcodecs.Imgcodecs.IMREAD_GRAYSCALE);
                        if (!testMat.empty()) {
                            int width = testMat.cols();
                            int height = testMat.rows();
                            testMat.release();
                            
                            if (width == 400 && height == 652) {
                                System.out.println("✅ Instance " + index + " resolution verified: 400x652");
                                
                                // Stop the instance after verification
                                ProcessBuilder stopAfterBuilder = new ProcessBuilder(MEMUC_PATH, "stop", "-i", String.valueOf(index));
                                stopAfterBuilder.start();
                                
                                return true;
                            } else {
                                System.err.println("❌ Wrong resolution for instance " + index + ": " + width + "x" + height);
                            }
                        }
                    }
                }
                
                if (attempt < 3) {
                    Thread.sleep(3000);
                }
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("❌ Exception optimizing instance " + index + ": " + e.getMessage());
            return false;
        }
    }
    
    private void refreshInstanceInTable(MemuInstance instance) {
        for (int i = 0; i < instances.size(); i++) {
            if (instances.get(i).index == instance.index) {
                if (i < tableModel.getRowCount()) {
                    tableModel.setValueAt(instance.status, i, 2); // Status column
                }
                break;
            }
        }
    }

    public MemuInstance createInstance() {
        int newIndex = findNextAvailableIndex();
        MemuInstance newInstance = new MemuInstance(newIndex, "New Instance " + newIndex, "Stopped", "");
        instances.add(newInstance);
        saveSettings();
        refreshInstances();
        return newInstance;
    }

    private int findNextAvailableIndex() {
        return instances.stream()
            .mapToInt(inst -> inst.index)
            .max()
            .orElse(0) + 1;
    }

    public void refreshInstances() {
        new SwingWorker<List<MemuInstance>, Void>() {
            @Override protected List<MemuInstance> doInBackground() throws Exception {
                return getInstancesFromMemuc();
            }
            
            @Override protected void done() {
                try {
                    instances = get();
                    tableModel.setRowCount(0);
                    for (MemuInstance inst : instances) {
                        tableModel.addRow(new Object[]{
                            inst.index,
                            inst.name,
                            inst.status,
                            inst.deviceSerial,
                            "" // Actions column
                        });
                    }
                } catch (Exception ex) {
                    showError("Refresh Failed", "Couldn't get instances: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private List<MemuInstance> getInstancesFromMemuc() throws IOException {
        List<MemuInstance> result = new ArrayList<>();
        Process p = Runtime.getRuntime().exec(new String[]{MEMUC_PATH, "listvms"});
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    try {
                        int index = Integer.parseInt(parts[0].trim());
                        String name = parts[1].trim();
                        String status = getInstanceStatus(index);
                        result.add(new MemuInstance(index, name, status, ""));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return result;
    }

    private String getInstanceStatus(int index) {
        try {
            Process p = Runtime.getRuntime().exec(
                new String[]{MEMUC_PATH, "isvmrunning", "-i", String.valueOf(index)});
            
            boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return "Unknown";
            }
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String output = reader.readLine();
                if (output != null) {
                    String trimmedOutput = output.trim();
                    // Check for both "1" and "Running" responses
                    if (trimmedOutput.equals("1") || trimmedOutput.equalsIgnoreCase("Running")) {
                        return "Running";
                    } else {
                        return "Stopped";
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("Status check failed for instance " + index + ": " + ex.getMessage());
        }
        return "Unknown";
    }

    public void startInstance(int index) {
        MemuActions.startInstance(this, index, () -> {
            refreshInstances();
            // Remove the auto-optimization on start since we now have the optimize button
            enableAutoStartIfConfigured(index);
        });
    }

    private void enableAutoStartIfConfigured(int index) {
        Map<String, ModuleState<?>> modules = instanceModules.getOrDefault(index, Collections.emptyMap());
        
        ModuleState<?> autoStartModule = modules.get("Auto Start Game");
        if (autoStartModule != null && autoStartModule.enabled) {
            System.out.println("Auto Start Game is enabled for instance " + index);
            MemuInstance inst = getInstanceByIndex(index);
            if (inst != null) {
                new Thread(() -> {
                    try {
                        Thread.sleep(7000);
                        System.out.println("Starting AutoStartGameTask for instance " + index);
                        new AutoStartGameTask(inst, 10, () -> {
                            System.out.println("AutoStartGameTask completed for instance " + index);
                        }).execute();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
        }
    }

    public void stopInstance(int index) {
        MemuActions.stopInstance(this, index, this::refreshInstances);
    }

    public void showModulesDialog(MemuInstance instance) {
        new ModuleListDialog(this, instance).setVisible(true);
    }

    public MemuInstance getInstanceByIndex(int index) {
        return instances.stream()
            .filter(inst -> inst.index == index)
            .findFirst()
            .orElse(null);
    }

    private void startStatusUpdater() {
        statusTimer = new javax.swing.Timer(10000, e -> { // Check every 10 seconds instead of 3
            for (int i = 0; i < instances.size() && i < tableModel.getRowCount(); i++) {
                MemuInstance inst = instances.get(i);
                
                // Only check status occasionally to reduce spam
                if (Math.random() < 0.5) { // 50% chance each update
                    String actualStatus = getInstanceStatus(inst.index);
                    if (!actualStatus.equals(inst.status)) {
                        inst.status = actualStatus;
                        tableModel.setValueAt(actualStatus, i, 2); // Status is column 2
                    }
                }
                
                // Check resolution for running instances very rarely
                if ("Running".equals(inst.status) && Math.random() < 0.05) { // 5% chance
                    checkAndFixResolution(inst.index);
                }
            }
        });
        statusTimer.start();
    }
    
    private void checkAndFixResolution(int instanceIndex) {
        // Check resolution in background without blocking UI
        new Thread(() -> {
            try {
                String screenPath = "screenshots/resolution_check_" + instanceIndex + ".png";
                if (BotUtils.takeMenuScreenshotLegacy(instanceIndex, screenPath)) {
                    if (BotUtils.isOpenCvLoaded()) {
                        org.opencv.core.Mat screen = org.opencv.imgcodecs.Imgcodecs.imread(screenPath);
                        if (!screen.empty()) {
                            int currentWidth = screen.cols();
                            int currentHeight = screen.rows();
                            screen.release();
                            
                            // Check if resolution is wrong
                            if (currentWidth != MemuActions.getTargetWidth() || 
                                currentHeight != MemuActions.getTargetHeight()) {
                                
                                System.out.println("Auto-fixing resolution for instance " + instanceIndex + 
                                                 " from " + currentWidth + "x" + currentHeight + 
                                                 " to " + MemuActions.getTargetWidth() + "x" + MemuActions.getTargetHeight());
                                
                                MemuInstance inst = getInstanceByIndex(instanceIndex);
                                if (inst != null) {
                                    inst.setState("Fixing resolution...");
                                }
                                
                                MemuActions.ensureCorrectResolution(Main.this, instanceIndex, () -> {
                                    if (inst != null) {
                                        inst.setState("Resolution fixed");
                                    }
                                });
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Resolution check error for instance " + instanceIndex + ": " + ex.getMessage());
            }
        }).start();
    }

    public void saveSettings() {
        try (FileWriter writer = new FileWriter("settings.json")) {
            Gson gson = new GsonBuilder()
                .registerTypeAdapter(new TypeToken<ModuleState<?>>(){}.getType(), new ModuleStateAdapter())
                .setPrettyPrinting()
                .create();
            
            java.lang.reflect.Type type = new TypeToken<Map<Integer, Map<String, ModuleState<?>>>>(){}.getType();
            gson.toJson(instanceModules, type, writer);
        } catch (IOException ex) {
            showError("Save Failed", "Couldn't save settings: " + ex.getMessage());
        }
    }

    private void loadSettings() {
        File file = new File("settings.json");
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Gson gson = new GsonBuilder()
                    .registerTypeAdapter(new TypeToken<ModuleState<?>>(){}.getType(), new ModuleStateAdapter())
                    .create();
                
                java.lang.reflect.Type type = new TypeToken<Map<Integer, Map<String, ModuleState<?>>>>(){}.getType();
                Map<Integer, Map<String, ModuleState<?>>> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    instanceModules = loaded;
                }
            } catch (IOException | JsonParseException ex) {
                instanceModules = new HashMap<>();
            }
        } else {
            instanceModules = new HashMap<>();
        }
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}