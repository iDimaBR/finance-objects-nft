# FinanceObjects NFT

A Minecraft plugin that transforms in-game items into blockchain-backed NFT assets using the GameShift API.

FinanceObjects integrates directly with MMOItems and Spigot, allowing unique items discovered or obtained in-game to be synchronized as digital assets tied to a player's account.

Designed for MMORPG, economy and Web3-focused Minecraft servers.

---

## Features

| Feature | Description |
|---|---|
| NFT Item Sync | Synchronize Minecraft items with blockchain-backed assets |
| MMOItems Integration | Full MMOItems metadata and stat support |
| Asset Restoration | Restore owned NFT assets back into Minecraft |
| Ownership Tracking | Link items directly to player accounts |
| Metadata Preservation | Preserve lore, rarity and custom attributes |
| Async Requests | Non-blocking synchronization system |
| Inventory Protection | Prevent duplication and ownership inconsistencies |
| Adventure API Support | Modern text formatting with MiniMessage support |
| Spigot/Paper Support | Compatible with modern Minecraft server software |

---

## Supported Stack

| Technology | Usage |
|---|---|
| Java 16 | Main language |
| Spigot/Paper 1.21+ | Minecraft server platform |
| MMOItems | Custom item framework |
| MythicLib | MMOItems dependency |
| GameShift API | Blockchain/NFT synchronization |
| Adventure API | Text formatting system |

---

## How It Works

When a player obtains or registers a supported MMOItem:

1. The plugin extracts MMOItems NBT data
2. Metadata is converted into blockchain-compatible attributes
3. The item is synchronized as a unique asset
4. Ownership becomes linked to the player's account
5. Players can restore synchronized assets directly in-game

The system preserves:

- Item name
- Lore
- MMOItems stats
- Ownership data
- Discovery information
- Coordinates and world data

---

## Item Metadata

| Attribute | Description |
|---|---|
| found_by | Player name |
| world | World name |
| coords | Discovery coordinates |
| discovered_at | Timestamp |
| mmoitems:* | MMOItems custom attributes |

---

## Async Architecture

The plugin was designed to avoid blocking the Minecraft main thread.

| System | Purpose |
|---|---|
| CompletableFuture Workflow | Async processing |
| Retry Handling | Request recovery |
| Timeout Protection | API stability |
| Concurrent Sync | Multiple player support |

---

## Example Use Cases

- Web3 Minecraft servers
- NFT MMORPG economies
- Blockchain-backed item ownership
- Cross-server item persistence
- Play-to-own systems
- Marketplace integrations

---

## License

This repository and its source code are private proprietary software.

You are NOT allowed to:
- Copy
- Modify
- Redistribute
- Sell
- Reupload
- Use commercially
- Use partially or entirely in other projects

Without explicit written permission from the repository owner.
