package xaero.pac.teamclaims;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.localization.AdaptiveLocalizer;
import xaero.pac.teamclaims.TeamClaimManager.BudgetInfo;
import xaero.pac.teamclaims.TeamClaimManager.ClaimPos;
import xaero.pac.teamclaims.TeamClaimManager.TeamBudget;
import xaero.pac.teamclaims.TeamClaimManager.TeamData;
import xaero.pac.teamclaims.config.TeamAction;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamConfigManager;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The read-only overview commands {@code /teamclaims info [player]} and {@code /teamclaims list [page]}. Nothing here
 * changes any state, and nothing is computed until a command is used. Every text is a localized
 * {@code gui.xaero_pac_team_claims_info_*}/{@code ..._list_*} message; the numbers and names in them are literal
 * arguments, so that their colours survive the server-side translation for players without the mod.
 */
public final class TeamClaimsOverview {

    public static final int LIST_PAGE_SIZE = 10;

    private static final String KEY = "gui.xaero_pac_team_claims_";
    private static final String UNLIMITED = "∞";

    private TeamClaimsOverview() {}

    // ==================== Command nodes ====================

    /** {@code info} for the caller's own team, and {@code info <player>} (permission level 2) for another player's. */
    static LiteralArgumentBuilder<CommandSourceStack> infoNode() {
        return Commands.literal("info")
                .executes(TeamClaimsOverview::executeInfo)
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .requires(source -> source.hasPermission(2))
                        .executes(TeamClaimsOverview::executeInfoOfPlayer));
    }

    static LiteralArgumentBuilder<CommandSourceStack> listNode() {
        return Commands.literal("list")
                .executes(context -> executeList(context, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> executeList(context, IntegerArgumentType.getInteger(context, "page"))));
    }

    private static int executeInfo(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = TeamClaimsCommands.requirePlayer(context.getSource());
        if (player == null) return 0;
        return showInfo(context.getSource(), player, player.getUUID(), null);
    }

    private static int executeInfoOfPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) {
            fail(source, source.getPlayer(), KEY + "info_player_too_many");
            return 0;
        }
        GameProfile profile = profiles.iterator().next();
        return showInfo(source, source.getPlayer(), profile.getId(), profile.getName());
    }

    private static int executeList(CommandContext<CommandSourceStack> context, int page) {
        ServerPlayer player = TeamClaimsCommands.requirePlayer(context.getSource());
        if (player == null) return 0;
        return showList(context.getSource(), player, player.getUUID(), page);
    }

    // ==================== /teamclaims info ====================

    /**
     * Sends the team overview of the party of {@code subjectId} to {@code source}: name and owner, the team's own
     * budget (team claims and team forceloads against the team limits), the member count and what the next member
     * would add, a pending over-limit deadline, the forceload state, the team roles, and one line per member with
     * their private budget and how many of the team claims they technically own. The numbers are the ones
     * {@link TeamClaimManager#getBudgetInfo} returns, which are the ones that are enforced.
     *
     * @param viewer      the player running the command, null when there is none (the texts then use the server's
     *                    default translation)
     * @param subjectName the name of the player whose team it is, null if that is the viewer
     * @return 1 on success, 0 if the player is in no team
     */
    public static int showInfo(CommandSourceStack source, @Nullable ServerPlayer viewer, UUID subjectId,
            @Nullable String subjectName) {
        TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
        TeamConfigManager configManager = TeamClaimsCommon.getTeamConfigManager();
        MinecraftServer server = source.getServer();
        IServerData<?, ?> serverData = ServerData.from(server);
        if (claimManager == null || configManager == null || serverData == null) return 0;
        AdaptiveLocalizer localizer = serverData.getAdaptiveLocalizer();
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(subjectId);
        TeamConfig teamConfig = party == null ? null : configManager.getTeamConfig(party.getId());
        TeamBudget team = teamConfig == null ? null : claimManager.getTeamBudget(party.getId());
        if (team == null) {
            if (subjectName == null) fail(source, localizer, viewer, KEY + "info_no_team");
            else fail(source, localizer, viewer, KEY + "info_player_no_team", subjectName);
            return 0;
        }

        UUID partyId = party.getId();
        String teamName = teamConfig.getTeamName() == null || teamConfig.getTeamName().isBlank()
                ? configManager.resolvePartyName(party) : teamConfig.getTeamName();
        IPartyMemberAPI owner = party.getOwner();

        send(source, localizer, viewer, KEY + "info_header", ChatFormatting.GRAY,
                value(teamName, ChatFormatting.GOLD), value(owner == null ? "?" : owner.getUsername(), ChatFormatting.GOLD));
        boolean teamFull = team.claims() >= team.claimLimit() || team.forceloads() >= team.forceloadLimit();
        send(source, localizer, viewer, KEY + "info_team_budget", teamFull ? ChatFormatting.RED : ChatFormatting.GRAY,
                value(formatCount(team.claims(), team.claimLimit()), ChatFormatting.WHITE),
                value(formatCount(team.forceloads(), team.forceloadLimit()), ChatFormatting.WHITE));
        if (team.memberCount() < team.minMembers())
            send(source, localizer, viewer, KEY + "info_members_too_few", ChatFormatting.YELLOW,
                    value(team.memberCount(), ChatFormatting.WHITE), value(team.minMembers(), ChatFormatting.WHITE));
        send(source, localizer, viewer, KEY + "info_members", ChatFormatting.GRAY,
                value(team.memberCount(), ChatFormatting.WHITE), value(team.nextMemberClaims(), ChatFormatting.WHITE),
                value(team.nextMemberForceloads(), ChatFormatting.WHITE));
        if (team.claimDeadline() != 0 && team.claimsOverLimit() > 0) {
            long left = claimManager.millisUntil(team.claimDeadline());
            send(source, localizer, viewer, KEY + "info_over_limit_claims", ChatFormatting.RED,
                    value(team.claimsOverLimit(), ChatFormatting.WHITE), TeamClaimManager.durationOf(left));
        }
        if (team.forceloadDeadline() != 0 && team.forceloadsOverLimit() > 0) {
            long left = claimManager.millisUntil(team.forceloadDeadline());
            send(source, localizer, viewer, KEY + "info_over_limit_forceloads", ChatFormatting.RED,
                    value(team.forceloadsOverLimit(), ChatFormatting.WHITE), TeamClaimManager.durationOf(left));
        }
        if (team.forceloads() > 0) {
            switch (claimManager.getForceloadActivity(partyId)) {
                case ACTIVE -> send(source, localizer, viewer, KEY + "info_forceload_active", ChatFormatting.GREEN);
                case GRACE -> {
                    int minutes = Math.max(1, (claimManager.getGraceTicksLeft(partyId) + 1199) / 1200);
                    send(source, localizer, viewer, KEY + "info_forceload_grace", ChatFormatting.YELLOW, value(minutes, ChatFormatting.WHITE));
                }
                case INACTIVE -> send(source, localizer, viewer, KEY + "info_forceload_inactive", ChatFormatting.GRAY);
            }
        }
        send(source, localizer, viewer, KEY + "info_roles", ChatFormatting.GRAY,
                teamConfig.getRequiredRole(TeamAction.CLAIM).displayName(),
                teamConfig.getRequiredRole(TeamAction.UNCLAIM).displayName(),
                teamConfig.getRequiredRole(TeamAction.FORCELOAD).displayName());

        // One line per member: their private budget, which is theirs alone, and their share of the team claims
        for (IPartyMemberAPI member : sortedMembers(party)) {
            BudgetInfo budget = claimManager.getBudgetInfo(member.getUUID());
            boolean atLimit = budget.privateClaims() >= budget.privateClaimLimit()
                    || budget.privateForceloads() >= budget.privateForceloadLimit();
            boolean online = server.getPlayerList().getPlayer(member.getUUID()) != null;
            ChatFormatting nameColor = member.isOwner() ? ChatFormatting.GOLD : member.getRank().getColor();
            send(source, localizer, viewer, KEY + "info_member", atLimit ? ChatFormatting.RED : ChatFormatting.GRAY,
                    value(member.getUsername(), nameColor), rankName(member),
                    value(online ? "●" : "○", online ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
                    value(formatCount(budget.privateClaims(), budget.privateClaimLimit()), null),
                    value(formatCount(budget.privateForceloads(), budget.privateForceloadLimit()), null),
                    value(budget.ownedTeamClaims(), null));
        }
        return 1;
    }

    /** {@code count / limit}, the way OPAC's own claim count shows the limit. */
    private static String formatCount(int count, int limit) {
        return count + " / " + (limit == Integer.MAX_VALUE ? UNLIMITED : limit);
    }

    /** The owner first, then by rank from the highest, alphabetical within a rank. */
    private static List<IPartyMemberAPI> sortedMembers(IServerPartyAPI party) {
        return party.getMemberInfoStream()
                .sorted(Comparator.<IPartyMemberAPI>comparingInt(m -> m.isOwner() ? -1 : PartyMemberRank.values().length - m.getRank().ordinal())
                        .thenComparing(m -> m.getUsername().toLowerCase(Locale.ROOT))
                        .thenComparing(IPartyMemberAPI::getUsername))
                .toList();
    }

    private static Component rankName(IPartyMemberAPI member) {
        String rank = member.isOwner() ? "owner" : member.getRank().name().toLowerCase(Locale.ROOT);
        return Component.translatable(KEY + "info_rank_" + rank);
    }

    // ==================== /teamclaims list ====================

    /**
     * Sends one page of the team claims of the party of {@code subjectId}, {@link #LIST_PAGE_SIZE} per page sorted by
     * dimension, then x, then z, with a footer that turns the pages.
     *
     * @return 1 on success, 0 if the player is in no team or the page does not exist
     */
    public static int showList(CommandSourceStack source, @Nullable ServerPlayer viewer, UUID subjectId, int page) {
        TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
        TeamConfigManager configManager = TeamClaimsCommon.getTeamConfigManager();
        MinecraftServer server = source.getServer();
        IServerData<?, ?> serverData = ServerData.from(server);
        if (claimManager == null || configManager == null || serverData == null) return 0;
        AdaptiveLocalizer localizer = serverData.getAdaptiveLocalizer();
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(subjectId);
        TeamConfig teamConfig = party == null ? null : configManager.getTeamConfig(party.getId());
        if (teamConfig == null) {
            fail(source, localizer, viewer, KEY + "info_no_team");
            return 0;
        }

        TeamData teamData = claimManager.getTeamData(party.getId());
        if (teamData == null || teamData.getClaimCount() == 0) {
            send(source, localizer, viewer, KEY + "list_empty", ChatFormatting.YELLOW, value(teamConfig.getSubConfigId(), ChatFormatting.WHITE));
            return 1;
        }
        List<ClaimPos> claims = teamData.getTrackedClaims().stream()
                .sorted(Comparator.<ClaimPos, String>comparing(pos -> pos.dimension.toString())
                        .thenComparingInt(pos -> pos.x).thenComparingInt(pos -> pos.z))
                .toList();
        int pages = (claims.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE;
        if (page < 1 || page > pages) {
            fail(source, localizer, viewer, KEY + "list_page_invalid", page, pages);
            return 0;
        }

        String teamName = teamConfig.getTeamName() == null || teamConfig.getTeamName().isBlank()
                ? configManager.resolvePartyName(party) : teamConfig.getTeamName();
        send(source, localizer, viewer, KEY + "list_header", ChatFormatting.GRAY,
                value(teamName, ChatFormatting.GOLD), value(claims.size(), ChatFormatting.WHITE));
        for (ClaimPos pos : claims.subList((page - 1) * LIST_PAGE_SIZE, Math.min(claims.size(), page * LIST_PAGE_SIZE))) {
            UUID ownerId = teamData.getOwner(pos);
            IPartyMemberAPI ownerInfo = ownerId == null ? null : party.getMemberInfo(ownerId);
            String ownerName = ownerInfo != null ? ownerInfo.getUsername() : String.valueOf(ownerId);
            send(source, localizer, viewer, KEY + (teamData.isForceloaded(pos) ? "list_entry_forceloaded" : "list_entry"),
                    ChatFormatting.GRAY, value(shortDimension(pos.dimension), ChatFormatting.WHITE),
                    value(pos.x, ChatFormatting.WHITE), value(pos.z, ChatFormatting.WHITE),
                    value(pos.x * 16 + 8, ChatFormatting.WHITE), value(pos.z * 16 + 8, ChatFormatting.WHITE),
                    value(ownerName, ChatFormatting.AQUA));
        }

        MutableComponent footer = localizer.getFor(viewer, KEY + "list_footer", value(page, ChatFormatting.WHITE),
                value(pages, ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY);
        footer.append(" ").append(pageButton("[<]", page > 1 ? page - 1 : 0)).append(" ").append(pageButton("[>]", page < pages ? page + 1 : 0));
        source.sendSuccess(() -> footer, false);
        return 1;
    }

    /** A clickable {@code /teamclaims list <page>} button, greyed out and inert when there is no such page (0). */
    private static Component pageButton(String label, int targetPage) {
        if (targetPage == 0) return Component.literal(label).withStyle(ChatFormatting.DARK_GRAY);
        return Component.literal(label).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teamclaims list " + targetPage)));
    }

    /** {@code overworld} for {@code minecraft:overworld}, other namespaces stay as they are. */
    public static String shortDimension(ResourceLocation dimension) {
        return "minecraft".equals(dimension.getNamespace()) ? dimension.getPath() : dimension.toString();
    }

    // ==================== Messages ====================

    /** A literal message argument, coloured with {@code color} unless that is null. */
    private static Component value(Object value, @Nullable ChatFormatting color) {
        MutableComponent component = Component.literal(String.valueOf(value));
        return color == null ? component : component.withStyle(color);
    }

    private static void send(CommandSourceStack source, AdaptiveLocalizer localizer, @Nullable ServerPlayer viewer,
            String key, ChatFormatting style, Object... args) {
        source.sendSuccess(() -> localizer.getFor(viewer, key, args).withStyle(style), false);
    }

    private static void fail(CommandSourceStack source, AdaptiveLocalizer localizer, @Nullable ServerPlayer viewer,
            String key, Object... args) {
        source.sendFailure(localizer.getFor(viewer, key, args).withStyle(ChatFormatting.RED));
    }

    private static void fail(CommandSourceStack source, @Nullable ServerPlayer viewer, String key) {
        IServerData<?, ?> serverData = ServerData.from(source.getServer());
        if (serverData != null) fail(source, serverData.getAdaptiveLocalizer(), viewer, key);
    }
}
