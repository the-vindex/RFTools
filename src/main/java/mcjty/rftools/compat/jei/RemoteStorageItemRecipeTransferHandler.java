package mcjty.rftools.compat.jei;

import mcjty.rftools.blocks.storage.RemoteStorageItemContainer;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferRegistry;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RemoteStorageItemRecipeTransferHandler implements IRecipeTransferHandler<RemoteStorageItemContainer> {

    public static void register(IRecipeTransferRegistry transferRegistry) {
        transferRegistry.addRecipeTransferHandler(new RemoteStorageItemRecipeTransferHandler(), VanillaRecipeCategoryUid.CRAFTING);
    }

    @Override
    public Class<RemoteStorageItemContainer> getContainerClass() {
        return RemoteStorageItemContainer.class;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(@Nonnull RemoteStorageItemContainer container, @Nonnull IRecipeLayout recipeLayout, @Nonnull EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        if (doTransfer) {
            JeiTransfer.transfer(recipeLayout, player, JeiTransfer.stacksIn(container, container.getJEIRecipeAcceptor()), null);
        }

        return null;
    }

}
