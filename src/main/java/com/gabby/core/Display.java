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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;

import com.gabby.core.Mmu.Interrupts;

class Display {
    public static final int TILE_WIDTH = 8;
    public static final int TILE_HEIGHT = 8;
    public static final int SCREEN_WIDTH = 256;
    public static final int SCREEN_HEIGHT = 256;

    public static final int HBLANK_MODE = 0;
    public static final int VBLANK_MODE = 1;
    public static final int OAM_READ_MODE = 2;
    public static final int VRAM_READ_MODE = 3;

    protected int modeClock, vblankClock;
    protected int mode;
    protected int line, lastLine;
    protected int sizeMultiplyer;

	protected Mmu mmu;
    BufferedImage buffer;
    Emulator emulator;

    public Display(Mmu mmu, Emulator emulator) {
        modeClock = vblankClock = mode = line = lastLine = 0;

        buffer = new BufferedImage(160, 144, BufferedImage.TYPE_INT_ARGB);
        this.emulator = emulator;
        this.emulator.buffer = buffer;
        
        this.mmu = mmu;
        sizeMultiplyer = 1;
    }

    private Color getColorFromPalette(int pal, int c) {
        int palb = mmu.read(pal);

        int colorCode = (palb >> (c * 2)) & BitTwiddles.bx00000011;

        switch (colorCode) {
            case 0:
                return Color.WHITE;
            case 1:
                return Color.LIGHT_GRAY;
            case 2:
                return Color.DARK_GRAY;
            case 3:

                return Color.BLACK;
            default:
                return Color.RED; // THINGS HAVE GONE HORRIBLY WRONG!
        }
    }

    private void drawBackground() {
        int tiledata;
        int tilemap;
        
        //System.out.println("line: " + line);
        //System.out.println(Arrays.toString(ram.readRange(Ram.VRAM, 0x9800)));

        if ((mmu.read(Mmu.LCDC) & BitTwiddles.bx00000001) ==  0) {
            // Screen is off, so draw black.
            for (int i = 0; i < 160; i++) {
                buffer.setRGB(i, line, 0x000000000);
            }
        } else {
            if ((mmu.read(Mmu.LCDC) & BitTwiddles.bx00010000) == 0) {
                tiledata = Mmu.TILE_TABLE_TWO;
            } else {
                tiledata = Mmu.TILE_TABLE_ONE;
            }

            if ((mmu.read(Mmu.LCDC) & BitTwiddles.bx00000100) == 0) {
                tilemap = Mmu.TILE_MAP_ONE;
            } else {
                tilemap = Mmu.TILE_MAP_TWO;
            }

            int y = line + mmu.read(Mmu.SCY);
            int z = y & 7; // appar-ently the same as y % 8, but faster, row of tile
            int u = z * 8;
            y >>= 3; // num of tile

            int tileNum = y << 5;
            int tile;

            int lastTile = 999; // valid values are 0 to 255, so this forces the first one.
            int[] tileBuffer = new int[64];

            for (int i = 0; i < 32; i++) {
                if (tiledata == Mmu.TILE_TABLE_ONE) {
                    tile = mmu.read(tilemap + tileNum);
                } else {
                    tile = BitTwiddles.toSignedByte(mmu.read(tilemap + tileNum)) + 128; // This might be wrong
                }

                int t;

                if (tile != lastTile) {
                    lastTile = tile;
                    t = z << 1;
                    int tmpAddr = tiledata + (tile << 4) + t;
                    t <<= 2;

                    int b1 = mmu.read(tmpAddr);
                    int b2 = mmu.read(tmpAddr + 1);

                    tileBuffer[t + 7] = ((b2 & 1) | ((b1 & 1) << 1));
                    tileBuffer[t + 6] = (((b2 & 2) >> 1) | (((b1 & 2) >> 1) << 1));
                    tileBuffer[t + 5] = (((b2 & 4) >> 2) | (((b1 & 4) >> 2) << 1));
                    tileBuffer[t + 4] = (((b2 & 8) >> 3) | (((b1 & 8) >> 3) << 1));
                    tileBuffer[t + 3] = (((b2 & 16) >> 4) | (((b1 & 16) >> 4) << 1));
                    tileBuffer[t + 2] = (((b2 & 32) >> 5) | (((b1 & 32) >> 5) << 1));
                    tileBuffer[t + 1] = (((b2 & 64) >> 6) | (((b1 & 64) >> 6) << 1));
                    tileBuffer[t] = (((b2 & 128) >> 7) | (((b1 & 128) >> 7) << 1));
                }

                int x = BitTwiddles.toUnsignedByte((i << 3) - mmu.read(Mmu.SCX));

                for (int j = 0; j < 8; j++) {
                    if (x < 160) {
                        try {
                            for (int k = 0; k < sizeMultiplyer; k++)
                                buffer.setRGB(x + k, line + k, getColorFromPalette(Mmu.BGP, tileBuffer[u + j]).getRGB());
                        } catch (ArrayIndexOutOfBoundsException e) {
                            System.err.println(String.format("Out of bounds at: (%d, %d)", x, line));
                            e.printStackTrace();
                            System.exit(1);
                        }
                    }

                    x++;
                }

                tileNum++;
            }
        }
    }

