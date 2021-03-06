package com.energyxxer.trident.ui.orderlist;

import com.energyxxer.trident.ui.explorer.base.StyleProvider;

import java.awt.*;
import java.awt.event.MouseEvent;

public abstract class ItemButtonAction implements ItemAction {
    protected boolean leftAligned = false;

    public abstract Image getIcon();
    public abstract String getDescription();
    public void perform() {}

    private static final int buttonHGap = 6;
    private static final int buttonSize = 20;
    private static final int iconMargin = (buttonSize - 16) / 2;

    @Override
    public boolean isLeftAligned() {
        return leftAligned;
    }

    @Override
    public void render(Graphics g, ItemActionHost host, int x, int y, int w, int h, int mouseState, boolean actionEnabled) {
        StyleProvider styleProvider = host.getStyleProvider();

        int buttonVGap = (h - buttonSize) / 2;

        String styleKeyVariation = mouseState == 2 ? ".pressed" : mouseState == 1 ? ".rollover" : "";

        if(!isLeftAligned()) x -= buttonSize;

        Composite originalComposite = ((Graphics2D) g).getComposite();
        if(actionEnabled) {
            int buttonBorderThickness = styleProvider.getStyleNumbers().get("button" + styleKeyVariation + ".border.thickness");
            g.setColor(styleProvider.getColors().get("button" + styleKeyVariation + ".border.color"));
            g.fillRect(x - buttonBorderThickness, y + buttonVGap - buttonBorderThickness, buttonSize + 2*buttonBorderThickness, buttonSize + 2*buttonBorderThickness);

            g.setColor(styleProvider.getColors().get("button" + styleKeyVariation + ".background"));
            g.fillRect(x, y + buttonVGap, buttonSize, buttonSize);
        }
        if(!actionEnabled) ((Graphics2D) g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g.drawImage(getIcon(), x + iconMargin, y + buttonVGap + iconMargin, 16, 16, null);
        ((Graphics2D) g).setComposite(originalComposite);
    }

    @Override
    public int getRenderedWidth() {
        return buttonHGap + buttonSize;
    }

    @Override
    public int getHintOffset() {
        return (buttonSize / 2) + (isLeftAligned() ? 0 : buttonHGap);
    }

    @Override
    public String getHint() {
        return getDescription();
    }

    @Override
    public boolean intersects(Point p, int w, int h) {
        int buttonSize = 20;
        int buttonVGap = (h - buttonSize) / 2;

        return (p.getY() >= buttonVGap && p.getY() < buttonVGap + buttonSize) &&
                (p.getX() >= 0 && p.getX() < buttonSize);
    }

    @Override
    public void mouseReleased(MouseEvent e, ItemActionHost host) {
        if(e.getButton() == MouseEvent.BUTTON1) {
            e.consume();
            perform();
            host.performOperation(getActionCode());
        }
    }
}
