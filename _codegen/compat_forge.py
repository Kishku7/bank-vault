"""compat_forge.py -- Forge-specific era emitters for Bank Vault.

Era boundary: Forge 58 (MC 1.21.8) moved to EventBus 7 (BusGroup mod bus, Event.BUS statics,
@Mod ctor receives FMLJavaModLoadingContext). Forge 55-57 (MC 1.21.5-1.21.7) and older use
EventBus 6 (IEventBus mod bus via FMLJavaModLoadingContext.get(), MinecraftForge.EVENT_BUS).
"""
import compat_core


def eb7(ver):
    # EventBus 7 landed with Forge 56 (MC 1.21.6) -- gate-proven 2026-07-03
    return compat_core._vt(ver) >= (1, 21, 6)


ENTRY_EB7 = '''package com.kishku7.bankvault;

import com.kishku7.bankvault.command.BankCommand;
import com.kishku7.bankvault.net.ModNetworking;
import com.kishku7.bankvault.registry.ModBlockEntities;
import com.kishku7.bankvault.registry.ModBlocks;
import com.kishku7.bankvault.registry.ModCreativeTab;
import com.kishku7.bankvault.registry.ModItems;
import com.kishku7.bankvault.registry.ModMenus;
import com.kishku7.bankvault.vault.Catalog;
import com.kishku7.bankvault.vault.MultiblockManager;
import com.kishku7.bankvault.vault.UserSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint (58.x / EventBus 7): the @Mod constructor receives the loading context, the
 * mod bus is a BusGroup, game-bus events subscribe via each event's static BUS field.
 */
@Mod(BankVault.MOD_ID)
public final class BankVaultForge {

    public BankVaultForge(FMLJavaModLoadingContext context) {
        BankVault.LOGGER.info("[Bank Vault] Forge initializing (TravelersBackpack={})",
                BankVault.TRAVELERS_BACKPACK);
        BusGroup modBus = context.getModBusGroup();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModCreativeTab.TABS.register(modBus);

        ModNetworking.init();   // Forge builds its payload channel directly (no register-event)

        FMLCommonSetupEvent.getBus(modBus).addListener(e -> {
            Catalog.restoreMissingDefaults();
            Catalog.ensureLoaded();
            BankVault.LOGGER.info("[Bank Vault] registration complete");
        });

        ServerStartingEvent.BUS.addListener(e -> UserSettings.loadAll());
        RegisterCommandsEvent.BUS.addListener(e -> BankCommand.register(e.getDispatcher()));
        BlockEvent.BreakEvent.BUS.addListener(BankVaultForge::blockBroken);

        if (FMLEnvironment.dist.isClient()) {
            com.kishku7.bankvault.client.BankVaultForgeClient.init(modBus);
        }
    }

    /** Fabric's PlayerBlockBreakEvents.AFTER equivalent: BreakEvent fires PRE-removal, so the
     *  unform check is deferred to after the current tick phase (block is gone by then). */
    private static void blockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && MultiblockManager.isVaultBlock(event.getState())) {
            var pos = event.getPos().immutable();
            level.getServer().execute(() -> MultiblockManager.onRemoved(level, pos));
        }
    }
}'''