    private void drawWindow() {
        int lcdc = mmu.read(Mmu.LCDC);
        int wx = mmu.read(Mmu.WX), wy = mmu.read(Mmu.WY);
        int tileMap = 0;
        int tileData = 0;
        if (((lcdc & BitTwiddles.bx00000001) == 1) && ((lcdc & BitTwiddles.bx00100000) == 1)) {
            if ((wx <= 166) && (wy <= line) && (wx >= 7) && (line <= 143)) {
                if ((lcdc & BitTwiddles.bx01000000) == 0) {
                    tileMap = Mmu.TILE_MAP_ONE;
                } else {
                    tileMap = Mmu.TILE_MAP_TWO;
                }

                if ((lcdc & BitTwiddles.bx00010000) == 0) {
                    tileData = Mmu.TILE_TABLE_TWO;
                } else {
                    tileData = Mmu.TILE_TABLE_ONE;
                }

                int row = line - wy;
                int y = (row >> 3);
                row &= BitTwiddles.bx00000111;
                int tileNum = y << 5;
                int tile = 0;

                for (int i = 0; i < 32; i++) {
                    if (tileData == Mmu.TILE_TABLE_ONE) {
                        tile = mmu.read(tileData + tileNum);
                    } else {
                        tile = BitTwiddles.toSignedByte(mmu.read(tileData + tileNum)) + 128;
                    }


                    int z = row << 1;
                    int tmpAddr = tileData + (tile << 4) + z;
                    z <<= 2;

                    int b1 = mmu.read(tmpAddr);
                    int b2 = mmu.read(tmpAddr + 1);

                    int[] tileBuff = new int[64];

                    tileBuff[z + 7] = ((b2 & BitTwiddles.bx00000001) | ((b1 & BitTwiddles.bx00000001) << 1));
                    tileBuff[z + 6] = (((b2 & BitTwiddles.bx00000010) >> 1) | (((b1 & BitTwiddles.bx00000010) >> 1) << 1));
                    tileBuff[z + 5] = (((b2 & BitTwiddles.bx00000100) >> 2) | (((b1 & BitTwiddles.bx00000100) >> 2) << 1));
                    tileBuff[z + 4] = (((b2 & BitTwiddles.bx00001000) >> 3) | (((b1 & BitTwiddles.bx00001000) >> 3) << 1));
                    tileBuff[z + 3] = (((b2 & BitTwiddles.bx00010000) >> 4) | (((b1 & BitTwiddles.bx00010000) >> 4) << 1));
                    tileBuff[z + 2] = (((b2 & BitTwiddles.bx00100000) >> 5) | (((b1 & BitTwiddles.bx00100000) >> 5) << 1));
                    tileBuff[z + 1] = (((b2 & BitTwiddles.bx01000000) >> 6) | (((b1 & BitTwiddles.bx01000000) >> 6) << 1));
                    tileBuff[z] = (((b2 & BitTwiddles.bx10000000) >> 7) | (((b1 & BitTwiddles.bx10000000) >> 7) << 1));

                    int x = (i << 3) + wx - 7;
                    z = (line - (wy  & 7)) << 3;

                    for (int j = 0; j < 8; j++) {
                        if ((x > 0) && (x < 160)) {
                            for (int k = 0; k < sizeMultiplyer; k++)
                                buffer.setRGB(x + k, line + k, getColorFromPalette(Mmu.BGP, tileBuff[z]).getRGB());
                        }

                        x++;
                        z++;
                    }

                    tileNum++;
                }
            }
        }
    }

