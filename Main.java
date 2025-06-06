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

    public Main() {
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
            // Automatically optimize first, then start AutoStart after optimization
            optimizeInstanceInBackground(index);
        });
    }
    
    private void optimizeInstanceInBackground(int index) {
        // Check if optimization is already running for this instance
        MemuInstance inst = getInstanceByIndex(index);
        if (inst != null && inst.state.contains("optimizing")) {
            System.out.println("Optimization already in progress for instance " + index);
            return;
        }
        
        // Run optimization first, then start instance
        if (inst != null) {
            inst.setState("Optimizing before start...");
        }
        
        MemuActions.optimizeInstanceInBackground(index, () -> {
            if (inst != null) {
                inst.setState("Optimized - Starting...");
            }
            // After optimization, start the instance
            MemuActions.startInstance(this, index, () -> {
                refreshInstances();
                // Wait a moment then enable AutoStart if configured
                new Thread(() -> {
                    try {
                        Thread.sleep(5000); // Give instance time to fully boot
                        SwingUtilities.invokeLater(() -> {
                            enableAutoStartIfConfigured(index);
                            refreshInstances();
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            });
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