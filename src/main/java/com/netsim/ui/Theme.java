package com.netsim.ui;

import java.awt.Color;
import java.awt.Font;

/** Colors and fonts used when drawing the network. */
final class Theme {

    static final Color BG = new Color(0x1e1e2e);
    static final Color PANEL = new Color(0x181825);
    static final Color GRID = new Color(0x2a2a3d);
    static final Color TEXT = new Color(0xcdd6f4);
    static final Color SUBTEXT = new Color(0xa6adc8);

    static final Color LINK = new Color(0x7f849c);
    static final Color LINK_DOWN = new Color(0xf38ba8);
    static final Color ROUTER = new Color(0x89b4fa);
    static final Color ROUTER_SELECTED = new Color(0xf9e2af);
    static final Color ROUTER_LINK_START = new Color(0xa6e3a1);
    static final Color PATH = new Color(0xf9e2af);
    static final Color PACKET = new Color(0xfab387);
    static final Color PACKET_GLOW = new Color(250, 179, 135, 70);
    static final Color SHADOW = new Color(0, 0, 0, 90);

    static final Font FONT = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font FONT_BOLD = FONT.deriveFont(Font.BOLD);
    static final Font FONT_SMALL = FONT.deriveFont(12f);

    private Theme() {
    }
}