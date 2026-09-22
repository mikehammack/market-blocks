# Market Blocks 1.0.6 — Forge 1.12.2 smoke-test checklist

Jar: `forge1122/build/libs/marketblocks-1.0.6-forge1122.jar`
Target: Minecraft 1.12.2 + Forge 14.23.5.2860 (latest 1.12.2 build).

> The jar was built and bytecode-verified without a real client in this
> environment (see README.md). Every item below needs a human in a real
> 1.12.2 client. Work through in order; stop at the first failure and
> report it.

## Setup
- [ ] Fresh 1.12.2 Forge profile (14.23.5.2860), only this jar in `mods/`.
- [ ] Client launches to the main menu with no crash and no red error text
      in the log mentioning `marketblocks`.
- [ ] New single-player world (cheats ON) creates and loads.

## Blocks & items
- [ ] `/give @p marketblocks:market_stall` gives the Market Stall item with
      the oak/awning texture (not a purple-black cube).
- [ ] `/give @p marketblocks:admin_market` gives the Admin Market item with
      the gold-trimmed stone texture.
- [ ] Both blocks render correctly when placed (top texture on top).
- [ ] Market Stall recipe works: red wool across the top row, chest in the
      center, any planks elsewhere (3x3). The recipe book shows it.
- [ ] Breaking either block drops the block itself.

## Market Stall (survival player A, second account or LAN peer B)
- [ ] A places a stall: A becomes owner (owner GUI opens on right-click).
- [ ] A puts diamonds in the top rows, clicks one, sets price with +1/+10
      buttons; the info slot shows the price.
- [ ] A cannot own more than 5 stalls (6th placement is refunded with the
      stall-limit message). An operator bypasses the cap.
- [ ] B right-clicks A's stall: sees the buyer GUI with the diamond listing,
      price, and stock count.
- [ ] B left-clicks the listing: buys 1 diamond for the price; B's balance
      drops, the diamond arrives in B's inventory.
- [ ] B right-clicks the listing: buys a stack (or as many as affordable).
- [ ] B shift-clicks the listing: buys as many as possible.
- [ ] A's earnings grow by the sale amount (0% tax by default); A clicks the
      emerald to withdraw to their balance (`/balance` confirms).
- [ ] A non-owner, non-operator cannot break the stall (denied message).
- [ ] Owner breaks the stall: remaining stock goes to the owner's inventory
      (or drops at their feet if full); earnings land in their balance.
- [ ] Owner breaks the stall while OFFLINE (owner logs out, operator breaks
      it): on next login the owner gets the stock and a recovery message.
- [ ] `/balance` shows the starting $100 for a new player.
- [ ] `/pay B 10` moves $10 from A to B; both see confirmation messages.
- [ ] `/pay` with insufficient funds, self-pay, or bad amount is rejected.

## Admin Market (operator in Creative, survival player C)
- [ ] Survival player C right-clicks the Admin Market: buy/sell shop GUI
      opens with the 4 default entries (diamond, #logWood, iron, bread).
- [ ] C buys 1 diamond: balance drops $100, diamond arrives.
- [ ] C shift-clicks a bread stack in their inventory: sells it at the sell
      price, balance rises.
- [ ] C cannot switch to configure mode (lever shows as gray pane; clicking
      does nothing).
- [ ] C (survival, even with op) cannot break the Admin Market (denied).
- [ ] Creative operator right-clicks: same shop GUI, plus a working
      mode lever in the bottom row.
- [ ] Operator toggles to CONFIGURE, clicks +/- on an entry: prices change
      live and persist (check `config/marketblocks.cfg` afterwards).
- [ ] Operator shift-clicks an item from their inventory in CONFIGURE mode:
      it is added as a new priced entry.
- [ ] Operator presses Q (drop key) over an entry in CONFIGURE mode: the
      entry is removed.
- [ ] Survival player cannot PLACE the Admin Market (block pops off and is
      refunded with the Creative-only message).
- [ ] `/market reload` as operator: edits to `config/marketblocks.cfg`
      (e.g. a new `#ingotIron=8` line) take effect without restart.
- [ ] `/market reload` as non-operator is rejected.

## OreDictionary behavior (the 1.12.2 difference)
- [ ] Default config uses `#logWood=4` (NOT `#minecraft:logs` — 1.12.2 has
      no tags). Any log type (oak, spruce, …) buys/sells at that price.
- [ ] Add `#ingotIron=8` via config or GUI: iron ingots from any mod
      registered under that ore name trade at the ore price; an exact-id
      entry (e.g. `minecraft:iron_ingot=20`) takes precedence when both exist.

## Config notes
- [ ] First launch writes `config/marketblocks.cfg` with the documented
      defaults (100 starting, 5 stall cap, 0% tax, 60% sell ratio).
- [ ] A malformed price line is rejected at load with a console error and
      the previous config is kept (no crash).
