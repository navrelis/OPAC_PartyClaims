package xaero.pac.teamclaims;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.PlayerConfigOptions;

public class TeamClaimsCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
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
        String rawTeamName = StringArgumentType.getString(context, "name");
        // Sanitize formatting codes
        final String teamName = rawTeamName.replaceAll("[\u00A7&].", "");
        if (teamName.isBlank()) {
            source.sendFailure(Component.literal("\u00A7cTeam name cannot be empty."));
            return 0;
        }
        if (teamName.length() > 24) {
            source.sendFailure(Component.literal("\u00A7cTeam name is too long (max 24 characters)."));
            return 0;
        }
        try {
            IPartyManagerAPI partyManager = OpenPACServerAPI.get(player.server).getPartyManager();
            IServerPartyAPI existingParty = partyManager.getPartyByMember(player.getUUID());
            if (existingParty != null) {
                source.sendFailure(Component.literal("\u00A7cYou are already in a party! Leave your current party first."));
                return 0;
            }
            IServerPartyAPI newParty = partyManager.createPartyForOwner(player);
            if (newParty == null) {
                source.sendFailure(Component.literal("\u00A7cFailed to create party."));
                return 0;
            }
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(player.server).getPlayerConfigs();
            IPlayerConfigAPI ownerConfig = configManager.getLoadedConfig(player.getUUID());
            ownerConfig.tryToSet(PlayerConfigOptions.PARTY_NAME, teamName);
            if (TeamClaimsInit.getTeamConfigManager() != null) {
                TeamClaimsInit.getTeamConfigManager().createTeamConfig(newParty);
            }
            source.sendSuccess(() -> Component.literal("\u00A7aParty '\u00A7f" + teamName + "\u00A7a' created successfully!"), false);
            source.sendSuccess(() -> Component.literal("\u00A77A team sub-config has been created. Switch to it in the OPAC config to make team claims."), false);
            LOGGER.info("Player {} created party '{}' ({})", player.getName().getString(), teamName, newParty.getId());
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error executing /teamclaims create", e);
            source.sendFailure(Component.literal("\u00A7cAn error occurred creating the party."));
            return 0;
        }
    }
}
