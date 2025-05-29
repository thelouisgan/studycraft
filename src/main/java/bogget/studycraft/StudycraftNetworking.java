package bogget.studycraft;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudycraftNetworking {
    // Define packet identifiers
    public static final Identifier OPEN_QUIZ_PACKET = new Identifier(Studycraft.MOD_ID, "open_quiz");
    public static final Identifier SUBMIT_ANSWER_PACKET = new Identifier(Studycraft.MOD_ID, "submit_answer");
    public static final Identifier UPDATE_QUESTION_BANK_PACKET = new Identifier(Studycraft.MOD_ID, "update_question_bank");
    public static final Identifier REQUEST_STATS_PACKET = new Identifier(Studycraft.MOD_ID, "request_stats");
    public static final Identifier STATS_DATA_PACKET = new Identifier(Studycraft.MOD_ID, "stats_data");
    public static final Identifier GIVE_ITEM_PACKET = new Identifier(Studycraft.MOD_ID, "give_item");
    public static final Identifier DIFFICULTY_UPDATE_PACKET = new Identifier(Studycraft.MOD_ID, "difficulty_update");
    
    // Register all networking handlers
    public static void registerHandlers() {
        // Register server-side handlers
        ServerPlayNetworking.registerGlobalReceiver(SUBMIT_ANSWER_PACKET, StudycraftNetworking::handleSubmitAnswerPacket);
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_QUESTION_BANK_PACKET, StudycraftNetworking::handleUpdateQuestionBankPacket);
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_STATS_PACKET, StudycraftNetworking::handleRequestStatsPacket);
        ServerPlayNetworking.registerGlobalReceiver(GIVE_ITEM_PACKET, StudycraftNetworking::handleGiveItemPacket);
        ServerPlayNetworking.registerGlobalReceiver(DIFFICULTY_UPDATE_PACKET, StudycraftNetworking::handleDifficultyUpdatePacket);
    }
    
    // Client-side init method that should be called from StudycraftClient
    public static void registerClientHandlers() {
        // Register client-side handlers
        ClientPlayNetworking.registerGlobalReceiver(OPEN_QUIZ_PACKET, StudycraftNetworking::handleOpenQuizPacket);
        ClientPlayNetworking.registerGlobalReceiver(STATS_DATA_PACKET, StudycraftNetworking::handleStatsDataPacket);
    }
    
    // Method to send packet to open quiz on client
    public static void sendOpenQuizPacket(ServerPlayerEntity player) {
        // Get a random question from the question bank
        QuestionBank.QuizData quizData = Studycraft.getQuestionBank().getRandomQuestion();
        
        PacketByteBuf buf = PacketByteBufs.create();
        
        // Write the question data to the packet
        buf.writeString(quizData.getQuestion());
        buf.writeString(quizData.getCorrectAnswer());
        buf.writeInt(quizData.getCorrectIndex());
        
        // Write all answers
        List<String> answers = quizData.getAllAnswers();
        buf.writeInt(answers.size());
        for (String answer : answers) {
            buf.writeString(answer);
        }
        
        ServerPlayNetworking.send(player, OPEN_QUIZ_PACKET, buf);
    }
    
    // Client-side handler for opening quiz screen
    private static void handleOpenQuizPacket(MinecraftClient client, 
                                        ClientPlayNetworkHandler handler,
                                        PacketByteBuf buf, 
                                        PacketSender responseSender) {
        // Read the question data from the packet
        String question = buf.readString();
        String correctAnswer = buf.readString();
        int correctIndex = buf.readInt();
        
        // Read all answers
        int answerCount = buf.readInt();
        List<String> answers = new ArrayList<>();
        for (int i = 0; i < answerCount; i++) {
            answers.add(buf.readString());
        }
        
        // Create the quiz data
        QuestionBank.QuizData quizData = new QuestionBank.QuizData(question, correctAnswer, answers, correctIndex);
        
        // Execute on the main client thread
        client.execute(() -> {
            // Open the quiz screen with the question data
            client.setScreen(new QuizScreen(quizData));
        });
    }
    
    // Server-side handler for answer submission
    private static void handleSubmitAnswerPacket(MinecraftServer server,
                                               ServerPlayerEntity player,
                                               ServerPlayNetworkHandler handler,
                                               PacketByteBuf buf,
                                               PacketSender responseSender) {
        // Read answer result from packet (0 for correct, 1 for wrong)
        int answerResult = buf.readInt();
        // Read the question and correct answer
        String question = buf.readString();
        String correctAnswer = buf.readString();
        
        // Process on the server thread
        server.execute(() -> {
            boolean isCorrect = (answerResult == 0);
            
            // Update statistics
            Studycraft.getQuizStatistics().recordAnswer(player.getUuid(), question, isCorrect);
            
            if (isCorrect) {
                // Play a sound effect for correct answer
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), 
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 
                    0.5F, 1.0F);
                
                // Use the current hunger gain setting from the server instance
                int hungerGain = Studycraft.getServerHungerGain();
                float saturationGain = hungerGain * 0.25F; // Saturation is typically 25% of hunger
                
                // No message display for correct answers
                player.getHungerManager().add(hungerGain, saturationGain);
            } else {
                // Format the question and answer for the message
                String formattedQuestion = question.length() > 30 ? 
                    question.substring(0, 30) + "..." : question;
                
                // Send chat messages for wrong answers
                player.sendMessage(Text.literal("§c[StudyCraft]§r Wrong answer! Taking damage."), false);
                player.sendMessage(Text.literal("§6Question: §r" + formattedQuestion), false);
                player.sendMessage(Text.literal("§6Correct answer: §r" + correctAnswer), false);
                
                player.damage(player.getDamageSources().generic(), 2.0F); // 1 heart of damage
            }
        });
    }
    

    
    // Client method to send answer back to server
    public static void sendAnswerPacket(int answerResult, String question, String correctAnswer) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(answerResult);
        buf.writeString(question);
        buf.writeString(correctAnswer);
        ClientPlayNetworking.send(SUBMIT_ANSWER_PACKET, buf);
    }
    
    // Client method to send updated question bank to server
    public static void sendUpdateQuestionBankPacket(String newContent) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(newContent);
        ClientPlayNetworking.send(UPDATE_QUESTION_BANK_PACKET, buf);
    }
    
    // Server handler for updating question bank
    private static void handleUpdateQuestionBankPacket(MinecraftServer server,
                                                      ServerPlayerEntity player,
                                                      ServerPlayNetworkHandler handler,
                                                      PacketByteBuf buf,
                                                      PacketSender responseSender) {
        // Read the new content
        String newContent = buf.readString();
        
        // Process on the server thread
        server.execute(() -> {
            // Update the question bank
            Studycraft.updateQuestionBank(newContent);
            // Send temporary actionbar message instead of chat message
            player.sendMessage(Text.literal("§a[StudyCraft]§r Question bank updated!"), true);
        });
    }
    
    // Client method to request stats from server
    public static void requestStats() {
        PacketByteBuf buf = PacketByteBufs.create();
        ClientPlayNetworking.send(REQUEST_STATS_PACKET, buf);
    }
    
    // Client method to send difficulty update to server
    public static void sendDifficultyUpdatePacket(int hungerInterval, int hungerGain) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(hungerInterval);
        buf.writeInt(hungerGain);
        ClientPlayNetworking.send(DIFFICULTY_UPDATE_PACKET, buf);
    }
    
    // Server handler for difficulty updates
    private static void handleDifficultyUpdatePacket(MinecraftServer server,
                                                   ServerPlayerEntity player,
                                                   ServerPlayNetworkHandler handler,
                                                   PacketByteBuf buf,
                                                   PacketSender responseSender) {
        // Read the new difficulty settings
        int hungerInterval = buf.readInt();
        int hungerGain = buf.readInt();
        
        // Process on the server thread
        server.execute(() -> {
            // Update the difficulty settings on the server
            Studycraft.setServerHungerInterval(hungerInterval);
            Studycraft.setServerHungerGain(hungerGain);
            
            // --REDUNDANT AS ALREADY LOGGED IN CONFIGSCREEN -- //
            // Send confirmation to player
            /*String intervalSeconds = String.format("%.1f", hungerInterval / 20.0);
            String gainAmount = String.format("%.1f", hungerGain / 2.0);
            player.sendMessage(Text.literal("§a[StudyCraft]§r Difficulty updated! Hunger interval: " + 
                intervalSeconds + "s, Reward: +" + gainAmount + " drumsticks"), true);*/
        });
    }
    
    // Client method to send give item request to server
    public static void sendGiveItemPacket() {
        PacketByteBuf buf = PacketByteBufs.create();
        ClientPlayNetworking.send(GIVE_ITEM_PACKET, buf);
    }
    
    // Server handler for give item request
    private static void handleGiveItemPacket(MinecraftServer server,
                                           ServerPlayerEntity player,
                                           ServerPlayNetworkHandler handler,
                                           PacketByteBuf buf,
                                           PacketSender responseSender) {
        server.execute(() -> {
            // Give the player a Quiz Card item
            ItemStack quizCard = new ItemStack(Studycraft.QUIZ_ITEM);
            quizCard.setCustomName(Text.literal("Quiz Card"));
            
            // Try to add to inventory
            if (!player.getInventory().insertStack(quizCard)) {
                // If inventory is full, drop the item
                player.dropItem(quizCard, false);
            }
            
            // Send temporary actionbar message instead of chat message
            player.sendMessage(Text.literal("§a[StudyCraft]§r Quiz Card given!"), true);
        });
    }
    
    // Server handler for stats request
    private static void handleRequestStatsPacket(MinecraftServer server,
                                               ServerPlayerEntity player,
                                               ServerPlayNetworkHandler handler,
                                               PacketByteBuf buf,
                                               PacketSender responseSender) {
        server.execute(() -> {
            // Get player statistics
            QuizStatistics stats = Studycraft.getQuizStatistics();
            
            // Get player stats using the correct methods from QuizStatistics
            Map<String, QuizStatistics.StatsEntry> playerStats = stats.getAllStats(player.getUuid());
            double overallPercentage = stats.getOverallPercentCorrect(player.getUuid());
            
            // Create response packet
            PacketByteBuf response = PacketByteBufs.create();
            
            // Write overall percentage
            response.writeDouble(overallPercentage);
            
            // Write individual question stats
            response.writeInt(playerStats.size());
            for (Map.Entry<String, QuizStatistics.StatsEntry> entry : playerStats.entrySet()) {
                response.writeString(entry.getKey()); // Question text
                response.writeInt(entry.getValue().getTimesCorrect());
                response.writeInt(entry.getValue().getTimesWrong());
                response.writeFloat(entry.getValue().getPercentCorrect());
            }
            
            // Send stats back to client
            ServerPlayNetworking.send(player, STATS_DATA_PACKET, response);
        });
    }
    
    // Client handler for stats data - FIXED VERSION
    private static void handleStatsDataPacket(MinecraftClient client,
                                            ClientPlayNetworkHandler handler,
                                            PacketByteBuf buf,
                                            PacketSender responseSender) {
        // Read stats data
        double overallPercentage = buf.readDouble();
        
        int statsCount = buf.readInt();
        Map<String, QuizStatistics.StatsEntry> playerStats = new HashMap<>();
        
        for (int i = 0; i < statsCount; i++) {
            String question = buf.readString();
            int timesCorrect = buf.readInt();
            int timesWrong = buf.readInt();
            float percentCorrect = buf.readFloat();
            
            // Create a properly populated StatsEntry
            QuizStatistics.StatsEntry entry = new QuizStatistics.StatsEntry();
            
            // We need to set the values manually since the fields are private
            // We'll use a loop to simulate the correct/wrong answers being recorded
            for (int j = 0; j < timesCorrect; j++) {
                entry.incrementCorrect();
            }
            for (int j = 0; j < timesWrong; j++) {
                entry.incrementWrong();
            }
            
            playerStats.put(question, entry);
        }
        
        // Execute on client thread
        client.execute(() -> {
            // Update the client statistics with the received data
            Studycraft.getClientStats().updateStats(playerStats, (float) overallPercentage);
            
            // Notify the config screen that stats have been received
            if (client.currentScreen instanceof StudycraftConfigScreen) {
                ((StudycraftConfigScreen) client.currentScreen).onStatsReceived();
            }
        });
    }
}