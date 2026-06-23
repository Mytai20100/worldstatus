# Canva-Style Customization Guide

WorldStatus supports fully customizable image rendering for Discord commands using JSON templates. This guide explains all available configuration options and placeholders.

## Overview

Two template files control the appearance:
- `playerlist.json` - Player list display (`/playerlist` command)
- `worldstatus.json` - Server status display (`/world-status` command)

Both files are automatically created in `plugins/WorldStatus/` on first run.

## Configuration Structure

### Background

```json
"background": {
  "type": "image",
  "path": "background.png",
  "url": "https://example.com/background.png",
  "color": "#1E2028"
}
```

- `type`: `"image"` or `"color"`
- `path`: Local file path (relative to plugin folder)
- `url`: Direct image URL (alternative to `path`)
- `color`: Hex color code (used when type is `"color"` or image load fails)

### Logo

```json
"logo": {
  "enabled": true,
  "path": "logo.png",
  "url": "https://example.com/logo.png",
  "x": 50,
  "y": 30,
  "width": 100,
  "height": 100
}
```

- `enabled`: Toggle logo display
- `path`: Local file path
- `url`: Direct image URL
- `x`, `y`: Position coordinates
- `width`, `height`: Image dimensions

### Text Elements

```json
"title": {
  "text": "Server Status",
  "font": "Arial",
  "size": 32,
  "color": "#E4E6F0",
  "bold": true,
  "x": 170,
  "y": 60,
  "blur": 0
}
```

- `text`: Display text (supports placeholders)
- `font`: Font family name
- `size`: Font size in pixels
- `color`: Hex color code
- `bold`: Bold text toggle
- `x`, `y`: Position coordinates
- `blur`: Blur radius (0 = no blur)

### Canvas

```json
"canvas": {
  "width": 680,
  "dynamicHeight": true,
  "minHeight": 400,
  "padding": 20
}
```

- `width`: Image width in pixels
- `dynamicHeight`: Auto-adjust height based on content
- `minHeight`: Minimum height when dynamic
- `padding`: Internal padding

## Player List Configuration

### Layout Modes

```json
"playerList": {
  "layout": "vertical",
  "startX": 50,
  "startY": 150,
  "rowHeight": 35,
  "columnWidth": 200
}
```

- `layout`: `"vertical"` (default) or `"horizontal"`
- `startX`, `startY`: Starting position
- `rowHeight`: Height of each player row
- `columnWidth`: Width of each column (horizontal mode only)

### Player Columns

```json
"columns": [
  {
    "type": "text",
    "header": "Player",
    "value": "{player_name}",
    "font": "Arial",
    "size": 16,
    "color": "#E4E6F0",
    "bold": false,
    "x": 0,
    "blur": 0
  },
  {
    "type": "avatar",
    "header": "Avatar",
    "value": "{player_avatar}",
    "x": 0,
    "width": 32,
    "height": 32
  },
  {
    "type": "ping",
    "header": "Ping",
    "value": "{ping}",
    "mode": "icon",
    "font": "Arial",
    "size": 16,
    "color": "#5B8DFF",
    "x": 250
  }
]
```

**Column Types:**

- `"text"`: Plain text display
- `"avatar"`: Player head/skin image
- `"ping"`: Ping display with icon or text mode

**Ping Modes:**

- `mode: "text"`: Shows as `"123ms"`
- `mode: "icon"`: Shows as WiFi-style bars (4 bars, color-coded by latency)

### Styling

```json
"headerColor": "#A0A0A0",
"alternateRowColor": "#252735",
"rowColor": "#1E2028"
```

- `headerColor`: Column header text color
- `rowColor`: Default row background
- `alternateRowColor`: Alternate row background (zebra striping)

## World Status Configuration

### Stats Display

```json
"stats": [
  {
    "label": "TPS",
    "value": "{tps}",
    "font": "Arial",
    "size": 18,
    "color": "#E4E6F0",
    "labelColor": "#A0A0A0",
    "bold": false,
    "x": 50,
    "y": 150,
    "blur": 0,
    "showProgressBar": true,
    "progressBarWidth": 200,
    "progressBarHeight": 14
  }
]
```

