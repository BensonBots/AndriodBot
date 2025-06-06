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
        optimizeAllButton.setToolTipText("Optimize all stopped instances to 480x800 resolution");
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
            "This will optimize " + stoppedInstances.size() + " stopped instance(s) to 480x800 resolution.\n" +
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
            System.out.println("🔧 Optimizing instance " + index + " (configuration only)...");
            
            // Step 1: Ensure instance is stopped
            ProcessBuilder stopBuilder = new ProcessBuilder(MEMUC_PATH, "stop", "-i", String.valueOf(index));
            Process stopProcess = stopBuilder.start();
            stopProcess.waitFor(15, TimeUnit.SECONDS);
            Thread.sleep(3000); // Wait for complete shutdown
            
            System.out.println("🔄 Configuring custom resolution 480x800...");
            
            // Configure resolution settings - NO INSTANCE STARTING
            ProcessBuilder[] commands = {
                new ProcessBuilder(MEMUC_PATH, "setconfigex", "-i", String.valueOf(index), "disable_resize", "0"),
                new ProcessBuilder(MEMUC_PATH, "setconfigex", "-i", String.valueOf(index), "is_customed_resolution", "1"),
                new ProcessBuilder(MEMUC_PATH, "setconfigex", "-i", String.valueOf(index), "custom_resolution", "480", "800", "160"),
                new ProcessBuilder(MEMUC_PATH, "setconfigex", "-i", String.valueOf(index), "is_full_screen", "0"),
                new ProcessBuilder(MEMUC_PATH, "setconfigex", "-i", String.valueOf(index), "start_window_mode", "1"),
                new ProcessBuilder(MEMUC_PATH, "setconfigex", "-i", String.valueOf(index), "win_scaling_percent2", "75")
            };
            
            // Execute all resolution commands
            for (ProcessBuilder cmd : commands) {
                try {
                    cmd.start().waitFor(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    System.err.println("⚠️ Command failed: " + String.join(" ", cmd.command()));
                }
            }
            
            System.out.println("✅ Resolution configured: 480x800@160dpi, windowed mode, 75% scaling");
            
            System.out.println("🔄 Applying performance optimizations...");
            
            // Configure performance settings - NO INSTANCE STARTING
            ProcessBuilder[] perfCommands = {
                new ProcessBuilder(MEMUC_PATH, "setconfigex", "-i", String.valueOf(index), "cpus", "2"),
                new ProcessBuilder(MEMUC_PATH, "setconfigex", "-i", String.valueOf(index), "memory", "3000"),
                new ProcessBuilder(MEMUC_PATH, "setconfigex", "-i", String.valueOf(index), "fps", "30")
            };
            
            // Execute all performance commands
            for (ProcessBuilder cmd : perfCommands) {
                try {
                    cmd.start().waitFor(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    System.err.println("⚠️ Performance command failed: " + String.join(" ", cmd.command()));
                }
            }
            
            System.out.println("✅ Performance settings: 2 CPU cores, 3GB RAM, 30 FPS");
            System.out.println("✅ Instance " + index + " optimization complete");
            System.out.println("📝 Instance " + index + " remains stopped - start manually when ready");
            
            // NO INSTANCE STARTING CODE HERE - CONFIGURATION ONLY
            return true;
            
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
        statusTimer = new javax.swing.Timer(10000, e -> { // Check every 10 seconds
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
                
                // Resolution checking removed - use manual "Optimize All" button instead
            }
        });
        statusTimer.start();
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