    private void drawSprites() {
        int numSpritesToDisplay = 0, height = 0;
        int[] spritesToDraw = new int[40];

        if ((mmu.read(Mmu.LCDC) & BitTwiddles.bx00000010) > 0) {
            numSpritesToDisplay = 0;

            if ((mmu.read(Mmu.LCDC) & BitTwiddles.bx00000100) == 0)
                height = 7; // 8x8
            else
                height = 15; // 8x16
        }

        for (int i = 0; i < 40; i++) {
            int y = mmu.read(Mmu.OAM + (i << 2)) - 16; // x coords are -16 for some reason
            int x = mmu.read(Mmu.OAM + (i << 2) + 1) - 8; // y coords are -8
            
//            System.out.println(String.fo
// rmat("x: %d, y: %d\nox: %d, oy: %d", x, y, Ram.OAM + (i << 2) + 1, Ram.OAM + (i << 2)));

            if ((x > -8) && (y >= (line - height)) && (x < 160) && (y <= line)) {
                spritesToDraw[numSpritesToDisplay] = ((x + 8) << 6) | i;
                numSpritesToDisplay++;
            }
        }

        Arrays.sort(spritesToDraw);

        int[] spriteBuff = new int[128];

        for (int i = 0; i < numSpritesToDisplay; i++) {
        	// array is sorted in reverse..
            int sprite = spritesToDraw[spritesToDraw.length - (1 + i)] & BitTwiddles.bx00111111;
            
            int y = mmu.read(Mmu.OAM + (sprite << 2)) - 16;
            int x = mmu.read(Mmu.OAM + (sprite << 2) + 1) - 8;
            int pattern = mmu.read(Mmu.OAM + (sprite << 2) + 2);

            if (height == 15)
                pattern &= BitTwiddles.bx11111110;

            int flags = mmu.read(Mmu.OAM + (sprite << 2) + 3);
            
            int palette;
            if ((flags & BitTwiddles.bx00010000) == 0) {
            	palette = 1;
            } else {
                palette = 2;
            }

            for (int j = 0; j <= (height * 2) + 1; j += 2) {
                int b1 = mmu.read(Mmu.VRAM + (pattern << 4) + j);
                int b2 = mmu.read(Mmu.VRAM + (pattern << 4) + j + 1);
                //System.out.println(String.format("b1: %d, b2: %d", b1, b2));
                spriteBuff[(j << 2) + 7] =  ((b2 & BitTwiddles.bx00000001) | ((b1 & BitTwiddles.bx00000001) << 1));
                spriteBuff[(j << 2) + 6] = (((b2 & BitTwiddles.bx00000010) >> 1) | (((b1 & BitTwiddles.bx00000010) >> 1) << 1));
                spriteBuff[(j << 2) + 5] = (((b2 & BitTwiddles.bx00000100) >> 2) | (((b1 & BitTwiddles.bx00000100) >> 2) << 1));
                spriteBuff[(j << 2) + 4] = (((b2 & BitTwiddles.bx00001000) >> 3) | (((b1 & BitTwiddles.bx00001000) >> 3) << 1));
                spriteBuff[(j << 2) + 3] = (((b2 & BitTwiddles.bx00010000) >> 4) | (((b1 & BitTwiddles.bx00010000) >> 4) << 1));
                spriteBuff[(j << 2) + 2] = (((b2 & BitTwiddles.bx00100000) >> 5) | (((b1 & BitTwiddles.bx00100000) >> 5) << 1));
                spriteBuff[(j << 2) + 1] = (((b2 & BitTwiddles.bx01000000) >> 6) | (((b1 & BitTwiddles.bx01000000) >> 6) << 1));
                spriteBuff[(j << 2)] = (((b2 & BitTwiddles.bx10000000) >> 7) | (((b1 & BitTwiddles.bx10000000) >> 7) << 1));
            }

            if ((flags & BitTwiddles.bx00100000) != 0) {
                for (int j = 0; j < height; j++) { // x-axis flip
                    int t = spriteBuff[(j << 3) + 0];
                    spriteBuff[(j << 3) + 0] = spriteBuff[(j << 3) + 7];
                    spriteBuff[(j << 3) + 7] = t;
                    t = spriteBuff[(j << 3) + 1];
                    spriteBuff[(j << 3) + 1] = spriteBuff[(j << 3) + 6];
                    spriteBuff[(j << 3) + 6] = t;
                    t = spriteBuff[(j << 3) + 2];
                    spriteBuff[(j << 3) + 2] = spriteBuff[(j << 3) + 5];
                    spriteBuff[(j << 3) + 5] = t;
                    t = spriteBuff[(j << 3) + 3];
                    spriteBuff[(j << 3) + 3] = spriteBuff[(j << 3) + 4];
                    spriteBuff[(j << 3) + 4] = t;
                }
            }

            if ((flags & BitTwiddles.bx01000000) != 0) { // y-axis flip
                for (int j = 0; j < 8; j++) {
                    if (height == 7) {  /* Swap 8 pixels high sprites */
                        for (int h = 0; h < 25; h += 8) {
                            int t = spriteBuff[h + j];
                            spriteBuff[h + j] = spriteBuff[(56 - h) + j];
                            spriteBuff[(56 - h) + j] = t;
                        }
                    } else {   /* Swap 16 pixels high sprites */
                        for (int h = 0; h < 57; h += 8) {
                            int t = spriteBuff[h + j];
                            spriteBuff[h + j] = spriteBuff[(120 - h) + j];
                            spriteBuff[(120 - h) + j] = t;
                        }
                    }
                }
            }

            int bgc = getColorFromPalette(Mmu.BGP, 0).getRGB();
            int z = line - y;

            for (int j = 0; j < 8; j++) {
                if (((x + j) >= 0) && ((y + z) >= 0) && ((x + j) < 160) && ((y + z) < 144)) {
                    if (spriteBuff[(z << 3) | j] > 0) { // if inside the screen and not transparant
                        if (((flags & BitTwiddles.bx10000000) == 0) || (buffer.getRGB(x + j, y + z) == bgc)) {
                            for (int k = 0; k < sizeMultiplyer; k++) {
                                if (palette == 1) {
                                    buffer.setRGB(x + j + k, y + z + k, getColorFromPalette(Mmu.OBP0, spriteBuff[(z << 3) | j]).getRGB());
                                } else {
                                    buffer.setRGB(x + j + k, y + z + k, getColorFromPalette(Mmu.OBP1, spriteBuff[(z << 3) | j]).getRGB());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void step(int deltaClock) {
        int b = 0;
        vblankClock += deltaClock;

        if (vblankClock >= 70224) {
            line = 0;
            vblankClock -= 70224;
            modeClock = vblankClock;
        }

        line = (vblankClock / 456);
        mmu.write(Mmu.LY, line);

        int lcdc = mmu.read(Mmu.LCDC);

        if ((lcdc & BitTwiddles.bx10000000) == 1) {
            if (mmu.read(Mmu.LY) == mmu.read((Mmu.LYC))) {
                if ((mmu.read(Mmu.STAT) & BitTwiddles.bx00000100) == 0) {
                    b = mmu.read(Mmu.STAT);
                    b |= BitTwiddles.bx00000100;
                    mmu.write(Mmu.STAT, b);
                    if ((mmu.read(Mmu.STAT) & BitTwiddles.bx01000000) != 0) {
                        mmu.read(Mmu.IF);
                        b |= BitTwiddles.bx00000010;
                        mmu.write(Mmu.IF, b);
                    }
                }
            } else {
                b = mmu.read(Mmu.STAT);
                b &= BitTwiddles.bx11111011;
            }
        }

        if (vblankClock >= 65664) { // VBLANK
	        if ((mmu.read(Mmu.STAT) & BitTwiddles.bx00000011) != BitTwiddles.bx00000001) {
                b = mmu.read(Mmu.STAT);
                b &= BitTwiddles.bx11111100;
                b |= BitTwiddles.bx00000001;
                mmu.write(Mmu.STAT, b);

                mmu.interrupts.setInterrupt(Interrupts.VBLANK);

                /*b = ram.read(Ram.STAT);
                b |= BitTwiddles.bx00000001;
                ram.write(Ram.STAT, b);*/

                if ((mmu.read(Mmu.LCDC) & BitTwiddles.bx10000000) == 0) {
                    // TODO: TURN OFF LCD
                }

                emulator.buffer = this.buffer;
                emulator.repaint();
            }
        } else {
            modeClock += deltaClock;

            if (modeClock >= 456)
                modeClock -= 456;
            if (modeClock  <= 80) {
                b = mmu.read(Mmu.STAT);

                if ((b & BitTwiddles.bx00000011) != 2) {
                    b &= BitTwiddles.bx11111100;
                    b |= BitTwiddles.bx00000010;
                    mmu.write(Mmu.STAT, b);

                    if ((mmu.read(Mmu.STAT) & BitTwiddles.bx00100000) != 0) {
                        b = mmu.read(Mmu.IF);
                        b |= BitTwiddles.bx00000010;
                        mmu.write(Mmu.IF, b);
                    }
                }
            } else if (modeClock <= (172 + 80)) {
                b = mmu.read(Mmu.STAT);

                if ((b & BitTwiddles.bx00000011) != 3) {
                    b &= BitTwiddles.bx11111100;
                    b |= 3;
                    mmu.write(Mmu.STAT, b);
                }
            } else {
                b = mmu.read(Mmu.STAT);

                if ((b & BitTwiddles.bx00000011) != 0) {
                    b = mmu.read(Mmu.LCDC);

                    if ((b & BitTwiddles.bx10000000) > 0) {
                        if (lastLine != line) {
                            lastLine = line;
                            // TODO: Draw
                            // scanline(line);
                            drawBackground();
                            drawWindow();
                            drawSprites();
                        }
                    }

                    b = mmu.read(Mmu.STAT);
                    b &= BitTwiddles.bx11111100;
                    b |= 0;
                    mmu.write(Mmu.STAT, b);

                    if ((mmu.read(Mmu.STAT) & BitTwiddles.bx00001000) != 0) {
                        b = mmu.read(Mmu.IF);
                        b |= BitTwiddles.bx00000010;
                        mmu.write(Mmu.IF, b);
                    }
                }
            }
        }
    }

    public int getSizeMultiplyer() {
        return sizeMultiplyer;
    }

    public void setSizeMultiplyer(int sizeMultiplyer) {
        this.sizeMultiplyer = sizeMultiplyer;
    }
}