- `label`: Static label text
- `value`: Dynamic value (supports placeholders)
- `showProgressBar`: Toggle progress bar
- `progressBarWidth`, `progressBarHeight`: Bar dimensions

### Worlds Display

```json
"worlds": {
  "enabled": true,
  "startY": 400,
  "rowHeight": 30,
  "font": "Arial",
  "size": 16,
  "color": "#E4E6F0",
  "labelColor": "#A0A0A0",
  "bold": false,
  "blur": 0,
  "header": "World Sizes"
}
```

## Available Placeholders

### Player List Placeholders

| Placeholder | Description | Example |
|------------|-------------|---------|
| `{player_name}` | Player username | `"Notch"` |
| `{player_avatar}` | Player head image URL | Auto-rendered |
| `{uuid}` | Player UUID | `"069a79f4-44e9-4726-a5be-fca90e38aaf5"` |
| `{ping}` | Player ping in ms | `"45"` |
| `{player_ip}` | Player IP address | `"127.0.0.1"` |
| `{world}` | Current world name | `"world"` |
| `{online}` | Online player count | `"5"` |
| `{max}` | Max player slots | `"20"` |
| `{timestamp}` | Current date/time | `"2026-06-20 14:30:00"` |
| `{server_ip}` | Server IP address | `"play.example.com"` |

### World Status Placeholders

| Placeholder | Description | Example |
|------------|-------------|---------|
| `{tps}` | Current TPS | `"20.0"` |
| `{tps_5m}` | 5-minute average TPS | `"19.8"` |
| `{tps_15m}` | 15-minute average TPS | `"19.5"` |
| `{tps_30m}` | 30-minute average TPS | `"19.3"` |
| `{mspt}` | Current MSPT | `"15.2"` |
| `{mspt_5m}` | 5-minute average MSPT | `"16.1"` |
| `{mspt_15m}` | 15-minute average MSPT | `"17.5"` |
| `{mspt_30m}` | 30-minute average MSPT | `"18.2"` |
| `{ram_used}` | Used RAM | `"4.2 GB"` |
| `{ram_max}` | Max RAM | `"8.0 GB"` |
| `{ram_free}` | Free RAM | `"3.8 GB"` |
| `{ram_percent}` | RAM usage % | `"52.5"` |
| `{cpu}` | CPU usage % | `"35.2"` |
| `{cpu_cores}` | CPU core count | `"8"` |
| `{disk_used}` | Used disk space | `"120 GB"` |
| `{disk_total}` | Total disk space | `"500 GB"` |
| `{disk_free}` | Free disk space | `"380 GB"` |
| `{disk_percent}` | Disk usage % | `"24.0"` |
| `{online}` | Online players | `"5"` |
| `{max}` | Max players | `"20"` |
| `{timestamp}` | Current date/time | `"2026-06-20 14:30:00"` |
| `{server_ip}` | Server IP address | `"play.example.com"` |

### Prometheus Metrics Placeholders

All Prometheus metrics can be accessed using their metric names:

| Placeholder | Description |
|------------|-------------|
| `{chunks_loaded}` | Loaded chunk count |
| `{chunks_total}` | Total chunk count |
| `{entities_total}` | Total entity count |
| `{mobs_total}` | Total mob count |
| `{players_online}` | Online players |
| `{players_max}` | Max player slots |
| `{heap_used}` | Heap memory used (bytes) |
| `{heap_max}` | Heap memory max (bytes) |
| `{gc_time}` | GC time (ms) |
| `{gc_count}` | GC count |
| `{classes_loaded}` | Loaded class count |
| `{plugins_loaded}` | Loaded plugin count |

## Image Loading

Images can be loaded from three sources (checked in order):

1. **Local path**: `"path": "logo.png"` - Loaded from `plugins/WorldStatus/`
2. **URL**: `"url": "https://example.com/logo.png"` - Downloaded on-demand
3. **Fallback**: If both fail, the element is skipped

**Special URLs:**

- `{player_avatar}` - Auto-resolves to `https://crafatar.com/avatars/{uuid}?overlay=true&size=64`

## Enable/Disable Canva Mode

### World Status Command

Edit `config.yml`:

