package bogget.studycraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.FoodComponent;

import org.apache.logging.log4j.core.jmx.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Studycraft implements ModInitializer {
    public static final String MOD_ID = "studycraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static QuizStatistics quizStatistics;
    private static ClientStatistics clientStatistics = new ClientStatistics();

    // Server configuration variables
    private static int serverHungerInterval = 40; // Default: 2 seconds (40 ticks)
    private static int serverHungerGain = 4; // Default: 2 hunger points (1 drumstick)

    public static QuizStatistics getQuizStatistics() {
        return quizStatistics;
    }

    public static ClientStatistics getClientStats() {
        if (clientStatistics == null) {
            clientStatistics = new ClientStatistics();
        }
        return clientStatistics;
    }

    public static void updateQuestionBank(String newContent) {
        // Set the new content and reload questions
        QuestionBank newQuestionBank = new QuestionBank(newContent);
        setQuestionBank(newQuestionBank);
        LOGGER.info("Question bank updated with {} questions", newQuestionBank.getQuestionCount());
    }
    
    // Server configuration getters and setters
    public static int getServerHungerInterval() {
        return serverHungerInterval;
    }
    
    public static void setServerHungerInterval(int interval) {
        serverHungerInterval = interval;
        LOGGER.info("Server hunger interval set to {} ticks ({} seconds)", interval, interval / 20.0);
    }
    
    public static int getServerHungerGain() {
        return serverHungerGain;
    }
    
    public static void setServerHungerGain(int gain) {
        serverHungerGain = gain;
        LOGGER.info("Server hunger gain set to {} points ({} drumsticks)", gain, gain / 2.0);
    }
    
    private static QuestionBank questionBank;
    private int tickCounter = 0;
    private final int HUNGER_INTERVAL = 40; // 2 seconds (20 ticks per second)
    
    // Register our quiz item
    public static final QuizItem QUIZ_ITEM = new QuizItem(
        new FabricItemSettings()
            .maxCount(64)
            .food(new FoodComponent.Builder()
                .hunger(1) // Half a drumstick when eaten normally
                .saturationModifier(0.1f)
                .snack() // Can be eaten quickly
                .build())
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing StudyCraft mod");

        // Initialize the question bank
        questionBank = new QuestionBank();
        LOGGER.info("Loaded {} questions from question bank", questionBank.getQuestionCount());

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            quizStatistics = new QuizStatistics(server);
            LOGGER.info("Initialized quiz statistics");
        });

        // Register our item
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "quiz_card"), QUIZ_ITEM);
        
        StudycraftNetworking.registerHandlers();
        // Register server start event to send welcome message
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("StudyCraft server started!");
        });
        
        // Send message to player when they join
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            player.sendMessage(Text.literal("§6[StudyCraft]§r Welcome! Your hunger will deplete every " + 
                (serverHungerInterval / 20.0) + " seconds. Use quiz cards to earn food!"), false);
            player.sendMessage(Text.literal("§6[StudyCraft]§r Loaded " + questionBank.getQuestionCount() + " study questions."), false);
            LOGGER.info("Player {} joined with StudyCraft active", player.getName().getString());
        });
        
        // Register server tick event to handle hunger depletion
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            
            // Use the configurable hunger interval instead of hardcoded value
            if (tickCounter >= serverHungerInterval) {
                tickCounter = 0;
                
                // For each player, deplete hunger
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    int prevFoodLevel = player.getHungerManager().getFoodLevel();
                    
                    // Add exhaustion to deplete hunger by 1 point (half a drumstick)
                    player.getHungerManager().addExhaustion(4.0F);
                    
                    // Check if food level changed and log it
                    if (player.getHungerManager().getFoodLevel() < prevFoodLevel) {
                        LOGGER.info("Player {} hunger depleted: {} -> {}", 
                            player.getName().getString(), 
                            prevFoodLevel, 
                            player.getHungerManager().getFoodLevel());
                        
                        // Send message to player
                        player.sendMessage(Text.literal("§6[StudyCraft]§r Your hunger decreased by 1!"), true);
                    }
                }
            }
        });
    }
    
    // Get access to the question bank
    public static QuestionBank getQuestionBank() {
        return questionBank;
    }

    public static void setQuestionBank(QuestionBank questionBank) {
        Studycraft.questionBank = questionBank;
    }
    
}