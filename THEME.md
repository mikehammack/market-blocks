# THEME.md — Market Blocks

Visual identity for Market Blocks (1.12.2 Forge port; applies to all ports).
Read this before generating or editing ANY texture, model tint, or GUI art.

## Palette

| name          | hex     | role                                              |
|---------------|---------|---------------------------------------------------|
| stall red     | #D02F28 | market stall awning stripes                       |
| cream         | #EFEEE5 | awning light stripes                              |
| wood          | #AE6F26 | stall plank body                                  |
| wood dark     | #422717 | plank shading, outlines                           |
| admin black   | #1A1A1B | admin-tier block body (near-black stone)          |
| admin gold    | #E2B631 | admin market trim, borders, emblem accents        |
| admin navy    | #22324D | admin market detail shading                       |
| schematic blue| #3B7DDD | schematic market trim/emblem (blueprint accent)   |
| vault green   | #00A040 | admin market top emblem accent (intentional)      |
| awning shade  | #D0A090 | stall awning shading (intentional)                |

Derived shades (darken/lighten ≤15%) are fine for noise and edge shading;
do not introduce new hues.

## Style principles

- 16×16 pixel-art textures, vanilla-adjacent. Flat fills with light per-pixel
  noise; no gradients, no photographic detail.
- Tier language: the **stall** is merchant-folksy (red/cream awning, wood);
  **admin-tier** blocks share a near-black stone body with a metallic trim
  border (gold = admin market, blueprint blue = schematic market).
- Every admin-tier block carries a centered top emblem in its trim color.
- Sides carry a trim band along the bottom edge and a thin vertical trim
  line on one edge.
- GUI art (where custom) reuses the trim colors for mode buttons and headers.

## Motifs

- Awning stripes (stall), coin/vault gold (admin market), blueprint grid
  lines (schematic market).
- Ghost/display slots in GUIs use gray stained glass panes; never invent new
  GUI chrome colors.

## Per-asset notes

- `market_stall_top`: red/cream awning stripes.
- `market_stall_side`: horizontal wood planks.
- `admin_market_top`: black field, gold border, green-gold vault emblem.
- `admin_market_side`: black field, gold bottom band.
- `schematic_market_top`: black field, blueprint-blue border, blueprint grid emblem.
- `schematic_market_side`: black field, blueprint-blue bottom band.

## Do / Don't

- DO keep new blocks inside the tier language (dark body + trim color).
- DO run `python3 ~/workspace/modfactory/tools/check_theme.py THEME.md <asset_dir>`
  after adding textures; new hues are warnings, not errors, but explain them here.
- DON'T change intentional art (e.g. the stall awning) to silence the checker.
- DON'T use gold trim on non-admin blocks; gold means the infinite admin shop.
