package bogget.studycraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class StudycraftClient implements ClientModInitializer {
    private static KeyBinding configKeyBinding;
    
    @Override
    public void onInitializeClient() {
        // Register networking handlers
        StudycraftNetworking.registerClientHandlers();
        
        // Register key binding
        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.studycraft.config",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_Y, // Default to Y key
            "category.studycraft.main"
        ));
        
        // Register tick event to check for key press
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (configKeyBinding.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new StudycraftConfigScreen(null));
                }
            }
        });
    }
}