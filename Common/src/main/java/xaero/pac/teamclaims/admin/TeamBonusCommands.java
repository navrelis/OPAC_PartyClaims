package xaero.pac.teamclaims.admin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.packet.teamclaims.AdminTeamData;
import xaero.pac.common.packet.teamclaims.ClientboundAdminResultPacket.Result;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.teamclaims.config.TeamConfig;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /teamclaims bonus <team>} shows the per-team bonus and the limits of a team, {@code /teamclaims bonus <team>
 * claims|forceloads <n>} sets the bonus team claims or team forceloads, the same as the admin menu does. {@code <team>}
 * is a team name (quoted if it has spaces) or a party id. Only for permission level 2 or the {@code xaero.pac_admin_menu}
 * permission ({@link AdminAccess#isAdmin(CommandSourceStack)}).
 */
public final class TeamBonusCommands {

    private static final String KEY = "gui.xaero_pac_team_claims_admin_team_";

    private TeamBonusCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> bonusNode() {
        return Commands.literal("bonus")
                .requires(AdminAccess::isAdmin)
                .then(Commands.argument("team", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(AdminTeams.list(context.getSource().getServer())
                                .stream().map(team -> StringArgumentType.escapeIfRequired(team.name())), builder))
                        .executes(TeamBonusCommands::executeShow)
                        .then(Commands.literal("claims")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, TeamConfig.MAX_BONUS))
                                        .executes(context -> executeSet(context, false))))
                        .then(Commands.literal("forceloads")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, TeamConfig.MAX_BONUS))
                                        .executes(context -> executeSet(context, true)))));
    }

    /** The one team {@code <team>} stands for, null after telling the source that there is none or several. */
    @Nullable
    private static AdminTeamData resolve(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String input = StringArgumentType.getString(context, "team");
        List<AdminTeamData> teams = AdminTeams.resolve(source.getServer(), input);
        if (teams.size() == 1) return teams.get(0);
        if (teams.isEmpty()) fail(source, KEY + "unknown", input);
        else fail(source, KEY + "ambiguous", input, teams.stream().map(team -> team.partyId().toString()).collect(Collectors.joining(", ")));
        return null;
    }

    private static int executeShow(CommandContext<CommandSourceStack> context) {
        AdminTeamData team = resolve(context);
        if (team == null) return 0;
        send(context.getSource(), ChatFormatting.GRAY, KEY + "bonus_show", team.name(), String.valueOf(team.bonusClaims()),
                String.valueOf(team.bonusForceloads()), team.claims() + " / " + team.claimLimit(),
                team.forceloads() + " / " + team.forceloadLimit());
        return 1;
    }

    private static int executeSet(CommandContext<CommandSourceStack> context, boolean forceloads) {
        AdminTeamData team = resolve(context);
        if (team == null) return 0;
        CommandSourceStack source = context.getSource();
        Result result = TeamClaimsAdminHandler.setTeamBonus(source.getServer(), team.partyId(), forceloads,
                IntegerArgumentType.getInteger(context, "value"), source.getTextName());
        if (!result.success()) {
            fail(source, result.messageKey(), result.args().toArray());
            return 0;
        }
        send(source, ChatFormatting.GREEN, result.messageKey(), result.args().toArray());
        return 1;
    }

    private static Component message(CommandSourceStack source, String key, Object... args) {
        IServerData<?, ?> serverData = ServerData.from(source.getServer());
        ServerPlayer player = source.getPlayer();
        return serverData == null ? Component.translatable(key, args) : serverData.getAdaptiveLocalizer().getFor(player, key, args);
    }

    private static void send(CommandSourceStack source, ChatFormatting style, String key, Object... args) {
        Component text = message(source, key, args).copy().withStyle(style);
        source.sendSuccess(() -> text, true);
    }

    private static void fail(CommandSourceStack source, String key, Object... args) {
        source.sendFailure(message(source, key, args).copy().withStyle(ChatFormatting.RED));
    }
}
