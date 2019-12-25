package com.energyxxer.trident.ui.editor.behavior.editmanager.edits;

import com.energyxxer.trident.ui.editor.behavior.AdvancedEditor;
import com.energyxxer.trident.ui.editor.behavior.caret.CaretProfile;
import com.energyxxer.trident.ui.editor.behavior.caret.Dot;
import com.energyxxer.trident.ui.editor.behavior.caret.EditorCaret;
import com.energyxxer.trident.ui.editor.behavior.editmanager.Edit;
import com.energyxxer.trident.ui.editor.folding.FoldableDocument;

import javax.swing.text.BadLocationException;
import java.util.ArrayList;

/**
 * Created by User on 1/10/2017.
 */
public class DeletionEdit extends Edit {
    private boolean wholeWord = false;
    private boolean forwards = false;
    private ArrayList<String> previousValues = new ArrayList<>();
    private CaretProfile previousProfile;
    private CaretProfile nextProfile = null;
    private int deletionAmount = -1;

    public DeletionEdit(AdvancedEditor editor, int deletionAmount) {
        this(editor, deletionAmount, false);
    }
    public DeletionEdit(AdvancedEditor editor, int deletionAmount, boolean forwards) {
        this(editor, false, forwards);
        this.deletionAmount = deletionAmount;
    }
    public DeletionEdit(AdvancedEditor editor) {
        this(editor, false, false);
    }
    public DeletionEdit(AdvancedEditor editor, boolean wholeWord) {
        this(editor,wholeWord,false);
    }
    public DeletionEdit(AdvancedEditor editor, boolean wholeWord, boolean forwards) {
        previousProfile = editor.getCaret().getProfile();
        this.wholeWord = wholeWord;
        this.forwards = forwards;
    }

    @Override
    public boolean redo(AdvancedEditor editor) {
        FoldableDocument doc = editor.getFoldableDocument();
        EditorCaret caret = editor.getCaret();

        boolean actionPerformed = false;

        try {
            String result = doc.getUnfoldedText(); //Result

            int characterDrift = 0;

            previousValues.clear();
            nextProfile = new CaretProfile();

            for (int i = 0; i < previousProfile.size() - 1; i += 2) {
                int start = previousProfile.get(i) + characterDrift;
                int end = previousProfile.get(i + 1) + characterDrift;
                if(start == end) {
                    if(wholeWord) {
                        if(forwards) {
                            start = new Dot(start, end, editor).getPositionAfterWord();
                        } else {
                            start = new Dot(start, end, editor).getPositionBeforeWord();
                        }
                    } else {
                        if(deletionAmount > -1) {
                            if(forwards) {
                                start = Math.max(0, start + deletionAmount);
                            } else {
                                start = Math.max(0, start - deletionAmount);
                            }
                        } else {
                            if(forwards) {
                                start = new Dot(start, end, editor).getPositionAfter();
                            } else {
                                Dot tempDot = new Dot(start, end, editor);
                                if(tempDot.isInIndentation()) {
                                    start = tempDot.getRowStart()-1;
                                    if(start < 0) start = 0;
                                    end = tempDot.getRowContentStart();
                                } else {
                                    start = tempDot.getPositionBefore();
                                }
                            }
                        }
                    }
                }
                if(end < start) {
                    int temp = start;
                    start = end;
                    end = temp;
                }

                if(start != end) actionPerformed = true;

                previousValues.add(result.substring(start, end));
                result = result.substring(0, start) + result.substring(end);

                nextProfile.add(start,start);
                doc.remove(start, end - start);

                characterDrift += start - end;

                final int fstart = start;
                final int fend = end;

                editor.registerCharacterDrift(o -> (o >= fstart) ? ((o <= fend) ? fstart : o + (fstart-fend)): o);
            }

            if(actionPerformed) caret.setProfile(nextProfile);

        } catch(BadLocationException e) {
            e.printStackTrace();
        }
        return actionPerformed;
    }

    @Override
    public boolean undo(AdvancedEditor editor) {
        FoldableDocument doc = editor.getFoldableDocument();
        EditorCaret caret = editor.getCaret();

        boolean actionPerformed = false;

        try {
            String str = doc.getUnfoldedText();

            for (int i = nextProfile.size() -2; i >= 0; i -= 2) {
                int start = nextProfile.get(i);
                String previousValue = previousValues.get(i / 2);

                str = str.substring(0, start)
                        + previousValue
                        + str.substring(start);

                if(previousValue.length() != 0) actionPerformed = true;

                doc.insertString(start, previousValue, null);

                final int fstart = start;
                final int fplen = previousValue.length();

                editor.registerCharacterDrift(o -> (o >= fstart) ? o + fplen: o);
            }

            if(actionPerformed) caret.setProfile(previousProfile);

        } catch(BadLocationException e) {
            e.printStackTrace();
        }
        return actionPerformed;
    }
}
