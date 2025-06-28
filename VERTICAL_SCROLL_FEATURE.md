# Vertical Scrolling Feature

## Overview
This PDF viewer now supports a vertical scrolling mode similar to Google PDF viewer or Google Drive's PDF viewer. In this mode, all pages of the PDF are displayed vertically in a continuous scroll view, eliminating the need for page navigation buttons.

## Features Added

### 1. Vertical Scrolling Mode
- All PDF pages are rendered and displayed vertically
- Smooth scrolling between pages
- Page numbers displayed above each page
- Automatic page tracking based on scroll position

### 2. Toggle Button
- New "Vertical scroll" button in the toolbar
- Easy switching between traditional page mode and vertical scroll mode
- State is preserved during app lifecycle

### 3. Enhanced Navigation
- In vertical mode: Page navigation buttons are disabled
- Jump to page feature works in both modes
- Current page indicator updates based on scroll position

## How to Use

1. Open a PDF document
2. Tap the "Vertical scroll" button in the toolbar (grid icon)
3. The PDF will switch to vertical scrolling mode showing all pages
4. Scroll normally to view different pages
5. Tap the button again to return to traditional page mode

## Technical Implementation

### JavaScript Changes
- Added vertical scrolling rendering logic
- Implemented page tracking during scroll
- Added throttled scroll event handling
- Created dynamic page containers for multi-page layout

### Android Changes
- Added vertical scroll mode toggle in menu
- Updated page navigation logic
- Added state preservation for mode switching
- Modified menu behavior based on current mode

### CSS Changes
- Added styles for vertical page layout
- Implemented page number indicators
- Enhanced responsive design for different screen sizes

## Benefits

1. **Better Reading Experience**: View the entire document flow without interruption
2. **Natural Navigation**: Use familiar scrolling gestures
3. **Overview Capability**: See multiple pages at once
4. **Flexible Usage**: Switch between modes based on preference
5. **Touch Friendly**: Optimized for mobile touch interactions

## Mode Comparison

| Feature | Page Mode | Vertical Scroll Mode |
|---------|-----------|---------------------|
| Navigation | Previous/Next buttons | Natural scrolling |
| Page View | Single page | Multiple pages |
| Page Numbers | In title/toast | Above each page |
| Memory Usage | Lower (single page) | Higher (all pages) |
| Best For | Detailed reading | Document overview |

The vertical scrolling feature provides a modern, intuitive way to view PDF documents while maintaining the option to use the traditional page-by-page viewing mode.
