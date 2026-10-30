package mcjty.rftools.compat.jei;

import mcjty.rftools.blocks.storage.ModularStorageItemContainer;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferRegistry;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ModularStorageItemRecipeTransferHandler implements IRecipeTransferHandler<ModularStorageItemContainer> {

    public static void register(IRecipeTransferRegistry transferRegistry) {
        transferRegistry.addRecipeTransferHandler(new ModularStorageItemRecipeTransferHandler(), VanillaRecipeCategoryUid.CRAFTING);
    }

    @Override
    public Class<ModularStorageItemContainer> getContainerClass() {
        return ModularStorageItemContainer.class;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(@Nonnull ModularStorageItemContainer container, @Nonnull IRecipeLayout recipeLayout, @Nonnull EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        JeiTransfer.transfer(recipeLayout, player, JeiTransfer.stacksIn(container, container.getJEIRecipeAcceptor()), null, doTransfer);
        return null;
    }

}
