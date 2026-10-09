package xaero.pac.teamclaims;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.command.CommandRequirementProvider;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.common.server.player.data.ServerPlayerData;
import xaero.pac.common.server.player.localization.AdaptiveLocalizer;
import xaero.pac.teamclaims.config.TeamConfigManager;
import xaero.pac.teamclaims.ftbsync.FtbSyncCommands;

import javax.annotation.Nullable;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TeamClaimsCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        // Registered even when Team Claims is disabled (the server config isn't loaded yet at this point), but then not
        // usable: the requirement is checked when the command is used or listed. Every subcommand needs Team Claims to be
        // active; "create" has the same further requirements as OPAC's "/<parties> create" (parties enabled, the caller,
        // or the player they impersonate, not in a party yet), "territorymessages" is for everybody, "info" and "list" are
        // read-only overviews of the caller's team ("info <player>" of any player's, for permission level 2), "roles"
        // shows the team roles to any member and lets the party owner and admins change them, "convert" turns the
        // caller's own claims around them into team claims or back, "ftbsync" (permission level 2) reports on and
        // triggers the optional sync with FTB Teams.
        Predicate<CommandSourceStack> nonMemberRequirement = new CommandRequirementProvider().getNonMemberRequirement(p -> true, false);
        dispatcher.register(
                Commands.literal("teamclaims")
                        .requires(c -> TeamClaimsCommon.isActive())
                        .then(Commands.literal("create")
                                .requires(c -> ServerConfig.CONFIG.partiesEnabled.get() && nonMemberRequirement.test(c))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(TeamClaimsCommands::executeCreate)))
                        .then(Commands.literal("territorymessages")
                                .executes(context -> executeTerritoryMessages(context, null))
                                .then(Commands.literal("on")
                                        .executes(context -> executeTerritoryMessages(context, true)))
                                .then(Commands.literal("off")
                                        .executes(context -> executeTerritoryMessages(context, false))))
                        .then(TeamClaimsOverview.infoNode())
                        .then(TeamClaimsOverview.listNode())
                        .then(TeamRoles.rolesNode())
                        .then(TeamClaimsConvert.convertNode())
                        .then(FtbSyncCommands.ftbSyncNode())
        );
        LOGGER.info("Registered /teamclaims commands");
    }

    /**
     * The player running the command, or null after telling the source why that is not possible: Team Claims is no
     * longer active (e.g. a stale command tree after the server stopped) or the source is not a player.
     */
    @Nullable
    static ServerPlayer requirePlayer(CommandSourceStack source) {
        IServerData<?, ?> serverData = ServerData.from(source.getServer());
        ServerPlayer player = source.getPlayer();
        if (!TeamClaimsCommon.isActive()) {
            // Without the server data there is no localizer, the client then translates the key itself
            source.sendFailure((serverData == null ? Component.translatable("gui.xaero_pac_team_claims_disabled")
                    : serverData.getAdaptiveLocalizer().getFor(player, "gui.xaero_pac_team_claims_disabled"))
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        if (player == null) {
            source.sendFailure((serverData == null ? Component.translatable("gui.xaero_pac_team_claims_player_only")
                    : serverData.getAdaptiveLocalizer().getFor(null, "gui.xaero_pac_team_claims_player_only"))
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        return player;
    }

    private static int executeCreate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;
        GameProfile ownerProfile = ((ServerPlayerData) ServerPlayerData.from(player)).getPartiesImpersonatedPlayerProfile();
        if (ownerProfile == null) ownerProfile = player.getGameProfile();
        return createPartyWithTeamName(source, player, ownerProfile, StringArgumentType.getString(context, "name"), true);
    }

    /**
     * {@code /teamclaims territorymessages [on|off]}: sets whether the player sees the claim welcome messages (all of
     * them, not only the team ones), or just reports the current state when {@code enable} is null.
     */
    private static int executeTerritoryMessages(CommandContext<CommandSourceStack> context, @Nullable Boolean enable) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;
        TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
        IServerData<?, ?> serverData = ServerData.from(source.getServer());
        if (claimManager == null || serverData == null) return 0;//not both null while Team Claims is active
        if (enable != null) claimManager.setTerritoryMessagesEnabled(player.getUUID(), enable);
        boolean enabled = claimManager.areTerritoryMessagesEnabled(player.getUUID());
        String key = "gui.xaero_pac_team_claims_territory_messages_" + (enable == null ? "status_" : "") + (enabled ? "on" : "off");
        succeed(source, serverData.getAdaptiveLocalizer(), player, key, enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        return 1;
    }

    /**
     * The shared create path of {@code /teamclaims create <name>} and {@code /<parties> create <teamname>}: validates
     * the name ({@link TeamNames#validate}), creates the party for {@code ownerProfile} (the executing player or
     * whoever they impersonate, who may be offline), names it, sets up the team config and every member's team
     * sub-config right away, and reports to {@code source}. Nothing is created when the name is rejected.
     *
     * @param player  the executing player, null when there is none (messages then go to the source only)
     * @param teamCommandMessages  whether to add the Team Claims specific success lines of {@code /teamclaims create}
     * @return 1 on success, 0 on failure
     */
    public static int createPartyWithTeamName(CommandSourceStack source, @Nullable ServerPlayer player,
            GameProfile ownerProfile, String rawTeamName, boolean teamCommandMessages) {
        MinecraftServer server = source.getServer();
        IServerData<?, ?> serverData = ServerData.from(server);
        if (serverData == null) {
            source.sendFailure(Component.literal("Open Parties and Claims is not ready yet."));
            return 0;
        }
        AdaptiveLocalizer localizer = serverData.getAdaptiveLocalizer();
        try {
            IPartyManagerAPI partyManager = serverData.getPartyManager();
            if (!ServerConfig.CONFIG.partiesEnabled.get()) {
                fail(source, localizer, player, "gui.xaero_pac_team_claims_create_failed");
                return 0;
            }
            if (partyManager.getPartyByMember(ownerProfile.getId()) != null) {
                fail(source, localizer, player, "gui.xaero_pac_team_claims_create_already_in_party");
                return 0;
            }
            IPlayerConfigManagerAPI configManager = serverData.getPlayerConfigManager();
            IPlayerConfigAPI ownerConfig = configManager.getLoadedConfig(ownerProfile.getId());
            TeamNames.Result validation = TeamNames.validate(rawTeamName, ownerConfig);
            if (!validation.isValid()) {
                fail(source, localizer, player, validation.errorKey(), validation.errorArgs());
                return 0;
            }
            String teamName = validation.name();
            // The name would silently not be applied if the server doesn't let players configure it
            if (!ownerConfig.isOptionAllowed(PlayerConfigOptions.PARTY_NAME)
                    || ownerConfig instanceof xaero.pac.common.server.player.config.IPlayerConfig internalConfig
                    && internalConfig.isOptionDefaulted(PlayerConfigOptions.PARTY_NAME)) {
                fail(source, localizer, player, "gui.xaero_pac_team_claims_create_name_not_configurable");
                return 0;
            }

            IServerPartyAPI newParty = partyManager.createPartyForOwner(ownerProfile);
            if (newParty == null) {
                fail(source, localizer, player, "gui.xaero_pac_team_claims_create_failed");
                return 0;
            }
            IPlayerConfigAPI.SetResult nameResult = ownerConfig.tryToSet(PlayerConfigOptions.PARTY_NAME, teamName);
            if (nameResult != IPlayerConfigAPI.SetResult.SUCCESS) {
                // Not expected after the checks above; don't leave an unnamed party behind
                LOGGER.warn("[TeamClaims] Could not name the new party of {} '{}' ({}), removing it again",
                        ownerProfile.getName(), teamName, nameResult);
                partyManager.removePartyById(newParty.getId());
                fail(source, localizer, player, "gui.xaero_pac_team_claims_create_failed");
                return 0;
            }
            // Team config and every member's team sub-config right away (the party events would only get
            // to it at the end of the tick)
            TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
            if (tcm != null) tcm.createTeamConfig(newParty);

            succeed(source, localizer, player, "gui.xaero_parties_party_created", null);
            if (teamCommandMessages) {
                succeed(source, localizer, player, "gui.xaero_pac_team_claims_create_success", ChatFormatting.GREEN, teamName);
                succeed(source, localizer, player, "gui.xaero_pac_team_claims_create_sub_config_hint", ChatFormatting.GRAY);
            }
            if (player != null)
                serverData.getPlayerPermissionChangeHandler().sendCommandsAndUpdatePermissions(player, serverData, false);
            LOGGER.info("Player {} created party '{}' ({}) for {}", source.getTextName(), teamName, newParty.getId(),
                    ownerProfile.getName());
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error creating the party '{}' for {}", rawTeamName, ownerProfile.getName(), e);
            fail(source, localizer, player, "gui.xaero_pac_team_claims_create_error");
            return 0;
        }
    }

    private static void fail(CommandSourceStack source, AdaptiveLocalizer localizer, @Nullable ServerPlayer player, String key,
            Object... args) {
        source.sendFailure(localizer.getFor(player, key, args).withStyle(ChatFormatting.RED));
    }

    private static void succeed(CommandSourceStack source, AdaptiveLocalizer localizer, @Nullable ServerPlayer player,
            String key, @Nullable ChatFormatting style, Object... args) {
        Supplier<Component> message = style == null ? () -> localizer.getFor(player, key, args)
                : () -> localizer.getFor(player, key, args).withStyle(style);
        source.sendSuccess(message, false);
    }
}