ENTRY_EB6 = '''package com.kishku7.bankvault;

import com.kishku7.bankvault.command.BankCommand;
import com.kishku7.bankvault.net.ModNetworking;
import com.kishku7.bankvault.registry.ModBlockEntities;
import com.kishku7.bankvault.registry.ModBlocks;
import com.kishku7.bankvault.registry.ModCreativeTab;
import com.kishku7.bankvault.registry.ModItems;
import com.kishku7.bankvault.registry.ModMenus;
import com.kishku7.bankvault.vault.Catalog;
import com.kishku7.bankvault.vault.MultiblockManager;
import com.kishku7.bankvault.vault.UserSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint (EventBus 6 era, Forge <=57): classic no-arg @Mod constructor, mod bus via
 * FMLJavaModLoadingContext.get(), game-bus events on MinecraftForge.EVENT_BUS.
 */
@Mod(BankVault.MOD_ID)
public final class BankVaultForge {

    @SuppressWarnings("removal")   // FMLJavaModLoadingContext.get() is the era-correct accessor here
    public BankVaultForge() {
        BankVault.LOGGER.info("[Bank Vault] Forge initializing (TravelersBackpack={})",
                BankVault.TRAVELERS_BACKPACK);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModCreativeTab.TABS.register(modBus);

        ModNetworking.init();   // Forge builds its payload channel directly (no register-event)

        modBus.addListener((FMLCommonSetupEvent e) -> {
            Catalog.restoreMissingDefaults();
            Catalog.ensureLoaded();
            BankVault.LOGGER.info("[Bank Vault] registration complete");
        });

        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent e) -> UserSettings.loadAll());
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> BankCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener(BankVaultForge::blockBroken);

        if (FMLEnvironment.dist.isClient()) {
            com.kishku7.bankvault.client.BankVaultForgeClient.init(modBus);
        }
    }

    /** Fabric's PlayerBlockBreakEvents.AFTER equivalent: BreakEvent fires PRE-removal, so the
     *  unform check is deferred to after the current tick phase (block is gone by then). */
    private static void blockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && MultiblockManager.isVaultBlock(event.getState())) {
            var pos = event.getPos().immutable();
            level.getServer().execute(() -> MultiblockManager.onRemoved(level, pos));
        }
    }
}'''

CLIENT_EB7 = '''package com.kishku7.bankvault.client;

import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.net.ModNetworking;
import com.kishku7.bankvault.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Forge client bootstrap (58.x): no RegisterMenuScreensEvent on Forge -- bind the menu screen via
 * vanilla MenuScreens.register inside FMLClientSetupEvent (enqueueWork), and point the S2C sinks
 * at the screen cache. Only ever loaded on the client dist.
 */
public final class BankVaultForgeClient {

    private BankVaultForgeClient() {}

    public static void init(BusGroup modBus) {
        BankVault.LOGGER.info("[Bank Vault] client initializing");
        FMLClientSetupEvent.getBus(modBus).addListener(event -> event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.BANK_VAULT, BankVaultScreen::new);
            wireSinks();
        }));
    }

    private static void wireSinks() {
        ModNetworking.vaultSyncSink = payload -> {
            Minecraft mc = Minecraft.getInstance();
            if (com.kishku7.bankvault.BvCompat.currentScreen(mc) instanceof BankVaultScreen screen) screen.updateData(payload);
        };
        ModNetworking.uiStateSink = payload -> ClientUiState.set(payload.lastTab(), payload.sorts(),
                payload.showSections(), payload.pins());
        ModNetworking.sharingSink = payload -> {
            Minecraft mc = Minecraft.getInstance();
            if (com.kishku7.bankvault.BvCompat.currentScreen(mc) instanceof BankVaultScreen screen) screen.updateSharing(payload);
        };
    }
}'''

CLIENT_EB6 = '''package com.kishku7.bankvault.client;

import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.net.ModNetworking;
import com.kishku7.bankvault.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Forge client bootstrap (EventBus 6 era, Forge <=57): bind the menu screen via vanilla
 * MenuScreens.register inside FMLClientSetupEvent (enqueueWork), and point the S2C sinks at the
 * screen cache. Only ever loaded on the client dist.
 */
public final class BankVaultForgeClient {

    private BankVaultForgeClient() {}

    public static void init(IEventBus modBus) {
        BankVault.LOGGER.info("[Bank Vault] client initializing");
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.BANK_VAULT, BankVaultScreen::new);
            wireSinks();
        }));
    }

    private static void wireSinks() {
        ModNetworking.vaultSyncSink = payload -> {
            Minecraft mc = Minecraft.getInstance();
            if (com.kishku7.bankvault.BvCompat.currentScreen(mc) instanceof BankVaultScreen screen) screen.updateData(payload);
        };
        ModNetworking.uiStateSink = payload -> ClientUiState.set(payload.lastTab(), payload.sorts(),
                payload.showSections(), payload.pins());
        ModNetworking.sharingSink = payload -> {
            Minecraft mc = Minecraft.getInstance();
            if (com.kishku7.bankvault.BvCompat.currentScreen(mc) instanceof BankVaultScreen screen) screen.updateSharing(payload);
        };
    }
}'''


