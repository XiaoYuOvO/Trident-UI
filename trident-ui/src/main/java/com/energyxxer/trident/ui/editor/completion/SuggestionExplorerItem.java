package com.energyxxer.trident.ui.editor.completion;

import com.energyxxer.trident.ui.explorer.base.ExplorerMaster;
import com.energyxxer.trident.ui.explorer.base.StandardExplorerItem;

import java.awt.*;

public class SuggestionExplorerItem extends StandardExplorerItem {

    ExpandableSuggestionToken token;

    public SuggestionExplorerItem(ExpandableSuggestionToken token, ExplorerMaster master) {
        super(token, master, null);
        this.token = token;
    }

    @Override
    public void render(Graphics g) {
        if(token.isEnabled()) {
            Composite oldComposite = null;
            if(token.isDarkened()) {
                oldComposite = ((Graphics2D) g).getComposite();
                ((Graphics2D) g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            }
            super.render(g);
            if(oldComposite != null) {
                ((Graphics2D) g).setComposite(oldComposite);
            }
        }
    }
}
