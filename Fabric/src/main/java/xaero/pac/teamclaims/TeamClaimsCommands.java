package xaero.pac.teamclaims;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.common.server.player.localization.api.IAdaptiveLocalizerAPI;

public class TeamClaimsCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(
                Commands.literal("teamclaims")
                        .then(Commands.literal("create")
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
        IAdaptiveLocalizerAPI localizer = OpenPACServerAPI.get(player.server).getAdaptiveTextLocalizer();
        String rawTeamName = StringArgumentType.getString(context, "name");
        // Sanitize formatting codes
        final String teamName = rawTeamName.replaceAll("[" + ChatFormatting.PREFIX_CODE + "&].", "");
        if (teamName.isBlank()) {
            source.sendFailure(localizer.getFor(player, "gui.xaero_pac_team_claims_create_name_empty").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (teamName.length() > 24) {
            source.sendFailure(localizer.getFor(player, "gui.xaero_pac_team_claims_create_name_too_long").withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            IPartyManagerAPI partyManager = OpenPACServerAPI.get(player.server).getPartyManager();
            IServerPartyAPI existingParty = partyManager.getPartyByMember(player.getUUID());
            if (existingParty != null) {
                source.sendFailure(localizer.getFor(player, "gui.xaero_pac_team_claims_create_already_in_party").withStyle(ChatFormatting.RED));
                return 0;
            }
            IServerPartyAPI newParty = partyManager.createPartyForOwner(player);
            if (newParty == null) {
                source.sendFailure(localizer.getFor(player, "gui.xaero_pac_team_claims_create_failed").withStyle(ChatFormatting.RED));
                return 0;
            }
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(player.server).getPlayerConfigManager();
            IPlayerConfigAPI ownerConfig = configManager.getLoadedConfig(player.getUUID());
            ownerConfig.tryToSet(PlayerConfigOptions.PARTY_NAME, teamName);
            if (TeamClaimsInit.getTeamConfigManager() != null) {
                TeamClaimsInit.getTeamConfigManager().createTeamConfig(newParty);
            }
            source.sendSuccess(() -> localizer.getFor(player, "gui.xaero_pac_team_claims_create_success", teamName)
                    .withStyle(ChatFormatting.GREEN), false);
            source.sendSuccess(() -> localizer.getFor(player, "gui.xaero_pac_team_claims_create_sub_config_hint")
                    .withStyle(ChatFormatting.GRAY), false);
            LOGGER.info("Player {} created party '{}' ({})", player.getName().getString(), teamName, newParty.getId());
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error executing /teamclaims create", e);
            source.sendFailure(localizer.getFor(player, "gui.xaero_pac_team_claims_create_error").withStyle(ChatFormatting.RED));
            return 0;
        }
    }
}