def emit_entrypoint(cog, ver):
    for ln in (ENTRY_EB7 if eb7(ver) else ENTRY_EB6).split("\n"):
        cog.outl(ln)


def emit_client(cog, ver):
    for ln in (CLIENT_EB7 if eb7(ver) else CLIENT_EB6).split("\n"):
        cog.outl(ln)


# ---- forge networking: PayloadChannel @1.20.5+ (forge 50+); classic SimpleChannel at 1.20.1 ----
_FNET_MODERN_IMPORTS = """import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadProtocol;"""

_FNET_LEGACY_IMPORTS = """import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;"""


def emit_forgenet_imports(cog, ver):
    block = _FNET_MODERN_IMPORTS if compat_core.has_components(ver) else _FNET_LEGACY_IMPORTS
    for ln in block.split("\n"):
        cog.outl(ln)


_C2S_FORGE = ["Withdraw", "Upgrade", "Deposit", "GridView", "DepositAll", "ShareAction", "UiState"]
_S2C_FORGE = [("VaultSync", "vaultSyncSink"), ("UiStateSync", "uiStateSink"), ("SharingState", "sharingSink")]


def emit_forgenet_channel(cog, ver):
    if compat_core.has_components(ver):
        # modern text lives in the generated region of the cog source (passthrough via twin-less
        # marker: the modern content is emitted verbatim below)
        for ln in _FNET_MODERN_BLOCK.split("\n"):
            cog.outl(ln)
        return
    lines = [
        '    @SuppressWarnings("removal")   // NetworkRegistry/SimpleChannel IS the 1.20.1 (forge 47) networking API',
        "    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(",
        '            new ResourceLocation("bankvault", "main"), () -> "1", v -> true, v -> true);',
        "",
        "    /** Client -> server send seam (ClientNet delegates here). */",
        "    public static void sendToServer(BvPayload p) {",
        "        CHANNEL.sendToServer(p);",
        "    }",
        "",
        "    private static void sendToPlayer(ServerPlayer player, BvPayload p) {",
        "        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), p);",
        "    }",
        "",
        "    /** Register every message on the classic SimpleChannel. Called from the @Mod constructor. */",
        "    public static void init() {",
        "        int i = 0;",
    ]
    for n in _C2S_FORGE:
        lines += [
            "        CHANNEL.registerMessage(i++, " + n + "Payload.class, " + n + "Payload::write, " + n + "Payload::decode,",
            "                (m, ctx) -> { ctx.get().enqueueWork(() -> on" + n + "(m, ctx.get().getSender())); ctx.get().setPacketHandled(true); });",
        ]
    for n, sink in _S2C_FORGE:
        lines += [
            "        CHANNEL.registerMessage(i++, " + n + "Payload.class, " + n + "Payload::write, " + n + "Payload::decode,",
            "                (m, ctx) -> { ctx.get().enqueueWork(() -> " + sink + ".accept(m)); ctx.get().setPacketHandled(true); });",
        ]
    lines.append("    }")
    for ln in lines:
        cog.outl(ln)


