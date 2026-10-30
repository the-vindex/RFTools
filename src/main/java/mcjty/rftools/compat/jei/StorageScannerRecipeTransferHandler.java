package mcjty.rftools.compat.jei;

import mcjty.rftools.blocks.storagemonitor.GuiStorageScanner;
import mcjty.rftools.blocks.storagemonitor.StorageScannerContainer;
import mcjty.rftools.blocks.storagemonitor.StorageScannerTileEntity;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class StorageScannerRecipeTransferHandler implements IRecipeTransferHandler<StorageScannerContainer> {

    public static void register(IRecipeTransferRegistry transferRegistry) {
        transferRegistry.addRecipeTransferHandler(new StorageScannerRecipeTransferHandler(), VanillaRecipeCategoryUid.CRAFTING);
    }

    @Override
    public Class<StorageScannerContainer> getContainerClass() {
        return StorageScannerContainer.class;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(@Nonnull StorageScannerContainer container, @Nonnull IRecipeLayout recipeLayout, @Nonnull EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        StorageScannerTileEntity te = container.getStorageScannerTileEntity();
        BlockPos pos = te.getCraftingGridContainerPos();

        return JeiTransfer.transfer(recipeLayout, player, GuiStorageScanner.fromServer_inventory, pos, doTransfer);
    }

}
