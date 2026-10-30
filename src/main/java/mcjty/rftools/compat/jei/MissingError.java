package mcjty.rftools.compat.jei;

import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * "Some ingredients are missing" for the JEI '+' button: JEI's own slot error (tooltip plus red slots on hover), with the
 * type upgraded to COSMETIC (button stays clickable and transfers what is available) when the running HEI has it
 * (4.37.0+). The pack's HEI 4.34.3 only has INTERNAL and USER_FACING, so there the button is disabled while anything is
 * missing, exactly like stock JEI.
 */
public class MissingError implements IRecipeTransferError {

    private static final Type COSMETIC = cosmetic();

    private final IRecipeTransferError delegate;

    private MissingError(IRecipeTransferError delegate) {
        this.delegate = delegate;
    }

    public static IRecipeTransferError create(Collection<Integer> missingSlots) {
        return new MissingError(JeiTransfer.handlerHelper().createUserErrorForSlots(I18n.format("jei.tooltip.error.recipe.transfer.missing"), missingSlots));
    }

    private static Type cosmetic() {
        try {
            return Type.valueOf("COSMETIC");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public Type getType() {
        return COSMETIC != null ? COSMETIC : delegate.getType();
    }

    @Override
    public void showError(Minecraft minecraft, int mouseX, int mouseY, IRecipeLayout recipeLayout, int recipeX, int recipeY) {
        delegate.showError(minecraft, mouseX, mouseY, recipeLayout, recipeX, recipeY);
    }

    @Nullable
    @Override
    public String getSimpleReason() {
        return delegate.getSimpleReason();
    }
}
