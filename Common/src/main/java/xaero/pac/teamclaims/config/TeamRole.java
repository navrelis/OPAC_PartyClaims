package xaero.pac.teamclaims.config;

import net.minecraft.network.chat.Component;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The levels a team can require for a {@link TeamAction}, from the lowest to the highest: OPAC's party ranks
 * ({@link PartyMemberRank}) plus the party owner on top. A player's level is their rank, or {@link #OWNER} for the
 * party owner, whatever rank OPAC gives them.
 */
public enum TeamRole {
    MEMBER, CLAIMER, MODERATOR, ADMIN, OWNER;

    /** What every action requires unless the team changed it: everybody may, which is the behaviour without roles. */
    public static final TeamRole DEFAULT = MEMBER;

    private final String id = name().toLowerCase(Locale.ROOT);

    /** The lowercase name used by {@code /teamclaims roles}. */
    public String id() { return id; }

    public boolean isAtLeast(TeamRole required) { return ordinal() >= required.ordinal(); }

    /** The localized name, the same one {@code /teamclaims info} shows for a member's rank. */
    public Component displayName() { return Component.translatable("gui.xaero_pac_team_claims_info_rank_" + id); }

    /** The level of a party member. */
    public static TeamRole of(IPartyMemberAPI member) {
        if (member.isOwner()) return OWNER;
        PartyMemberRank rank = member.getRank();
        if (rank == null) return MEMBER;
        return switch (rank) {
            case MEMBER -> MEMBER;
            case CLAIMER -> CLAIMER;
            case MODERATOR -> MODERATOR;
            case ADMIN -> ADMIN;
        };
    }

    /** The level by its name in any case ({@code MEMBER} as stored, {@code member} as typed), null if unknown. */
    @Nullable
    public static TeamRole byName(@Nullable String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
