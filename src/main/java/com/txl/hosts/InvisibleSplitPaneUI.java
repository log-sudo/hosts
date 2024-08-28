package com.txl.hosts;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;

public class InvisibleSplitPaneUI extends BasicSplitPaneUI {
    @Override
    public BasicSplitPaneDivider createDefaultDivider() {
        BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this) {
            @Override
            public void setBorder(Border border) {
                super.setBorder(BorderFactory.createEmptyBorder());
            }
        };

        divider.setBackground(new Color(192, 192, 192));
        divider.setPreferredSize(new Dimension(4, 0));
        return divider;
    }

}
