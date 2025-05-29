package bogget.studycraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class QuestionBank {
    // Store this as a constant that can be easily replaced by users
    public static String RAW_QUESTION_BANK = """
Lithium	Crimson flame
Sodium	Yellow flame
Potassium	Lilac flame
Calcium	Orange-red flame
Forms a white precipitate in sodium hydroxide
Copper	Green flame
Aluminium	Forms a white precipitate in sodium hydroxide
Aluminium hydroxide dissolves in excess sodium hydroxide
Copper (II)	Forms a blue precipitate in sodium hydroxide
Iron (II)	Forms a green precipitate in sodium hydroxide
Iron (III)	Forms a brown precipitate in sodium hydroxide
Carbonates	Dissolves in a dilute acid to produce carbon dioxide which can be tested with lime water
Bromide	Produces a cream precipitate with silver nitrate in the presence of dilute nitric acid
Chloride	Produces a white precipitate with silver nitrate in the presence of dilute nitric acid
Iodide	Produces a yellow precipitate with silver nitrate in the presence of dilute nitric acid
Sulfate	Produce a white precipitate with barium chloride in the presence of dilute hydrochloric acid
            """;
    
    private static class QuizQuestion {
        private final String question;
        private final String correctAnswer;
        
        public QuizQuestion(String question, String correctAnswer) {
            this.question = question;
            this.correctAnswer = correctAnswer;
        }
        
        public String getQuestion() {
            return question;
        }
        
        public String getCorrectAnswer() {
            return correctAnswer;
        }
    }
    
    private final List<QuizQuestion> questions = new ArrayList<>();
    private final Random random = new Random();
    
    public QuestionBank() {
        parseQuestionBank(RAW_QUESTION_BANK);
    }

    public QuestionBank(String content) {
        parseQuestionBank(content);
        // Update the static field so it persists
        RAW_QUESTION_BANK = content;
    }
    
    private void parseQuestionBank(String content) {
        // Clear existing questions
        questions.clear();
        // Split the raw question bank by newlines
        String[] lines = content.split("\n");
        
        // Process each line
        for (String line : lines) {
            // Split by tab character
            String[] parts = line.split("\t", 2);
            
            // Skip if we don't have exactly two parts
            if (parts.length != 2) {
                Studycraft.LOGGER.warn("Skipping invalid question bank line: " + line);
                continue;
            }
            
            // Extract question and answer
            String question = parts[0].trim();
            String answer = parts[1].trim();
            
            // Add to our list
            questions.add(new QuizQuestion(question, answer));
        }
        
        Studycraft.LOGGER.info("Loaded {} questions from question bank", questions.size());
    }
    
    public int getQuestionCount() {
        return questions.size();
    }
    
    public QuizData getRandomQuestion() {
        if (questions.isEmpty()) {
            // Fallback question if none loaded
            return new QuizData(
                "No questions loaded. Check your question bank format.",
                "OK",
                List.of("Error", "Missing", "Questions"),
                0
            );
        }
        
        // Get a random question
        QuizQuestion question = questions.get(random.nextInt(questions.size()));
        
        // Generate wrong answers (use 3 other random answers)
        List<String> wrongAnswers = new ArrayList<>();
        List<Integer> usedIndices = new ArrayList<>();
        usedIndices.add(questions.indexOf(question)); // Don't use the correct answer
        
        // Try to get 3 wrong answers
        for (int i = 0; i < 3 && i < questions.size() - 1; i++) {
            int index;
            do {
                index = random.nextInt(questions.size());
            } while (usedIndices.contains(index));
            
            usedIndices.add(index);
            wrongAnswers.add(questions.get(index).getCorrectAnswer());
        }
        
        // If we don't have enough questions, add some default wrong answers
        while (wrongAnswers.size() < 3) {
            wrongAnswers.add("Option " + (wrongAnswers.size() + 1));
        }
        
        // Combine correct and wrong answers
        List<String> allAnswers = new ArrayList<>();
        allAnswers.add(question.getCorrectAnswer());
        allAnswers.addAll(wrongAnswers);
        
        // Shuffle the answers
        Collections.shuffle(allAnswers);
        
        // Find where the correct answer ended up
        int correctIndex = allAnswers.indexOf(question.getCorrectAnswer());
        
        return new QuizData(question.getQuestion(), question.getCorrectAnswer(), allAnswers, correctIndex);
    }
    
    public static class QuizData {
        private final String question;
        private final String correctAnswer;
        private final List<String> allAnswers;
        private final int correctIndex;
        
        public QuizData(String question, String correctAnswer, List<String> allAnswers, int correctIndex) {
            this.question = question;
            this.correctAnswer = correctAnswer;
            this.allAnswers = allAnswers;
            this.correctIndex = correctIndex;
        }
        
        public String getQuestion() {
            return question;
        }
        
        public String getCorrectAnswer() {
            return correctAnswer;
        }
        
        public List<String> getAllAnswers() {
            return allAnswers;
        }
        
        public int getCorrectIndex() {
            return correctIndex;
        }
    }
}