```yaml
discord:
  world-status-mode: "canva"  # "canva" or "embed"
```

- `"canva"`: Shows full image only (no Discord embed)
- `"embed"`: Shows Discord embed with attached image (default)

### Player List Command

Edit `config.yml`:

```yaml
discord:
  playerlist-render-mode: "canva"  # "text" or "canva"
```

- `"text"`: Classic text-based embed
- `"canva"`: Custom image rendering

## Example Configurations

### Minimal Player List (Vertical)

```json
{
  "enabled": true,
  "background": {
    "type": "color",
    "color": "#1E2028"
  },
  "logo": {
    "enabled": false
  },
  "title": {
    "text": "Online Players",
    "font": "Arial",
    "size": 28,
    "color": "#FFFFFF",
    "bold": true,
    "x": 50,
    "y": 50,
    "blur": 0
  },
  "playerList": {
    "layout": "vertical",
    "startX": 50,
    "startY": 100,
    "rowHeight": 40,
    "columns": [
      {
        "type": "avatar",
        "header": "",
        "value": "{player_avatar}",
        "x": 0,
        "width": 32,
        "height": 32
      },
      {
        "type": "text",
        "header": "Player",
        "value": "{player_name}",
        "font": "Arial",
        "size": 16,
        "color": "#E4E6F0",
        "bold": false,
        "x": 50,
        "blur": 0
      },
      {
        "type": "ping",
        "header": "Ping",
        "value": "{ping}",
        "mode": "icon",
        "x": 300
      }
    ],
    "headerColor": "#A0A0A0",
    "alternateRowColor": "#252735",
    "rowColor": "#1E2028"
  },
  "canvas": {
    "width": 500,
    "dynamicHeight": true,
    "minHeight": 300,
    "padding": 20
  }
}
```

### Horizontal Player Grid

```json
{
  "playerList": {
    "layout": "horizontal",
    "startX": 50,
    "startY": 150,
    "rowHeight": 100,
    "columnWidth": 150,
    "columns": [
      {
        "type": "avatar",
        "value": "{player_avatar}",
        "x": 0,
        "y": 0,
        "width": 64,
        "height": 64
      },
      {
        "type": "text",
        "value": "{player_name}",
        "font": "Arial",
        "size": 14,
        "color": "#FFFFFF",
        "bold": true,
        "x": 0,
        "y": 70,
        "blur": 0
      }
    ]
  }
}
```

### Advanced World Status with URLs

```json
{
  "enabled": true,
  "background": {
    "type": "image",
    "url": "https://i.imgur.com/example.png",
    "color": "#000000"
  },
  "logo": {
    "enabled": true,
    "url": "https://crafatar.com/avatars/069a79f4-44e9-4726-a5be-fca90e38aaf5?overlay=true&size=128",
    "x": 50,
    "y": 30,
    "width": 100,
    "height": 100
  },
  "title": {
    "text": "Server Status - {server_ip}",
    "font": "Arial",
    "size": 32,
    "color": "#FFD700",
    "bold": true,
    "x": 170,
    "y": 60,
    "blur": 2
  },
  "stats": [
    {
      "label": "TPS",
      "value": "{tps} (5m: {tps_5m})",
      "showProgressBar": true
    },
    {
      "label": "Players",
      "value": "{online}/{max}",
      "showProgressBar": false
    },
    {
      "label": "Entities",
      "value": "{entities_total} entities, {mobs_total} mobs",
      "showProgressBar": false
    }
  ]
}
```

## Troubleshooting

**Image not loading:**
- Check file path is relative to `plugins/WorldStatus/`
- Verify URL is publicly accessible
- Check console for error messages

**Placeholders not replaced:**
- Ensure placeholder syntax is exact: `{placeholder_name}`
- Check placeholder is supported for that template type
- Some placeholders require specific metrics to be enabled

**Layout issues:**
- Verify `x`, `y` coordinates are within canvas bounds
- Check `dynamicHeight` if content is cut off
- Adjust `rowHeight` and `columnWidth` for better spacing

**Ping icons not showing:**
- Set column `type` to `"ping"`
- Set `mode` to `"icon"`
- Ensure player has valid ping data