_FNET_MODERN_BLOCK = '    private static Channel<CustomPacketPayload> CHANNEL;\n\n    /** Client -> server send seam (ClientNet delegates here). */\n    public static void sendToServer(CustomPacketPayload p) {\n        CHANNEL.send(p, PacketDistributor.SERVER.noArg());\n    }\n\n    private static void sendToPlayer(ServerPlayer player, CustomPacketPayload p) {\n        CHANNEL.send(p, PacketDistributor.PLAYER.with(player));\n    }\n\n    /** Wrap a codec so a second decode of an already-consumed buffer (single-player double-decode)\n     *  rewinds to the payload start rather than overrunning (Forge PayloadChannel quirk). */\n    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> sp(\n            StreamCodec<RegistryFriendlyByteBuf, T> inner) {\n        return new StreamCodec<RegistryFriendlyByteBuf, T>() {\n            @Override\n            public T decode(RegistryFriendlyByteBuf buf) {\n                if (buf.readableBytes() == 0 && buf.writerIndex() > 0) buf.readerIndex(0);\n                return inner.decode(buf);\n            }\n            @Override\n            public void encode(RegistryFriendlyByteBuf buf, T val) {\n                inner.encode(buf, val);\n            }\n        };\n    }\n\n    /** Build + register the payload channel. Called from the @Mod constructor (no register-event on Forge). */\n    public static void init() {\n        PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> proto =\n                ChannelBuilder.named(Identifier.fromNamespaceAndPath("bankvault", "main"))\n                        .networkProtocolVersion(1)\n                        .optional()\n                        .payloadChannel()\n                        .play();\n\n        // C2S\n        proto.serverbound()\n                .add(WithdrawPayload.TYPE, sp(WithdrawPayload.CODEC),\n                        (m, c) -> c.enqueueWork(() -> onWithdraw(m, c.getSender())))\n                .add(UpgradePayload.TYPE, sp(UpgradePayload.CODEC),\n                        (m, c) -> c.enqueueWork(() -> onUpgrade(m, c.getSender())))\n                .add(DepositPayload.TYPE, sp(DepositPayload.CODEC),\n                        (m, c) -> c.enqueueWork(() -> onDeposit(m, c.getSender())))\n                .add(GridViewPayload.TYPE, sp(GridViewPayload.CODEC),\n                        (m, c) -> c.enqueueWork(() -> onGridView(m, c.getSender())))\n                .add(DepositAllPayload.TYPE, sp(DepositAllPayload.CODEC),\n                        (m, c) -> c.enqueueWork(() -> onDepositAll(m, c.getSender())))\n                .add(ShareActionPayload.TYPE, sp(ShareActionPayload.CODEC),\n                        (m, c) -> c.enqueueWork(() -> onShareAction(m, c.getSender())))\n                .add(UiStatePayload.TYPE, sp(UiStatePayload.CODEC),\n                        (m, c) -> c.enqueueWork(() -> onUiState(m, c.getSender())));\n\n        // S2C -- handlers run client-side only; the sinks keep client classes off the server.\n        CHANNEL = proto.clientbound()\n                .add(VaultSyncPayload.TYPE, sp(VaultSyncPayload.CODEC),\n                        (m, c) -> c.enqueueWork(() -> vaultSyncSink.accept(m)))\n                .add(UiStateSyncPayload.TYPE, sp(UiStateSyncPayload.CODEC),\n                        (m, c) -> c.enqueueWork(() -> uiStateSink.accept(m)))\n                .add(SharingStatePayload.TYPE, sp(SharingStatePayload.CODEC),\n                        (m, c) -> c.enqueueWork(() -> sharingSink.accept(m)))\n                .build();\n    }'


def emit_forge_clientnet(cog, ver):
    if compat_core.has_components(ver):
        body = """import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client networking seam: shared client code sends to the server through this one name. */
public final class ClientNet {
    private ClientNet() {}

    public static void sendToServer(CustomPacketPayload payload) {
        ModNetworking.sendToServer(payload);
    }
}"""
    else:
        body = """import com.kishku7.bankvault.net.BvPayload;

/** Client networking seam: shared client code sends to the server through this one name. */
public final class ClientNet {
    private ClientNet() {}

    public static void sendToServer(BvPayload payload) {
        ModNetworking.sendToServer(payload);
    }
}"""
    for ln in body.split("\n"):
        cog.outl(ln)
