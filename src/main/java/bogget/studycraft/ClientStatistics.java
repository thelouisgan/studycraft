package bogget.studycraft;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side statistics class that stores a local copy of player statistics
 * received from the server.
 */
public class ClientStatistics {
    // Use ConcurrentHashMap for thread safety
    private final Map<String, QuizStatistics.StatsEntry> stats = new ConcurrentHashMap<>();
    private volatile float overallPercent = 0.0f;
    private volatile boolean statsLoaded = false;
    
    /**
     * Updates the client-side statistics with data received from the server.
     * 
     * @param newStats Map of question -> stats entry
     * @param overallPercent The overall percentage of correct answers
     */
    public synchronized void updateStats(Map<String, QuizStatistics.StatsEntry> newStats, float overallPercent) {
        this.stats.clear();
        if (newStats != null) {
            // Safely copy all non-null entries
            for (Map.Entry<String, QuizStatistics.StatsEntry> entry : newStats.entrySet()) {
                if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                    this.stats.put(entry.getKey(), entry.getValue());
                }
            }
            this.overallPercent = Float.isNaN(overallPercent) ? 0.0f : overallPercent;
            this.statsLoaded = true;
        }
    }
    
    /**
     * Returns a copy of the current statistics.
     * 
     * @return Map of question -> stats entry (never null)
     */
    public synchronized Map<String, QuizStatistics.StatsEntry> getStats() {
        return new HashMap<>(stats);
    }
    
    /**
     * Returns the overall percentage of correct answers.
     * 
     * @return The overall percentage
     */
    public float getOverallPercent() {
        return overallPercent;
    }

    /**
     * Returns total correct answers across all questions.
     * 
     * @return Total correct answers
     */
    public int getTotalCorrect() {
        int total = 0;
        for (QuizStatistics.StatsEntry entry : stats.values()) {
            if (entry != null) {
                total += entry.getTimesCorrect();
            }
        }
        return total;
    }

    /**
     * Returns total answers across all questions.
     * 
     * @return Total answers
     */
    public int getTotalAnswers() {
        int total = 0;
        for (QuizStatistics.StatsEntry entry : stats.values()) {
            if (entry != null) {
                total += entry.getTotal();
            }
        }
        return total;
    }

    /**
     * Returns whether statistics have been loaded from the server.
     * 
     * @return true if stats have been loaded, false otherwise
     */
    public boolean areStatsLoaded() {
        return statsLoaded;
    }
    
    /**
     * Returns the number of statistics entries.
     * 
     * @return The count of statistics entries
     */
    public int getStatsCount() {
        return stats.size();
    }
    
    /**
     * Returns a sorted list of statistics entries.
     * This method is thread-safe and handles all edge cases.
     * 
     * @return List of map entries sorted by question (never null)
     */
    public synchronized List<Map.Entry<String, QuizStatistics.StatsEntry>> getSortedStats() {
        List<Map.Entry<String, QuizStatistics.StatsEntry>> result = new ArrayList<>();
        
        if (stats.isEmpty()) {
            return result;
        }
        
        // Manually iterate to avoid lambda issues and ensure thread safety
        for (Map.Entry<String, QuizStatistics.StatsEntry> entry : stats.entrySet()) {
            if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                result.add(entry);
            }
        }
        
        // Sort using a simple comparator
        result.sort(new Comparator<Map.Entry<String, QuizStatistics.StatsEntry>>() {
            @Override
            public int compare(Map.Entry<String, QuizStatistics.StatsEntry> o1, 
                             Map.Entry<String, QuizStatistics.StatsEntry> o2) {
                if (o1 == null || o1.getKey() == null) return 1;
                if (o2 == null || o2.getKey() == null) return -1;
                return o1.getKey().compareTo(o2.getKey());
            }
        });
        
        return result;
    }
    
    /**
     * Clears all statistics.
     */
    public synchronized void clearStats() {
        this.stats.clear();
        this.overallPercent = 0.0f;
        this.statsLoaded = false;
    }
    
    /**
     * Gets statistics for a specific question.
     * 
     * @param question The question to get stats for
     * @return The stats entry, or null if not found
     */
    public QuizStatistics.StatsEntry getStatsForQuestion(String question) {
        if (question == null) return null;
        return stats.get(question);
    }
}