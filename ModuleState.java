package newgame;

import java.io.Serializable;

public class ModuleState<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    public boolean enabled;
    public T settings;

    public ModuleState(boolean enabled, T settings) {
        this.enabled = enabled;
        this.settings = settings;
    }

    // Helper method to create a disabled module state
    public static <T> ModuleState<T> disabled() {
        return new ModuleState<>(false, null);
    }

    // Helper method to create an enabled module state with settings
    public static <T> ModuleState<T> enabled(T settings) {
        return new ModuleState<>(true, settings);
    }

    @Override
    public String toString() {
        return "ModuleState{" +
               "enabled=" + enabled +
               ", settings=" + (settings != null ? settings.toString() : "null") +
               '}';
    }
}