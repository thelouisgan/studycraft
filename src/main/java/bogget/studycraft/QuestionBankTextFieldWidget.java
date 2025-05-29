package bogget.studycraft;

import bogget.studycraft.mixin.TextFieldWidgetAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class QuestionBankTextFieldWidget extends TextFieldWidget {
    private static final int TEXT_PADDING = 4;
    private int scrollPosition = 0;
    private final int maxVisibleLines;
    private final MinecraftClient client;
    
    // Cursor position tracking
    private int cursorLine = 0;
    private int cursorColumn = 0;
    private long lastBlinkTime = 0;
    private boolean cursorVisible = true;
    private static final long CURSOR_BLINK_RATE = 500; // milliseconds
    
    // Selection tracking
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private int selectionStartLine = -1;
    private int selectionEndLine = -1;
    private boolean isDragging = false;
    private int dragStartPos = -1;
    
    // Line tracking
    private List<String> lines = new ArrayList<>();
    
    // Tab representation
    private static final String TAB_VISUAL = "    "; // 4 spaces for display only
    private static final char TAB_CHAR = '\t'; // Actual tab character for storage
    
    // Undo/Redo system
    private Stack<TextState> undoStack = new Stack<>();
    private Stack<TextState> redoStack = new Stack<>();
    private boolean isUndoRedoOperation = false; // Flag to prevent undo states during undo/redo
    private static final int MAX_UNDO_HISTORY = 100; // Limit history size
    
    // Text state class for undo/redo
    private static class TextState {
        final String text;
        final int cursorPosition;
        final int selectionStart;
        final int selectionEnd;
        
        TextState(String text, int cursorPosition, int selectionStart, int selectionEnd) {
            this.text = text;
            this.cursorPosition = cursorPosition;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
        }
    }
    
    public QuestionBankTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height) {
        super(textRenderer, x, y, width, height, Text.literal("Question Bank"));
        this.setMaxLength(Integer.MAX_VALUE);
        this.setEditable(true);
        this.maxVisibleLines = height / (textRenderer.fontHeight + 2);
        this.client = MinecraftClient.getInstance();
        updateLines();
        
        // Save initial state after everything is set up
        isUndoRedoOperation = true; // Prevent this from being saved as an undoable action
        saveUndoState();
        isUndoRedoOperation = false;
    }

    private boolean isPrimaryModifierPressed(int modifiers) {
        if (Util.getOperatingSystem() == Util.OperatingSystem.OSX) {
            // On Mac, use Command key (Super/Meta)
            return (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
        } else {
            // On Windows/Linux, use Control key
            return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        }
    }
    
    /**
     * Saves current state to undo stack
     */
    private void saveUndoState() {
        if (isUndoRedoOperation) return; // Don't save state during undo/redo operations
        
        String currentText = this.getText();
        int currentCursor = this.getCursor();
        int currentSelStart = hasSelection() ? Math.min(selectionStart, selectionEnd) : -1;
        int currentSelEnd = hasSelection() ? Math.max(selectionStart, selectionEnd) : -1;
        
        // Don't save if state hasn't changed
        if (!undoStack.isEmpty()) {
            TextState lastState = undoStack.peek();
            if (lastState.text.equals(currentText) && 
                lastState.cursorPosition == currentCursor &&
                lastState.selectionStart == currentSelStart &&
                lastState.selectionEnd == currentSelEnd) {
                return;
            }
        }
        
        undoStack.push(new TextState(currentText, currentCursor, currentSelStart, currentSelEnd));
        
        // Limit undo history size
        if (undoStack.size() > MAX_UNDO_HISTORY) {
            undoStack.removeElementAt(0);
        }
        
        // Clear redo stack when new action is performed
        redoStack.clear();
    }
    
    /**
     * Performs undo operation
     */
    public boolean undo() {
        if (undoStack.size() <= 1) return false; // Keep at least one state (current)
        
        // Save current state to redo stack
        TextState currentState = new TextState(
            this.getText(), 
            this.getCursor(),
            hasSelection() ? Math.min(selectionStart, selectionEnd) : -1,
            hasSelection() ? Math.max(selectionStart, selectionEnd) : -1
        );
        redoStack.push(currentState);
        
        // Remove current state from undo stack
        undoStack.pop();
        
        // Get previous state
        TextState previousState = undoStack.peek();
        
        // Apply previous state
        isUndoRedoOperation = true;
        this.setText(previousState.text);
        this.setCursor(previousState.cursorPosition);
        
        // Restore selection
        if (previousState.selectionStart != -1 && previousState.selectionEnd != -1) {
            setSelection(previousState.selectionStart, previousState.selectionEnd);
        } else {
            clearSelection();
        }
        
        updateLines();
        isUndoRedoOperation = false;
        
        return true;
    }
    
    /**
     * Performs redo operation
     */
    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        
        // Get next state from redo stack
        TextState nextState = redoStack.pop();
        
        // Save current state to undo stack
        saveUndoState();
        
        // Apply next state
        isUndoRedoOperation = true;
        this.setText(nextState.text);
        this.setCursor(nextState.cursorPosition);
        
        // Restore selection
        if (nextState.selectionStart != -1 && nextState.selectionEnd != -1) {
            setSelection(nextState.selectionStart, nextState.selectionEnd);
        } else {
            clearSelection();
        }
        
        updateLines();
        isUndoRedoOperation = false;
        
        return true;
    }
    
    /**
     * Checks if undo is available
     */
    public boolean canUndo() {
        return undoStack.size() > 1;
    }
    
    /**
     * Checks if redo is available
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
    
    private void updateLines() {
        lines.clear();
        // Important: Use -1 to keep empty lines
        String[] textLines = this.getText().split("\n", -1);
        for (String line : textLines) {
            lines.add(line);
        }
        
        // Update cursor position based on selection
        updateCursorPosition();
        updateSelectionPositions();
    }
    
    private void updateCursorPosition() {
        int cursorPos = this.getCursor();
        int pos = 0;
        cursorLine = 0;
        cursorColumn = 0;
        
        for (String line : lines) {
            int lineLength = line.length();
            if (pos + lineLength >= cursorPos) {
                cursorColumn = cursorPos - pos;
                break;
            }
            pos += lineLength + 1; // +1 for newline
            cursorLine++;
        }
        
        // Adjust scroll if cursor is out of view
        if (cursorLine < scrollPosition) {
            scrollPosition = cursorLine;
        } else if (cursorLine >= scrollPosition + maxVisibleLines) {
            scrollPosition = cursorLine - maxVisibleLines + 1;
        }
    }
    
    private void updateSelectionPositions() {
        if (selectionStart == -1 || selectionEnd == -1) {
            selectionStartLine = -1;
            selectionEndLine = -1;
            return;
        }
        
        // Calculate start position
        int pos = 0;
        selectionStartLine = 0;
        
        for (String line : lines) {
            int lineLength = line.length();
            if (pos + lineLength >= selectionStart) {
                break;
            }
            pos += lineLength + 1; // +1 for newline
            selectionStartLine++;
        }
        
        // Calculate end position
        pos = 0;
        selectionEndLine = 0;
        
        for (String line : lines) {
            int lineLength = line.length();
            if (pos + lineLength >= selectionEnd) {
                break;
            }
            pos += lineLength + 1; // +1 for newline
            selectionEndLine++;
        }
    }
    
    private void setSelection(int start, int end) {
        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }
        
        selectionStart = start;
        selectionEnd = end;
        updateSelectionPositions();
        
        // Reset cursor blink when selection changes
        lastBlinkTime = System.currentTimeMillis();
        cursorVisible = true;
    }
    
    private void clearSelection() {
        selectionStart = selectionEnd = -1;
        selectionStartLine = -1;
        selectionEndLine = -1;
    }
    
    private boolean hasSelection() {
        return selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd;
    }
    
    public String getSelectedText() {
        if (!hasSelection()) return "";
        String text = this.getText();
        return text.substring(Math.min(selectionStart, selectionEnd), Math.max(selectionStart, selectionEnd));
    }
    
    private void deleteSelection() {
        if (!hasSelection()) return;
        
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        
        String currentText = this.getText();
        String newText = currentText.substring(0, start) + currentText.substring(end);
        
        this.setText(newText);
        this.setCursor(start);
        clearSelection();
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isMouseOver(mouseX, mouseY)) { // Left click
            // Calculate which line was clicked
            int relativeY = (int)(mouseY - this.getY() - TEXT_PADDING);
            int lineHeight = ((TextFieldWidgetAccessor)(Object)this).getTextRenderer().fontHeight + 2;
            int clickedLineOffset = relativeY / lineHeight;
            int clickedLine = scrollPosition + clickedLineOffset;
            
            // Make sure the clicked line is valid
            if (clickedLine >= 0 && clickedLine < lines.size()) {
                // Calculate which column was clicked
                int relativeX = (int)(mouseX - this.getX() - TEXT_PADDING);
                String line = lines.get(clickedLine);
                int clickedColumn = 0;
                
                // Find the closest character position based on X coordinate
                TextRenderer textRenderer = ((TextFieldWidgetAccessor)(Object)this).getTextRenderer();
                
                // Convert tabs to visual representation for width calculation
                String displayLine = line.replace(String.valueOf(TAB_CHAR), TAB_VISUAL);
                
                if (relativeX > 0 && !displayLine.isEmpty()) {
                    int currentX = 0;
                    
                    // Iterate through each character to find the closest position
                    for (int i = 0; i <= displayLine.length(); i++) {
                        int charWidth = 0;
                        if (i < displayLine.length()) {
                            charWidth = textRenderer.getWidth(String.valueOf(displayLine.charAt(i)));
                        }
                        
                        // Check if click is closer to this position or the next
                        if (relativeX <= currentX + charWidth / 2) {
                            // Convert back from display position to actual position
                            clickedColumn = convertDisplayPositionToActual(line, i);
                            break;
                        }
                        
                        currentX += charWidth;
                        
                        // If we've reached the end of the line
                        if (i == displayLine.length()) {
                            clickedColumn = convertDisplayPositionToActual(line, i);
                            break;
                        }
                    }
                    
                    // Make sure column doesn't exceed line length
                    clickedColumn = Math.min(clickedColumn, line.length());
                } else if (relativeX <= 0) {
                    clickedColumn = 0;
                } else {
                    // Click is beyond the end of the line
                    clickedColumn = line.length();
                }
                
                // Calculate the absolute cursor position
                int newCursorPos = calculatePositionFromLineCol(clickedLine, clickedColumn);
                
                // Handle shift-click for selection
                if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                    GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) {
                    
                    if (!hasSelection()) {
                        // Start new selection from current cursor position
                        setSelection(this.getCursor(), newCursorPos);
                    } else {
                        // Extend existing selection
                        setSelection(selectionStart, newCursorPos);
                    }
                } else {
                    // Clear selection on normal click
                    clearSelection();
                    
                    // Start potential drag selection
                    isDragging = true;
                    dragStartPos = newCursorPos;
                }
                
                // Set the cursor position
                this.setCursor(newCursorPos);
                
                // Update internal cursor tracking
                cursorLine = clickedLine;
                cursorColumn = clickedColumn;
                
                // Reset cursor blink
                lastBlinkTime = System.currentTimeMillis();
                cursorVisible = true;
                
                // Focus the widget
                this.setFocused(true);
                
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && isDragging && this.isMouseOver(mouseX, mouseY)) {
            // Calculate position under mouse
            int relativeY = (int)(mouseY - this.getY() - TEXT_PADDING);
            int lineHeight = ((TextFieldWidgetAccessor)(Object)this).getTextRenderer().fontHeight + 2;
            int draggedLineOffset = relativeY / lineHeight;
            int draggedLine = scrollPosition + draggedLineOffset;
            
            // Clamp to valid lines
            draggedLine = Math.max(0, Math.min(draggedLine, lines.size() - 1));
            
            if (draggedLine >= 0 && draggedLine < lines.size()) {
                // Calculate column
                int relativeX = (int)(mouseX - this.getX() - TEXT_PADDING);
                String line = lines.get(draggedLine);
                int draggedColumn = 0;
                
                TextRenderer textRenderer = ((TextFieldWidgetAccessor)(Object)this).getTextRenderer();
                String displayLine = line.replace(String.valueOf(TAB_CHAR), TAB_VISUAL);
                
                if (relativeX > 0 && !displayLine.isEmpty()) {
                    int currentX = 0;
                    for (int i = 0; i <= displayLine.length(); i++) {
                        int charWidth = 0;
                        if (i < displayLine.length()) {
                            charWidth = textRenderer.getWidth(String.valueOf(displayLine.charAt(i)));
                        }
                        
                        if (relativeX <= currentX + charWidth / 2) {
                            draggedColumn = convertDisplayPositionToActual(line, i);
                            break;
                        }
                        
                        currentX += charWidth;
                        
                        if (i == displayLine.length()) {
                            draggedColumn = convertDisplayPositionToActual(line, i);
                            break;
                        }
                    }
                    draggedColumn = Math.min(draggedColumn, line.length());
                } else if (relativeX <= 0) {
                    draggedColumn = 0;
                } else {
                    draggedColumn = line.length();
                }
                
                int draggedPos = calculatePositionFromLineCol(draggedLine, draggedColumn);
                
                // Update selection
                setSelection(dragStartPos, draggedPos);
                this.setCursor(draggedPos);
                
                // Update cursor tracking
                cursorLine = draggedLine;
                cursorColumn = draggedColumn;
            }
            
            return true;
        }
        
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;
            dragStartPos = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    /**
     * Converts a position in the display string (with visual tabs) back to actual position
     */
    private int convertDisplayPositionToActual(String actualLine, int displayPosition) {
        int actualPosition = 0;
        int displayPos = 0;
        
        for (int i = 0; i < actualLine.length() && displayPos < displayPosition; i++) {
            if (actualLine.charAt(i) == TAB_CHAR) {
                // Tab takes up 4 display positions but 1 actual position
                displayPos += TAB_VISUAL.length();
                if (displayPos > displayPosition) {
                    // Click was within the tab, so position cursor at the tab
                    break;
                }
            } else {
                displayPos++;
            }
            actualPosition++;
        }
        
        return actualPosition;
    }
    
    @Override
    public void write(String text) {
        // Only save undo state for meaningful changes (not during undo/redo)
        if (!isUndoRedoOperation) {
            saveUndoState();
        }
        
        // If there's a selection, delete it first
        if (hasSelection()) {
            deleteSelection();
        }
        
        // Special handling for pasted text that might contain tabs and newlines
        if (text.length() > 1) {
            // This is likely a paste operation
            // Preserve tabs and newlines exactly as they appear
            String processedText = text;
            
            int cursorPos = this.getCursor();
            String currentText = this.getText();
            String newText = currentText.substring(0, cursorPos) + processedText + currentText.substring(cursorPos);
            
            isUndoRedoOperation = true; // Prevent setText from saving another undo state
            this.setText(newText);
            isUndoRedoOperation = false;
            this.setCursor(cursorPos + processedText.length());
            updateLines();
            return;
        }
        
        // Handle special characters in single character input
        if (text.equals("\n") || text.equals("\r") || text.equals("\r\n")) {
            // Handle Enter key by inserting a newline at the cursor position
            int cursorPos = this.getCursor();
            String currentText = this.getText();
            String newText = currentText.substring(0, cursorPos) + "\n" + currentText.substring(cursorPos);
            isUndoRedoOperation = true;
            this.setText(newText);
            isUndoRedoOperation = false;
            this.setCursor(cursorPos + 1);
            updateLines();
        } else if (text.equals("\t")) {
            // Handle Tab key by inserting a tab character at the cursor position
            int cursorPos = this.getCursor();
            String currentText = this.getText();
            String newText = currentText.substring(0, cursorPos) + String.valueOf(TAB_CHAR) + currentText.substring(cursorPos);
            isUndoRedoOperation = true;
            this.setText(newText);
            isUndoRedoOperation = false;
            this.setCursor(cursorPos + 1);
            updateLines();
        } else {
            super.write(text);
            updateLines();
        }
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean shiftPressed = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        // Use the new helper method to check for primary modifier key (Ctrl on Windows/Linux, Cmd on Mac)
        boolean primaryModPressed = isPrimaryModifierPressed(modifiers);
        
        // Handle Primary+Z (Undo) - Ctrl+Z on Windows/Linux, Cmd+Z on Mac
        if (keyCode == GLFW.GLFW_KEY_Z && primaryModPressed && !shiftPressed) {
            return undo();
        }
        
        // Handle Primary+Y or Primary+Shift+Z (Redo) - Ctrl+Y/Ctrl+Shift+Z on Windows/Linux, Cmd+Y/Cmd+Shift+Z on Mac
        if ((keyCode == GLFW.GLFW_KEY_Y && primaryModPressed) || 
            (keyCode == GLFW.GLFW_KEY_Z && primaryModPressed && shiftPressed)) {
            return redo();
        }
        
        // Handle Primary+A (Select All) - Ctrl+A on Windows/Linux, Cmd+A on Mac
        if (keyCode == GLFW.GLFW_KEY_A && primaryModPressed) {
            setSelection(0, this.getText().length());
            this.setCursor(this.getText().length());
            updateCursorPosition();
            return true;
        }
        
        // Handle Primary+C (Copy) - Ctrl+C on Windows/Linux, Cmd+C on Mac
        if (keyCode == GLFW.GLFW_KEY_C && primaryModPressed) {
            if (hasSelection()) {
                client.keyboard.setClipboard(getSelectedText());
            }
            return true;
        }
        
        // Handle Primary+X (Cut) - Ctrl+X on Windows/Linux, Cmd+X on Mac
        if (keyCode == GLFW.GLFW_KEY_X && primaryModPressed) {
            if (hasSelection()) {
                saveUndoState();
                client.keyboard.setClipboard(getSelectedText());
                deleteSelection();
                updateLines();
            }
            return true;
        }
        
        // Handle Primary+V (Paste) - Ctrl+V on Windows/Linux, Cmd+V on Mac
        if (keyCode == GLFW.GLFW_KEY_V && primaryModPressed) {
            String clipboard = client.keyboard.getClipboard();
            if (clipboard != null) {
                this.write(clipboard);
                return true;
            }
        }
        
        // Save undo state for destructive operations (but not during undo/redo)
        boolean isDestructiveOperation = (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) && 
                                    (hasSelection() || this.getText().length() > 0);

        if (isDestructiveOperation && !isUndoRedoOperation) {
            saveUndoState();
        }
        
        // Handle Delete and Backspace with selection
        if ((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) && hasSelection()) {
            deleteSelection();
            updateLines();
            return true;
        }
        
        // Handle arrow keys with selection
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            int currentPos = this.getCursor();
            int newPos = Math.max(0, currentPos - 1);
            
            if (shiftPressed) {
                if (!hasSelection()) {
                    setSelection(currentPos, newPos);
                } else {
                    setSelection(selectionStart, newPos);
                }
            } else {
                clearSelection();
            }
            
            this.setCursor(newPos);
            updateCursorPosition();
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            int currentPos = this.getCursor();
            int newPos = Math.min(this.getText().length(), currentPos + 1);
            
            if (shiftPressed) {
                if (!hasSelection()) {
                    setSelection(currentPos, newPos);
                } else {
                    setSelection(selectionStart, newPos);
                }
            } else {
                clearSelection();
            }
            
            this.setCursor(newPos);
            updateCursorPosition();
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (cursorLine > 0) {
                int currentPos = this.getCursor();
                int targetPos = calculatePositionFromLineCol(cursorLine - 1, Math.min(cursorColumn, lines.get(cursorLine - 1).length()));
                
                if (shiftPressed) {
                    if (!hasSelection()) {
                        setSelection(currentPos, targetPos);
                    } else {
                        setSelection(selectionStart, targetPos);
                    }
                } else {
                    clearSelection();
                }
                
                this.setCursor(targetPos);
                updateCursorPosition();
                return true;
            }
        }
        
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (cursorLine < lines.size() - 1) {
                int currentPos = this.getCursor();
                int targetPos = calculatePositionFromLineCol(cursorLine + 1, Math.min(cursorColumn, lines.get(cursorLine + 1).length()));
                
                if (shiftPressed) {
                    if (!hasSelection()) {
                        setSelection(currentPos, targetPos);
                    } else {
                        setSelection(selectionStart, targetPos);
                    }
                } else {
                    clearSelection();
                }
                
                this.setCursor(targetPos);
                updateCursorPosition();
                return true;
            }
        }
        
        // Handle Home key
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            int currentPos = this.getCursor();
            int lineStartPos = calculatePositionFromLineCol(cursorLine, 0);
            
            if (shiftPressed) {
                if (!hasSelection()) {
                    setSelection(currentPos, lineStartPos);
                } else {
                    setSelection(selectionStart, lineStartPos);
                }
            } else {
                clearSelection();
            }
            
            this.setCursor(lineStartPos);
            updateCursorPosition();
            return true;
        }
        
        // Handle End key
        if (keyCode == GLFW.GLFW_KEY_END) {
            int currentPos = this.getCursor();
            int lineEndPos = calculatePositionFromLineCol(cursorLine, lines.get(cursorLine).length());
            
            if (shiftPressed) {
                if (!hasSelection()) {
                    setSelection(currentPos, lineEndPos);
                } else {
                    setSelection(selectionStart, lineEndPos);
                }
            } else {
                clearSelection();
            }
            
            this.setCursor(lineEndPos);
            updateCursorPosition();
            return true;
        }
        
        // Handle regular keys that clear selection
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (hasSelection()) {
                deleteSelection();
            }
            this.write("\n");
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_TAB) {
            if (hasSelection()) {
                deleteSelection();
            }
            this.write("\t");
            return true;
        } else {
            // For other keys, if there's a selection and it's a character input, clear selection first
            boolean result = super.keyPressed(keyCode, scanCode, modifiers);
            updateLines();
            return result;
        }
    }
    
    private int calculatePositionFromLineCol(int line, int column) {
        int pos = 0;
        for (int i = 0; i < line; i++) {
            pos += lines.get(i).length() + 1; // +1 for newline
        }
        return pos + column;
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw background
        context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF000000);
        context.drawBorder(this.getX(), this.getY(), this.width, this.height, 0xFFAAAAAA);
        
        // Get text renderer
        TextRenderer textRenderer = ((TextFieldWidgetAccessor)(Object)this).getTextRenderer();
        
        // Draw text lines with real newlines and tabs
        if (!lines.isEmpty()) {
            int endIndex = Math.min(scrollPosition + maxVisibleLines, lines.size());
            int y = this.getY() + TEXT_PADDING;
            
            for (int i = scrollPosition; i < endIndex; i++) {
                // Replace tab characters with visual representation for display only
                String displayLine = lines.get(i).replace(String.valueOf(TAB_CHAR), TAB_VISUAL);
                
                // Draw selection highlighting for this line
                if (hasSelection()) {
                    drawSelectionHighlight(context, textRenderer, i, y, displayLine);
                }
                
                // Draw the line
                context.drawText(textRenderer, displayLine, this.getX() + TEXT_PADDING, y, 0xFFFFFF, false);
                y += textRenderer.fontHeight + 2;
            }
            
            // Draw cursor if this field has focus and is editable
            if (this.isFocused() && ((TextFieldWidgetAccessor)(Object)this).isEditable()) {
                // Update cursor blink
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBlinkTime > CURSOR_BLINK_RATE) {
                    cursorVisible = !cursorVisible;
                    lastBlinkTime = currentTime;
                }
                
                // Draw cursor if visible and in view (and no selection or cursor is at selection boundary)
                if (cursorVisible && cursorLine >= scrollPosition && cursorLine < scrollPosition + maxVisibleLines &&
                    (!hasSelection() || this.getCursor() == selectionStart || this.getCursor() == selectionEnd)) {
                    int cursorY = this.getY() + TEXT_PADDING + (cursorLine - scrollPosition) * (textRenderer.fontHeight + 2);
                    int cursorX = this.getX() + TEXT_PADDING;
                    
                    // Calculate cursor X position accounting for tabs
                    if (cursorColumn > 0) {
                        String textBeforeCursor = lines.get(cursorLine).substring(0, cursorColumn).replace(String.valueOf(TAB_CHAR), TAB_VISUAL);
                        cursorX += textRenderer.getWidth(textBeforeCursor);
                    }
                    
                    // Draw cursor line
                    context.fill(cursorX, cursorY, cursorX + 1, cursorY + textRenderer.fontHeight, 0xFFFFFFFF);
                }
            }
            
            // Draw scroll indicators if needed
            if (lines.size() > maxVisibleLines) {
                // Up arrow if scrolled down
                if (scrollPosition > 0) {
                    context.drawText(textRenderer, "▲", this.getX() + this.width - 10, this.getY() + 2, 0xFFFFFF, false);
                }
                
                // Down arrow if can scroll more
                if (scrollPosition + maxVisibleLines < lines.size()) {
                    context.drawText(textRenderer, "▼", this.getX() + this.width - 10, this.getY() + this.height - 10, 0xFFFFFF, false);
                }
                
                // Draw scroll position indicator
                String scrollInfo = (scrollPosition + 1) + "-" + Math.min(scrollPosition + maxVisibleLines, lines.size()) + "/" + lines.size();
                context.drawText(textRenderer, scrollInfo, this.getX() + this.width - textRenderer.getWidth(scrollInfo) - 4, 
                    this.getY() + this.height - textRenderer.fontHeight - 2, 0xAAAAAA, false);
            }
        }
    }
    
    private void drawSelectionHighlight(DrawContext context, TextRenderer textRenderer, int lineIndex, int lineY, String displayLine) {
        // Check if this line has any selection
        if (selectionStartLine == -1 || selectionEndLine == -1) return;
        
        int actualSelStart = Math.min(selectionStart, selectionEnd);
        int actualSelEnd = Math.max(selectionStart, selectionEnd);
        
        if (actualSelStart == actualSelEnd) return;
        
        // Calculate line positions
        int lineStart = calculatePositionFromLineCol(lineIndex, 0);
        int lineEnd = calculatePositionFromLineCol(lineIndex, lines.get(lineIndex).length());
        
        // Check if selection intersects with this line
        if (actualSelEnd <= lineStart || actualSelStart >= lineEnd) return;
        
        // Calculate selection bounds within this line
        int selStartInLine = Math.max(0, actualSelStart - lineStart);
        int selEndInLine = Math.min(lines.get(lineIndex).length(), actualSelEnd - lineStart);
        
        if (selStartInLine >= selEndInLine) return;
        
        // Convert to display positions for proper tab handling
        String actualLine = lines.get(lineIndex);
        int displayStartPos = convertActualPositionToDisplay(actualLine, selStartInLine);
        int displayEndPos = convertActualPositionToDisplay(actualLine, selEndInLine);
        
        // Calculate X positions
        int selectionStartX = this.getX() + TEXT_PADDING;
        int selectionEndX = this.getX() + TEXT_PADDING;
        
        if (displayStartPos > 0) {
            String textBefore = displayLine.substring(0, Math.min(displayStartPos, displayLine.length()));
            selectionStartX += textRenderer.getWidth(textBefore);
        }
        
        if (displayEndPos > 0) {
            String textBefore = displayLine.substring(0, Math.min(displayEndPos, displayLine.length()));
            selectionEndX += textRenderer.getWidth(textBefore);
        }
        
        // If selection goes to end of line, extend to a reasonable width
        if (selEndInLine >= actualLine.length()) {
            selectionEndX += 4; // Small padding to show selection at line end
        }
        
        // Draw selection background
        context.fill(selectionStartX, lineY, selectionEndX, lineY + textRenderer.fontHeight, 0x663366FF);
    }
    
    private int convertActualPositionToDisplay(String actualLine, int actualPosition) {
        int displayPosition = 0;
        
        for (int i = 0; i < Math.min(actualPosition, actualLine.length()); i++) {
            if (actualLine.charAt(i) == TAB_CHAR) {
                displayPosition += TAB_VISUAL.length();
            } else {
                displayPosition++;
            }
        }
        
        return displayPosition;
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        // Handle scrolling
        if (amount > 0 && scrollPosition > 0) {
            scrollPosition--;
            return true;
        } else if (amount < 0) {
            if (scrollPosition < lines.size() - maxVisibleLines) {
                scrollPosition++;
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        // Only save undo state for printable characters and not during undo/redo
        if (chr >= 32 && !isUndoRedoOperation) {
            saveUndoState();
        }
        
        // Clear selection when typing regular characters
        if (hasSelection() && chr >= 32) { // Printable characters
            deleteSelection();
        }
        
        boolean result = super.charTyped(chr, modifiers);
        updateLines();
        return result;
    }
    
    @Override
    public void setCursor(int cursor) {
        super.setCursor(cursor);
        updateCursorPosition();
    }
    
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) {
            // Reset cursor blink when gaining focus
            lastBlinkTime = System.currentTimeMillis();
            cursorVisible = true;
        } else {
            // Clear selection when losing focus
            clearSelection();
        }
    }
    
    /**
     * Ensures tabs and newlines are preserved in the text
     */
    @Override
    public void setText(String text) {
        // Only save undo state if this is not an undo/redo operation and text is actually different
        if (!isUndoRedoOperation && !text.equals(this.getText())) {
            saveUndoState();
        }
        
        super.setText(text);
        clearSelection();
        updateLines();
    }
    
    /**
     * Make sure we return text with all tabs and newlines preserved
     */
    @Override
    public String getText() {
        String text = super.getText();
        return text;
    }
    
    /**
     * Clears all undo/redo history
     */
    public void clearHistory() {
        undoStack.clear();
        redoStack.clear();
        // Save current state as the new starting point
        saveUndoState();
    }
    
    /**
     * Gets the current number of undo operations available
     */
    public int getUndoCount() {
        return Math.max(0, undoStack.size() - 1);
    }
    
    /**
     * Gets the current number of redo operations available
     */
    public int getRedoCount() {
        return redoStack.size();
    }
}