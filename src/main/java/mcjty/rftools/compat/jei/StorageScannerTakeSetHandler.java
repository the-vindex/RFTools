package mcjty.rftools.compat.jei;

import mcjty.rftools.blocks.storagemonitor.GuiStorageScanner;
import mcjty.rftools.blocks.storagemonitor.PacketRequestItem;
import mcjty.rftools.blocks.storagemonitor.StorageScannerContainer;
import mcjty.rftools.blocks.storagemonitor.StorageScannerTileEntity;
import mcjty.rftools.network.RFToolsMessages;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.LogManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * JEI '+' on an Astral Sorcery recipe (altar tiers, starlight infuser, grindstone, transmutation, lightwell) while the
 * Storage Scanner is open: takes ONE full set of the recipe's item inputs from the storage system into the player's
 * inventory (each click = one more set). Uses only stock server behaviour: one {@link PacketRequestItem} per stack, the
 * same request the scanner's item list sends on click (with the server default requestStraightToInventory=true the items
 * go to the player's inventory, otherwise into the scanner's output slot). Never returns a JEI error; what the storage
 * lacks is drawn red by {@link MissingOverlay} and named in one action-bar line. Fails soft.
 */
public class StorageScannerTakeSetHandler implements IRecipeTransferHandler<StorageScannerContainer> {

    /** Astral Sorcery's JEI category uids (ModIntegrationJEI in astralsorcery-1.12.2-1.10.27). */
    static final String[] AS_CATEGORIES = {
            "astralsorcery.altar.discovery", "astralsorcery.altar.attunement", "astralsorcery.altar.constellation",
            "astralsorcery.altar.trait", "astralsorcery.infuser", "astralsorcery.grindstone",
            "astralsorcery.lightTransmutation", "astralsorcery.lightwell"};

    public static void register(IRecipeTransferRegistry registry) {
        StorageScannerTakeSetHandler handler = new StorageScannerTakeSetHandler();
        for (String uid : AS_CATEGORIES) {
            registry.addRecipeTransferHandler(handler, uid);
        }
    }

