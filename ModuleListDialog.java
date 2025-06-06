package newgame;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class ModuleListDialog extends JDialog {
    private final Main main;
    private final MemuInstance instance;
    private final Map<String, JCheckBox> checkboxes = new LinkedHashMap<>();

    public ModuleListDialog(Main main, MemuInstance instance) {
        super(main, "Module Configuration - " + instance.name, true);
        this.main = main;
        this.instance = instance;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        setSize(400, 200);
        setLocationRelativeTo(getParent());

        JPanel modulePanel = new JPanel();
        modulePanel.setLayout(new BoxLayout(modulePanel, BoxLayout.Y_AXIS));
        modulePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Auto Start Game and Auto Gather Resources modules
        String[] modules = {"Auto Start Game", "Auto Gather Resources"};
        Map<String, ModuleState<?>> instanceModules = Main.instanceModules
            .getOrDefault(instance.index, new HashMap<>());

        for (String module : modules) {
            JCheckBox cb = new JCheckBox(module);
            cb.setSelected(instanceModules.containsKey(module) && 
                instanceModules.get(module).enabled);
            cb.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            checkboxes.put(module, cb);
            modulePanel.add(cb);
            modulePanel.add(Box.createVerticalStrut(10));
        }

        JButton executeGatherBtn = new JButton("Execute Gather");
        executeGatherBtn.addActionListener(e -> executeGathering());
        
        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> saveSettings());

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(executeGatherBtn);
        bottomPanel.add(saveBtn);

        add(new JScrollPane(modulePanel), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void executeGathering() {
        // Start the new gathering task
        new GatherResourcesTask(instance).execute();
        dispose();
    }

    private void saveSettings() {
        Map<String, ModuleState<?>> modules = new HashMap<>();
        checkboxes.forEach((name, cb) -> {
            ModuleState<?> current = Main.instanceModules
                .getOrDefault(instance.index, new HashMap<>())
                .get(name);
            modules.put(name, new ModuleState<>(cb.isSelected(), 
                current != null ? current.settings : null));
        });
        
        Main.instanceModules.put(instance.index, modules);
        main.saveSettings();
        dispose();
    }
}