package bogget.studycraft;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.ArrayList;

public class QuizScreen extends Screen {
    private final QuestionBank.QuizData quizData;
    private final List<String> answers;
    private final int correctAnswerIndex;
    
    // Variables for the result display
    private boolean showingResult = false;
    private boolean isCorrect = false;
    private int selectedAnswerIndex = -1;
    private long resultDisplayStartTime = 0;
    private static final long RESULT_DISPLAY_DURATION = 3000; // 3 seconds in milliseconds
    
    // Custom answer panel (replacing buttons)
    private List<AnswerPanel> answerPanels = new ArrayList<>();
    
    // Grid layout settings
    private static final int GRID_COLUMNS = 2;
    private static final int GRID_ROWS = 2;
    private static final int PANEL_PADDING = 15;
    private static final int PANEL_MARGIN = 10;
    
    public QuizScreen(QuestionBank.QuizData quizData) {
        super(Text.literal("Quiz Question"));
        this.quizData = quizData;
        this.answers = quizData.getAllAnswers();
        this.correctAnswerIndex = quizData.getCorrectIndex();
    }
    
    @Override
    protected void init() {
        super.init();
        answerPanels.clear();
        
        int totalAnswers = answers.size();
        int columns = Math.min(GRID_COLUMNS, totalAnswers);
        int rows = MathHelper.ceil((float) totalAnswers / columns);
        
        // Calculate panel dimensions based on grid layout
        int availableWidth = (int)(width * 0.9); // Increased from 0.8 to 0.9 (90% of screen width)
        int panelWidth = (availableWidth / columns) - (PANEL_MARGIN * 2);
        int panelHeight = 60; // Fixed height for now
        
        // Calculate starting position (centered)
        int startX = (width - availableWidth) / 2;
        int startY = height / 3 + 30; // Below question text
        
        // Create answer panels
        for (int i = 0; i < answers.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            
            int x = startX + column * (panelWidth + PANEL_MARGIN * 2);
            int y = startY + row * (panelHeight + PANEL_MARGIN);
            
            AnswerPanel panel = new AnswerPanel(
                x, y, panelWidth, panelHeight,
                answers.get(i), i
            );
            
            answerPanels.add(panel);
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        
        // Draw question
        String question = quizData.getQuestion();
        
        // Use word wrapping for long questions
        Text questionText = Text.literal(question);
        List<OrderedText> wrappedOrderedTexts = textRenderer.wrapLines(questionText, width - 40);
        
        int lineY = height / 6; // Move question text higher
        for (OrderedText orderedText : wrappedOrderedTexts) {
            context.drawCenteredTextWithShadow(textRenderer, orderedText, width / 2, lineY, 0xFFFFFF);
            lineY += textRenderer.fontHeight + 2;
        }
        
        // Draw answer panels
        for (AnswerPanel panel : answerPanels) {
            panel.render(context, mouseX, mouseY);
        }
        
        // Draw result message if showing result
        if (showingResult) {
            String resultMessage = isCorrect ? 
                "§a✓ Correct! §r" :
                "§c✗ Wrong! §r";
                
            Text resultText = Text.literal(resultMessage);
            context.drawCenteredTextWithShadow(
                textRenderer,
                resultText.asOrderedText(), 
                width / 2, 
                height - 50, 
                0xFFFFFF
            );
            
            // Check if it's time to close the screen
            long currentTime = System.currentTimeMillis();
            if (isCorrect && (currentTime - resultDisplayStartTime > 500)) {
                close();
            }
            else if (!isCorrect && (currentTime - resultDisplayStartTime > RESULT_DISPLAY_DURATION)) {
                close();
            }
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!showingResult && button == 0) { // Left click
            for (AnswerPanel panel : answerPanels) {
                if (panel.isHovered((int)mouseX, (int)mouseY)) {
                    // Handle answer selection
                    selectedAnswerIndex = panel.index;
                    isCorrect = (selectedAnswerIndex == correctAnswerIndex);
                    showingResult = true;
                    resultDisplayStartTime = System.currentTimeMillis();
                    
                    // Send answer to server
                    StudycraftNetworking.sendAnswerPacket(isCorrect ? 0 : 1, quizData.getQuestion(), quizData.getCorrectAnswer());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public void tick() {
        super.tick();
        
        // Check if we need to close the screen
        if (showingResult && System.currentTimeMillis() - resultDisplayStartTime > RESULT_DISPLAY_DURATION) {
            close();
        }
    }
    
    @Override
    public boolean shouldPause() {
        return true;
    }
    
    /**
     * Custom panel class to replace buttons with better text wrapping
     */
    private class AnswerPanel {
        private final int x, y, width, height;
        private final String text;
        private final int index;
        private static final int TEXT_PADDING = 10;
        
        public AnswerPanel(int x, int y, int width, int height, String text, int index) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.text = text;
            this.index = index;
        }
        
        public void render(DrawContext context, int mouseX, int mouseY) {
            // Determine background color based on state
            int bgColor;
            boolean hovered = isHovered(mouseX, mouseY);
            
            if (showingResult) {
                if (index == selectedAnswerIndex) {
                    bgColor = isCorrect ? 0xFF00AA00 : 0xFFAA0000; // Green if correct, red if wrong
                } else if (index == correctAnswerIndex && !isCorrect) {
                    bgColor = 0xFF00AA00; // Green for correct answer when wrong answer selected
                } else {
                    bgColor = 0xFF505050; // Dark gray for unselected answers
                }
            } else {
                bgColor = hovered ? 0xFF707070 : 0xFF505050; // Lighter gray when hovered
            }
            
            // Draw panel background
            context.fill(x, y, x + width, y + height, bgColor);
            
            // Draw border
            context.drawBorder(x, y, width, height, hovered ? 0xFFFFFFFF : 0xFF888888);
            
            // Prepare text for rendering
            Text answerText = Text.literal(text);
            List<OrderedText> wrappedText = textRenderer.wrapLines(answerText, width - (TEXT_PADDING * 2));
            
            // Calculate text scaling if needed
            float scale = 1.0f;  // Default scale
            int totalTextHeight = wrappedText.size() * (textRenderer.fontHeight + 2) - 2;
            
            // If text height exceeds available height, scale it down
            if (totalTextHeight > height - (TEXT_PADDING * 2)) {
                scale = (float)(height - (TEXT_PADDING * 2)) / totalTextHeight;
                // Limit the minimum scale to ensure text isn't too small
                scale = Math.max(0.6f, scale);
            }
            
            // Save matrix state before scaling
            context.getMatrices().push();
            
            // Apply scaling if needed
            if (scale < 1.0f) {
                // Move to panel center, apply scale, then move back
                float centerX = x + width / 2.0f;
                float centerY = y + height / 2.0f;
                context.getMatrices().translate(centerX, centerY, 0);
                context.getMatrices().scale(scale, scale, 1.0f);
                context.getMatrices().translate(-centerX, -centerY, 0);
            }
            
            // Calculate text Y position to center vertically
            int scaledTextHeight = (int)(totalTextHeight * scale);
            int textY = y + (height - scaledTextHeight) / 2;
            
            // Draw the text
            for (OrderedText line : wrappedText) {
                context.drawTextWithShadow(textRenderer, line, x + TEXT_PADDING, textY, 0xFFFFFF);
                textY += (textRenderer.fontHeight + 2) * scale;
            }
            
            // Restore matrix state
            context.getMatrices().pop();
        }
        
        public boolean isHovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
}