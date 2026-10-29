package mcjty.rftools.compat.jei;

import com.google.gson.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Client-side memory of how the player last filled the crafting grid for a result: result key ("id@meta") -> the 9 grid
 * stacks (id, meta, count; no NBT). Stored as JSON, written with a temp file + atomic move. No Minecraft types.
 */
public class RecipeMemory {

    private static final Logger LOGGER = LogManager.getLogger("rftools");

    public static class Stack {
        public final String id;
        public final int meta;
        public final int count;

        public Stack(String id, int meta, int count) {
            this.id = id;
            this.meta = meta;
            this.count = count;
        }

        public String key() {
            return id + "@" + meta;
        }
    }

    private final Path file;
    private Map<String, Stack[]> entries;

    public RecipeMemory(Path file) {
        this.file = file;
    }

    /** The 9 remembered grid stacks (null = empty slot), or null if the result has no entry. */
    public Stack[] get(String resultKey) {
        return load().get(resultKey);
    }

    public void record(String resultKey, Stack[] grid) {
        load().put(resultKey, grid.clone());
        save();
    }

    private Map<String, Stack[]> load() {
        if (entries != null) {
            return entries;
        }
        entries = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return entries;
        }
        try {
            JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonArray array = e.getValue().getAsJsonArray();
                Stack[] grid = new Stack[9];
                for (int i = 0; i < 9 && i < array.size(); i++) {
                    if (array.get(i).isJsonObject()) {
                        JsonObject o = array.get(i).getAsJsonObject();
                        grid[i] = new Stack(o.get("id").getAsString(), o.get("meta").getAsInt(), o.get("count").getAsInt());
                    }
                }
                entries.put(e.getKey(), grid);
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Ignoring unreadable recipe memory " + file + ": " + ex);
            entries = new LinkedHashMap<>();
        }
        return entries;
    }

    private void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Stack[]> e : entries.entrySet()) {
            JsonArray array = new JsonArray();
            for (Stack s : e.getValue()) {
                if (s == null) {
                    array.add(JsonNull.INSTANCE);
                } else {
                    JsonObject o = new JsonObject();
                    o.addProperty("id", s.id);
                    o.addProperty("meta", s.meta);
                    o.addProperty("count", s.count);
                    array.add(o);
                }
            }
            root.add(e.getKey(), array);
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            LOGGER.warn("Could not save recipe memory " + file + ": " + ex);
        }
    }
}
