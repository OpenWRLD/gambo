/*  Copyright (c) 2012 by Vincent Pacelli and Omar Rizwan

    This file is part of Gabby.

    Gabby is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gabby is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Gabby.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.gabby.core;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

class Display {
    public static final int TILE_WIDTH = 8;
    public static final int TILE_HEIGHT = 8;
    public static final int SCREEN_WIDTH = 256;
    public static final int SCREEN_HEIGHT = 256;

    public static final int HBLANK_MODE = 0;
    public static final int VBLANK_MODE = 1;
    public static final int OAM_READ_MODE = 2;
    public static final int VRAM_READ_MODE = 3;

    
    protected Graphics2D g;
    protected int clock;
    protected int mode;
    protected int line;
    protected Ram ram;
    BufferedImage buffer;
    Emulator emulator;

    public Display(Ram ram, Emulator emulator) {
        clock = mode = line = 0;
        this.ram = ram;
        buffer = new BufferedImage(160, 144, BufferedImage.TYPE_INT_ARGB);
        this.emulator = emulator;
    }

    public Graphics2D getG() { return g; }
    public void setG(Graphics2D g) { this.g = g; }

    protected void drawTile(Graphics2D g, Ram ram, int spriteNumber, int table, int x, int y) {
        ByteBuffer spriteData = ByteBuffer.allocate(16);
        spriteData.put(ram.getMemory().array(), table + spriteNumber * 16, 16);
        Color c = new Color(255, 0, 255, 255);
		
        for (int i = 0; i < SCREEN_HEIGHT; i++) {
            for (int j = 0; j < SCREEN_WIDTH; j++) {
                Color color = BitTwiddles.getColorFromBytePair(j, spriteData.get(i), spriteData.get(i + 1));
                g.setPaint(color);
                g.drawLine(x + j, y + i, x + j, y + i);
            }
        }
    }

    protected void drawTILE_MAP(Graphics2D g, Ram ram) {
        if (BitTwiddles.getBit(0, ram.getMemory().get(Ram.LCDC)) == 1) {
            if (BitTwiddles.getBit(3, ram.getMemory().get(Ram.LCDC)) == 0) {
                for (int i = 0; i < 32 * 32; i++) {
                    int patternNumber = ram.getMemory().get(Ram.TILE_MAP_ONE + i);
                    int scx = ram.getMemory().get(Ram.SCX);
                    int scy = ram.getMemory().get(Ram.SCY);
					
                    if (scx < 0)
                        scx += 256;
                    else if (scx > 256)
                        scx -= 256;
                    if (scy < 0)
                        scy += 256;
                    else if (scy > 256)
                        scy -= 256;
                    
                    if (patternNumber < 0)
                        patternNumber += 256;
                    if (BitTwiddles.getBit(4, ram.getMemory().get(Ram.LCDC)) == 1)
                        drawTile(g, ram, patternNumber, Ram.TILE_TABLE_ONE, (i % 32) + scx, (int) Math.floor(i / 32) + scy);
                    else
                        drawTile(g, ram, patternNumber, Ram.TILE_TABLE_TWO, (i % 32) + scx, (int) Math.floor(i / 32) + scy);
                }
            } else {
                for (int i = 0; i < 32 * 32; i++) {
                    int patternNumber = ram.getMemory().get(Ram.TILE_MAP_TWO + i);
                    int scx = ram.getMemory().get(Ram.SCX);
                    int scy = ram.getMemory().get(Ram.SCY);
					
                    if (scx < 0)
                        scx += 256;
                    else if (scx > 256)
                        scx -= 256;
                    if (scy < 0)
                        scy += 256;
                    else if (scy > 256)
                        scy -= 256;
                    
                    if (patternNumber < 0)
                        patternNumber += 128; // since the id's are signed
                    if (BitTwiddles.getBit(4, ram.getMemory().get(Ram.LCDC)) == 1)
                        drawTile(g, ram, patternNumber, Ram.TILE_TABLE_ONE, (i % 32) + scx, (int) Math.floor(i / 32) + scy);
                    else
                        drawTile(g, ram, patternNumber, Ram.TILE_TABLE_TWO, (i % 32) + scx, (int) Math.floor(i / 32) + scy);
                }
            }
        }
    }
    
    protected void drawWindow(Graphics2D g, Ram ram) {
        if (BitTwiddles.getBit(5, ram.getMemory().get(Ram.LCDC)) == 1) {
            if (BitTwiddles.getBit(6, ram.getMemory().get(Ram.LCDC)) == 0) {
                for (int i = 0; i < 32 * 32; i++) {
                    int patternNumber = ram.getMemory().get(Ram.TILE_MAP_ONE + i);
                    int wx = ram.getMemory().get(Ram.WX);
                    int wy = ram.getMemory().get(Ram.WY);
					
                    if (wx < 0)
                        wx += 256;
                    if (wy < 0)
                        wy += 256;
                    if (patternNumber < 0)
                        patternNumber += 256;
                    if (BitTwiddles.getBit(4, ram.getMemory().get(Ram.LCDC)) == 1)
                        drawTile(g, ram, patternNumber, Ram.TILE_TABLE_ONE, (i % 32) + wx, (int) Math.floor(i / 32) + wy);
                    else
                        drawTile(g, ram, patternNumber, Ram.TILE_TABLE_TWO, (i % 32) + wx, (int) Math.floor(i / 32) + wy);
                }
            } else {
                for (int i = 0; i < 32 * 32; i++) {
                    int patternNumber = ram.getMemory().get(Ram.TILE_MAP_TWO + i);
                    int scx = ram.getMemory().get(Ram.SCX);
                    int scy = ram.getMemory().get(Ram.SCY);
					
                    if (scx < 0)
                        scx += 256;
                    if (scy < 0)
                        scy += 256;
                    if (patternNumber < 0)
                        patternNumber += 128; // since the id's are signed
                    if (BitTwiddles.getBit(4, ram.getMemory().get(Ram.LCDC)) == 1)
                        drawTile(g, ram, patternNumber, Ram.TILE_TABLE_ONE, (i % 32) + scx, (int) Math.floor(i / 32) + scy);
                    else
                        drawTile(g, ram, patternNumber, Ram.TILE_TABLE_TWO, (i % 32) + scx, (int) Math.floor(i / 32) + scy);
                }
            }
        }	
    }

    public void draw(Ram ram, Graphics2D g) {
        if (BitTwiddles.getBit(7, ram.getMemory().get(Ram.LCDC)) != 0) {
            drawTILE_MAP(g, ram);
            drawWindow(g, ram);

            Sprite.drawAllSprites(ram.getMemory(), g);
        }
    }

    public void scanline(int line) {
        int bgmap = (BitTwiddles.getBit(3, ram.getMemory().get(Ram.LCDC)) == 0) ? Ram.TILE_MAP_ONE : Ram.TILE_MAP_TWO;
        int lineOffset = bgmap + (((line + ram.getMemory().get(Ram.SCY)) & 0xFF) / 8);
        int firstTileOffset = ram.getMemory().get(Ram.SCX) / 8;
        int y = (line + ram.getMemory().get(Ram.SCY)) & 0x7;
        int x = ram.getMemory().get(Ram.SCX);
        int tile = ram.getMemory().get(lineOffset + firstTileOffset);
        Color c = null;
        Graphics2D g = buffer.createGraphics();

        if (BitTwiddles.getBit(3, ram.getMemory().get(Ram.LCDC)) == 1 && tile < 128) {
            tile += 256;
        }

        for (int i = 0; i < 160; i++) {
            c = BitTwiddles.getColorFromBytePair(x, ram.getMemory().get(0x8000 + tile), ram.getMemory().get(0x8000 + tile + 1));
            g.setPaint(c);
            g.drawLine(x, x, y, y);
            x++;

            if (x == 8) {
                x = 0;
                lineOffset = (lineOffset + 1) & 31;
                tile = ram.getMemory().get(lineOffset + firstTileOffset);

                if (BitTwiddles.getBit(3, ram.getMemory().get(Ram.LCDC)) == 1 && tile < 128) {
                    tile += 256;
                }
            }
        }
        
        g.dispose();
    }

    public void step(int deltaClock) {
        clock += deltaClock;

        switch (mode) {
            case OAM_READ_MODE:
                if (clock > 79) {
                    clock = 0;
                    mode = VRAM_READ_MODE;
                }

                break;
            case VRAM_READ_MODE:
                if (clock > 171) {
                    clock = 0;
                    mode = HBLANK_MODE;

                    scanline(line);
                }

                break;
            case HBLANK_MODE:
                if (clock > 203) {
                    clock = 0;
                    line++;

                    if (line == 143) { // at right
                        mode = VBLANK_MODE;
                        emulator.bufferFromBuffer(buffer);
                        emulator.repaint();
                    } else {
                        mode = OAM_READ_MODE;
                    }
                }

                break;
            case VBLANK_MODE:
                if (clock > 455) { // at bottom
                    clock = 0;
                    line++;

                    if (line > 153) {
                        mode = 2;
                        line = 0;
                    }
                }

                break;
            default:
                break;
        }
        
        ram.getMemory().put(Ram.LY, (byte) line);
    }
}
