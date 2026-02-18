package bogget.studycraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
//import net.minecraft.text.Text;

public class QuizItem extends Item {
    
    public QuizItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);
        
        if (!world.isClient) {
            // Server-side: Tell the client to open the quiz screen
            //player.sendMessage(Text.literal("§6[StudyCraft]§r Opening quiz question..."), false);
            
            // No sound here - moved to the answer handling when correct
            
            // Cast to ServerPlayerEntity for the networking method
            StudycraftNetworking.sendOpenQuizPacket((ServerPlayerEntity) player);
        }
        
        // Don't consume the item
        return TypedActionResult.success(itemStack);
    }
}