    @Override
    public Class<StorageScannerContainer> getContainerClass() {
        return StorageScannerContainer.class;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(@Nonnull StorageScannerContainer container, @Nonnull IRecipeLayout recipeLayout,
                                               @Nonnull EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        try {
            take(container, recipeLayout, player, doTransfer);
        } catch (RuntimeException e) {
            LogManager.getLogger("rftools").warn("JEI take-set failed: " + e);
        }
        return null;
    }

    private static String id(int index) {
        return "s" + index;
    }

    static boolean matches(ItemStack wanted, ItemStack stored) {
        if (wanted.getItem() != stored.getItem()) {
            return false;
        }
        if (wanted.getMetadata() != OreDictionary.WILDCARD_VALUE && wanted.getMetadata() != stored.getMetadata()) {
            return false;
        }
        return !wanted.hasTagCompound() || ItemStack.areItemStackTagsEqual(wanted, stored);
    }

    private static void take(StorageScannerContainer container, IRecipeLayout recipeLayout, EntityPlayer player, boolean doTransfer) {
        List<ItemStack> storage = GuiStorageScanner.fromServer_inventory;
        Map<Integer, ? extends IGuiIngredient<ItemStack>> guiIngredients = recipeLayout.getItemStacks().getGuiIngredients();

        // Storage-only stock, one key per distinct stored stack; each slot's alternatives are the stored stacks that
        // match one of the stacks JEI shows for it (AS's ItemHandle: oredict list, exact stack or a filled bucket).
        Map<String, Long> stock = new HashMap<>();
        for (int i = 0; i < storage.size(); i++) {
            ItemStack s = storage.get(i);
            if (s != null && !s.isEmpty()) {
                stock.put(id(i), (long) s.getCount());
            }
        }
        Map<Integer, List<IngredientPicker.Alternative>> slots = new HashMap<>();
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : guiIngredients.entrySet()) {
            if (!entry.getValue().isInput()) {
                continue;
            }
            List<IngredientPicker.Alternative> alts = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            boolean any = false;
            for (ItemStack wanted : entry.getValue().getAllIngredients()) {
                if (wanted == null || wanted.isEmpty()) {
                    continue;
                }
                any = true;
                for (int i = 0; i < storage.size(); i++) {
                    ItemStack s = storage.get(i);
                    if (s != null && !s.isEmpty() && matches(wanted, s) && seen.add(i)) {
                        alts.add(new IngredientPicker.Alternative(id(i), wanted.getCount()));
                    }
                }
            }
            if (any) {
                // A slot with ingredients but nothing matching in storage gets an unsatisfiable alternative: missing.
                slots.put(entry.getKey(), alts.isEmpty()
                        ? Collections.singletonList(new IngredientPicker.Alternative("none", 1)) : alts);
            }
        }

        MissingIngredients.Assignment set = MissingIngredients.assign(slots, stock);
        List<String> missingNames = JeiTransfer.missingNames(guiIngredients, set.missing);
        MissingOverlay.record(recipeLayout, set.missing, missingNames);
        if (!doTransfer) {
            return;
        }

        Map<String, Long> want = set.totals();
        Map<String, Integer> maxStack = new HashMap<>();
        Map<String, Long> room = new HashMap<>();
        for (String key : want.keySet()) {
            ItemStack s = storage.get(Integer.parseInt(key.substring(1)));
            maxStack.put(key, s.getMaxStackSize());
            long r = 0;
            for (ItemStack inv : player.inventory.mainInventory) {
                if (!inv.isEmpty() && inv.isItemEqual(s) && ItemStack.areItemStackTagsEqual(inv, s)) {
                    r += Math.max(0, inv.getMaxStackSize() - inv.getCount());
                }
            }
            room.put(key, r);
        }
        int empty = 0;
        for (ItemStack inv : player.inventory.mainInventory) {
            if (inv.isEmpty()) {
                empty++;
            }
        }
        Map<String, Long> fits = MissingIngredients.fit(want, maxStack, room, empty);

        StorageScannerTileEntity te = container.getStorageScannerTileEntity();
        BlockPos invPos = GuiStorageScanner.fromServer_inventoryPos == null ? new BlockPos(-1, -1, -1) : GuiStorageScanner.fromServer_inventoryPos;
        long taken = 0;
        List<String> noRoom = new ArrayList<>();
        for (Map.Entry<String, Long> e : fits.entrySet()) {
            ItemStack stored = storage.get(Integer.parseInt(e.getKey().substring(1)));
            long left = e.getValue();
            int max = Math.max(1, stored.getMaxStackSize());
            while (left > 0) {
                int n = (int) Math.min(max, left);
                ItemStack request = stored.copy();
                request.setCount(1);
                RFToolsMessages.INSTANCE.sendToServer(new PacketRequestItem(te.getDimension(), te.getStorageScannerPos(), invPos, request, n));
                left -= n;
            }
            taken += e.getValue();
            long short_ = want.get(e.getKey()) - e.getValue();
            if (short_ > 0) {
                noRoom.add(stored.getDisplayName() + " x" + short_);
            }
            stored.shrink((int) Math.min(stored.getCount(), e.getValue()));   // keep the client's view in step until the next refresh
        }

        player.sendStatusMessage(new TextComponentString(summary(set.missing.isEmpty() && noRoom.isEmpty(), taken, missingNames, noRoom)), true);
    }

    static String summary(boolean fullSet, long taken, List<String> missing, List<String> noRoom) {
        StringBuilder sb = new StringBuilder(fullSet ? "Took 1 set (" + taken + " items)" : "Took " + taken + " items of 1 set");
        if (!missing.isEmpty()) {
            sb.append("; missing: ").append(String.join(", ", missing));
        }
        if (!noRoom.isEmpty()) {
            sb.append("; no room: ").append(String.join(", ", noRoom));
        }
        return sb.toString();
    }
}
