package newgame;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.IOException;

public class ModuleStateAdapter extends TypeAdapter<ModuleState<?>> {
    @Override
    public void write(JsonWriter out, ModuleState<?> value) throws IOException {
        out.beginObject();
        out.name("enabled").value(value.enabled);
        
        if (value.settings != null) {
            out.name("settings");
            // For now, we'll just write a simple string representation
            // You can expand this when you add new module types
            out.value(value.settings.toString());
        }
        out.endObject();
    }

    @Override
    public ModuleState<?> read(JsonReader in) throws IOException {
        boolean enabled = false;
        String settings = null;
        
        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            if (name.equals("enabled")) {
                enabled = in.nextBoolean();
            } else if (name.equals("settings")) {
                settings = in.nextString();
            }
        }
        in.endObject();
        
        // For now, just return with null settings since we removed GatherResourcesDialog
        // You can expand this when you add new module types
        return new ModuleState<>(enabled, null);
    }
}