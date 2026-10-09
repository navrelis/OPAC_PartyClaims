package xaero.pac.teamclaims.ftbsync;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import org.slf4j.Logger;
import xaero.pac.teamclaims.TeamClaimsCommon;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The listeners on FTB Teams' {@link TeamEvent}s. FTB Teams fires them on the server thread, in the middle of its
 * own code and without catching anything, so a listener here only takes a note for the sync (which works it off at
 * the end of the tick) and never lets an exception out.
 * <p>
 * Listened to: {@code CREATED}, {@code DELETED}, {@code PLAYER_CHANGED} (every join, leave and kick, also of
 * offline players, and a player's first login), {@code PLAYER_LEFT_PARTY} (for its "team deleted" flag),
 * {@code OWNERSHIP_TRANSFERRED} and {@code PROPERTIES_CHANGED} (only when the display name changed). Not listened
 * to: {@code LOADED}/{@code SAVED} (fired for every team all the time), {@code PLAYER_JOINED_PARTY} (online
 * players only, {@code PLAYER_CHANGED} covers it), the ally events (allies are not synced). Invitations, declining
 * one, promoting and demoting have no event; the periodic merge finds them.
 * <p>
 * The events are static, so the listeners are registered once per JVM and look up the sync of the current server
 * each time; without one (no server, sync off) they do nothing.
 */
final class FtbSyncEvents {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean registered;

    private FtbSyncEvents() {}

    static synchronized void register() {
        if (registered) return;
        registered = true;
        TeamEvent.CREATED.register(event -> note("CREATED", sync -> {
            if (event.getTeam().isPartyTeam()) sync.onFtbTeamChanged(event.getTeam().getId());
        }));
        TeamEvent.DELETED.register(event -> note("DELETED", sync -> {
            if (event.getTeam().isPartyTeam()) sync.onFtbTeamDeleted(event.getTeam().getId());
        }));
        TeamEvent.PLAYER_CHANGED.register(event -> note("PLAYER_CHANGED", sync -> {
            Team previous = event.getPreviousTeam().orElse(null);
            if (previous == null) sync.onFtbPlayerKnown(event.getPlayerId());
            else if (previous.isPartyTeam()) sync.onFtbTeamChanged(previous.getId());
            if (event.getTeam().isPartyTeam()) sync.onFtbTeamChanged(event.getTeam().getId());
        }));
        TeamEvent.PLAYER_LEFT_PARTY.register(event -> note("PLAYER_LEFT_PARTY", sync -> {
            if (event.getTeamDeleted()) sync.onFtbTeamDeleted(event.getTeam().getId());
        }));
        TeamEvent.OWNERSHIP_TRANSFERRED.register(event -> note("OWNERSHIP_TRANSFERRED", sync -> {
            if (event.getTeam().isPartyTeam()) sync.onFtbTeamChanged(event.getTeam().getId());
        }));
        TeamEvent.PROPERTIES_CHANGED.register(event -> note("PROPERTIES_CHANGED", sync -> {
            Team team = event.getTeam();
            // Fired for every property of every kind of team, also when nothing changed
            if (team.isPartyTeam() && !Objects.equals(team.getProperty(TeamProperties.DISPLAY_NAME),
                    event.getPreviousProperties().get(TeamProperties.DISPLAY_NAME)))
                sync.onFtbTeamChanged(team.getId());
        }));
    }

    private static void note(String eventName, Consumer<FtbTeamsSync> action) {
        try {
            FtbTeamsSync sync = TeamClaimsCommon.getFtbTeamsSync();
            if (sync != null) action.accept(sync);
        } catch (Exception | LinkageError e) {
            // Never into FTB Teams' own code. The periodic reconciliation makes up for the missed note.
            LOGGER.warn("[TeamClaims] FTB Teams sync could not take note of FTB Teams' {} event", eventName, e);
        }
    }
}
