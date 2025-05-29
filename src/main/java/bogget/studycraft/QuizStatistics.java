package bogget.studycraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QuizStatistics {
    private static final Logger LOGGER = Studycraft.LOGGER;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // Map structure: playerUuid -> questionText -> StatsEntry
    private Map<UUID, Map<String, StatsEntry>> playerStats = new HashMap<>();
    private File statsFile;
    
    public static class StatsEntry {
        private int timesCorrect = 0;
        private int timesWrong = 0;
        
        public StatsEntry() {}
        
        public void incrementCorrect() {
            timesCorrect++;
        }
        
        public void incrementWrong() {
            timesWrong++;
        }
        
        public int getTimesCorrect() {
            return timesCorrect;
        }
        
        public int getTimesWrong() {
            return timesWrong;
        }
        
        public int getTotal() {
            return timesCorrect + timesWrong;
        }
        
        public float getPercentCorrect() {
            if (getTotal() == 0) return 0;
            return (float) timesCorrect / getTotal() * 100f;
        }
    }
    
    public QuizStatistics(MinecraftServer server) {
        File worldDir = new File(server.getRunDirectory(), "world");
        File dataDir = new File(worldDir, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        
        this.statsFile = new File(dataDir, "studycraft_stats.json");
        loadStats();
    }
    
    private void loadStats() {
        if (statsFile.exists()) {
            try (FileReader reader = new FileReader(statsFile)) {
                Type type = new TypeToken<Map<UUID, Map<String, StatsEntry>>>(){}.getType();
                playerStats = GSON.fromJson(reader, type);
                if (playerStats == null) {
                    playerStats = new HashMap<>();
                }
                LOGGER.info("Loaded quiz statistics for {} players", playerStats.size());
            } catch (IOException e) {
                LOGGER.error("Failed to load quiz statistics", e);
                playerStats = new HashMap<>();
            }
        }
    }
    
    public void saveStats() {
        try (FileWriter writer = new FileWriter(statsFile)) {
            GSON.toJson(playerStats, writer);
            LOGGER.info("Saved quiz statistics");
        } catch (IOException e) {
            LOGGER.error("Failed to save quiz statistics", e);
        }
    }
    
    public void recordAnswer(UUID playerId, String question, boolean correct) {
        // Get or create player map
        Map<String, StatsEntry> playerMap = playerStats.computeIfAbsent(playerId, k -> new HashMap<>());
        
        // Get or create stats entry for this question
        StatsEntry entry = playerMap.computeIfAbsent(question, k -> new StatsEntry());
        
        // Update stats
        if (correct) {
            entry.incrementCorrect();
        } else {
            entry.incrementWrong();
        }
        
        // Save after every update
        saveStats();
    }
    
    public StatsEntry getQuestionStats(UUID playerId, String question) {
        Map<String, StatsEntry> playerMap = playerStats.getOrDefault(playerId, new HashMap<>());
        return playerMap.getOrDefault(question, new StatsEntry());
    }
    
    public Map<String, StatsEntry> getAllStats(UUID playerId) {
        return playerStats.getOrDefault(playerId, new HashMap<>());
    }
    
    public float getOverallPercentCorrect(UUID playerId) {
        Map<String, StatsEntry> playerMap = playerStats.getOrDefault(playerId, new HashMap<>());
        if (playerMap.isEmpty()) {
            return 0;
        }
        
        int totalCorrect = 0;
        int totalAnswers = 0;
        
        for (StatsEntry entry : playerMap.values()) {
            totalCorrect += entry.getTimesCorrect();
            totalAnswers += entry.getTotal();
        }
        
        return totalAnswers > 0 ? (float) totalCorrect / totalAnswers * 100f : 0;
    }
}
