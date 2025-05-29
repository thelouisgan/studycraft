package bogget.studycraft.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextFieldWidget.class)
public interface TextFieldWidgetAccessor {
    @Accessor("textRenderer")
    TextRenderer getTextRenderer();

    @Accessor("editable")
    boolean isEditable();
}
