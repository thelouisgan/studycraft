package bogget.studycraft;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import bogget.studycraft.QuestionBankTextFieldWidget;

public class StudycraftConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget textField;
    private boolean showingStats = false;
    private boolean statsLoaded = false;
    private List<Map.Entry<String, QuizStatistics.StatsEntry>> statsList = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int LINES_PER_PAGE = 5;
    
    // Store the original raw content separately
    private String rawQuestionBankContent = "";
    
    // Static fields to maintain difficulty state across screen instances
    private static int persistentHungerInterval = 40; // Default 2 seconds (40 ticks)
    private static int persistentHungerGain = 2; // Default 2 hunger points (1 drumstick)
    private static int persistentDifficultyIndex = 1; // Default to Normal
    
    // Instance fields that sync with persistent state
    private int currentHungerInterval;
    private int currentHungerGain;
    private int currentDifficultyIndex;
    
    // Clickable area for attribution
    private int attributionX = 20;
    private int attributionY = 27;
    private int attributionWidth;
    private int attributionHeight;
    private Text attributionText = Text.literal("ganlouis.com · hack club");
    
    // Difficulty presets
    private static final DifficultyPreset[] DIFFICULTY_PRESETS = {
        new DifficultyPreset("Easy", 60, 3, "3 seconds between hunger loss, +1.5 drumsticks per correct answer"),
        new DifficultyPreset("Normal", 40, 2, "2 seconds between hunger loss, +1 drumstick per correct answer"),
        new DifficultyPreset("Hard", 30, 2, "1.5 seconds between hunger loss, +1 drumstick per correct answer"),
        new DifficultyPreset("Extreme", 20, 1, "1 second between hunger loss, +0.5 drumsticks per correct answer")
    };
    
    private void loadCurrentDifficultySettings() {
        // Load from persistent static fields
        this.currentHungerInterval = persistentHungerInterval;
        this.currentHungerGain = persistentHungerGain;
        this.currentDifficultyIndex = persistentDifficultyIndex;
        
        // Optionally, try to get current settings from the server/game state if available
        // Example: if (Studycraft.hasCurrentSettings()) {
        //     this.currentHungerInterval = Studycraft.getCurrentHungerInterval();
        //     this.currentHungerGain = Studycraft.getCurrentHungerGain();
        //     matchSettingsToPreset();
        // }
        
        // Match the loaded settings to the appropriate preset
        matchSettingsToPreset();
    }
    
    private void matchSettingsToPreset() {
        // Try to find which preset matches the current settings
        for (int i = 0; i < DIFFICULTY_PRESETS.length; i++) {
            DifficultyPreset preset = DIFFICULTY_PRESETS[i];
            if (preset.hungerInterval == currentHungerInterval && preset.hungerGain == currentHungerGain) {
                currentDifficultyIndex = i;
                return;
            }
        }
        
        // If no exact match found, default to Normal but keep the actual current values
        currentDifficultyIndex = 1;
    };
    

    
    public StudycraftConfigScreen(Screen parent) {
        super(Text.literal("StudyCraft Configuration"));
        this.parent = parent;
        // Initialize with the actual raw content
        this.rawQuestionBankContent = QuestionBank.RAW_QUESTION_BANK;
        
        // Load current difficulty settings from the game state
        loadCurrentDifficultySettings();
    }

    @Override
    protected void init() {
        super.init();
        
        // Calculate attribution text dimensions now that textRenderer is available
        this.attributionWidth = textRenderer.getWidth(attributionText);
        this.attributionHeight = textRenderer.fontHeight;
        
        initTopRow();
        initMainButtons();

        if (showingStats) {
            initStatsView();
        } else {
            initQuestionBankEditor();
        }
    }
    
    private void initTopRow() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int spacing = 10;
        
        // Title positioned at top left
        // (Title will be drawn in render method)
        
        // Hunger Loss Button - top right
        String intervalText = String.format("Hunger Loss: %.1f", currentHungerInterval / 20.0);
        ButtonWidget hungerIntervalButton = ButtonWidget.builder(
            Text.literal(intervalText),
            (button) -> {
                cycleDifficulty();
                updateDifficultySettings();
                // Refresh the screen to update button text
                clearChildren();
                init();
            }
        )
        .dimensions(width - buttonWidth * 2 - spacing - 10, 10, buttonWidth, buttonHeight)
        .tooltip(Tooltip.of(createHungerIntervalTooltip()))
        .build();
        
        // Reward Button - top right, next to hunger loss
        String gainText = String.format("Reward: +%.1f", currentHungerGain / 2.0);
        ButtonWidget hungerGainButton = ButtonWidget.builder(
            Text.literal(gainText),
            (button) -> {
                cycleDifficulty();
                updateDifficultySettings();
                // Refresh the screen to update button text
                clearChildren();
                init();
            }
        )
        .dimensions(width - buttonWidth - 10, 10, buttonWidth, buttonHeight)
        .tooltip(Tooltip.of(createHungerGainTooltip()))
        .build();
        
        addDrawableChild(hungerIntervalButton);
        addDrawableChild(hungerGainButton);
    }
    
    private void cycleDifficulty() {
        currentDifficultyIndex = (currentDifficultyIndex + 1) % DIFFICULTY_PRESETS.length;
    }
    
    private void updateDifficultySettings() {
        DifficultyPreset preset = DIFFICULTY_PRESETS[currentDifficultyIndex];
        currentHungerInterval = preset.hungerInterval;
        currentHungerGain = preset.hungerGain;
        
        // Update persistent static fields
        persistentHungerInterval = currentHungerInterval;
        persistentHungerGain = currentHungerGain;
        persistentDifficultyIndex = currentDifficultyIndex;
        
        // Send the new settings to the server
        StudycraftNetworking.sendDifficultyUpdatePacket(currentHungerInterval, currentHungerGain);
        
        // Send chat message about difficulty change
        if (client.player != null) {
            String intervalText = String.format("%.1f", currentHungerInterval / 20.0);
            String gainText = String.format("%.1f", currentHungerGain / 2.0);
            client.player.sendMessage(Text.literal("§a[StudyCraft]§r Difficulty: " + preset.name + 
                " - Hunger interval: " + intervalText + "s, Reward: +" + gainText + " drumsticks"), false);
        }
    }
    
    private Text createHungerIntervalTooltip() {
        DifficultyPreset current = DIFFICULTY_PRESETS[currentDifficultyIndex];
        return Text.literal(String.format("Difficulty: %s\n\n%s\n\nClick to cycle through difficulties", 
            current.name, current.description));
    }
    
    private Text createHungerGainTooltip() {
        DifficultyPreset current = DIFFICULTY_PRESETS[currentDifficultyIndex];
        return Text.literal(String.format("Difficulty: %s\n\n%s\n\nClick to cycle through difficulties", 
            current.name, current.description));
    }
    
    private void initQuestionBankEditor() {
        // Clear only the content area widgets, keep main buttons and top row buttons
        removeContentWidgets();

        textField = new QuestionBankTextFieldWidget(
            client.textRenderer,
            width / 2 - (width - 40)/2,
            85, // Position below the main navigation buttons
            width - 40,
            height - 145  // Leave space for bottom buttons
        );
        
        // Set the text field to display the raw content properly
        textField.setText(rawQuestionBankContent);
        
        // Enable multiline support
        textField.setMaxLength(65536); // Increase max length for large question banks
        
        addDrawableChild(textField);

        // Save Question Bank button - bottom left
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Save Question Bank"),
            button -> {
                // Get the raw text from the field and update our stored content
                rawQuestionBankContent = textField.getText();
                
                // Send the raw content to the server
                StudycraftNetworking.sendUpdateQuestionBankPacket(rawQuestionBankContent);
                client.player.sendMessage(Text.literal("§a[StudyCraft]§r Saved!"), false);
            })
            //.dimensions(20, height - 55, 150, 20)
            .dimensions(width / 2 - 150 - 5, height - 55, 150, 20)
            .build()
        );

        // Give Quiz Card button - bottom right
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Give Quiz Card"),
            button -> {
                if (client.player != null) {
                    StudycraftNetworking.sendGiveItemPacket();
                    client.player.sendMessage(Text.literal("§a[StudyCraft]§r Quiz card added to inventory!"), false);
                }
            })
            //.dimensions(width - 170, height - 55, 150, 20)
            .dimensions(width / 2 + 5, height - 55, 150, 20)
            .build()
        );
    }

    private void initMainButtons() {
        int buttonWidth = 150;
        int buttonY = 40; // Position below the top row
        
        // Edit Question Bank button - center left
        ButtonWidget editorButton = ButtonWidget.builder(
            Text.literal("Edit Question Bank"),
            (button) -> {
                showingStats = false;
                // Refresh the raw content from the current question bank
                rawQuestionBankContent = QuestionBank.RAW_QUESTION_BANK;
                clearChildren();
                init();
            }
        )
        .dimensions(width / 2 - buttonWidth - 5, buttonY, buttonWidth, 20)
        .build();
        
        // View Statistics button - center right
        ButtonWidget statsButton = ButtonWidget.builder(
            Text.literal("View Statistics"),
            (button) -> {
                if (!showingStats) {
                    showingStats = true;
                    statsLoaded = false; // Reset stats loaded flag
                    loadStats();
                    clearChildren();
                    init();
                }
            }
        )
        .dimensions(width / 2 + 5, buttonY, buttonWidth, 20)
        .build();
        
        // Done button - bottom center
        ButtonWidget doneButton = ButtonWidget.builder(
            Text.literal("Done"),
            (button) -> client.setScreen(parent)
        )
        .dimensions(width / 2 - 75, height - 30, 150, 20)
        .build();
        
        addDrawableChild(editorButton);
        addDrawableChild(statsButton);
        addDrawableChild(doneButton);
    }
    
    private void loadStats() {
        // Request stats from the server
        if (client.player != null) {
            StudycraftNetworking.requestStats();
        }
    }
    
    private void initStatsView() {
        // Remove content area widgets but keep main buttons and top row buttons
        removeContentWidgets();
        
        // Add scroll buttons
        ButtonWidget scrollUpButton = ButtonWidget.builder(
            Text.literal("↑"),
            (button) -> scrollUp()
        )
        .dimensions(width - 30, 85, 20, 20)
        .build();
        
        ButtonWidget scrollDownButton = ButtonWidget.builder(
            Text.literal("↓"),
            (button) -> scrollDown()
        )
        .dimensions(width - 30, height - 60, 20, 20)
        .build();
        
        addDrawableChild(scrollUpButton);
        addDrawableChild(scrollDownButton);

        // Give Quiz Card button for stats view - centre
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Give Quiz Card"),
            button -> {
                if (client.player != null) {
                    StudycraftNetworking.sendGiveItemPacket();
                    client.player.sendMessage(Text.literal("§a[StudyCraft]§r Quiz card added to inventory!"), false);
                }
            })
            .dimensions(width / 2 - 75, height - 55, 150, 20)
            .build()
        );
    }
    
    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
        }
    }
    
    private void scrollDown() {
        if (scrollOffset < Math.max(0, statsList.size() - LINES_PER_PAGE)) {
            scrollOffset++;
        }
    }
    
    private void removeContentWidgets() {
        // Store references to buttons we want to preserve
        List<ButtonWidget> preservedButtons = new ArrayList<>();
        
        // Find and store the buttons to preserve
        for (var child : children()) {
            if (child instanceof ButtonWidget button) {
                int buttonY = button.getY();
                // Keep top row buttons, main navigation buttons, and bottom done button
                if (buttonY == 10 || buttonY == 40 || buttonY == height - 30) {
                    preservedButtons.add(button);
                }
            }
        }
        
        // Clear all children
        clearChildren();
        
        // Re-add the preserved buttons
        for (ButtonWidget button : preservedButtons) {
            addDrawableChild(button);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if attribution text was clicked
        if (button == 0 && isPointOverAttribution(mouseX, mouseY)) {
            // Open the website
            Util.getOperatingSystem().open("https://ganlouis.com");
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private boolean isPointOverAttribution(double mouseX, double mouseY) {
        return mouseX >= attributionX && mouseX <= attributionX + attributionWidth &&
               mouseY >= attributionY && mouseY <= attributionY + attributionHeight;
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        // Only handle scrolling when in stats view
        if (showingStats && statsLoaded && !statsList.isEmpty()) {
            if (verticalAmount > 0) {
                // Scroll up (negative direction in list)
                scrollUp();
                return true;
            } else if (verticalAmount < 0) {
                // Scroll down (positive direction in list)
                scrollDown();
                return true;
            }
        }
        
        // If not in stats view or no scrolling needed, pass to parent
        return super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        
        // Draw the title at top left
        context.drawTextWithShadow(textRenderer, this.title, 20, 15, 0xFFFFFF);
        
        // Draw attribution text below the title with hover effect
        boolean isHovering = isPointOverAttribution(mouseX, mouseY);
        int textColor = isHovering ? 0xAAAAAA : 0x888888; // Lighter when hovering
        context.drawTextWithShadow(textRenderer, attributionText, attributionX, attributionY, textColor);
        
        // Show tooltip when hovering over attribution
        if (isHovering) {
            List<Text> tooltipLines = List.of(
                Text.literal("Made by Louis Gan"),
                Text.literal("a project by Hack Club, a global nonprofit"),
                Text.literal("network of high school computer hackers,"),
                Text.literal("makers and coders")
            );
            context.drawTooltip(textRenderer, tooltipLines, (int)mouseX, (int)mouseY);
        }
        
        if (showingStats) {
            renderStatsView(context);
        } else {
            // Draw helper text for question bank editor
            context.drawTextWithShadow(textRenderer, 
                Text.literal("Paste exported Quizlet set (tab-separated) here:"),
                20, 70, 0xAAAAAA);
        }
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    private void renderStatsView(DrawContext context) {
        if (!statsLoaded) {
            // Show loading message while stats are being fetched
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.literal("Loading statistics..."), 
                width / 2, height / 2, 0xFFFFFF);
            return;
        }
        
        // Draw overall stats
        float overallPercent = Studycraft.getClientStats().getOverallPercent();
        context.drawTextWithShadow(textRenderer, 
            Text.literal(String.format("Overall: %.1f%% correct (%d/%d)", 
                overallPercent, 
                Studycraft.getClientStats().getTotalCorrect(), 
                Studycraft.getClientStats().getTotalAnswers())),
            20, 70, 0xFFFFFF);
        
        // Draw divider
        context.fill(20, 85, width - 20, 86, 0xFFAAAAAA);
        
        // Get stats entries sorted by question
        statsList = Studycraft.getClientStats().getSortedStats();
        
        if (statsList.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.literal("No statistics available yet. Answer some questions first!"), 
                width / 2, height / 2, 0xAAAAAA);
            return;
        }
        
        // Draw stats entries - paginated
        int y = 95;
        int endIndex = Math.min(scrollOffset + LINES_PER_PAGE, statsList.size());
        
        for (int i = scrollOffset; i < endIndex; i++) {
            Map.Entry<String, QuizStatistics.StatsEntry> entry = statsList.get(i);
            String question = entry.getKey();
            QuizStatistics.StatsEntry stats = entry.getValue();
            
            // Truncate long questions
            if (question.length() > 30) {
                question = question.substring(0, 27) + "...";
            }
            
            // Draw question
            context.drawTextWithShadow(textRenderer, Text.literal(question), 25, y, 0xFFFFFF);
            
            // Draw stats
            String statsText = String.format("✓ %d  ✗ %d  (%.1f%%)", 
                stats.getTimesCorrect(), stats.getTimesWrong(), stats.getPercentCorrect());
            
            context.drawTextWithShadow(textRenderer, Text.literal(statsText), 
                width / 2 + 5, y, 0xAAAAAA);
            
            // Progress bar background
            context.fill(25, y + 12, width - 35, y + 16, 0xFF333333);
            
            // Progress bar fill
            int fillWidth = (int)((width - 60) * stats.getPercentCorrect() / 100f);
            int color = getColorForPercentage(stats.getPercentCorrect());
            context.fill(25, y + 12, 25 + fillWidth, y + 16, color);
            
            y += 25;
        }
        
        // Pagination info
        if (statsList.size() > LINES_PER_PAGE) {
            String pageInfo = String.format("Showing %d-%d of %d", 
                scrollOffset + 1, 
                Math.min(scrollOffset + LINES_PER_PAGE, statsList.size()), 
                statsList.size());
            
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(pageInfo), 
                width / 2, height - 80, 0xAAAAAA);
        }
    }
    
    private int getColorForPercentage(float percent) {
        if (percent >= 80) {
            return 0xFF00AA00; // Green
        } else if (percent >= 60) {
            return 0xFF88AA00; // Yellow-green
        } else if (percent >= 40) {
            return 0xFFAAAA00; // Yellow
        } else if (percent >= 20) {
            return 0xFFAA5500; // Orange
        } else {
            return 0xFFAA0000; // Red
        }
    }
    
    @Override
    public boolean shouldPause() {
        return true;
    }
    
    // Method to be called when stats data is received
    public void onStatsReceived() {
        statsLoaded = true;
    }
    
    // Inner class for difficulty presets
    private static class DifficultyPreset {
        final String name;
        final int hungerInterval; // ticks between hunger depletion
        final int hungerGain; // hunger points gained per correct answer
        final String description;
        
        DifficultyPreset(String name, int hungerInterval, int hungerGain, String description) {
            this.name = name;
            this.hungerInterval = hungerInterval;
            this.hungerGain = hungerGain;
            this.description = description;
        }
    }
}