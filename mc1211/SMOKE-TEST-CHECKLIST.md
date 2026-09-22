# Market Blocks 1.0.6 — 1.21.1 smoke-test checklist

For Mike's in-client testing of `marketblocks-1.0.6-forge1211.jar` (Forge
1.21.1-52.x) and `marketblocks-1.0.6-fabric1211.jar` (Fabric, needs Fabric
API for 1.21.1). Test each loader separately in a fresh instance.

## 1. Startup
- [ ] Game loads to the main menu with no crash and no red errors mentioning
      `marketblocks` in `logs/latest.log`.
- [ ] `config/marketblocks.toml` is created on first launch with the
      documented defaults (100 starting balance, 5 stall cap, 0% tax,
      60% sell ratio, example price list).

## 2. Blocks, items, textures
- [ ] Both blocks appear in the Functional Blocks creative tab with correct
      textures (stall: oak planks + red/white awning; admin: dark gold-trimmed
      stone + emerald coin).
- [ ] Held items and inventory icons render correctly (not purple/black).
- [ ] Placed blocks render correctly from all sides.

## 3. Market Stall (player economy)
- [ ] Placing a stall claims ownership (owner GUI opens on right-click).
- [ ] Stock items in the top rows, select one, set a price with the +/- buttons.
- [ ] A second player (or second account) sees the buyer GUI with prices.
- [ ] Buying 1 / a stack / shift-click bulk works; balances update on both sides.
- [ ] Breaking your own stall returns stock + earnings; breaking someone
      else's stall as a non-op is blocked with the denial message.
- [ ] Breaking as an operator works (moderation override).
- [ ] Stall cap: placing a 6th stall is refused with the limit message
      (operators bypass).

## 4. Admin Market
- [ ] Survival player **cannot** place the Admin Market (block pops off with
      the Creative-only message); creative player can.
- [ ] Survival player **cannot** break it; creative player can.
- [ ] Any player can open it and buy/sell at config prices.
- [ ] Creative player can toggle configure mode, adjust prices, add/remove
      entries; changes persist to `config/marketblocks.toml`.
- [ ] `/market reload` (op) hot-reloads the config.

## 5. Commands & economy
- [ ] `/balance` shows the starting $100 for a new player.
- [ ] `/pay <player> <amount>` transfers; insufficient funds are refused.
- [ ] Balances, stall counts, and pending deliveries survive a restart
      (check the world save).

## 6. Recipe
- [ ] The Market Stall recipe (red wool top row, chest center, any planks)
      crafts in a crafting table and appears in the recipe book.

## 7. Break-protection specifics (the 26.x mixin was replaced)
- [ ] Non-owner, non-op in Survival **and** Creative cannot break a stall.
- [ ] Non-creative player cannot break the Admin Market in any mode.

If anything fails, grab the relevant section of `logs/latest.log`
(especially any `Exception loading model` lines) and send it back.
