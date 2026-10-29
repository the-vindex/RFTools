package mcjty.rftools.compat.jei;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class RecipeMemoryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void savesAndLoads() throws Exception {
        Path file = tmp.getRoot().toPath().resolve("rftools/client_recipe_memory.json");
        RecipeMemory.Stack[] grid = new RecipeMemory.Stack[9];
        grid[0] = new RecipeMemory.Stack("ic2:ingot", 7, 1);
        grid[4] = new RecipeMemory.Stack("minecraft:chest", 0, 1);
        new RecipeMemory(file).record("ironchest:iron_chest@0", grid);

        assertTrue(Files.exists(file));
        assertFalse("temp file is moved into place", Files.exists(file.resolveSibling("client_recipe_memory.json.tmp")));
        RecipeMemory.Stack[] loaded = new RecipeMemory(file).get("ironchest:iron_chest@0");
        assertNotNull(loaded);
        assertEquals("ic2:ingot@7", loaded[0].key());
        assertEquals("minecraft:chest@0", loaded[4].key());
        assertEquals(1, loaded[4].count);
        assertNull(loaded[1]);
        assertNull(new RecipeMemory(file).get("minecraft:stick@0"));
    }

    @Test
    public void missingFileIsEmpty() {
        assertNull(new RecipeMemory(tmp.getRoot().toPath().resolve("none.json")).get("a@0"));
    }

    @Test
    public void corruptFileIsEmptyAndGetsReplaced() throws Exception {
        Path file = tmp.getRoot().toPath().resolve("memory.json");
        Files.write(file, "{ not json".getBytes(StandardCharsets.UTF_8));
        RecipeMemory memory = new RecipeMemory(file);
        assertNull(memory.get("a@0"));
        memory.record("a@0", new RecipeMemory.Stack[]{new RecipeMemory.Stack("b", 0, 1), null, null, null, null, null, null, null, null});
        assertEquals("b@0", new RecipeMemory(file).get("a@0")[0].key());
    }
}
