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

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class TeamClaimsCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        // Same requirements as OPAC's "/<parties> create": parties enabled, the caller (or the player they
        // impersonate) not in a party yet
        dispatcher.register(
                Commands.literal("teamclaims")
                        .requires(c -> ServerConfig.CONFIG.partiesEnabled.get())
                        .then(Commands.literal("create")
                                .requires(new CommandRequirementProvider().getNonMemberRequirement(p -> true, false))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(TeamClaimsCommands::executeCreate)))
        );
        LOGGER.info("Registered /teamclaims commands");
    }

    private static int executeCreate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        GameProfile ownerProfile = ((ServerPlayerData) ServerPlayerData.from(player)).getPartiesImpersonatedPlayerProfile();
        if (ownerProfile == null) ownerProfile = player.getGameProfile();
        return createPartyWithTeamName(source, player, ownerProfile, StringArgumentType.getString(context, "name"), true);
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
                fail(source, localizer, player, validation.errorKey());
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

    private static void fail(CommandSourceStack source, AdaptiveLocalizer localizer, @Nullable ServerPlayer player, String key) {
        source.sendFailure(localizer.getFor(player, key).withStyle(ChatFormatting.RED));
    }

    private static void succeed(CommandSourceStack source, AdaptiveLocalizer localizer, @Nullable ServerPlayer player,
            String key, @Nullable ChatFormatting style, Object... args) {
        Supplier<Component> message = style == null ? () -> localizer.getFor(player, key, args)
                : () -> localizer.getFor(player, key, args).withStyle(style);
        source.sendSuccess(message, false);
    }
}
